package com.tm.farm

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

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
    const val GRASS_PER_WATER = 6      // trung bình; mỗi lần tưới mọc 5..7 đám
    const val GRASS_KIND_MIN = 8       // 8..15 là các dáng cỏ lớn (13..15 có hoa)
    const val GRASS_KIND_MAX = 15
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

data class Spot(val x: Float, val y: Float, val kind: Int = 0)

/** Vùng đất cho gà, cỏ, trứng: hình elip (lồi nên đi thẳng giữa 2 điểm không ra ngoài, không đụng giếng và xe). */
object Field {
    const val CX = 0.50f
    const val CY = 0.565f
    const val RX = 0.31f
    const val RY = 0.235f

    fun at(a: Float, b: Float): Spot {
        val r = sqrt(a)
        val t = b * 2 * PI.toFloat()
        return Spot(CX + RX * r * cos(t), CY + RY * r * sin(t))
    }

    /** Kéo điểm ở ngoài về mép vùng đất. */
    fun clamp(x: Float, y: Float): Spot {
        val d = hypot((x - CX) / RX, (y - CY) / RY)
        return if (d <= 1f) Spot(x, y) else Spot(CX + (x - CX) / d * 0.999f, CY + (y - CY) / d * 0.999f)
    }
}

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
    var fy: Float = 0f,
    var gk: Int = 12              // dáng đám cỏ đang ăn
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
        val s = Field.at(noise(now, id.toLong()), noise(now, id + 99L))
        chickens.add(Chicken(id, now, ax = s.x, ay = s.y, at = now))
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
        fun way(i: Long) = if (i == 0L) Spot(c.ax, c.ay) else Field.at(noise(seed, i * 2), noise(seed, i * 2 + 1))
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
        c.fx = p.x; c.fy = p.y; c.ax = g.x; c.ay = g.y; c.gk = g.kind
        c.eatStart = time; c.t = time
    }

    private fun feedHungry(now: Long) { for (c in chickens) tryEat(c, now) }

    fun canWater() = water > 0 && grass.size < Rules.GRASS_MAX

    /** Mọc một vùng cỏ quanh center (null = chỗ ngẫu nhiên). Mỗi lần khác số đám, độ rộng và dáng. */
    fun waterGrass(now: Long, center: Spot? = null): Boolean {
        advance(now)
        if (!canWater()) return false
        water--
        val mid = center ?: Field.at(noise(now, 1), noise(now, 2))
        val count = Rules.GRASS_PER_WATER - 1 + (noise(now, 3) * 3).toInt()
        val spread = 0.14f + 0.14f * noise(now, 4)
        val kinds = Rules.GRASS_KIND_MAX - Rules.GRASS_KIND_MIN + 1
        var i = 0L
        var added = 0
        while (added < count && grass.size < Rules.GRASS_MAX) {
            val dx = noise(now, 10 + i) - 0.5f
            val dy = noise(now, 500 + i) - 0.5f
            val kind = Rules.GRASS_KIND_MIN + (noise(now, 900 + i) * kinds).toInt().coerceAtMost(kinds - 1)
            i++
            val p = Field.clamp(mid.x + dx * spread, mid.y + dy * spread)
            if (i < 60 && grass.any { abs(it.x - p.x) < 0.035f && abs(it.y - p.y) < 0.045f }) continue
            grass.add(p.copy(kind = kind)); added++
        }
        feedHungry(now)
        return true
    }

    /** Hình cỏ khi bị ăn: left từ 1 (nguyên) về 0, cỏ thưa dần qua các giai đoạn. */
    fun grassFrame(kind: Int, left: Float) =
        if (left > 0.85f) kind else (left / 0.85f * 8).toInt().coerceIn(0, 7)

    fun canRefill() = water < Rules.WELL_MAX && refillAt == 0L

    fun refill(now: Long): Boolean {
        advance(now)
        if (!canRefill()) return false
        coins -= minOf(coins, Rules.REFILL_COST)
        refillAt = now + Rules.REFILL_TIME
        return true
    }

    /** Thiếu cỏ: số đám cỏ còn trống ít hơn số gà. */
    fun lackGrass() = grass.size < chickens.size

    /** Chạm một ô trên bãi: có trứng trong ô thì nhặt, không thì tưới quanh ô nếu thiếu cỏ. */
    fun tapCell(now: Long, scene: Scene, cell: Int): String {
        advance(now)
        val here = scene.eggsInCell(cell, eggs)
        if (here.isNotEmpty()) {
            settle(now)
            var n = 0
            for (e in here) if (eggs.remove(e)) { stock++; n++ }
            afterPickup(now)
            return "Đã nhặt $n trứng"
        }
        if (!lackGrass()) return "Cỏ còn đủ"
        if (waterGrass(now, scene.cellCenter(cell))) return "Đã tưới cỏ (giếng còn $water)"
        return "Hết nước, hãy chạm vào giếng"
    }

    /** Chạm xe tải: bán trứng trong kho. */
    fun tapTruck(now: Long): String {
        advance(now)
        if (truckAt != 0L) return "Xe đang đi bán"
        val n = stock
        return if (sell(now)) "Xe chở $n trứng đi bán" else "Kho chưa có trứng"
    }

    /** Chạm widget giếng: bơm đầy. */
    fun tapWell(now: Long): String {
        advance(now)
        if (refillAt != 0L) return "Giếng đang bơm"
        return if (refill(now)) "Bắt đầu bơm đầy giếng" else "Giếng đang đầy"
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
