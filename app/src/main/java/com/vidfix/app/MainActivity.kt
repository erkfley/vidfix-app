package com.vidfix.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.concurrent.Executors

/**
 * VidFix - single-screen UI: pick video, pick presets, tap start.
 * Language switch (EN/ID) uses AppCompat per-app locales and persists.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var txtFile: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtLog: TextView
    private lateinit var btnPick: Button
    private lateinit var btnStart: Button
    private lateinit var btnCancel: Button
    private lateinit var btnOpen: Button
    private lateinit var progress: ProgressBar

    private lateinit var spContent: Spinner
    private lateinit var spUpscale: Spinner
    private lateinit var spColor: Spinner
    private lateinit var spFps: Spinner
    private lateinit var spRam: Spinner

    private var pickedUri: Uri? = null
    private var resultUri: Uri? = null
    private var resultFile: java.io.File? = null
    private var userCancelled = false
    private var busy = false

    private val executor = Executors.newSingleThreadExecutor()

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                pickedUri = uri
                txtFile.text = uri.lastPathSegment ?: uri.toString()
                btnStart.isEnabled = !busy
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtFile = findViewById(R.id.txtFile)
        txtStatus = findViewById(R.id.txtStatus)
        txtLog = findViewById(R.id.txtLog)
        btnPick = findViewById(R.id.btnPick)
        btnStart = findViewById(R.id.btnStart)
        btnCancel = findViewById(R.id.btnCancel)
        btnOpen = findViewById(R.id.btnOpen)
        progress = findViewById(R.id.progress)

        spContent = findViewById(R.id.spContent)
        spUpscale = findViewById(R.id.spUpscale)
        spColor = findViewById(R.id.spColor)
        spFps = findViewById(R.id.spFps)
        spRam = findViewById(R.id.spRam)

        setupSpinners()

        findViewById<Button>(R.id.btnLang).setOnClickListener { toggleLanguage() }
        btnPick.setOnClickListener {
            filePicker.launch(arrayOf("video/*"))
        }
        btnStart.setOnClickListener { startProcessing() }
        btnCancel.setOnClickListener {
            userCancelled = true
            Engine.cancel()
        }
        btnOpen.setOnClickListener { openResult() }

        // RAM auto-detection preview for the spinner label.
        val info = RamTier.detect(this)
        val autoLabel = getString(R.string.ram_auto, info.gb, info.tier)
        val ramItems = mutableListOf(autoLabel) +
            resources.getStringArray(R.array.ram_options).toList()
        spRam.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, ramItems
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spRam.setSelection(0)
    }

    private fun setupSpinners() {
        fun attach(sp: Spinner, resId: Int, defaultSel: Int = 0) {
            sp.adapter = ArrayAdapter.createFromResource(
                this, resId, android.R.layout.simple_spinner_item
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            sp.setSelection(defaultSel)
        }
        attach(spContent, R.array.content_options, 0)
        attach(spUpscale, R.array.upscale_options, 2)   // default: 1080p
        attach(spColor, R.array.color_options, 1)       // default: Auto-Fix
        attach(spFps, R.array.fps_options, 2)           // default: 60 fps
    }

    private fun toggleLanguage() {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val next = if (current.startsWith("id")) "en" else "id"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(next))
        // Activity is recreated automatically by AppCompat.
    }

    // ------------------------------------------------------------- processing

    private fun startProcessing() {
        val uri = pickedUri
        if (uri == null) {
            txtStatus.text = getString(R.string.need_file)
            return
        }
        if (busy) return

        val contentValues = resources.getStringArray(R.array.content_values)
        val contentSel = contentValues[spContent.selectedItemPosition]

        val upLevel = spUpscale.selectedItemPosition          // 0..4
        val colLevel = spColor.selectedItemPosition           // 0..3
        val fpsLevel = spFps.selectedItemPosition             // 0..3

        val tierKey = if (spRam.selectedItemPosition == 0) {
            RamTier.detect(this).tier
        } else {
            RamTier.tierFor(RamTier.choicesGb[spRam.selectedItemPosition - 1])
        }

        if (upLevel == 4 && tierKey == "safe") {
            txtStatus.text = getString(R.string.warn_4k)
        }
        if (fpsLevel == 3 && tierKey == "safe") {
            txtStatus.text = getString(R.string.warn_safe)
        }

        busy = true
        userCancelled = false
        btnStart.isEnabled = false
        btnPick.isEnabled = false
        btnCancel.visibility = View.VISIBLE
        btnOpen.visibility = View.GONE
        progress.progress = 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val opts = Engine.Opts(contentSel, upLevel, colLevel, fpsLevel, tierKey)

        executor.execute {
            var analysis: Detect.Result? = null
            if (contentSel == "auto") {
                runOnUiThread { txtStatus.text = getString(R.string.analyzing) }
                val safIn = com.arthenica.ffmpegkit.FFmpegKitConfig
                    .getSafParameterForRead(this, uri)
                val info = com.arthenica.ffmpegkit.FFprobeKit
                    .getMediaInformation(safIn).mediaInformation
                val dur = info?.duration?.toDoubleOrNull() ?: 5.0
                analysis = Detect.analyze(this, safIn, dur)
                runOnUiThread {
                    txtStatus.text = getString(R.string.detect_result, analysis.type)
                }
            }

            runOnUiThread { txtStatus.text = getString(R.string.starting) }

            Engine.run(
                this, uri, opts, analysis,
                onProgress = { pct ->
                    runOnUiThread {
                        progress.progress = pct
                        txtStatus.text = getString(R.string.working, pct)
                    }
                },
                onStatus = { },
                onDone = { output ->
                    runOnUiThread { onFinished(output) }
                }
            )
        }
    }

    private fun onFinished(output: Engine.Output) {
        busy = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        btnStart.isEnabled = pickedUri != null
        btnPick.isEnabled = true
        btnCancel.visibility = View.GONE

        if (output.ok) {
            resultUri = output.uri
            resultFile = output.file
            txtStatus.text = getString(
                if (output.uri != null) R.string.done else R.string.done_legacy
            )
            btnOpen.visibility = View.VISIBLE
        } else if (userCancelled) {
            txtStatus.text = getString(R.string.cancelled)
        } else {
            txtStatus.text = getString(R.string.failed, output.code)
            txtLog.text = "FFmpeg exit code ${output.code}\n" +
                "Try: lower preset / shorter clip / more free RAM."
        }
    }

    private fun openResult() {
        try {
            if (resultUri != null) {
                val i = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(resultUri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(i)
            } else if (resultFile != null) {
                val share = androidx.core.content.FileProvider.getUriForFile(
                    this, "$packageName.fileprovider", resultFile!!
                )
                val i = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(share, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(i)
            }
        } catch (e: Exception) {
            txtStatus.text = e.message ?: "no player"
        }
    }

    override fun onDestroy() {
        if (busy) Engine.cancel()
        super.onDestroy()
    }
}
