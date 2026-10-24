package com.zenkai.novatuner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Profile(
    val pkg: String,
    var label: String,
    var gameMode: Int = 2,
    var downscale: Int = 0,
    var fps: Int = 0,
    var angle: Boolean = false,
    var compile: Int = 0,
    var standby: Boolean = true,
    var whitelist: Boolean = false,
    var killBg: Boolean = false
)

object Store {
    private const val FILE = "nova"
    private const val KEY = "profiles"

    fun load(c: Context): MutableList<Profile> {
        val s = c.getSharedPreferences(FILE, 0).getString(KEY, "[]") ?: "[]"
        val list = mutableListOf<Profile>()
        try {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                list.add(
                    Profile(
                        pkg = o.getString("pkg"),
                        label = o.optString("label", o.getString("pkg")),
                        gameMode = o.optInt("gameMode", 2),
                        downscale = o.optInt("downscale", 0),
                        fps = o.optInt("fps", 0),
                        angle = o.optBoolean("angle", false),
                        compile = o.optInt("compile", 0),
                        standby = o.optBoolean("standby", true),
                        whitelist = o.optBoolean("whitelist", false),
                        killBg = o.optBoolean("killBg", false)
                    )
                )
            }
        } catch (e: Exception) {
        }
        return list
    }

    fun save(c: Context, list: List<Profile>) {
        val a = JSONArray()
        for (p in list) {
            a.put(
                JSONObject()
                    .put("pkg", p.pkg)
                    .put("label", p.label)
                    .put("gameMode", p.gameMode)
                    .put("downscale", p.downscale)
                    .put("fps", p.fps)
                    .put("angle", p.angle)
                    .put("compile", p.compile)
                    .put("standby", p.standby)
                    .put("whitelist", p.whitelist)
                    .put("killBg", p.killBg)
            )
        }
        c.getSharedPreferences(FILE, 0).edit().putString(KEY, a.toString()).apply()
    }
}
