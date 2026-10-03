package cn.tgbug.nekotodo

import android.app.Application
import coil3.SingletonImageLoader

class NekoTodoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SingletonImageLoader.setSafe { container.imageLoader }
    }
}
