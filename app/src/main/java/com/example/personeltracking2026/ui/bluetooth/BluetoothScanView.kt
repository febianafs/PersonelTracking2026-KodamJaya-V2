package com.example.personeltracking2026.ui.bluetooth

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Custom view yang menampilkan animasi ripple (gelombang memutar)
 * di sekitar ikon Bluetooth — mirip efek "scanning" pada gambar referensi.
 */
class BluetoothScanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Paint untuk lingkaran ripple ──────────────────────────────────────
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#2979FF") // warna primary / biru
    }

    // ── Paint untuk lingkaran tengah (background icon) ───────────────────
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1A2A3A") // warna gelap
    }

    // ── Animasi ──────────────────────────────────────────────────────────
    private var animationProgress = 0f
    private val rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animationProgress = it.animatedValue as Float
            invalidate()
        }
    }

    // Jumlah gelombang ripple yang ditampilkan sekaligus
    private val rippleCount = 3

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = minOf(cx, cy) * 0.95f
        val iconRadius = minOf(cx, cy) * 0.32f

        // Gambar ripple (gelombang)
        for (i in 0 until rippleCount) {
            val offset = i.toFloat() / rippleCount
            val progress = (animationProgress + offset) % 1f
            val radius = iconRadius + (maxRadius - iconRadius) * progress
            val alpha = ((1f - progress) * 180).toInt().coerceIn(0, 255)
            ripplePaint.alpha = alpha
            canvas.drawCircle(cx, cy, radius, ripplePaint)
        }

        // Gambar lingkaran tengah
        canvas.drawCircle(cx, cy, iconRadius, circlePaint)

        // Icon Bluetooth di tengah (digambar manual sebagai glyph sederhana)
        drawBluetoothIcon(canvas, cx, cy, iconRadius * 0.55f)
    }

    /**
     * Menggambar ikon Bluetooth sederhana di atas canvas.
     * (Alternatif: gunakan BitmapDrawable dari resource ic_bluetooth)
     */
    private fun drawBluetoothIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.18f
            color = Color.parseColor("#2979FF")
            strokeCap = Paint.Cap.ROUND
        }

        val h = size * 1.6f
        val w = size * 0.7f

        // Garis vertikal tengah
        canvas.drawLine(cx, cy - h / 2, cx, cy + h / 2, iconPaint)

        // Diagonal kanan atas
        canvas.drawLine(cx, cy - h / 2, cx + w, cy - h / 4, iconPaint)
        canvas.drawLine(cx + w, cy - h / 4, cx, cy, iconPaint)

        // Diagonal kanan bawah
        canvas.drawLine(cx, cy, cx + w, cy + h / 4, iconPaint)
        canvas.drawLine(cx + w, cy + h / 4, cx, cy + h / 2, iconPaint)
    }

    /** Mulai animasi scanning */
    fun startAnimation() {
        if (!rippleAnimator.isRunning) rippleAnimator.start()
    }

    /** Hentikan animasi */
    fun stopAnimation() {
        rippleAnimator.cancel()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rippleAnimator.cancel()
    }
}