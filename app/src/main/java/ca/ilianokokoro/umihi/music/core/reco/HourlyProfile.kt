package ca.ilianokokoro.umihi.music.core.reco

import ca.ilianokokoro.umihi.music.core.playback.PlayEndReason
import ca.ilianokokoro.umihi.music.models.PlayEvent

/** Coarse time-of-day buckets for the hourly listening profile. */
enum class DayBucket {
    /** 06:00 - 11:59. */
    MORNING,

    /** 12:00 - 17:59. */
    DAY,

    /** 18:00 - 22:59. */
    EVENING,

    /** 23:00 - 05:59. */
    NIGHT,
}

/** How many artists each bucket keeps in the hourly distribution. */
const val HOURLY_TOP_ARTISTS = 5

/** Maps a local hour (0-23) to its [DayBucket]; out-of-range hours clamp into NIGHT. */
fun dayBucket(hourOfDay: Int): DayBucket = when (hourOfDay) {
    in 6..11 -> DayBucket.MORNING
    in 12..17 -> DayBucket.DAY
    in 18..22 -> DayBucket.EVENING
    else -> DayBucket.NIGHT
}

/**
 * Top artists per time-of-day bucket, most played first (ties: stable by name). Early skips are
 * excluded — a rejected track says nothing about what the listener wants at that hour. Artists
 * are grouped by [artistKey] but reported under their display name (first raw spelling seen), and
 * tracks with no known artist are skipped. Only buckets with at least one play appear in the map.
 */
fun hourlyArtistDistribution(
    events: List<PlayEvent>,
    artistByYoutubeId: Map<String, String>,
    topPerBucket: Int = HOURLY_TOP_ARTISTS,
): Map<DayBucket, List<String>> {
    // bucket -> artistKey -> (display name, play count)
    val counts = HashMap<DayBucket, HashMap<String, Pair<String, Int>>>()

    for (event in events) {
        if (event.endReason == PlayEndReason.SKIPPED_EARLY.name) continue
        val rawArtist = artistByYoutubeId[event.youtubeId]?.trim().orEmpty()
        if (rawArtist.isEmpty()) continue

        val bucket = dayBucket(event.hourOfDay)
        val perArtist = counts.getOrPut(bucket) { HashMap() }
        val key = artistKey(rawArtist)
        val (display, count) = perArtist[key] ?: (rawArtist to 0)
        perArtist[key] = display to (count + 1)
    }

    return counts.mapValues { (_, perArtist) ->
        perArtist.values
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(topPerBucket.coerceAtLeast(0))
            .map { it.first }
    }
}
