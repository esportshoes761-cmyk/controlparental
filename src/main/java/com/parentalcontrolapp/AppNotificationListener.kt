package com.parentalcontrolapp

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.app.Notification

class AppNotificationListener : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        BackendClient.init(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val notification = sbn.notification
        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val event = "Notificación: ${sbn.packageName} - $title: $text"
        AppRepository.addNotificationEvent(this, event)

        val payload = NotificationPayload(
            deviceId = AppRepository.getDeviceId(this),
            packageName = sbn.packageName,
            title = title,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        BackendClient.sendNotification(payload)
        Log.i("AppNotificationListener", event)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op
    }
}
