package com.fffemotes.skintools.master.data.remote.config

import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit

/**
 * The second way Remote Config syncs (the first is `base.rc_sync_hours`): a silent FCM push to topic [TOPIC] with
 * data `type: rc_sync` syncs at once. Any other push is ignored; nothing is shown to the user.
 */
class RcSyncMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != TYPE) return
        // Kept alive until the sync lands: Android may stop a process started only for the push once this returns.
        runCatching { AppConfigStore.syncNow(applicationContext)?.let { Tasks.await(it, 20, TimeUnit.SECONDS) } }
    }

    override fun onNewToken(token: String) = Unit // topic-based: no token kept

    companion object {
        const val TOPIC = "rc_sync"
        private const val TYPE = "rc_sync"
    }
}
