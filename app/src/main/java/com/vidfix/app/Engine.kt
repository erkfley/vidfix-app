package com.vidfix.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * FFmpeg enhancement engine - port of vidfix/enhance.py.
 *
 * Filter order inside one ffmpeg pass (fast + quality-safe):
 *   1. upscale (scale lanczos + unsharp mask)
 *   2. color   (eq / curves / colorbalance)
 *   3. fps     (minterpolate) - LAST: new frames inherit the finished look
 *
 * Math used (executed by FFmpeg at C speed):
 *   Lanczos:  L(x) = sinc(x)*sinc(x/3)          unsharp: out = in + a*(in-blur(in))
 *   Gamma:    out = 255*(in/255)^(1/g)          S-curve: cubic spline through points
 *   Blend:    out = (1-a)*A + a*B               MCI: adaptive overlapped block ME
 */
object Engine {

    data class Opts(
        val content: String,     // real | anime | game
        val upLevel: Int,        // 0 keep, 1 720p, 2 1080p, 3 1440p, 4 4K
        val colLevel: Int,       // 0 keep, 1 auto-fix, 2 vivid, 3 cinematic
        val fpsLevel: Int,       // 0 keep, 1 30, 2 60, 3 120
        val tierKey: String      // safe | balanced | full
    )

    data class Output(
        val ok: Boolean,
        val uri: Uri?,           // MediaStore uri (API 29+)
        val file: File?,         // legacy app-folder file
        val code: Int
    )

    @Volatile private var currentSessionId: Long = -1

    fun cancel() {
        val id = currentSessionId
        if (id >= 0) FFmpegKit.cancel(id)
    }

    // ------------------------------------------------------------ math utils

    /** H.264 wants even dimensions: dim = round(dim/2)*2 */
    fun even(x: Int): Int = max(2, (x / 2.0).roundToInt() * 2)

    /** Aspect-preserving fit inside a box, evenized. */
    fun fitInside(sw: Int, sh: Int, bw: Int, bh: Int): IntArray {
        val r = min(bw.toDouble() / sw, bh.toDouble() / sh)
        return intArrayOf(even((sw * r).roundToInt()), even((sh * r).roundToInt()))
    }

    /** Auto-Fix gamma from measured brightness (0..1). */
    fun adaptiveGamma(meanLuma: Double): Double {
        val g = when {
            meanLuma < 0.42 -> 1.0 + 0.5 * (0.42 - meanLuma)
            meanLuma > 0.68 -> 1.0 - 0.8 * (meanLuma - 0.68)
            else -> 1.0
        }
        return g.coerceIn(0.9, 1.35)
    }

    // -------------------------------------------------------- filter builders

    private val upTargets = arrayOf(
        intArrayOf(1280, 720), intArrayOf(1920, 1080),
        intArrayOf(2560, 1440), intArrayOf(3840, 2160)
    )

    fun buildUpscale(level: Int, content: String, sw: Int, sh: Int): Pair<String?, IntArray> {
        if (level == 0) return null to intArrayOf(sw, sh)
        val box = upTargets[(level - 1).coerceIn(0, 3)]
        val tw = fitInside(sw, sh, box[0], box[1])
        val sharp = when (content) { "anime" -> 0.5; "game" -> 0.85; else -> 0.7 }
        val f = "scale=${tw[0]}:${tw[1]}:flags=lanczos,unsharp=5:5:$sharp:5:5:0.0"
        return f to tw
    }

    fun buildColor(level: Int, content: String, analysis: Detect.Result?): String? {
        if (level == 0) return null
        val luma = analysis?.luma ?: 0.5
        return when (level) {
            1 -> {
                val g = adaptiveGamma(luma)
                val sat = if (content == "anime") 1.10 else 1.08
                "eq=gamma=${"%.2f".format(g)}:contrast=1.06:saturation=${"%.2f".format(sat)}"
            }
            2 -> {
                val sat = when (content) { "anime" -> 1.18; "game" -> 1.14; else -> 1.12 }
                "curves=all='0/0 0.25/0.22 0.5/0.5 0.75/0.79 1/1'," +
                "eq=saturation=${"%.2f".format(sat)}," +
                "colorbalance=rs=0.03:bs=-0.03"
            }
            3 ->
                "curves=all='0/0.035 0.25/0.24 0.5/0.5 0.75/0.77 1/0.965'," +
                "eq=contrast=1.08:saturation=0.96," +
                "colorbalance=gs=-0.02:bs=0.05:rm=-0.03"
            else -> null
        }
    }

    fun buildFps(level: Int, tierKey: String): String? {
        if (level == 0) return null
        val p = RamTier.params(tierKey)
        val fps = min(arrayOf(0, 30, 60, 120)[level.coerceIn(0, 3)], p.maxFps)
        return if (p.miMode == "blend") {
            "minterpolate=fps=$fps:mi_mode=blend"
        } else {
            "minterpolate=fps=$fps:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:" +
            "vsbmc=1:scd=fdiff:scd_threshold=8"
        }
    }

    // ----------------------------------------------------------------- output

    private fun prepareOutput(context: Context, baseName: String): Pair<String, Uri?> {
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${baseName}_enhanced.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/VidFix")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values
            )!!
            val saf = FFmpegKitConfig.getSafParameterForWrite(context, uri)
            Pair(saf, uri)
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)!!
            val file = File(dir, "${baseName}_enhanced.mp4")
            Pair(file.absolutePath, null)
        }
    }

    private fun finishPending(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, v, null, null)
        }
    }

    // ------------------------------------------------------------------- run

    fun run(
        context: Context,
        inputUri: Uri,
        opts: Opts,
        analysis: Detect.Result?,
        onProgress: (Int) -> Unit,
        onStatus: (String) -> Unit,
        onDone: (Output) -> Unit
    ) {
        val safIn = FFmpegKitConfig.getSafParameterForRead(context, inputUri)

        val info = FFprobeKit.getMediaInformation(safIn).mediaInformation
        val duration = info?.duration?.toDoubleOrNull() ?: 0.0
        val srcW = info?.streams?.firstOrNull()?.width ?: 0
        val srcH = info?.streams?.firstOrNull()?.height ?: 0
        if (srcW <= 0 || srcH <= 0) {
            onDone(Output(false, null, null, -1))
            return
        }

        val filters = mutableListOf<String>()
        val up = buildUpscale(opts.upLevel, opts.content, srcW, srcH)
        up.first?.let { filters.add(it) }
        buildColor(opts.colLevel, opts.content, analysis)?.let { filters.add(it) }
        buildFps(opts.fpsLevel, opts.tierKey)?.let { filters.add(it) }
        if (up.first == null) {
            filters.add("scale=trunc(iw/2)*2:trunc(ih/2)*2")
        }

        val tier = RamTier.params(opts.tierKey)
        val baseName = queryDisplayName(context, inputUri) ?: "video"
        val (outParam, outUri) = prepareOutput(context, baseName)
        val legacyFile = if (outParam is String) File(outParam) else null

        fun ffmpegArgs(useAac: Boolean): Array<String> {
            val args = mutableListOf(
                "-y", "-hide_banner", "-loglevel", "error", "-nostats",
                "-i", safIn,
                "-vf", filters.joinToString(","),
                "-c:v", "libx264",
                "-preset", tier.x264Preset,
                "-crf", "18",
                "-pix_fmt", "yuv420p",
                "-threads", tier.threads.toString(),
                "-movflags", "+faststart"
            )
            if (useAac) args.addAll(arrayOf("-c:a", "aac", "-b:a", "192k"))
            else args.addAll(arrayOf("-c:a", "copy"))
            args.add(outParam)
            return args.toTypedArray()
        }

        var lastPct = -1
        fun handleStats(timeSec: Double) {
            if (duration > 0) {
                val pct = (timeSec / duration * 100).toInt().coerceIn(0, 100)
                if (pct != lastPct) {
                    lastPct = pct
                    onProgress(pct)
                }
            }
        }

        fun complete(session: FFmpegSession, attempt: Int) {
            val rc = session.returnCode
            val cancelled = ReturnCode.isCancel(session.returnCode)
            if (ReturnCode.isSuccess(rc)) {
                outUri?.let { finishPending(context, it) }
                onDone(Output(true, outUri, legacyFile, 0))
            } else if (attempt == 0 && !cancelled) {
                // Audio copy often fails for exotic codecs -> retry with AAC.
                onStatus("audio->aac")
                FFmpegKit.executeWithArgumentsAsync(
                    ffmpegArgs(useAac = true),
                    { s2 -> complete(s2, 1) },
                    { },
                    { st -> handleStats(st.time) }
                )
            } else {
                if (outUri != null && Build.VERSION.SDK_INT >= 29) {
                    // Remove pending ghost entry on failure.
                    try { context.contentResolver.delete(outUri, null, null) } catch (e: Exception) {}
                }
                onDone(Output(false, null, null, rc?.value ?: -1))
            }
        }

        val session = FFmpegKit.executeWithArgumentsAsync(
            ffmpegArgs(useAac = false),
            { s -> complete(s, 0) },
            { },
            { st -> handleStats(st.time) }
        )
        currentSessionId = session.sessionId
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    n?.substringBeforeLast('.')
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
