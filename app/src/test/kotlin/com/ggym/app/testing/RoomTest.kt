package com.ggym.app.testing

import com.ggym.app.data.room.GgymDatabase
import org.junit.After
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

    @Before
    fun openDatabase() {
        database = createTestDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }
}
