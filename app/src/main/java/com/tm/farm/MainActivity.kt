package com.tm.farm

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val loop = object : Runnable {
        override fun run() {
            farmView().farm?.let { it.advance(Store.now(this@MainActivity)); show(it) }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        farmView().onEggTap = { egg -> apply { f, now -> f.collectOne(now, egg) } }
        farmView().onWellTap = { apply { f, now -> toast(f.tapWell(now)) } }
        farmView().onTruckTap = { apply { f, now -> toast(f.tapTruck(now)) } }
        bind(R.id.water) { f, now -> f.waterGrass(now) }
        bind(R.id.refill) { f, now -> f.refill(now) }
        bind(R.id.collect) { f, now -> f.collect(now) }
        bind(R.id.sell) { f, now -> f.sell(now) }
        bind(R.id.buy) { f, now -> f.buyChicken(now) }
        button(R.id.speed).setOnClickListener {
            val next = when (Store.speed(this)) { 1 -> 60; 60 -> 600; else -> 1 }
            Store.update(this)
            Store.setSpeed(this, next)
            refresh(Store.update(this))
        }
        button(R.id.reset).setOnClickListener {
            AlertDialog.Builder(this).setMessage("Xoá toàn bộ tiến trình?")
                .setPositiveButton("Xoá") { _, _ ->
                    Store.save(this, Farm.new(Store.now(this)))
                    refresh(Store.update(this))
                }
                .setNegativeButton("Huỷ", null).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh(Store.update(this))
        handler.post(loop)
    }

    override fun onPause() {
        handler.removeCallbacks(loop)
        Store.update(this)
        super.onPause()
    }

    private fun button(id: Int) = findViewById<Button>(id)

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun farmView() = findViewById<FarmView>(R.id.farm)

    private fun refresh(f: Farm) {
        farmView().farm = f
        farmView().invalidate()
        show(f)
    }

    private fun apply(action: (Farm, Long) -> Unit) = refresh(Store.update(this, action))

    private fun bind(id: Int, action: (Farm, Long) -> Unit) {
        button(id).setOnClickListener { apply(action) }
    }

    private fun show(f: Farm) {
        val extra = mutableListOf<String>()
        if (f.refillAt != 0L) extra.add("💧 đầy lúc ${clock(this, f.refillAt)}")
        if (f.truckAt != 0L) extra.add("🚚 về lúc ${clock(this, f.truckAt)} (+${f.truckMoney} xu)")
        findViewById<TextView>(R.id.status).text =
            "💰 ${f.coins}   🐔 ${f.chickens.size} (đói ${f.chickens.count { it.hungry }})   🌱 ${f.grass.size}\n" +
            "💧 ${f.water}/${Rules.WELL_MAX}   🥚 bãi ${f.eggs.size}/${Rules.NEST_MAX}, kho ${f.stock}\n" +
            extra.joinToString("   ")
        button(R.id.water).isEnabled = f.canWater()
        button(R.id.refill).isEnabled = f.canRefill()
        button(R.id.collect).isEnabled = f.eggs.isNotEmpty()
        button(R.id.sell).isEnabled = f.stock > 0 && f.truckAt == 0L
        button(R.id.buy).isEnabled = f.coins >= Rules.CHICKEN_COST && f.chickens.size < Rules.CHICKEN_MAX
        button(R.id.speed).text = "Debug: tua x${Store.speed(this)}"
    }
}
