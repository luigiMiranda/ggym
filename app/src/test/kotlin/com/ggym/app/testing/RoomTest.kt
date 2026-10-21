package com.ggym.app.testing

import com.ggym.app.data.room.GgymDatabase
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A database-backed test: the real schema, an in-memory instance, one per test.
 *
 * Room needs a SQLite runtime, so these tests run under Robolectric on the JVM rather than on a
 * device or an emulator. The SDK is pinned to the app's `compileSdk` so the tests exercise the
 * same framework the app ships against.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
abstract class RoomTest {

    // Internal, not protected: `GgymDatabase` is internal to the data layer, and this keeps the
    // class itself usable from any test in this source set.
    internal lateinit var database: GgymDatabase
        private set

    /**
     * Opens this test's database, closing the previous test's.
     *
     * The previous one is closed **here rather than in an `@After`**, and the ordering is the whole
     * point. A JUnit rule wraps `@Before` and `@After`, so the Compose rule the screen tests use
     * disposes its composition *after* any `@After` has run. Closing in `@After` therefore pulled the
     * database out from under a ViewModel that was still collecting a Room flow, and the next
     * subscription — `combine` restarts one readily — threw `IllegalStateException: Database is
     * closed` from inside the test.
     *
     * The failure was load-sensitive rather than obvious: it surfaced in full-suite runs of
     * `DayDetailScreenTest` (twice, reproducibly) and never when that class ran alone. Closing at the
     * top of the following test means the previous composition, its `viewModelScope` and every
     * collector it owned are gone before the connection is touched. The cost is one open in-memory
     * database left at the end of a class, which Robolectric discards with the sandbox.
     */
    @Before
    fun openDatabase() {
        if (this::database.isInitialized) database.close()
        database = createTestDatabase()
    }
}
