package com.example.personeltracking2026kodamjayav2.ui.personel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

@Suppress("DEPRECATION")
class HeartbeatMonitorView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val paint: Paint = Paint().apply {
        color = 0xFFFF0000.toInt() // Warna merah untuk garis detak jantung
        strokeWidth = 5f
        isAntiAlias = true
    }
    private var offsetX = 0f  // Posisi horizontal grafik
    private val dataPoints = mutableListOf<Float>()

    // Timer untuk memperbarui detak jantung
    private val handler = Handler()

    // Runnable untuk update grafik
    private val updateRunnable = object : Runnable {
        override fun run() {
            val heartRate = getHeartRate()  // Ambil BPM
            updateGraph(heartRate)
            handler.postDelayed(this, 50)  // Update setiap 50 ms
        }
    }

    init {
        handler.post(updateRunnable)
    }

    // Fungsi untuk menggambar grafik
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerY = height / 2f
        val scaleY = 100f  // Skala untuk mempengaruhi tinggi gelombang
        val scaleX = 10f  // Skala untuk jarak horizontal antar titik

        // Gambar garis sinusoida untuk BPM yang lebih besar dari 0
        for (i in 1 until dataPoints.size) {
            val x1 = (i - 1) * scaleX
            val y1 = centerY + (sin(dataPoints[i - 1]) * scaleY)
            val x2 = i * scaleX
            val y2 = centerY + (sin(dataPoints[i]) * scaleY)

            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        // Gambar garis lurus jika BPM adalah 0
        if (dataPoints.isEmpty() || dataPoints.last() == 0f) {
            val startX = 0f
            val endX = width.toFloat()
            paint.color = 0xFF808080.toInt()  // Warna abu-abu untuk 0 BPM
            canvas.drawLine(startX, centerY, endX, centerY, paint)
        }
    }

    // Update data titik untuk grafik
    private fun updateGraph(bpm: Int) {
        // Update warna dan animasi berdasarkan BPM
        val color = when {
            bpm == 0 -> 0xFF808080.toInt()  // Abu-abu untuk 0 BPM (garis lurus)
            bpm < 60 -> 0xFF0000FF.toInt()  // Biru untuk BPM rendah (Bradikardia)
            bpm in 60..80 -> 0xFF00FF00.toInt()  // Hijau untuk BPM normal
            bpm in 81..100 -> 0xFFFFFF00.toInt()  // Kuning untuk BPM agak cepat
            bpm in 101..120 -> 0xFFFFA500.toInt()  // Oranye untuk BPM sedikit terlalu cepat
            bpm > 120 -> 0xFFFF0000.toInt()  // Merah untuk BPM sangat tinggi (Tachycardia)
            else -> 0xFF00FF00.toInt()  // Default Hijau jika ada masalah dalam perhitungan
        }

        // Setel warna garis sesuai kondisi BPM
        paint.color = color

        if (bpm == 0) {
            // Untuk BPM 0, kita hanya menambahkan satu titik data 0
            dataPoints.add(0f)
        } else {
            // Gambar zigzag dengan sinusoida berdasarkan BPM
            dataPoints.add((bpm * Math.PI / 60.0 * offsetX.toDouble()).toFloat())
            if (dataPoints.size > 40) {
                dataPoints.removeAt(0)
            }
        }

        offsetX += 1f
        postInvalidate()  // Meminta untuk menggambar ulang view dengan aman
    }

    // Fungsi untuk mendapatkan heart rate yang sebenarnya, untuk sekarang kita simulasikan
    private fun getHeartRate(): Int {
        // Simulasi detak jantung, bisa diganti dengan data nyata
        return (60 + Math.random() * 40).toInt()  // Menghasilkan angka antara 60 hingga 100
    }
}