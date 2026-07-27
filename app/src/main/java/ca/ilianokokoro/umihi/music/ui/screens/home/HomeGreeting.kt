package ca.ilianokokoro.umihi.music.ui.screens.home

/**
 * Time-of-day greeting shown in the home header. The account never exposes a display name,
 * so the greeting stands alone.
 */
enum class HomeGreeting {
    MORNING,
    AFTERNOON,
    EVENING,
}

/** Local carousel sections of the home tab whose relative order depends on the time of day. */
enum class HomeCarousel {
    RESUME,
    RECENT,
    FOR_YOU,
}

/**
 * Picks the greeting for a local hour (0..23). Early night hours (before 5) still read as
 * "evening" — nobody wants a cheery "good morning" at 2am.
 */
fun greetingForHour(hour: Int): HomeGreeting = when (hour) {
    in 5..11 -> HomeGreeting.MORNING
    in 12..17 -> HomeGreeting.AFTERNOON
    else -> HomeGreeting.EVENING
}

/**
 * Order of the local carousels for a local hour (0..23). In the evening (18h and later)
 * "resume listening" moves to the top: that is when people pick a track back up.
 */
fun homeCarouselOrder(hour: Int): List<HomeCarousel> =
    if (greetingForHour(hour) == HomeGreeting.EVENING) {
        listOf(HomeCarousel.RESUME, HomeCarousel.RECENT, HomeCarousel.FOR_YOU)
    } else {
        listOf(HomeCarousel.RECENT, HomeCarousel.RESUME, HomeCarousel.FOR_YOU)
    }
