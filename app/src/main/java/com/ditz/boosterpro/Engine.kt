package com.ditz.boosterpro

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow

/** Pusat logika Gaming Mode: aktifkan & pulihkan setelan HP. */
object Engine {
    val gamingMode = MutableStateFlow(false)
    val detectorOn = MutableStateFlow(false)
    val activeGame = MutableStateFlow<String?>(null)

    fun activate(c: Context, pkg: String) {
        val ctx = c.applicationContext
        if (gamingMode.value) return
        val sp = Prefs.sp(ctx)
        val ed = sp.edit()
        val nm = ctx.getSystemService(NotificationManager::class.java)

        // Simpan kondisi awal supaya bisa dipulihkan persis.
        ed.putInt("prev_filter", nm.currentInterruptionFilter)

        if (Prefs.lockBrightness(ctx) && Settings.System.canWrite(ctx)) {
            val cr = ctx.contentResolver
            ed.putInt("prev_bright_mode",
                Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 1))
            // 0 = manual (auto-brightness mati)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        }
        ed.putBoolean("did_boost", true).apply()

        if (Prefs.silence(ctx)) setFilter(ctx, NotificationManager.INTERRUPTION_FILTER_NONE)

        if (Prefs.hud(ctx) && Settings.canDrawOverlays(ctx)) {
            ctx.startService(Intent(ctx, HudService::class.java))
        }
        activeGame.value = pkg
        gamingMode.value = true
    }

    fun deactivate(c: Context) {
        val ctx = c.applicationContext
        val sp = Prefs.sp(ctx)
        if (!gamingMode.value && !sp.getBoolean("did_boost", false)) return

        ctx.stopService(Intent(ctx, HudService::class.java))

        if (Prefs.silence(ctx)) {
            val prev = sp.getInt("prev_filter", NotificationManager.INTERRUPTION_FILTER_ALL)
            setFilter(ctx, if (prev == NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
                NotificationManager.INTERRUPTION_FILTER_ALL else prev)
        }
        if (sp.contains("prev_bright_mode") && Settings.System.canWrite(ctx)) {
            Settings.System.putInt(ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE, sp.getInt("prev_bright_mode", 1))
        }
        sp.edit().remove("prev_bright_mode").putBoolean("did_boost", false).apply()
        activeGame.value = null
        gamingMode.value = false
    }

    /** Lewat NotificationListenerService dulu; fallback ke akses Jangan Ganggu. */
    private fun setFilter(ctx: Context, filter: Int) {
        val l = NotifListener.instance
        if (l != null) {
            try { l.requestInterruptionFilter(filter); return } catch (_: Exception) {}
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(filter)
    }
}
