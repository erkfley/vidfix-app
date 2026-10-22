package com.vidfix.app

import android.app.ActivityManager
import android.content.Context

/**
 * RAM detection & performance tiers - port of vidfix/ram.py.
 *
 * Tier controls: encoder threads, x264 preset, FPS interpolation mode
 * ('blend' = cheap, 'mci' = motion-compensated) and max FPS level.
 */
object RamTier {

    data class Info(val gb: Double, val tier: String)

    data class Params(
        val threads: Int,
        val x264Preset: String,
        val miMode: String,
        val maxFps: Int
    )

    /** Total device RAM in GiB (approximate - what the kernel reports). */
    fun detect(context: Context): Info {
        val gb = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mem)
            mem.totalMem / (1024.0 * 1024.0 * 1024.0)
        } catch (e: Exception) {
            8.0
        }
        return Info(gb, tierFor(gb))
    }

    fun tierFor(gb: Double): String = when {
        gb < 5.0 -> "safe"
        gb < 10.0 -> "balanced"
        else -> "full"
    }

    fun params(tier: String): Params = when (tier) {
        "safe" -> Params(threads = 4, x264Preset = "veryfast", miMode = "blend", maxFps = 60)
        "full" -> Params(threads = 8, x264Preset = "medium", miMode = "mci", maxFps = 120)
        else -> Params(threads = 6, x264Preset = "medium", miMode = "mci", maxFps = 120)
    }

    /** Fixed RAM choices shown in the spinner, mapped to GB values. */
    val choicesGb = doubleArrayOf(3.0, 4.0, 6.0, 8.0, 12.0, 16.0)
}
