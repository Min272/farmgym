package com.tm.farm

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

const val ACT_CELL = "com.tm.farm.CELL"
const val ACT_WELL = "com.tm.farm.WELL"
const val ACT_TRUCK = "com.tm.farm.TRUCK"
const val ACT_TICK = "com.tm.farm.TICK"

class FarmWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { Store.update(c) }
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, options: Bundle) { Store.update(c) }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        var message: String? = null
        Store.update(c) { f, now ->
            message = when (i.action) {
                ACT_CELL -> f.tapCell(
                    now, Scene(i.getFloatExtra("w", 1f), i.getFloatExtra("h", 1f)), i.getIntExtra("cell", 0)
                )
                ACT_WELL -> f.tapWell(now)
                ACT_TRUCK -> f.tapTruck(now)
                else -> null            // ACT_TICK: chỉ vẽ lại
            }
        }
        message?.let { Toast.makeText(c, it, Toast.LENGTH_SHORT).show() }
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
    private val CELLS = intArrayOf(R.id.cell0, R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4, R.id.cell5, R.id.cell6, R.id.cell7, R.id.cell8, R.id.cell9, R.id.cell10, R.id.cell11)

    fun render(c: Context, f: Farm, now: Long) {
        val m = AppWidgetManager.getInstance(c)
        for (id in m.getAppWidgetIds(ComponentName(c, FarmWidget::class.java))) {
            m.updateAppWidget(id, build(c, f, now, id, m.getAppWidgetOptions(id)))
        }
        schedule(c, f, now)
    }

    private fun broadcast(c: Context, intent: Intent): PendingIntent = PendingIntent.getBroadcast(
        c, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun action(c: Context, name: String) = broadcast(c, Intent(c, ActionReceiver::class.java).setAction(name))

    /** data khác nhau để mỗi ô của mỗi widget có PendingIntent riêng. */
    private fun cellAction(c: Context, widget: Int, cell: Int, w: Int, h: Int) = broadcast(
        c, Intent(c, ActionReceiver::class.java).setAction(ACT_CELL)
            .setData(Uri.parse("farm://cell/$widget/$cell"))
            .putExtra("cell", cell).putExtra("w", w.toFloat()).putExtra("h", h.toFloat())
    )

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Đặt view vào ô vuông bằng cách chỉnh padding của lớp phủ toàn khung. */
    private fun place(v: RemoteViews, id: Int, b: Box, w: Int, h: Int) {
        val size = b.size.toInt()
        val l = (b.x - b.size / 2).toInt().coerceIn(0, maxOf(0, w - size))
        val t = (b.y - b.size / 2).toInt().coerceIn(0, maxOf(0, h - size))
        v.setViewPadding(id, l, t, maxOf(0, w - l - size), maxOf(0, h - t - size))
    }

    private const val LAYER_PIXELS = 180_000f   // giữ ảnh dưới khoảng 720 KB

    /** Vẽ tất cả đám cỏ vào một ảnh, thay vì mỗi đám một view. */
    private fun grassLayer(
        c: Context, scene: Scene, tufts: List<Triple<Float, Float, Int>>, w: Int, h: Int
    ): Bitmap {
        val k = minOf(1f, sqrt(LAYER_PIXELS / (w.toFloat() * h)))
        val out = Bitmap.createBitmap(maxOf(1, (w * k).toInt()), maxOf(1, (h * k).toInt()), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.scale(out.width / w.toFloat(), out.height / h.toFloat())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val images = HashMap<Int, Bitmap>()
        val rect = RectF()
        for ((x, y, frame) in tufts) {
            val img = images.getOrPut(frame) { BitmapFactory.decodeResource(c.resources, Sprites.GRASS[frame]) }
            val cx = scene.sx(x)
            val cy = scene.sy(y) + scene.foot
            val half = scene.tuftSize / 2
            rect.set(cx - half, cy - half, cx + half, cy + half)
            canvas.drawBitmap(img, null as Rect?, rect, paint)
        }
        return out
    }

    private fun build(c: Context, f: Farm, now: Long, widget: Int, options: Bundle): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_farm)
        // Kích thước ước lượng theo tuỳ chọn của launcher (màn hình dọc)
        val density = c.resources.displayMetrics.density
        val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 320
        val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 320
        val w = (wDp * density).toInt()
        val h = (hDp * density).toInt()
        val scene = Scene(w.toFloat(), h.toFloat())
        v.setImageViewResource(R.id.map, Sprites.MAPS[scene.variant])

        // Cỏ còn nguyên + cỏ đang bị ăn (thưa dần)
        val tufts = f.grass.map { Triple(it.x, it.y, it.kind) } + f.chickens.filter { it.eating }.map {
            val left = 1f - ((now - it.eatStart).toFloat() / Rules.EAT_TIME).coerceIn(0f, 1f)
            Triple(it.ax, it.ay, f.grassFrame(it.gk, left))
        }
        v.setImageViewBitmap(R.id.grass_layer, grassLayer(c, scene, tufts.sortedBy { it.second }, w, h))
        for (i in EGGS.indices) {
            val e = f.eggs.getOrNull(i)
            v.setViewVisibility(EGGS[i], if (e == null) View.INVISIBLE else View.VISIBLE)
            if (e != null) place(v, EGGS[i], scene.egg(e), w, h)
        }
        // Gà ở thấp hơn vẽ sau để che đúng thứ tự
        val birds = f.chickens.map { it to f.pose(it, now) }.sortedBy { it.second.y }
        for (i in SLOTS.indices) {
            val pair = birds.getOrNull(i)
            v.setViewVisibility(SLOTS[i], if (pair == null) View.INVISIBLE else View.VISIBLE)
            if (pair == null) continue
            val (k, p) = pair
            val x = scene.sx(if (k.eating) k.ax else p.x)
            val y = scene.sy(if (k.eating) k.ay else p.y)
            for (j in 0 until 8) {
                val goingLeft = j < 4
                val step = if (goingLeft) j else 7 - j
                val offset = if (k.eating) 0f else (1.5f - step) * scene.chicken * 0.18f
                val img = when {
                    k.eating -> Sprites.EAT[j]
                    goingLeft -> if (k.hungry) Sprites.HUNGRY[j] else Sprites.WALK[2][j]
                    else -> Sprites.RIGHT[j]
                }
                v.setImageViewResource(FRAMES[i][j], img)
                place(v, FRAMES[i][j], Box(x + offset, y, scene.chicken), w, h)
            }
        }

        // Giếng
        for (j in WELL.indices) {
            val img = when {
                f.refillAt != 0L -> Sprites.PUMP[j]
                f.water > 0 -> R.drawable.well_full
                else -> R.drawable.well_empty
            }
            v.setImageViewResource(WELL[j], img)
        }
        val wellText = if (f.refillAt != 0L) "⏳ ${clock(c, f.refillAt)}" else "💧 ${f.water}/${Rules.WELL_MAX}"
        v.setTextViewText(R.id.well_text, wellText)
        place(v, R.id.well_box, scene.well, w, h)
        v.setOnClickPendingIntent(R.id.well_hit, action(c, ACT_WELL))

        // Xe tải
        v.setViewVisibility(R.id.truck_img, if (f.truckAt == 0L) View.VISIBLE else View.INVISIBLE)
        v.setTextViewText(R.id.truck_text, if (f.truckAt != 0L) "🚚 ${clock(c, f.truckAt)}" else "🥚 ${f.stock}")
        place(v, R.id.truck_box, scene.truck, w, h)
        v.setOnClickPendingIntent(R.id.truck_hit, action(c, ACT_TRUCK))

        place(v, R.id.gear_box, scene.gear, w, h)
        v.setOnClickPendingIntent(R.id.gear_hit, openApp(c))

        val hungry = f.chickens.count { it.hungry }
        val info = "💰 ${f.coins}  🌱 ${f.grass.size}" + (if (hungry > 0) "  ❗$hungry" else "") +
            if (f.chickens.isEmpty()) "  ☠ Hết gà" else ""
        v.setTextViewText(R.id.info, info)

        for (cell in CELLS.indices) v.setOnClickPendingIntent(CELLS[cell], cellAction(c, widget, cell, w, h))
        return v
    }

    /** Hẹn giờ không chính xác tuyệt đối: Android có thể trễ để tiết kiệm pin. */
    private fun schedule(c: Context, f: Farm, now: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val tick = action(c, ACT_TICK)
        am.cancel(tick)
        val ids = AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, FarmWidget::class.java))
        val next = f.nextEventAt(now) ?: return
        if (ids.isNotEmpty()) am.setAndAllowWhileIdle(AlarmManager.RTC, Store.toReal(c, next) + 1000, tick)
    }
}
