package app.pocketledger

import android.app.Application
import app.pocketledger.data.AppContainer

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
