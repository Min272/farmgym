package com.tm.farm

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val ACT_COLLECT = "com.tm.farm.COLLECT"
const val ACT_WATER = "com.tm.farm.WATER"
const val ACT_REFILL = "com.tm.farm.REFILL"
const val ACT_TICK = "com.tm.farm.TICK"

class PastureWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { Store.update(c) }
}

class WellWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { Store.update(c) }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Store.update(c) { f, now ->
            when (i.action) {
                ACT_COLLECT -> f.collect(now)
                ACT_WATER -> f.waterGrass(now)
                ACT_REFILL -> f.refill(now)
            }
        }
    }
}

/** Giờ thật dạng HH:mm. Dùng mốc giờ thay đếm ngược để không bị chạy số âm khi widget cập nhật trễ. */
fun clock(c: Context, gameTime: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(Store.toReal(c, gameTime)))

object Widgets {
    private val SLOTS = intArrayOf(R.id.slot0, R.id.slot1, R.id.slot2, R.id.slot3, R.id.slot4, R.id.slot5)
    private val FRAMES = arrayOf(
        intArrayOf(R.id.f0_0, R.id.f0_1, R.id.f0_2, R.id.f0_3, R.id.f0_4, R.id.f0_5, R.id.f0_6, R.id.f0_7),
        intArrayOf(R.id.f1_0, R.id.f1_1, R.id.f1_2, R.id.f1_3, R.id.f1_4, R.id.f1_5, R.id.f1_6, R.id.f1_7),
        intArrayOf(R.id.f2_0, R.id.f2_1, R.id.f2_2, R.id.f2_3, R.id.f2_4, R.id.f2_5, R.id.f2_6, R.id.f2_7),
        intArrayOf(R.id.f3_0, R.id.f3_1, R.id.f3_2, R.id.f3_3, R.id.f3_4, R.id.f3_5, R.id.f3_6, R.id.f3_7),
        intArrayOf(R.id.f4_0, R.id.f4_1, R.id.f4_2, R.id.f4_3, R.id.f4_4, R.id.f4_5, R.id.f4_6, R.id.f4_7),
        intArrayOf(R.id.f5_0, R.id.f5_1, R.id.f5_2, R.id.f5_3, R.id.f5_4, R.id.f5_5, R.id.f5_6, R.id.f5_7),
    )
    private val EGGS = intArrayOf(R.id.egg0, R.id.egg1, R.id.egg2, R.id.egg3, R.id.egg4, R.id.egg5, R.id.egg6, R.id.egg7, R.id.egg8, R.id.egg9, R.id.egg10, R.id.egg11)
    private val WELL = intArrayOf(R.id.well0, R.id.well1, R.id.well2, R.id.well3, R.id.well4, R.id.well5, R.id.well6, R.id.well7)
    private val EAT = intArrayOf(R.drawable.chicken_eat0, R.drawable.chicken_eat1, R.drawable.chicken_eat2, R.drawable.chicken_eat3, R.drawable.chicken_eat4, R.drawable.chicken_eat5, R.drawable.chicken_eat6, R.drawable.chicken_eat7)
    private val HUNGRY = intArrayOf(R.drawable.chicken_hungry0, R.drawable.chicken_hungry1, R.drawable.chicken_hungry2, R.drawable.chicken_hungry3, R.drawable.chicken_hungry4, R.drawable.chicken_hungry5, R.drawable.chicken_hungry6, R.drawable.chicken_hungry7)
    private val PUMP = intArrayOf(R.drawable.well_pump0, R.drawable.well_pump1, R.drawable.well_pump2, R.drawable.well_pump3, R.drawable.well_pump4, R.drawable.well_pump5, R.drawable.well_pump6, R.drawable.well_pump7)

    fun render(c: Context, f: Farm, now: Long) {
        val m = AppWidgetManager.getInstance(c)
        m.updateAppWidget(ComponentName(c, PastureWidget::class.java), pasture(c, f, now))
        m.updateAppWidget(ComponentName(c, WellWidget::class.java), well(c, f))
        schedule(c, f, now)
    }

    private fun action(c: Context, name: String): PendingIntent = PendingIntent.getBroadcast(
        c, 0, Intent(c, ActionReceiver::class.java).setAction(name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun pasture(c: Context, f: Farm, now: Long): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_pasture)
        for (i in SLOTS.indices) {
            val t = f.chickens.getOrNull(i)
            v.setViewVisibility(SLOTS[i], if (t == null) View.INVISIBLE else View.VISIBLE)
            if (t == null) continue
            val eating = t != 0L && now < t - Rules.LAY_TIME + Rules.EAT_TIME
            for (j in 0 until 8) {
                val img = when {
                    t == 0L -> HUNGRY[j]
                    eating -> EAT[j]
                    else -> R.drawable.chicken_idle
                }
                v.setImageViewResource(FRAMES[i][j], img)
            }
        }
        for (i in EGGS.indices) {
            v.setViewVisibility(EGGS[i], if (i < f.eggsOnGround) View.VISIBLE else View.INVISIBLE)
        }
        v.setViewVisibility(R.id.grass_img, if (f.grass > 0) View.VISIBLE else View.INVISIBLE)
        v.setTextViewText(R.id.info, "🌱 ${f.grass}   🥚 ${f.eggsOnGround}/${Rules.NEST_MAX}")
        v.setTextViewText(R.id.btn_collect, "Nhặt 🥚 ${f.eggsOnGround}")
        v.setTextViewText(R.id.btn_water, "Tưới 💧 ${f.water}")
        v.setOnClickPendingIntent(R.id.btn_collect, action(c, ACT_COLLECT))
        v.setOnClickPendingIntent(R.id.btn_water, action(c, ACT_WATER))
        v.setOnClickPendingIntent(R.id.field, openApp(c))
        return v
    }

    private fun well(c: Context, f: Farm): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_well)
        for (j in WELL.indices) {
            val img = when {
                f.refillAt != 0L -> PUMP[j]        // đang bơm -> chạy hoạt ảnh
                f.water > 0 -> R.drawable.well_full
                else -> R.drawable.well_empty
            }
            v.setImageViewResource(WELL[j], img)
        }
        val text = when {
            f.refillAt != 0L -> "Đầy lúc ${clock(c, f.refillAt)}"
            f.water == 0 -> "Bơm (${Rules.REFILL_COST} xu)"
            else -> "💧 ${f.water}/${Rules.WELL_MAX}"
        }
        v.setTextViewText(R.id.well_text, text)
        v.setOnClickPendingIntent(R.id.well_root, if (f.water == 0) action(c, ACT_REFILL) else openApp(c))
        return v
    }

    /** Hẹn giờ không chính xác tuyệt đối: Android có thể trễ để tiết kiệm pin. */
    private fun schedule(c: Context, f: Farm, now: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val tick = action(c, ACT_TICK)
        am.cancel(tick)
        val m = AppWidgetManager.getInstance(c)
        val hasWidget = listOf(PastureWidget::class.java, WellWidget::class.java)
            .any { m.getAppWidgetIds(ComponentName(c, it)).isNotEmpty() }
        val next = f.nextEventAt(now) ?: return
        if (hasWidget) am.setAndAllowWhileIdle(AlarmManager.RTC, Store.toReal(c, next) + 1000, tick)
    }
}
