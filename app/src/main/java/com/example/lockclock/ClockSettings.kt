package com.example.lockclock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

data class ClockPreset(val name: String, val style: Int, val templateId: String)

object ClockSettings {
    const val KEY = "constant_lockscreen_info"
    private const val PREFS = "backup"

    fun read(ctx: Context): String? =
        Settings.Secure.getString(ctx.contentResolver, KEY)

    fun canWrite(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasOriginalBackup(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("original")

    /**
     * Recursively patches every "style" and "templateId" entry found anywhere in
     * the JSON tree, including values that are themselves JSON strings. Keeps
     * each value's original type so the system reader stays happy.
     */
    private fun patchNode(node: Any, style: Int, templateId: String, hits: IntArray): Boolean {
        var any = false
        when (node) {
            is JSONObject -> {
                for (k in node.keys().asSequence().toList()) {
                    val v = node.opt(k) ?: continue
                    when {
                        k.equals("style", true) && (v is Int || v is Long || v is String) -> {
                            node.put(k, if (v is String) style.toString() else style)
                            hits[0]++; any = true
                        }
                        k.equals("templateid", true) && (v is String) -> {
                            node.put(k, templateId)
                            hits[1]++; any = true
                        }
                        v is String && v.trimStart().startsWith("{") ->
                            runCatching { JSONObject(v) }.getOrNull()?.let { inner ->
                                if (patchNode(inner, style, templateId, hits)) {
                                    node.put(k, inner.toString()); any = true
                                }
                            }
                        else -> if (patchNode(v, style, templateId, hits)) any = true
                    }
                }
            }
            is JSONArray -> for (i in 0 until node.length())
                if (patchNode(node.opt(i), style, templateId, hits)) any = true
        }
        return any
    }

    /** Regex fallback for values that aren't valid JSON: fixes "style":N and "templateId":"x" in raw text. */
    private val styleJsonRegex = Regex("""(?i)("style"\s*:\s*)("?)(-?\d+)("?)""")
    private val templateJsonRegex = Regex("""(?i)("templateid"\s*:\s*")([^"]*)(")""")

    /** Rewrites only the style number and templateid; everything else is untouched. */
    fun apply(ctx: Context, style: Int, templateId: String): Result<String> = runCatching {
        check(canWrite(ctx)) { "WRITE_SECURE_SETTINGS not granted. Run the ADB command shown above." }
        val original = read(ctx) ?: error("Key '$KEY' not found in Secure settings.")

        val hits = intArrayOf(0, 0)
        val updated: String = runCatching {
            val root = JSONObject(original)
            patchNode(root, style, templateId, hits)
            if (hits[0] == 0 || hits[1] == 0) error("__nohit__")
            root.toString()
        }.getOrElse {
            val s = styleJsonRegex.replace(original) { m -> m.groupValues[1] + m.groupValues[2] + style + m.groupValues[4] }
            val t = templateJsonRegex.replace(s) { m -> m.groupValues[1] + templateId + m.groupValues[3] }
            if (t == original) error(
                "Couldn't find a \"style\"/\"templateId\" field to patch in the current value:\n$original"
            )
            t
        }

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (!hasOriginalBackup(ctx)) prefs.putString("original", original) // true original, saved once
        prefs.putString("previous", original).apply()

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
