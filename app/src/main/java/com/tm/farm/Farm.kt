package com.tm.farm

/** Logic thuần Kotlin, không phụ thuộc Android. Mọi thời gian là mili-giây "giờ game". */
object Rules {
    const val MIN = 60_000L
    const val LAY_TIME = 120 * MIN      // gà ăn xong -> đẻ
    const val EAT_TIME = 20 * MIN       // thời gian hiện hoạt ảnh đang ăn
    const val REFILL_TIME = 30 * MIN    // bơm đầy giếng
    const val TRUCK_TIME = 60 * MIN     // xe đi bán

    const val WELL_MAX = 5              // số lần tưới mỗi giếng đầy
    const val GRASS_PER_WATER = 4
    const val GRASS_MAX = 20
    const val NEST_MAX = 12             // trứng tối đa trên bãi
    const val CHICKEN_MAX = 6

    const val REFILL_COST = 5
    const val CHICKEN_COST = 100
    const val EGG_PRICE = 10
    const val START_COINS = 100
}

data class Farm(
    var coins: Int = Rules.START_COINS,
    var water: Int = Rules.WELL_MAX,
    var refillAt: Long = 0,                 // 0 = không bơm
    var grass: Int = 0,
    var eggsOnGround: Int = 0,
    var stock: Int = 0,                     // trứng trong kho
    var truckAt: Long = 0,                  // 0 = xe đang ở nhà
    var truckMoney: Int = 0,
    var chickens: MutableList<Long> = mutableListOf(0L, 0L) // 0 = đói, khác 0 = giờ đẻ
) {
    /** Tính mọi thứ đã xảy ra tới thời điểm now (offline progress). */
    fun advance(now: Long) {
        if (refillAt != 0L && now >= refillAt) { water = Rules.WELL_MAX; refillAt = 0 }
        if (truckAt != 0L && now >= truckAt) { coins += truckMoney; truckMoney = 0; truckAt = 0 }
        // Xử lý lần đẻ sớm nhất trước, để cỏ được chia đúng thứ tự thời gian.
        while (eggsOnGround < Rules.NEST_MAX) {
            var i = -1
            for (k in chickens.indices) {
                val t = chickens[k]
                if (t != 0L && t <= now && (i < 0 || t < chickens[i])) i = k
            }
            if (i < 0) break
            val laidAt = chickens[i]
            eggsOnGround++
            chickens[i] = 0
            eat(i, laidAt)
        }
    }

    private fun eat(i: Int, at: Long) {
        if (chickens[i] == 0L && grass > 0) { grass--; chickens[i] = at + Rules.LAY_TIME }
    }

    private fun feedHungry(now: Long) { for (i in chickens.indices) eat(i, now) }

    fun canWater(now: Long): Boolean {
        advance(now)
        return water > 0 && grass < Rules.GRASS_MAX
    }

    fun waterGrass(now: Long): Boolean {
        if (!canWater(now)) return false
        water--
        grass = minOf(Rules.GRASS_MAX, grass + Rules.GRASS_PER_WATER)
        feedHungry(now)
        return true
    }

    /** Chỉ bơm khi giếng cạn. Hết tiền vẫn bơm được để không kẹt game. */
    fun refill(now: Long): Boolean {
        advance(now)
        if (water > 0 || refillAt != 0L) return false
        coins -= minOf(coins, Rules.REFILL_COST)
        refillAt = now + Rules.REFILL_TIME
        return true
    }

    fun collect(now: Long): Int {
        advance(now)
        val n = eggsOnGround
        // Gà bị nghẽn vì bãi đầy sẽ đẻ tính từ lúc dọn trứng.
        if (n >= Rules.NEST_MAX) {
            for (i in chickens.indices) if (chickens[i] != 0L && chickens[i] < now) chickens[i] = now
        }
        stock += n
        eggsOnGround = 0
        advance(now)
        return n
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
        chickens.add(0L)
        eat(chickens.lastIndex, now)
        return true
    }

    /** Mốc gần nhất mà widget cần vẽ lại, null nếu không có. */
    fun nextEventAt(now: Long): Long? {
        val list = mutableListOf(refillAt, truckAt)
        for (t in chickens) if (t != 0L) {
            list.add(t)
            list.add(t - Rules.LAY_TIME + Rules.EAT_TIME)
        }
        return list.filter { it > now }.minOrNull()
    }
}
