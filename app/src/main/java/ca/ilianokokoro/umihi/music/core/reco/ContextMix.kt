package ca.ilianokokoro.umihi.music.core.reco

/**
 * "Morning mix" / "Evening mix": the daily-mix pipeline re-run with context-heavy weights
 * ([RecoWeights.CONTEXT]) and a candidate pool biased toward the artists of the current
 * time-of-day bucket. The card only exists inside its slot (morning 5-11 h, evening from 18 h).
 */

/**
 * Stable identifier stored in [ca.ilianokokoro.umihi.music.models.PlayEvent.sourceContext] for
 * plays started from the morning/evening mix card — like [DAILY_MIX_SOURCE_CONTEXT], the
 * feedback loop keys on this exact value, never on the localized card title.
 */
const val CONTEXT_MIX_SOURCE_CONTEXT = "context-mix"

/** A context mix is a session for one moment of the day, shorter than the daily mix. */
const val CONTEXT_MIX_TARGET_SIZE = 20

/**
 * The bucket the context mix targets at [hourOfDay], or null outside both slots (the card is
 * simply not offered then). The morning slot opens at 5 h — one hour before [DayBucket.MORNING]
 * — because early risers want their morning profile, not last night's.
 */
fun contextMixBucket(hourOfDay: Int): DayBucket? = when (hourOfDay) {
    in 5..11 -> DayBucket.MORNING
    in 18..23 -> DayBucket.EVENING
    else -> null
}

/**
 * Biases a candidate pool toward [bucket]: candidates whose artist sits in the bucket's top
 * artists move to the front. The partition is stable, so each half keeps its source ranking —
 * discovery order still means something downstream. A bucket with no profile data (or only
 * unknown artists) leaves the pool untouched.
 */
fun biasCandidatesToBucket(
    candidates: List<DailyMixCandidate>,
    profile: TasteProfile,
    bucket: DayBucket,
): List<DailyMixCandidate> {
    val bucketArtistKeys = profile.hourlyTopArtists[bucket].orEmpty()
        .map { artistKey(it) }
        .filterTo(HashSet()) { it.isNotBlank() }
    if (bucketArtistKeys.isEmpty()) return candidates

    val (matching, others) = candidates.partition {
        artistKey(it.song.artist) in bucketArtistKeys
    }
    return matching + others
}
