package com.ditz.boosterpro

import android.service.notification.NotificationListenerService

/**
 * Wajib aktif agar aplikasi bisa mengatur interruption filter (Total Silence)
 * saat panggilan / chat WhatsApp masuk ketika Gaming Mode ON.
 */
class NotifListener : NotificationListenerService() {
    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }

    companion object { @Volatile var instance: NotifListener? = null }
}
