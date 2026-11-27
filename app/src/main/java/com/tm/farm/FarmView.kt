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

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private fun bmp(id: Int): Bitmap = BitmapFactory.decodeResource(resources, id)
    private val bg = bmp(R.drawable.bg_pasture)
    private val tuft = bmp(R.drawable.grass_tuft)
    private val egg = bmp(R.drawable.egg)
    private val idle = bmp(R.drawable.chicken_idle)
    private val eat = Sprites.EAT.map { bmp(it) }
    private val walk = Sprites.WALK.map { dir -> dir.map { bmp(it) } }

    private val chickenSize get() = width * 0.17f
    // Chừa lề để gà và trứng không bị cắt
    private fun px(x: Float) = chickenSize / 2 + x * (width - chickenSize)
    private fun py(y: Float) = chickenSize / 2 + y * (height - chickenSize)

    private fun draw(c: Canvas, b: Bitmap, x: Float, y: Float, size: Float, flip: Boolean = false) {
        rect.set(x - size / 2, y - size / 2, x + size / 2, y + size / 2)
        if (flip) { c.save(); c.scale(-1f, 1f, x, y) }
        c.drawBitmap(b, null as Rect?, rect, paint)
        if (flip) c.restore()
    }

    override fun onDraw(canvas: Canvas) {
        val f = farm ?: return
        val now = Store.now(context)
        f.advance(now)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bg, null as Rect?, rect, paint)

        val s = chickenSize
        val foot = s * 0.3f
        for (g in f.grass) draw(canvas, tuft, px(g.x), py(g.y) + foot, s * 0.6f)
        // Đám cỏ đang bị ăn: nhỏ và mờ dần
        for (c in f.chickens) if (c.eating) {
            val left = 1f - ((now - c.eatStart).toFloat() / Rules.EAT_TIME).coerceIn(0f, 1f)
            paint.alpha = (60 + 195 * left).toInt()
            draw(canvas, tuft, px(c.ax) - s * 0.2f, py(c.ay) + foot, s * 0.6f * (0.3f + 0.7f * left))
            paint.alpha = 255
        }
        for (e in f.eggs) draw(canvas, egg, px(e.x), py(e.y) + foot, s * 0.32f)

        val tick = SystemClock.uptimeMillis()
        for ((c, p) in f.chickens.map { it to f.pose(it, now) }.sortedBy { it.second.y }) {
            val x = px(p.x)
            val y = py(p.y)
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
            val foot = chickenSize * 0.3f
            val hit = f.eggs.minByOrNull { hypot(px(it.x) - event.x, py(it.y) + foot - event.y) }
            if (hit != null && hypot(px(hit.x) - event.x, py(hit.y) + foot - event.y) < chickenSize * 0.4f) onEggTap(hit)
        }
        return true
    }
}
