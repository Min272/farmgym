package com.tm.farm

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val loop = object : Runnable {
        override fun run() { show(Store.load(this@MainActivity).also { it.advance(Store.now(this@MainActivity)) }); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        bind(R.id.water) { f, now -> f.waterGrass(now) }
        bind(R.id.refill) { f, now -> f.refill(now) }
        bind(R.id.collect) { f, now -> f.collect(now) }
        bind(R.id.sell) { f, now -> f.sell(now) }
        bind(R.id.buy) { f, now -> f.buyChicken(now) }
        button(R.id.speed).setOnClickListener {
            val next = when (Store.speed(this)) { 1 -> 60; 60 -> 600; else -> 1 }
            Store.update(this)
            Store.setSpeed(this, next)
            show(Store.update(this))
        }
        button(R.id.reset).setOnClickListener {
            AlertDialog.Builder(this).setMessage("Xoá toàn bộ tiến trình?")
                .setPositiveButton("Xoá") { _, _ ->
                    Store.save(this, Farm())
                    show(Store.update(this))
                }
                .setNegativeButton("Huỷ", null).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Store.update(this)
        handler.post(loop)
    }

    override fun onPause() {
        handler.removeCallbacks(loop)
        Store.update(this)
        super.onPause()
    }

    private fun button(id: Int) = findViewById<Button>(id)

    private fun bind(id: Int, action: (Farm, Long) -> Unit) {
        button(id).setOnClickListener { show(Store.update(this, action)) }
    }

    private fun show(f: Farm) {
        val now = Store.now(this)
        val hungry = f.chickens.count { it == 0L }
        val nextEgg = f.chickens.filter { it > now }.minOrNull()
        findViewById<TextView>(R.id.status).text = buildString {
            appendLine("💰 ${f.coins} xu")
            appendLine("🐔 ${f.chickens.size}/${Rules.CHICKEN_MAX} con (đói: $hungry)")
            appendLine("🌱 Cỏ: ${f.grass}/${Rules.GRASS_MAX}")
            appendLine("💧 Giếng: ${f.water}/${Rules.WELL_MAX}" + if (f.refillAt != 0L) " (đầy lúc ${clock(this@MainActivity, f.refillAt)})" else "")
            appendLine("🥚 Trên bãi: ${f.eggsOnGround}/${Rules.NEST_MAX} | Kho: ${f.stock}")
            if (nextEgg != null) appendLine("⏳ Trứng tiếp theo: ${clock(this@MainActivity, nextEgg)}")
            if (f.truckAt != 0L) appendLine("🚚 Xe về lúc ${clock(this@MainActivity, f.truckAt)} (+${f.truckMoney} xu)")
        }
        button(R.id.water).isEnabled = f.water > 0 && f.grass < Rules.GRASS_MAX
        button(R.id.refill).isEnabled = f.water == 0 && f.refillAt == 0L
        button(R.id.collect).isEnabled = f.eggsOnGround > 0
        button(R.id.sell).isEnabled = f.stock > 0 && f.truckAt == 0L
        button(R.id.buy).isEnabled = f.coins >= Rules.CHICKEN_COST && f.chickens.size < Rules.CHICKEN_MAX
        button(R.id.speed).text = "Debug: tua x${Store.speed(this)}"
    }
}
