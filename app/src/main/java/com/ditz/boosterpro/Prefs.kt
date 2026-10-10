package com.ditz.boosterpro

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    val DEFAULT_GAMES = setOf(
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.mobile.legends",
        "com.tencent.ig",
        "com.pubg.imobile",
        "com.miHoYo.GenshinImpact",
        "com.activision.callofduty.shooter",
        "com.garena.game.codm"
    )

    fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("ditz_booster", Context.MODE_PRIVATE)

    fun games(c: Context): Set<String> = sp(c).getStringSet("games", DEFAULT_GAMES) ?: DEFAULT_GAMES
    fun setGames(c: Context, g: Set<String>) = sp(c).edit().putStringSet("games", g).apply()

    fun hud(c: Context) = sp(c).getBoolean("hud", true)
    fun setHud(c: Context, v: Boolean) = sp(c).edit().putBoolean("hud", v).apply()

    fun silence(c: Context) = sp(c).getBoolean("silence", true)
    fun setSilence(c: Context, v: Boolean) = sp(c).edit().putBoolean("silence", v).apply()

    fun lockBrightness(c: Context) = sp(c).getBoolean("lock_bright", true)
    fun setLockBrightness(c: Context, v: Boolean) = sp(c).edit().putBoolean("lock_bright", v).apply()
}
