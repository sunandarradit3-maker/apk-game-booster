package com.ditz.boosterpro

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*

/** Polling foreground app via UsageStatsManager -> aktif/nonaktif Gaming Mode. */
class DetectorService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var foregroundPkg: String? = null
    private var first = true

    private val ignored = setOf("com.android.systemui", "com.google.android.inputmethod.latin")

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("ditz", "Ditz Booster", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, "ditz")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Ditz Booster Pro aktif")
            .setContentText("Menunggu game dibuka…")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)

        Engine.detectorOn.value = true
        scope.launch {
            while (isActive) { tick(); delay(1500) }
        }
        return START_STICKY
    }

    private fun tick() {
        val pkg = latestForeground() ?: foregroundPkg
        foregroundPkg = pkg
        if (pkg == null || pkg == packageName) return
        val games = Prefs.games(this)
        if (pkg in games) Engine.activate(this, pkg) else Engine.deactivate(this)
    }

    private fun latestForeground(): String? {
        val usm = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val window = if (first) 30 * 60_000L else 10_000L
        first = false
        val ev = usm.queryEvents(now - window, now) ?: return null
        val e = UsageEvents.Event()
        var last: String? = null
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.packageName in ignored) continue
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> last = e.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (last == e.packageName) last = null
            }
        }
        return last
    }

    override fun onDestroy() {
        scope.cancel()
        Engine.deactivate(this)
        Engine.detectorOn.value = false
        super.onDestroy()
    }
}
