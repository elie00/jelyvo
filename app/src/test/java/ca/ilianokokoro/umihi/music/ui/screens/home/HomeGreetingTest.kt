package ca.ilianokokoro.umihi.music.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGreetingTest {

    @Test
    fun `morning runs from 5 to 11`() {
        assertEquals(HomeGreeting.MORNING, greetingForHour(5))
        assertEquals(HomeGreeting.MORNING, greetingForHour(9))
        assertEquals(HomeGreeting.MORNING, greetingForHour(11))
    }

    @Test
    fun `afternoon runs from 12 to 17`() {
        assertEquals(HomeGreeting.AFTERNOON, greetingForHour(12))
        assertEquals(HomeGreeting.AFTERNOON, greetingForHour(15))
        assertEquals(HomeGreeting.AFTERNOON, greetingForHour(17))
    }

    @Test
    fun `evening covers 18h onward and the small hours`() {
        assertEquals(HomeGreeting.EVENING, greetingForHour(18))
        assertEquals(HomeGreeting.EVENING, greetingForHour(23))
        assertEquals(HomeGreeting.EVENING, greetingForHour(0))
        assertEquals(HomeGreeting.EVENING, greetingForHour(4))
    }

    @Test
    fun `resume listening leads in the evening`() {
        assertEquals(
            listOf(HomeCarousel.RESUME, HomeCarousel.RECENT, HomeCarousel.FOR_YOU),
            homeCarouselOrder(18),
        )
        assertEquals(HomeCarousel.RESUME, homeCarouselOrder(22).first())
        assertEquals(HomeCarousel.RESUME, homeCarouselOrder(2).first())
    }

    @Test
    fun `recently played leads during the day`() {
        assertEquals(
            listOf(HomeCarousel.RECENT, HomeCarousel.RESUME, HomeCarousel.FOR_YOU),
            homeCarouselOrder(10),
        )
        assertEquals(HomeCarousel.RECENT, homeCarouselOrder(17).first())
    }

    @Test
    fun `every carousel appears exactly once whatever the hour`() {
        for (hour in 0..23) {
            assertEquals(HomeCarousel.entries.toSet(), homeCarouselOrder(hour).toSet())
        }
    }
}
