package com.tm.farm

import kotlin.math.abs
import kotlin.math.hypot

/** Logic thuần Kotlin. Thời gian: mili-giây "giờ game". Toạ độ bản đồ: 0..1. */
object Rules {
    const val MIN = 60_000L
    const val EAT_TIME = 5 * MIN        // chạy tới cỏ + ăn; cỏ biến mất dần
    const val FED_TIME = 60 * MIN       // từ no đầy tới lúc đói (còn 40%)
    const val LAY_TIME = 120 * MIN      // tổng thời gian không đói để đẻ 1 trứng
    const val HUNGRY_PART = 0.4f
    const val REFILL_TIME = 30 * MIN
    const val TRUCK_TIME = 60 * MIN

    const val WELL_MAX = 5
    const val GRASS_PER_WATER = 6
    const val GRASS_MAX = 24
    const val NEST_MAX = 12
    const val CHICKEN_MAX = 6

    const val REFILL_COST = 5
    const val CHICKEN_COST = 100
    const val EGG_PRICE = 10
    const val START_COINS = 100

    // Chỉ ảnh hưởng hình ảnh (giây thật)
    const val SEG_SEC = 6.0             // mỗi chặng đi lang thang
    const val MOVE_PART = 0.7           // phần đi, còn lại đứng nghỉ
    const val RUN_SPEED = 0.2           // tốc độ chạy tới cỏ (bản đồ/giây)
}

data class Spot(val x: Float, val y: Float)

data class Chicken(
    val id: Int,
    var t: Long,                  // mốc đã tính tới
    var fed: Long = 0,            // thời gian còn lại trước khi đói
    var prog: Long = 0,           // tiến trình đẻ
    var eatStart: Long = 0,       // 0 = không ăn
    var ax: Float,                // mốc đi lang thang; khi đang ăn là vị trí đám cỏ
    var ay: Float,
    var at: Long,
    var fx: Float = 0f,           // vị trí xuất phát khi chạy tới cỏ
    var fy: Float = 0f
) {
    val eating get() = eatStart != 0L
    val hungry get() = !eating && fed == 0L
}

enum class Act { WALK, STAND, EAT }

data class Pose(val x: Float, val y: Float, val dx: Float, val dy: Float, val act: Act)

/** Số "ngẫu nhiên" 0..1 cố định theo đầu vào, để app và widget tính ra giống nhau. */
fun noise(a: Long, b: Long): Float {
    var z = a * -7046029254386353131L + b * -4658895280553007687L + 1234567L
    z = (z xor (z ushr 30)) * -4658895280553007687L
    z = (z xor (z ushr 27)) * -7723592293110705685L
    z = z xor (z ushr 31)
    return ((z ushr 40).toFloat() / (1L shl 24).toFloat())
}

data class Farm(
    var coins: Int = Rules.START_COINS,
    var water: Int = Rules.WELL_MAX,
    var refillAt: Long = 0,
    var stock: Int = 0,
    var truckAt: Long = 0,
    var truckMoney: Int = 0,
    val grass: MutableList<Spot> = mutableListOf(),
    val eggs: MutableList<Spot> = mutableListOf(),
    val chickens: MutableList<Chicken> = mutableListOf()
) {
    /** Tốc độ tua (debug). Dùng để đổi giờ game sang giây thật khi tính vị trí. */
    var timeScale = 1

    companion object {
        fun new(now: Long) = Farm().also { f -> repeat(2) { f.addChicken(now) } }
    }

    private fun addChicken(now: Long) {
        val id = chickens.size
        chickens.add(Chicken(id, now, ax = 0.3f + 0.4f * noise(now, id.toLong()),
            ay = 0.3f + 0.4f * noise(now, id + 99L), at = now))
    }

    private fun sec(ms: Long) = ms / 1000.0 / timeScale

    /** Vị trí và hành động của gà tại thời điểm time. */
    fun pose(c: Chicken, time: Long): Pose {
        if (c.eating) {
            val dist = hypot(c.ax - c.fx, c.ay - c.fy).toDouble()
            val run = dist / Rules.RUN_SPEED
            val el = maxOf(0.0, sec(time - c.eatStart))
            if (el < run) {
                val k = (el / run).toFloat()
                return Pose(c.fx + (c.ax - c.fx) * k, c.fy + (c.ay - c.fy) * k, c.ax - c.fx, c.ay - c.fy, Act.WALK)
            }
            return Pose(c.ax, c.ay, -1f, 0f, Act.EAT)
        }
        val el = maxOf(0.0, sec(time - c.at)) / Rules.SEG_SEC
        val n = el.toLong()
        val part = el - n
        val seed = c.at * 31 + c.id
        fun way(i: Long) = if (i == 0L) Spot(c.ax, c.ay) else Spot(noise(seed, i * 2), noise(seed, i * 2 + 1))
        val a = way(n)
        val b = way(n + 1)
        val k = minOf(1.0, part / Rules.MOVE_PART).toFloat()
        return Pose(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, b.x - a.x, b.y - a.y,
            if (part < Rules.MOVE_PART) Act.WALK else Act.STAND)
    }

    /** Mức no 0..1 để vẽ thanh no. */
    fun satiety(c: Chicken, time: Long): Float {
        if (c.eating) return ((time - c.eatStart).toFloat() / Rules.EAT_TIME).coerceIn(0f, 1f)
        if (c.fed == 0L) return Rules.HUNGRY_PART
        val left = (c.fed - (time - c.t)).coerceAtLeast(0)
        return Rules.HUNGRY_PART + (1 - Rules.HUNGRY_PART) * left / Rules.FED_TIME
    }

    private fun canLay(c: Chicken) = eggs.size < Rules.NEST_MAX && Rules.LAY_TIME - c.prog <= c.fed

    private fun nextOf(c: Chicken): Long? = when {
        c.eating -> c.eatStart + Rules.EAT_TIME
        c.fed == 0L -> null
        canLay(c) -> c.t + (Rules.LAY_TIME - c.prog)
        else -> c.t + c.fed
    }

    /** Tính mọi thứ đã xảy ra tới now, theo đúng thứ tự thời gian (offline progress). */
    fun advance(now: Long) {
        if (refillAt != 0L && now >= refillAt) { water = Rules.WELL_MAX; refillAt = 0 }
        if (truckAt != 0L && now >= truckAt) { coins += truckMoney; truckMoney = 0; truckAt = 0 }
        while (true) {
            var best: Chicken? = null
            var bestT = Long.MAX_VALUE
            for (c in chickens) {
                val e = nextOf(c) ?: continue
                if (e <= now && e < bestT) { best = c; bestT = e }
            }
            val c = best ?: break
            if (c.eating) {                       // ăn xong -> no đầy, đi lang thang từ chỗ cỏ
                c.eatStart = 0; c.fed = Rules.FED_TIME; c.t = bestT; c.at = bestT
            } else if (canLay(c)) {               // đẻ ngay dưới chân
                val p = pose(c, bestT)
                eggs.add(Spot(p.x, p.y))
                c.fed -= Rules.LAY_TIME - c.prog
                c.prog = 0; c.t = bestT
                if (c.fed == 0L) tryEat(c, bestT)
            } else {                              // đói
                c.prog = minOf(Rules.LAY_TIME, c.prog + c.fed)
                c.fed = 0; c.t = bestT
                tryEat(c, bestT)
            }
        }
    }

    /** Gà đói chạy tới đám cỏ gần nhất. Đám cỏ đó được "giữ chỗ" cho con gà này. */
    private fun tryEat(c: Chicken, time: Long) {
        if (!c.hungry || grass.isEmpty()) return
        val p = pose(c, time)
        var best = 0
        for (i in 1 until grass.size) {
            if (hypot(grass[i].x - p.x, grass[i].y - p.y) < hypot(grass[best].x - p.x, grass[best].y - p.y)) best = i
        }
        val g = grass.removeAt(best)
        c.fx = p.x; c.fy = p.y; c.ax = g.x; c.ay = g.y
        c.eatStart = time; c.t = time
    }

    private fun feedHungry(now: Long) { for (c in chickens) tryEat(c, now) }

    fun canWater() = water > 0 && grass.size < Rules.GRASS_MAX

    /** Mọc một vùng cỏ ở chỗ ngẫu nhiên. */
    fun waterGrass(now: Long): Boolean {
        advance(now)
        if (!canWater()) return false
        water--
        val cx = 0.15f + 0.7f * noise(now, 1)
        val cy = 0.15f + 0.7f * noise(now, 2)
        var i = 0L
        var added = 0
        while (added < Rules.GRASS_PER_WATER && grass.size < Rules.GRASS_MAX) {
            val dx = noise(now, 10 + i) - 0.5f
            val dy = noise(now, 500 + i) - 0.5f
            i++
            val s = Spot((cx + dx * 0.3f).coerceIn(0f, 1f), (cy + dy * 0.3f).coerceIn(0f, 1f))
            if (i < 60 && grass.any { abs(it.x - s.x) < 0.05f && abs(it.y - s.y) < 0.06f }) continue
            grass.add(s); added++
        }
        feedHungry(now)
        return true
    }

    fun refill(now: Long): Boolean {
        advance(now)
        if (water > 0 || refillAt != 0L) return false
        coins -= minOf(coins, Rules.REFILL_COST)
        refillAt = now + Rules.REFILL_TIME
        return true
    }

    /** Đưa tiến trình các gà không ăn tới now (gọi sau advance). */
    private fun settle(now: Long) {
        for (c in chickens) {
            if (c.eating || c.fed == 0L) continue
            val el = (now - c.t).coerceIn(0, c.fed)
            c.prog = minOf(Rules.LAY_TIME, c.prog + el)
            c.fed -= el
            c.t = now
        }
    }

    private fun afterPickup(now: Long) {
        advance(now)
        feedHungry(now)
    }

    fun collect(now: Long): Int {
        advance(now)
        settle(now)
        val n = eggs.size
        stock += n
        eggs.clear()
        afterPickup(now)
        return n
    }

    /** Nhặt đúng một quả trứng (chạm vào trứng trong app). */
    fun collectOne(now: Long, egg: Spot): Boolean {
        advance(now)
        settle(now)
        val ok = eggs.remove(egg)
        if (ok) stock++
        afterPickup(now)
        return ok
    }

    fun sell(now: Long): Boolean {
        advance(now)
        if (stock == 0 || truckAt != 0L) return false
        truckMoney = stock * Rules.EGG_PRICE
        stock = 0
        truckAt = now + Rules.TRUCK_TIME
        return true
    }

    fun buyChicken(now: Long): Boolean {
        advance(now)
        if (coins < Rules.CHICKEN_COST || chickens.size >= Rules.CHICKEN_MAX) return false
        coins -= Rules.CHICKEN_COST
        addChicken(now)
        tryEat(chickens.last(), now)
        return true
    }

    /** Mốc gần nhất widget cần vẽ lại. */
    fun nextEventAt(now: Long): Long? {
        val list = mutableListOf(refillAt, truckAt)
        for (c in chickens) {
            nextOf(c)?.let { list.add(it) }
            if (c.eating) list.add(c.eatStart + Rules.EAT_TIME / 2)
        }
        return list.filter { it > now }.minOrNull()
    }
}
