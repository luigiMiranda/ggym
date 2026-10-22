package com.ggym.app.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which tab a route belongs to — the rule an optional route argument quietly breaks.
 *
 * A `NavDestination` reports the *pattern* it was declared with, so once Stats accepts
 * `stats?exerciseId={exerciseId}` it never reports plain `stats` again. Matching tabs on the full
 * string would leave the bottom bar lighting nothing at every depth, which is why [Destination]
 * compares the route's base. These tests pin that, at the pattern and at a concrete URL.
 */
class DestinationTest {

    @Test
    fun `a tab matches its own plain route`() {
        Destination.entries.forEach { destination ->
            assertEquals(destination, Destination.fromRoute(destination.route))
            assertEquals(destination, Destination.owningTab(destination.route))
        }
    }

    @Test
    fun `the stats route with an exercise argument is still the stats tab`() {
        assertEquals(
            "the pattern is what the destination reports, so it must match the tab",
            Destination.Stats,
            Destination.fromRoute(Routes.STATS),
        )
        assertEquals(Destination.Stats, Destination.owningTab(Routes.STATS))
        assertEquals(Destination.Stats, Destination.owningTab(Routes.stats(7)))
    }

    @Test
    fun `a drill-down still belongs to the tab it was opened from`() {
        // A NavDestination reports the route pattern it was declared with, so these are the strings
        // `owningTab` actually receives at runtime, not a concrete URL.
        assertEquals(Destination.Workouts, Destination.owningTab(Routes.ROUTINE))
        assertEquals(Destination.Workouts, Destination.owningTab(Routes.DAY))
        assertEquals(Destination.Workouts, Destination.owningTab(Routes.EXERCISE_PICKER))
        assertEquals(Destination.Workouts, Destination.owningTab(Routes.LIBRARY))
        assertEquals(Destination.Workouts, Destination.owningTab(Routes.WORKOUT))
        assertEquals(Destination.Calendar, Destination.owningTab(Routes.HISTORY))
    }

    @Test
    fun `a route that is not a tab lights nothing`() {
        assertNull(Destination.fromRoute("nonsense"))
        assertNull(Destination.owningTab("nonsense"))
        assertNull(Destination.owningTab("nonsense?x=1"))
        assertNull(Destination.owningTab(null))
    }

    @Test
    fun `the base of a route is everything before its query`() {
        assertEquals("stats", Destination.baseRoute(Routes.STATS))
        assertEquals("stats", Destination.baseRoute(Routes.stats(7)))
        assertEquals("workouts", Destination.baseRoute(Destination.Workouts.route))
        assertNull(Destination.baseRoute(null))
    }
}
