package com.tm.farm

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Một vùng vuông trên màn hình: tâm (x, y), cạnh size. */
data class Box(val x: Float, val y: Float, val size: Float) {
    fun contains(px: Float, py: Float) = abs(px - x) <= size / 2 && abs(py - y) <= size / 2
}

/**
 * Đổi toạ độ bản đồ (tỷ lệ trên ảnh gốc) sang pixel cho khung rộng w, cao h.
 * App và widget dùng chung nên giếng, xe, gà luôn khớp với nền.
 */
class Scene(val w: Float, val h: Float) {
    companion object {
        const val SRC_W = 649f
        const val SRC_H = 381f
        const val BAND = 70f                               // dải trời không bị kéo giãn
        val CROPS = floatArrayOf(0f, 0.07f, 0.12f)         // ngang, vuông, dọc
        val ASPECTS = floatArrayOf(SRC_W / SRC_H, 1.05f, 0.78f)
        const val COLS = 4
        const val ROWS = 3
    }

    val variant = ASPECTS.indices.minByOrNull { abs(ln(w / h / ASPECTS[it])) }!!
    private val crop = CROPS[variant]
    private val mapH = SRC_W * (1 - 2 * crop) / ASPECTS[variant]
    private val stretch = (mapH - BAND) / (SRC_H - BAND)

    fun sx(u: Float) = (u - crop) / (1 - 2 * crop) * w
    fun sy(v: Float): Float {
        val y = v * SRC_H
        return (if (y < BAND) y else BAND + (y - BAND) * stretch) / mapH * h
    }
    fun u(x: Float) = x / w * (1 - 2 * crop) + crop
    fun v(y: Float): Float {
        val m = y / h * mapH
        return (if (m < BAND) m else BAND + (m - BAND) / stretch) / SRC_H
    }

    private val short = min(w, h)
    val chicken = short * 0.16f
    val foot = chicken * 0.3f
    val eggSize = chicken * 0.34f
    val tuftSize = chicken * 0.62f

    private fun box(x: Float, y: Float, size: Float) =
        Box(min(max(x, size / 2), w - size / 2), min(max(y, size / 2), h - size / 2), size)

    val well = box(sx(0.14f), sy(0.23f), short * 0.27f)
    val truck = box(sx(0.87f), sy(0.88f), short * 0.21f)
    val gear = box(w, 0f, short * 0.13f)

    fun egg(e: Spot) = Box(sx(e.x), sy(e.y) + foot, eggSize)

    /** Trứng thuộc ô lưới nào (nới thêm nửa quả để chạm sát mép vẫn nhặt được). */
    fun eggsInCell(cell: Int, eggs: List<Spot>): List<Spot> {
        val cw = w / COLS
        val ch = h / ROWS
        val left = (cell % COLS) * cw - eggSize / 2
        val top = (cell / COLS) * ch - eggSize / 2
        return eggs.filter {
            val b = egg(it)
            b.x >= left && b.x <= left + cw + eggSize && b.y >= top && b.y <= top + ch + eggSize
        }
    }

    /** Điểm trên bản đồ ứng với giữa ô lưới. */
    fun cellCenter(cell: Int) = Field.clamp(
        u((cell % COLS + 0.5f) * w / COLS), v((cell / COLS + 0.5f) * h / ROWS)
    )
}
