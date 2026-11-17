package com.quarenta.calendario

import android.app.Application
import com.quarenta.calendario.data.CrashStore

/** The app process: the first thing it does is get ready to write down an error, should one happen. */
class FortyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashStore.install(this)
    }
}
