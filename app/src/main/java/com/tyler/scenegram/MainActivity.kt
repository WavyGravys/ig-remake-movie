package com.tyler.scenegram

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tyler.scenegram.director.AppViewModel
import com.tyler.scenegram.notifications.NotificationController
import com.tyler.scenegram.ui.SceneGramApp

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            SceneGramApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationController.EXTRA_OPEN_CHAT, false) == true) {
            viewModel.openChat(intent.getStringExtra(NotificationController.EXTRA_CHAT_ID))
        }
    }
}
