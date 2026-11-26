package com.example.ytdl

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var urlBox: EditText
    private lateinit var modeSpin: Spinner
    private lateinit var qualSpin: Spinner
    private lateinit var playlistCb: CheckBox
    private lateinit var btn: Button
    private lateinit var cookieBtn: Button
    private lateinit var updBtn: Button
    private lateinit var bar: ProgressBar
    private lateinit var statusTv: TextView
    private lateinit var logTv: TextView
    private lateinit var logScroll: ScrollView

    @Volatile private var ready = false
    @Volatile private var busy = false

    private val sizeRegex = Regex("""of\s+~?\s*([\d.]+)\s*([KMGT]?)i?B""")
    private val speedRegex = Regex("""at\s+([\d.]+\s*[KMGT]?i?B/s)""")

    // yt-dlp ki progress line se total size nikalta hai, jaise "of 10.23MiB"
    private fun parseTotalBytes(line: String): Long {
        val m = sizeRegex.find(line) ?: return 0L
        val v = m.groupValues[1].toDoubleOrNull() ?: return 0L
        val mult = when (m.groupValues[2]) {
            "K" -> 1024.0
            "M" -> 1024.0 * 1024
            "G" -> 1024.0 * 1024 * 1024
            "T" -> 1024.0 * 1024 * 1024 * 1024
            else -> 1.0
        }
        return (v * mult).toLong()
    }

    private fun fmtSize(b: Long): String {
        val v = b.coerceAtLeast(0L).toDouble()
        return when {
            v >= 1024.0 * 1024 * 1024 -> "%.2f GB".format(v / (1024.0 * 1024 * 1024))
            v >= 1024.0 * 1024 -> "%.2f MB".format(v / (1024.0 * 1024))
            v >= 1024.0 -> "%.1f KB".format(v / 1024.0)
            else -> "%.0f B".format(v)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()

        // yt-dlp aur ffmpeg ko pehli baar tayyar karna (background mein)
        thread {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
                ready = true
                runOnUiThread { statusTv.text = "Tayyar" }
            } catch (e: Exception) {
                runOnUiThread { statusTv.text = "Init error: ${e.message}" }
            }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            fitsSystemWindows = true
        }

        root.addView(TextView(this).apply {
            text = "YouTube Downloader"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })

        urlBox = EditText(this).apply {
            hint = "YouTube link yahan paste karein"
            setSingleLine(true)
        }
        root.addView(urlBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modeSpin = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Video (MP4)", "Audio (MP3)"))
        }
        qualSpin = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Best", "1080p", "720p", "480p", "360p"))
            setSelection(1)
        }
        row.addView(modeSpin, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(qualSpin, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        playlistCb = CheckBox(this).apply { text = "Poori playlist download karein" }
        root.addView(playlistCb)

        btn = Button(this).apply {
            text = "Download"
            setOnClickListener { startDownload() }
        }
        root.addView(btn)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cookieBtn = Button(this).apply {
            text = "Cookies (cookies.txt)"
            setOnClickListener { pickCookies() }
        }
        updBtn = Button(this).apply {
            text = "yt-dlp Update"
            setOnClickListener { updateYtDlp() }
        }
        row2.addView(cookieBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(updBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row2)

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        root.addView(bar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        statusTv = TextView(this).apply { text = "Tayyar ho raha hai..." }
        root.addView(statusTv)

        logTv = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            textSize = 12f
            gravity = Gravity.START
        }
        logScroll = ScrollView(this).apply { addView(logTv) }
        root.addView(logScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })

        setContentView(root)
    }

    private fun log(msg: String) = runOnUiThread {
        logTv.append(msg + "\n")
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private val reqCookies = 42

    private fun pickCookies() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, reqCookies)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqCookies && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openInputStream(uri)?.use { inp ->
                    File(filesDir, "cookies.txt").outputStream().use { inp.copyTo(it) }
                }
                statusTv.text = "Cookies load ho gayi"
                log("cookies.txt load ho gayi. Ab link dobara try karein.")
            } catch (e: Exception) {
                log("Cookies error: ${e.message}")
            }
        }
    }

    private fun updateYtDlp() {
        if (!ready) {
            Toast.makeText(this, "App abhi tayyar ho rahi hai", Toast.LENGTH_SHORT).show()
            return
        }
        updBtn.isEnabled = false
        statusTv.text = "yt-dlp update ho raha hai..."
        thread {
            try {
                val st = YoutubeDL.getInstance().updateYoutubeDL(application, YoutubeDL.UpdateChannel.NIGHTLY)
                log("Update: $st")
                runOnUiThread { statusTv.text = "Update mukammal" }
            } catch (e: Exception) {
                log("Update error: ${e.message}")
                runOnUiThread { statusTv.text = "Update error" }
            } finally {
                runOnUiThread { updBtn.isEnabled = true }
            }
        }
    }

    private fun startDownload() {
        val url = urlBox.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Pehle link paste karein", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ready) {
            Toast.makeText(this, "App abhi tayyar ho rahi hai, thori der ruken", Toast.LENGTH_SHORT).show()
            return
        }
        if (busy) return

        busy = true
        btn.isEnabled = false
        bar.progress = 0
        val audio = modeSpin.selectedItemPosition == 1
        val quality = qualSpin.selectedItem.toString()
        val playlist = playlistCb.isChecked

        thread {
            val tmp = File(filesDir, "dl").apply { deleteRecursively(); mkdirs() }
            try {
                log("Shuru: $url")
                val req = YoutubeDLRequest(url)
                req.addOption("-o", tmp.absolutePath + "/%(title).150s.%(ext)s")
                req.addOption("--windows-filenames")
                req.addOption(if (playlist) "--yes-playlist" else "--no-playlist")

                // Agar cookies.txt load ki hui hai to YouTube ke "Sign in" check ke liye use hogi
                val ck = File(filesDir, "cookies.txt")
                if (ck.exists()) req.addOption("--cookies", ck.absolutePath)

                if (audio) {
                    req.addOption("-x")
                    req.addOption("--audio-format", "mp3")
                    req.addOption("--audio-quality", "0")
                } else {
                    if (quality == "Best") {
                        req.addOption("-f", "bv*+ba/b")
                    } else {
                        val h = quality.replace("p", "")
                        req.addOption(
                            "-f",
                            "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/b[height<=$h][ext=mp4]/bv*[height<=$h]+ba/b[height<=$h]/b"
                        )
                    }
                    req.addOption("--merge-output-format", "mp4")
                }

                YoutubeDL.getInstance().execute(req, "ytdl_job") { progress, eta, line ->
                    val pct = progress.coerceIn(0f, 100f)
                    val total = parseTotalBytes(line)
                    val text = StringBuilder("%.1f%%".format(pct))
                    if (total > 0) {
                        val done = (total * pct / 100.0).toLong()
                        text.append("\nDownload hua: ${fmtSize(done)}  /  Total: ${fmtSize(total)}")
                        text.append("\nBaqi size: ${fmtSize(total - done)}")
                    }
                    val speed = speedRegex.find(line)?.groupValues?.get(1)
                    if (speed != null) text.append("\nSpeed: $speed")
                    if (eta > 0) text.append("   |   Baqi waqt: ${eta}s")
                    runOnUiThread {
                        bar.progress = pct.toInt()
                        statusTv.text = text.toString()
                    }
                }

                runOnUiThread { statusTv.text = "Gallery/Downloads mein save ho raha hai..." }
                val saved = saveToDownloads(tmp)
                runOnUiThread {
                    bar.progress = 100
                    statusTv.text = "Mukammal!"
                }
                log("Save ho gaya ($saved file): Downloads/YouTubeDL")
            } catch (e: Exception) {
                runOnUiThread { statusTv.text = "Error" }
                log("Error: ${e.message}")
            } finally {
                tmp.deleteRecursively()
                busy = false
                runOnUiThread { btn.isEnabled = true }
            }
        }
    }

    // Download hui files ko phone ke public Downloads/YouTubeDL folder mein copy karta hai
    private fun saveToDownloads(dir: File): Int {
        var count = 0
        dir.listFiles()?.filter {
            it.isFile && !it.name.endsWith(".part") &&
                !it.name.endsWith(".ytdl") && !it.name.endsWith(".temp")
        }?.forEach { f ->
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, f.name)
                put(MediaStore.Downloads.MIME_TYPE, mimeOf(f.name))
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/YouTubeDL")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    f.inputStream().use { it.copyTo(out) }
                }
                count++
            }
        }
        return count
    }

    private fun mimeOf(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}
