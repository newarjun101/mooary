package com.arjun.mooary

import android.app.Application
import com.arjun.mooary.core.notifications.ReminderReceiver
import com.arjun.mooary.core.platform.AndroidPlatform
import com.arjun.mooary.di.diaryModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Android entry point.
 *
 * [AndroidPlatform.initialise] runs before `startKoin` on purpose: the graph resolves a
 * `FileExporter` at construction time and that needs a `Context`, so the order here is
 * load-bearing rather than stylistic.
 */
class DiaryApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AndroidPlatform.initialise(this)
        // Ahead of startKoin and of anything the graph resolves, because a push can arrive
        // before the first screen and the channel has to already exist by then.
        ReminderReceiver.ensureChannel(this)
        startKoin {
            androidContext(this@DiaryApplication)
            modules(diaryModules)
        }
    }
}
