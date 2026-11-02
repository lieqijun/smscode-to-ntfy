package cn.ppps.smscodetontfy

import android.app.Application
import cn.ppps.smscodetontfy.data.AppSettings

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.appContext = this
    }
}
