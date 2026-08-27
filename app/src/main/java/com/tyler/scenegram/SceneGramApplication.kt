package com.tyler.scenegram

import android.app.Application
import com.tyler.scenegram.notifications.NotificationController

class SceneGramApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationController.createChannel(this)
    }
}
