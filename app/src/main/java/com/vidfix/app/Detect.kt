package com.vidfix.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Content auto-detection (real / anime / game) - port of vidfix/detect.py.
 *
 * HEURISTIC MATH on 5 tiny (160x90) frames extracted by FFmpeg:
 *  1) unique color ratio  u = |unique colors| / |pixels|
 *     anime/cel shading -> few colors -> LOW u
 *  2) edge density        e = mean( |grad| > 60 ) on BT.601 luma
 *     games/screen recordings (HUD, text) -> HIGH e
 *  3) noise sigma (Donoho) sigma = median(|Laplacian|) / 0.6745
 *     camera sensors always inject noise -> HIGH sigma for real life
 *
 * Decision tree (order matters):
 *     sigma >= 4.0          -> real
 *     u     <= 0.12         -> anime
 *     e     >= 0.28         -> game
 *     else                  -> real
 */
object Detect {

    const val W = 160
    const val H = 90
    private const val SAMPLES = 5

    data class Result(
        val type: String,     // "real" | "anime" | "game"
        val colors: Double,   // unique color ratio
        val edges: Double,    // edge density
        val noise: Double,    // Donoho sigma
        val luma: Double      // mean brightness 0..1
    )

    fun analyze(context: Context, inputSaf: String, durationSec: Double): Result {
        val dir = File(context.cacheDir, "frames").apply { deleteRecursively(); mkdirs() }
        val span = maxOf(durationSec - 0.5, 0.5)

        for (i in 0 until SAMPLES) {
            val ts = 0.1 + span * i / SAMPLES
            val out = File(dir, "f$i.jpg").absolutePath
            val args = arrayOf(
                "-ss", String.format("%.2f", ts),
                "-i", inputSaf,
                "-frames:v", "1",
                "-vf", "scale=$W:$H",
                "-q:v", "3",
                out
            )
            val session = FFmpegKit.executeWithArguments(args)
            if (!ReturnCode.isSuccess(session.returnCode)) continue
        }

        val bmps = dir.listFiles { f -> f.name.endsWith(".jpg") }
            ?.mapNotNull { BitmapFactory.decodeFile(it.absolutePath) }
            ?.filter { it.width == W && it.height == H }
            ?: emptyList()

        if (bmps.isEmpty()) {
            return Result("real", 0.0, 0.0, 0.0, 0.5)
        }

        val u = bmps.map { uniqueColorRatio(it) }.sorted()[bmps.size / 2]
        val e = bmps.map { edgeDensity(it) }.sorted()[bmps.size / 2]
        val s = bmps.map { noiseSigma(it) }.sorted()[bmps.size / 2]
        val lum = bmps.map { meanLuma(it) }.average()

        bmps.forEach { it.recycle() }
        dir.deleteRecursively()

        val type = when {
            s >= 4.0 -> "real"
            u <= 0.12 -> "anime"
            e >= 0.28 -> "game"
            else -> "real"
        }
        return Result(type, u, e, s, lum)
    }

    /** BT.601 luma plane: Y = 0.299R + 0.587G + 0.114B */
    private fun luma(bmp: Bitmap): DoubleArray {
        val px = IntArray(W * H)
        bmp.getPixels(px, 0, W, 0, 0, W, H)
        val y = DoubleArray(W * H)
        for (i in px.indices) {
            val c = px[i]
            y[i] = 0.299 * ((c shr 16) and 0xFF) +
                   0.587 * ((c shr 8) and 0xFF) +
                   0.114 * (c and 0xFF)
        }
        return y
    }

    private fun uniqueColorRatio(bmp: Bitmap): Double {
        val px = IntArray(W * H)
        bmp.getPixels(px, 0, W, 0, 0, W, H)
        val set = HashSet<Int>(px.size / 2)
        for (c in px) set.add(c)
        return set.size.toDouble() / px.size
    }

    private fun edgeDensity(bmp: Bitmap): Double {
        val y = luma(bmp)
        var count = 0
        var total = 0
        for (row in 0 until H - 1) {
            for (col in 0 until W - 1) {
                val i = row * W + col
                val dx = abs(y[i + 1] - y[i])
                val dy = abs(y[i + W] - y[i])
                if (hypot(dx, dy) > 60.0) count++
                total++
            }
        }
        return count.toDouble() / maxOf(total, 1)
    }

    /** Donoho robust estimator: sigma = median(|Laplacian|) / 0.6745 */
    private fun noiseSigma(bmp: Bitmap): Double {
        val y = luma(bmp)
        val laps = DoubleArray((W - 2) * (H - 2))
        var k = 0
        for (row in 1 until H - 1) {
            for (col in 1 until W - 1) {
                val i = row * W + col
                val lap = -4.0 * y[i] + y[i - 1] + y[i + 1] + y[i - W] + y[i + W]
                laps[k++] = abs(lap)
            }
        }
        laps.sort()
        val median = if (laps.isEmpty()) 0.0 else laps[laps.size / 2]
        return median / 0.6745
    }

    private fun meanLuma(bmp: Bitmap): Double {
        val y = luma(bmp)
        var sum = 0.0
        for (v in y) sum += v
        return sum / y.size / 255.0
    }
}
