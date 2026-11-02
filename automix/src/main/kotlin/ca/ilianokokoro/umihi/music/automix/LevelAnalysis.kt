/*
 * Copyright (C) 2026 Jelyvo contributors
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */
package ca.ilianokokoro.umihi.music.automix

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * What a track's loudness alone can say: where its sound starts and ends, and whether it trails off
 * quietly. It claims no tempo, so the planner never tries to match beats on its evidence.
 */
object LevelAnalysis {
    const val FRAME_SECONDS = 0.1

    private const val SILENCE_DB = -50.0
    /** A tail this far under the track's typical loudness has stopped carrying the song. */
    private const val QUIET_TAIL_BELOW_TYPICAL_DB = 14.0
    private const val MIN_QUIET_TAIL_SECONDS = 3.0
    private const val MAX_QUIET_TAIL_SECONDS = 30.0
    private const val SMOOTHING_FRAMES = 10
    private const val ENERGY_CURVE_POINTS = 240

    /** @param levels one RMS level per [FRAME_SECONDS], as a [LevelMeter] gives them. */
    fun analyze(trackId: String, levels: FloatArray): TrackAnalysis {
        val duration = levels.size * FRAME_SECONDS
        val db = DoubleArray(levels.size) { decibels(levels[it].toDouble()) }
        val first = db.indexOfFirst { it > SILENCE_DB }
        val last = db.indexOfLast { it > SILENCE_DB }
        if (first == -1) {
            return TrackAnalysis(
                status = TrackAnalysis.STATUS_READY,
                trackId = trackId,
                duration = duration,
            )
        }

        val quietTailStart = quietTailStart(db, first, last)
        return TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            trackId = trackId,
            duration = duration,
            audibleStartTime = first * FRAME_SECONDS,
            contentEndTime = (last + 1) * FRAME_SECONDS,
            outroStartTime = quietTailStart ?: 0.0,
            mixOutTime = quietTailStart ?: 0.0,
            energyCurve = energyCurve(levels),
        )
    }

    private fun decibels(rms: Double): Double = if (rms <= 0.0) -120.0 else 20 * log10(rms)

    /** Where a quiet ending starts, or null for a track that plays at full level to its end. */
    private fun quietTailStart(db: DoubleArray, first: Int, last: Int): Double? {
        val audible = db.slice(first..last).filter { it > SILENCE_DB }.sorted()
        val threshold = audible[audible.size / 2] - QUIET_TAIL_BELOW_TYPICAL_DB
        var start = last + 1
        while (start > first && smoothed(db, start - 1) < threshold) start--
        val tailSeconds = (last + 1 - start) * FRAME_SECONDS
        return (start * FRAME_SECONDS).takeIf {
            start > first && tailSeconds in MIN_QUIET_TAIL_SECONDS..MAX_QUIET_TAIL_SECONDS
        }
    }

    /** Levels over about a second, so one quiet beat does not read as the song ending. */
    private fun smoothed(db: DoubleArray, index: Int): Double {
        val from = max(0, index - SMOOTHING_FRAMES / 2)
        val to = min(db.size - 1, index + SMOOTHING_FRAMES / 2)
        var sum = 0.0
        for (i in from..to) sum += db[i]
        return sum / (to - from + 1)
    }

    private fun energyCurve(levels: FloatArray): List<EnergySample> {
        val step = max(1, levels.size / ENERGY_CURVE_POINTS)
        return (levels.indices step step).map { start ->
            val end = min(levels.size, start + step)
            var peak = 0f
            for (i in start until end) peak = max(peak, levels[i])
            EnergySample(time = start * FRAME_SECONDS, energy = levelFromRms(peak.toDouble()))
        }
    }
}
