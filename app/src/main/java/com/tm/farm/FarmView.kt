package com.tm.farm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Màn chơi chuyển động trong app. */
class FarmView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var farm: Farm? = null
    var onEggTap: (Spot) -> Unit = {}
    var onWellTap: () -> Unit = {}
    var onTruckTap: () -> Unit = {}

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private fun bmp(id: Int): Bitmap = BitmapFactory.decodeResource(resources, id)
    private val maps = Sprites.MAPS.map { bmp(it) }
    private val tufts = Sprites.GRASS.map { bmp(it) }
    private val egg = bmp(R.drawable.egg)
    private val idle = bmp(R.drawable.chicken_idle)
    private val eat = Sprites.EAT.map { bmp(it) }
    private val walk = Sprites.WALK.map { dir -> dir.map { bmp(it) } }
    private val wellFull = bmp(R.drawable.well_full)
    private val wellEmpty = bmp(R.drawable.well_empty)
    private val pump = Sprites.PUMP.map { bmp(it) }
    private val truck = bmp(R.drawable.truck)

    private fun scene() = Scene(width.toFloat(), height.toFloat())

    private fun draw(c: Canvas, b: Bitmap, x: Float, y: Float, size: Float, flip: Boolean = false) {
        rect.set(x - size / 2, y - size / 2, x + size / 2, y + size / 2)
        if (flip) { c.save(); c.scale(-1f, 1f, x, y) }
        c.drawBitmap(b, null as Rect?, rect, paint)
        if (flip) c.restore()
    }

    private fun draw(c: Canvas, b: Bitmap, box: Box) = draw(c, b, box.x, box.y, box.size)

    private fun label(c: Canvas, text: String, box: Box) {
        paint.textSize = 13 * resources.displayMetrics.scaledDensity
        paint.textAlign = Paint.Align.CENTER
        paint.color = 0xFFFFFFFF.toInt()
        paint.setShadowLayer(5f, 0f, 0f, 0xFF000000.toInt())
        c.drawText(text, box.x, box.y + box.size / 2, paint)
        paint.clearShadowLayer()
        paint.color = 0xFF000000.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val f = farm ?: return
        if (width == 0 || height == 0) return
        val now = Store.now(context)
        f.advance(now)
        val scene = scene()
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(maps[scene.variant], null as Rect?, rect, paint)

        // Cỏ nguyên và cỏ đang bị ăn (thưa dần), vẽ từ xa tới gần
        val grass = f.grass.map { it to it.kind } + f.chickens.filter { it.eating }.map {
            val left = 1f - ((now - it.eatStart).toFloat() / Rules.EAT_TIME).coerceIn(0f, 1f)
            Spot(it.ax, it.ay) to f.grassFrame(it.gk, left)
        }
        for ((g, frame) in grass.sortedBy { it.first.y }) {
            draw(canvas, tufts[frame], scene.sx(g.x), scene.sy(g.y) + scene.foot, scene.tuftSize)
        }
        for (e in f.eggs) draw(canvas, egg, scene.egg(e))

        val tickWell = SystemClock.uptimeMillis()
        val wellImage = when {
            f.refillAt != 0L -> pump[((tickWell / 160) % pump.size).toInt()]
            f.water > 0 -> wellFull
            else -> wellEmpty
        }
        draw(canvas, wellImage, scene.well)
        label(canvas, if (f.refillAt != 0L) "⏳ ${clock(context, f.refillAt)}" else "💧 ${f.water}/${Rules.WELL_MAX}", scene.well)
        if (f.truckAt == 0L) draw(canvas, truck, scene.truck)
        label(canvas, if (f.truckAt != 0L) "🚚 ${clock(context, f.truckAt)}" else "🥚 ${f.stock}", scene.truck)

        val s = scene.chicken
        val tick = SystemClock.uptimeMillis()
        for ((c, p) in f.chickens.map { it to f.pose(it, now) }.sortedBy { it.second.y }) {
            val x = scene.sx(p.x)
            val y = scene.sy(p.y)
            when (p.act) {
                Act.EAT -> draw(canvas, eat[((tick / 120 + c.id) % eat.size).toInt()], x, y, s)
                Act.STAND -> draw(canvas, idle, x, y, s, p.dx > 0)
                Act.WALK -> {
                    // 5 hướng trong sprite sheet: 0=xuống, 2=ngang, 4=lên. Sang phải thì lật hình.
                    val angle = atan2(Math.abs(p.dx * width), p.dy * height)
                    val dir = (angle / (PI / 4)).roundToInt().coerceIn(0, 4)
                    val speed = if (c.eating) 60 else 100
                    draw(canvas, walk[dir][((tick / speed + c.id) % 8).toInt()], x, y, s, p.dx > 0)
                }
            }
            // Thanh no
            val v = f.satiety(c, now)
            paint.color = 0x66000000
            canvas.drawRect(x - s * 0.25f, y - s * 0.52f, x + s * 0.25f, y - s * 0.47f, paint)
            paint.color = if (c.hungry) 0xFFE53935.toInt() else 0xFF29B6F6.toInt()
            canvas.drawRect(x - s * 0.25f, y - s * 0.52f, x - s * 0.25f + s * 0.5f * v, y - s * 0.47f, paint)
            paint.color = 0xFF000000.toInt()
        }
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val f = farm ?: return true
            val scene = scene()
            when {
                scene.well.contains(event.x, event.y) -> onWellTap()
                scene.truck.contains(event.x, event.y) -> onTruckTap()
                else -> {
                    fun dist(e: Spot) = scene.egg(e).let { hypot(it.x - event.x, it.y - event.y) }
                    val hit = f.eggs.minByOrNull { dist(it) }
                    if (hit != null && dist(hit) < scene.chicken * 0.4f) onEggTap(hit)
                }
            }
        }
        return true
    }
}
