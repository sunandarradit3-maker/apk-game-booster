package com.ditz.boosterpro

import android.app.Service
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.IBinder
import android.view.*
import android.widget.TextView
import androidx.core.content.ContextCompat

/** HUD overlay: FPS (Choreographer) + suhu baterai (ACTION_BATTERY_CHANGED). */
class HudService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var tv: TextView
    private var fps = 0
    private var temp = 0f

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            render()
        }
    }

    private val frame = object : Choreographer.FrameCallback {
        var frames = 0
        var t0 = 0L
        override fun doFrame(ns: Long) {
            if (t0 == 0L) t0 = ns
            frames++
            val dt = ns - t0
            if (dt >= 1_000_000_000L) {
                fps = (frames * 1_000_000_000L / dt).toInt()
                frames = 0; t0 = ns
                render()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(24, 12, 24, 12)
            background = GradientDrawable().apply {
                setColor(Color.argb(170, 10, 10, 12))
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#FF2D3A"))
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 80 }
        wm.addView(tv, lp)

        ContextCompat.registerReceiver(this, battery,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        Choreographer.getInstance().postFrameCallback(frame)
        render()
    }

    private fun render() { tv.text = "FPS $fps   ${"%.1f".format(temp)}°C" }

    override fun onDestroy() {
        Choreographer.getInstance().removeFrameCallback(frame)
        try { unregisterReceiver(battery) } catch (_: Exception) {}
        try { wm.removeView(tv) } catch (_: Exception) {}
        super.onDestroy()
    }
}
