package com.tm.farm

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, options: Bundle) { Store.update(c) }
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
    private val GRASS = intArrayOf(R.id.grass0, R.id.grass1, R.id.grass2, R.id.grass3, R.id.grass4, R.id.grass5, R.id.grass6, R.id.grass7, R.id.grass8, R.id.grass9, R.id.grass10, R.id.grass11, R.id.grass12, R.id.grass13, R.id.grass14, R.id.grass15, R.id.grass16, R.id.grass17, R.id.grass18, R.id.grass19, R.id.grass20, R.id.grass21, R.id.grass22, R.id.grass23, R.id.grass24, R.id.grass25, R.id.grass26, R.id.grass27, R.id.grass28, R.id.grass29)
    private val EGGS = intArrayOf(R.id.egg0, R.id.egg1, R.id.egg2, R.id.egg3, R.id.egg4, R.id.egg5, R.id.egg6, R.id.egg7, R.id.egg8, R.id.egg9, R.id.egg10, R.id.egg11)
    private val WELL = intArrayOf(R.id.well0, R.id.well1, R.id.well2, R.id.well3, R.id.well4, R.id.well5, R.id.well6, R.id.well7)

    fun render(c: Context, f: Farm, now: Long) {
        val m = AppWidgetManager.getInstance(c)
        for (id in m.getAppWidgetIds(ComponentName(c, PastureWidget::class.java))) {
            m.updateAppWidget(id, pasture(c, f, now, m.getAppWidgetOptions(id)))
        }
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

    /** Đặt ảnh vuông cạnh size (px) tại tâm (x, y) bằng cách chỉnh padding. */
    private fun place(v: RemoteViews, id: Int, x: Float, y: Float, size: Float, w: Int, h: Int) {
        val l = (x - size / 2).toInt().coerceIn(0, maxOf(0, w - size.toInt()))
        val t = (y - size / 2).toInt().coerceIn(0, maxOf(0, h - size.toInt()))
        v.setViewPadding(id, l, t, maxOf(0, w - l - size.toInt()), maxOf(0, h - t - size.toInt()))
    }

    private fun pasture(c: Context, f: Farm, now: Long, options: Bundle): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_pasture)
        // Kích thước vùng cỏ ước lượng theo kích thước widget (dọc màn hình)
        val density = c.resources.displayMetrics.density
        val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
        val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 220
        val w = (wDp * density).toInt()
        val h = (maxOf(80, hDp - 46) * density).toInt()
        val s = minOf(w * 0.2f, h * 0.35f)
        fun px(x: Float) = s / 2 + x * (w - s)
        fun py(y: Float) = s / 2 + y * (h - s)

        // Cỏ còn nguyên + cỏ đang bị ăn (mờ dần)
        val tufts = f.grass.map { Triple(it.x, it.y, 255) } + f.chickens.filter { it.eating }.map {
            val left = 1f - ((now - it.eatStart).toFloat() / Rules.EAT_TIME).coerceIn(0f, 1f)
            Triple(it.ax, it.ay, (60 + 195 * left).toInt())
        }
        for (i in GRASS.indices) {
            val g = tufts.getOrNull(i)
            v.setViewVisibility(GRASS[i], if (g == null) View.INVISIBLE else View.VISIBLE)
            if (g == null) continue
            place(v, GRASS[i], px(g.first), py(g.second) + s * 0.3f, s * 0.6f, w, h)
            v.setInt(GRASS[i], "setImageAlpha", g.third)
        }
        for (i in EGGS.indices) {
            val e = f.eggs.getOrNull(i)
            v.setViewVisibility(EGGS[i], if (e == null) View.INVISIBLE else View.VISIBLE)
            if (e != null) place(v, EGGS[i], px(e.x), py(e.y) + s * 0.3f, s * 0.32f, w, h)
        }
        for (i in SLOTS.indices) {
            val k = f.chickens.getOrNull(i)
            v.setViewVisibility(SLOTS[i], if (k == null) View.INVISIBLE else View.VISIBLE)
            if (k == null) continue
            // Đang ăn: đứng tại đám cỏ. Còn lại: đi qua lại quanh vị trí hiện tại.
            val p = f.pose(k, now)
            val x = px(if (k.eating) k.ax else p.x)
            val y = py(if (k.eating) k.ay else p.y)
            for (j in 0 until 8) {
                val goingLeft = j < 4
                val step = if (goingLeft) j else 7 - j
                val offset = if (k.eating) 0f else (1.5f - step) * s * 0.22f
                val img = when {
                    k.eating -> Sprites.EAT[j]
                    goingLeft -> if (k.hungry) Sprites.HUNGRY[j] else Sprites.WALK[2][j]
                    else -> Sprites.RIGHT[j]
                }
                v.setImageViewResource(FRAMES[i][j], img)
                place(v, FRAMES[i][j], x + offset, y, s, w, h)
            }
        }
        v.setTextViewText(R.id.info, "🌱 ${f.grass.size}   🥚 ${f.eggs.size}/${Rules.NEST_MAX}   ❗ ${f.chickens.count { it.hungry }}")
        v.setTextViewText(R.id.btn_collect, "Nhặt 🥚 ${f.eggs.size}")
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
                f.refillAt != 0L -> Sprites.PUMP[j]
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
