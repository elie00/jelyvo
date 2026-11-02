/*
 * Copyright (C) 2026 Jelyvo contributors
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */
package ca.ilianokokoro.umihi.music.automix

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Turns interleaved audio into one RMS level per [LevelAnalysis.FRAME_SECONDS], across all channels.
 * Holds only those levels, so a whole track fits in a few kilobytes.
 */
class LevelMeter(sampleRate: Int, private val channels: Int) {
    private val samplesPerFrame =
        max(1, (sampleRate * LevelAnalysis.FRAME_SECONDS).toInt()) * channels
    private var levels = FloatArray(INITIAL_FRAMES)
    private var frames = 0
    private var sum = 0.0
    private var count = 0

    /** Takes one sample, in the range -1 to 1. */
    fun add(sample: Float) {
        sum += sample * sample
        if (++count == samplesPerFrame) closeFrame()
    }

    /** The levels so far. An unfinished last frame counts, because it is still sound. */
    fun finish(): FloatArray {
        if (count > 0) closeFrame()
        return levels.copyOf(frames)
    }

    private fun closeFrame() {
        if (frames == levels.size) levels = levels.copyOf(levels.size * 2)
        levels[frames++] = sqrt(sum / count).toFloat()
        sum = 0.0
        count = 0
    }

    private companion object {
        const val INITIAL_FRAMES = 3_000
    }
}
