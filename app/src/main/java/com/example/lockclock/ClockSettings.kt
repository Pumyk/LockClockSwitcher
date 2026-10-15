package com.example.lockclock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

data class ClockPreset(val name: String, val style: Int, val templateId: String)

object ClockSettings {
    const val KEY = "constant_lockscreen_info"
    private const val PREFS = "backup"

    private val styleRegex = Regex("(?i)(?<![a-z])(style:)\\d+")
    private val templateRegex = Regex("(?i)(templateid:)[^,;\"}\\s]+")

    fun read(ctx: Context): String? =
        Settings.Secure.getString(ctx.contentResolver, KEY)

    fun canWrite(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasOriginalBackup(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("original")

    /** Rewrites only the style number and templateid; everything else is untouched. */
    fun apply(ctx: Context, style: Int, templateId: String): Result<String> = runCatching {
        check(canWrite(ctx)) { "WRITE_SECURE_SETTINGS not granted. Run the ADB command shown above." }
        val original = read(ctx) ?: error("Key '$KEY' not found in Secure settings.")
        check(styleRegex.containsMatchIn(original) && templateRegex.containsMatchIn(original)) {
            "Couldn't find style: / templateid: in the current value:\n$original"
        }

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (!hasOriginalBackup(ctx)) prefs.putString("original", original) // true original, saved once
        prefs.putString("previous", original).apply()

        val updated = original
            .let { s -> styleRegex.replace(s) { it.groupValues[1] + style } }
            .let { s -> templateRegex.replace(s) { it.groupValues[1] + templateId } }

        check(Settings.Secure.putString(ctx.contentResolver, KEY, updated)) { "Write was rejected by the system." }
        updated
    }

    fun restore(ctx: Context, which: String = "original"): Result<String> = runCatching {
        check(canWrite(ctx)) { "WRITE_SECURE_SETTINGS not granted." }
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(which, null)
            ?: error("No backup saved yet.")
        check(Settings.Secure.putString(ctx.contentResolver, KEY, saved)) { "Write was rejected by the system." }
        saved
    }
}
