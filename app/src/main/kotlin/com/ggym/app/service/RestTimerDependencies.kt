package com.ggym.app.service

import android.content.Context
import com.ggym.app.GgymApplication
import com.ggym.app.domain.repository.RestTimerStore
import com.ggym.app.domain.workout.RestTimerUseCases

/**
 * What the rest timer's background half — [RestTimerService] and [RestTimerAlertReceiver] — works
 * with, in one bundle.
 *
 * The framework constructs both of them, so neither can take its dependencies through a
 * constructor; this is what they are handed instead. Production builds it once in
 * `AppContainer`; a test replaces it with fakes, which is also how a `ToneGenerator` or a real
 * notification never has to run in a JVM test.
 */
class RestTimerDependencies(
    val store: RestTimerStore,
    val timers: RestTimerUseCases,
    val notifications: RestTimerNotifications,
    val alarms: RestTimerAlarms,
    val alerts: RestAlertPlayer,
) {

    /** The one end-of-rest implementation both entry points use. */
    val completion: RestTimerCompletion = RestTimerCompletion(store, alarms, notifications, alerts)
}

/**
 * How the service and the receiver find their dependencies.
 *
 * In the app they come from the container. A test sets [override] instead, because a framework
 * component has no constructor to inject through and no unit test should drive real audio or a real
 * alarm clock; every test that sets it clears it again afterwards.
 */
internal object RestTimerEnvironment {

    @Volatile
    var override: RestTimerDependencies? = null

    fun dependencies(context: Context): RestTimerDependencies =
        override ?: (context.applicationContext as GgymApplication).container.restTimerDependencies
}
