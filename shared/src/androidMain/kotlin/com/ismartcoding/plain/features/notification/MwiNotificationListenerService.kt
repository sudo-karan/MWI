package com.ismartcoding.plain.features.notification

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ismartcoding.plain.web.WebEventType
import com.ismartcoding.plain.webserver.AndroidWebServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.os.Bundle

/**
 * Mirrors notifications to the web console (spec §6/§8). User-granted binding
 * (BIND_NOTIFICATION_LISTENER_SERVICE). Active notifications are exposed as a snapshot list and
 * changes push a `NOTIFICATION` WS event; inline reply uses the notification's own RemoteInput.
 */
class MwiNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh(notify = true)

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh(notify = true)

    private fun refresh(notify: Boolean = false) {
        val list = runCatching { activeNotifications?.map { it.toDNotification() } }.getOrNull() ?: emptyList()
        store.value = list
        if (notify) {
            scope.launch {
                runCatching {
                    AndroidWebServer.wsHub.broadcast(WebEventType.NOTIFICATION, ByteArray(0))
                }
            }
        }
    }

    fun reply(key: String, text: String): Boolean = runCatching {
        val sbn = activeNotifications?.firstOrNull { it.key == key } ?: return false
        val action = sbn.notification.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true } ?: return false
        val remoteInputs = action.remoteInputs ?: return false
        val intent = Intent()
        val results = Bundle().apply {
            remoteInputs.forEach { putCharSequence(it.resultKey, text) }
        }
        RemoteInput.addResultsToIntent(remoteInputs, intent, results)
        action.actionIntent.send(this, 0, intent)
        true
    }.getOrDefault(false)

    private fun StatusBarNotification.toDNotification(): DNotification {
        val extras = notification.extras
        return DNotification(
            key = key,
            packageName = packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: "",
            postTime = postTime,
            canReply = notification.actions?.any { it.remoteInputs?.isNotEmpty() == true } == true,
            clearable = isClearable,
        )
    }

    companion object {
        @Volatile
        private var instance: MwiNotificationListenerService? = null

        val store = MutableStateFlow<List<DNotification>>(emptyList())

        fun list(): List<DNotification> = store.value

        fun cancel(keys: List<String>): Boolean {
            val svc = instance ?: return false
            keys.forEach { svc.cancelNotification(it) }
            return true
        }

        fun replyTo(key: String, text: String): Boolean = instance?.reply(key, text) ?: false
    }
}
