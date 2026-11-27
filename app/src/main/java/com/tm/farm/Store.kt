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

    private fun spots(a: JSONArray) = MutableList(a.length() / 3) {
        Spot(a.getDouble(it * 3).toFloat(), a.getDouble(it * 3 + 1).toFloat(), a.getInt(it * 3 + 2))
    }

    private fun json(list: List<Spot>) = JSONArray().also { a ->
        list.forEach { a.put(it.x.toDouble()).put(it.y.toDouble()).put(it.kind) }
    }

    fun load(c: Context): Farm {
        val s = prefs(c).getString("save3", null)
        if (s == null) {
            // Lần đầu: lưu ngay để vị trí gà không đổi giữa các lần đọc
            return Farm.new(now(c)).also { it.timeScale = speed(c); save(c, it) }
        }
        val o = JSONObject(s)
        val now = now(c)
        val born = o.optJSONArray("eggBorn")   // bản lưu cũ không có -> tính từ bây giờ
        val a = o.getJSONArray("chickens")
        val chickens = MutableList(a.length()) {
            val k = a.getJSONObject(it)
            Chicken(
                k.getInt("id"), k.getLong("t"), k.getLong("fed"), k.getLong("prog"), k.getLong("eat"),
                k.getDouble("ax").toFloat(), k.getDouble("ay").toFloat(), k.getLong("at"),
                k.getDouble("fx").toFloat(), k.getDouble("fy").toFloat(), k.getInt("gk"),
                k.optLong("hungry", 0L)
            ).also { if (it.hungry && it.hungrySince == 0L) it.hungrySince = now }
        }
        val eggs = spots(o.getJSONArray("eggs"))
        for (i in eggs.indices) eggs[i] = eggs[i].copy(born = born?.getLong(i) ?: now)
        return Farm(
            o.getInt("coins"), o.getInt("water"), o.getLong("refillAt"), o.getInt("stock"),
            o.getLong("truckAt"), o.getInt("truckMoney"),
            spots(o.getJSONArray("grass")), eggs, chickens,
            o.optLong("nextWater", now), o.optInt("deaths"), o.optInt("spoiled")
        ).also {
            it.timeScale = speed(c)
            if (born == null) save(c, it)      // chốt mốc giờ khi nâng cấp bản lưu cũ
        }
    }

    fun save(c: Context, f: Farm) {
        val chickens = JSONArray()
        for (k in f.chickens) chickens.put(
            JSONObject().put("id", k.id).put("t", k.t).put("fed", k.fed).put("prog", k.prog)
                .put("eat", k.eatStart).put("ax", k.ax.toDouble()).put("ay", k.ay.toDouble())
                .put("at", k.at).put("fx", k.fx.toDouble()).put("fy", k.fy.toDouble()).put("gk", k.gk)
                .put("hungry", k.hungrySince)
        )
        val o = JSONObject()
            .put("coins", f.coins).put("water", f.water).put("refillAt", f.refillAt)
            .put("stock", f.stock).put("truckAt", f.truckAt).put("truckMoney", f.truckMoney)
            .put("grass", json(f.grass)).put("eggs", json(f.eggs)).put("chickens", chickens)
            .put("eggBorn", JSONArray(f.eggs.map { it.born }))
            .put("nextWater", f.nextWaterAt).put("deaths", f.deaths).put("spoiled", f.spoiled)
        prefs(c).edit().putString("save3", o.toString()).apply()
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
