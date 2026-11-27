package com.tm.farm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Lưu game và đồng hồ game (có tua nhanh để test). */
object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("farm", Context.MODE_PRIVATE)

    fun speed(c: Context) = prefs(c).getInt("speed", 1)

    fun now(c: Context): Long {
        val p = prefs(c)
        val real = System.currentTimeMillis()
        val realBase = p.getLong("realBase", real)
        val gameBase = p.getLong("gameBase", real)
        return gameBase + (real - realBase) * speed(c)
    }

    fun setSpeed(c: Context, s: Int) {
        val game = now(c)
        prefs(c).edit().putInt("speed", s)
            .putLong("realBase", System.currentTimeMillis())
            .putLong("gameBase", game).apply()
    }

    /** Đổi mốc giờ game sang giờ thật. */
    fun toReal(c: Context, gameTime: Long) =
        System.currentTimeMillis() + maxOf(0L, gameTime - now(c)) / speed(c)

    fun load(c: Context): Farm {
        val s = prefs(c).getString("save", null) ?: return Farm()
        val o = JSONObject(s)
        val a = o.getJSONArray("chickens")
        return Farm(
            o.getInt("coins"), o.getInt("water"), o.getLong("refillAt"), o.getInt("grass"),
            o.getInt("eggs"), o.getInt("stock"), o.getLong("truckAt"), o.getInt("truckMoney"),
            MutableList(a.length()) { a.getLong(it) }
        )
    }

    fun save(c: Context, f: Farm) {
        val o = JSONObject()
            .put("coins", f.coins).put("water", f.water).put("refillAt", f.refillAt)
            .put("grass", f.grass).put("eggs", f.eggsOnGround).put("stock", f.stock)
            .put("truckAt", f.truckAt).put("truckMoney", f.truckMoney)
            .put("chickens", JSONArray(f.chickens))
        prefs(c).edit().putString("save", o.toString()).apply()
    }

    /** Đọc -> cập nhật -> thao tác -> lưu -> vẽ lại widget. */
    fun update(c: Context, action: (Farm, Long) -> Unit = { _, _ -> }): Farm {
        val f = load(c)
        val now = now(c)
        f.advance(now)
        action(f, now)
        save(c, f)
        Widgets.render(c, f, now)
        return f
    }
}
