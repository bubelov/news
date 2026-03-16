package app

import android.app.Application
import di.Di

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Di.init(this)
    }
}