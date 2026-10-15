package com.example.lockclock

import android.content.Context
import org.json.JSONArray

/** Reads app/src/main/assets/presets.json. Add entries there; no code changes needed. */
fun loadPresets(ctx: Context): List<ClockPreset> = runCatching {
    val text = ctx.assets.open("presets.json").bufferedReader().use { it.readText() }
    val arr = JSONArray(text)
    List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        ClockPreset(o.getString("name"), o.getInt("style"), o.getString("templateId"))
    }
}.getOrDefault(emptyList())
