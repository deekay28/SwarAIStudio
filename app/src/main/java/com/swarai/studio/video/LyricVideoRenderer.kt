package com.swarai.studio.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.swarai.studio.export.Subtitles
import com.swarai.studio.export.TimedLine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

enum class VideoOrientation(val label: String, val width: Int, val height: Int) {
    LANDSCAPE("16:9 YouTube", 1280, 720),
    PORTRAIT("9:16 Shorts", 720, 1280),
}

data class VideoProject(
    val audio: File,
    val background: File,
    val title: String,
    val lines: List<TimedLine>,
    /** Peak amplitudes sampled at [peaksPerSecond], used by the visualizer. */
    val peaks: FloatArray,
    val peaksPerSecond: Int,
    val durationMs: Long,
    val orientation: VideoOrientation,
    val showVisualizer: Boolean = true,
    val showLyrics: Boolean = true,
)

/** Draws lyrics (wrapped, centred) and an amplitude-bar visualizer onto a full-frame transparent overlay. */
@OptIn(UnstableApi::class)
private class LyricVisualizerOverlay(private val project: VideoProject) : BitmapOverlay() {
    private val w = project.orientation.width
    private val h = project.orientation.height
    private val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val unit = minOf(w, h).toFloat()
    private val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = unit * 0.058f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(unit * 0.012f, 0f, unit * 0.004f, 0xCC000000.toInt())
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFB547.toInt() }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val bars = 40
    private val smoothed = FloatArray(bars)
    private var lastLine: TimedLine? = null
    private var lyricLayout: StaticLayout? = null

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = presentationTimeUs / 1000
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (project.showVisualizer) drawBars(ms)
        if (project.showLyrics) drawLyric(ms)
        return bitmap
    }

    private fun drawBars(ms: Long) {
        val areaW = w * 0.84f
        val left = (w - areaW) / 2
        val baseY = if (project.orientation == VideoOrientation.PORTRAIT) h * 0.86f else h * 0.93f
        val maxH = h * 0.12f
        val barW = areaW / bars
        val centre = (ms * project.peaksPerSecond / 1000).toInt()
        for (i in 0 until bars) {
            // Mirror around the centre so the newest audio sits in the middle.
            val offset = kotlin.math.abs(i - bars / 2)
            val idx = (centre - offset).coerceIn(0, max(0, project.peaks.size - 1))
            val target = project.peaks.getOrElse(idx) { 0f }
            smoothed[i] = smoothed[i] * 0.55f + target * 0.45f
            val bh = max(unit * 0.006f, smoothed[i] * maxH)
            canvas.drawRoundRect(RectF(left + i * barW + barW * 0.18f, baseY - bh, left + (i + 1) * barW - barW * 0.18f, baseY), barW * 0.3f, barW * 0.3f, barPaint)
        }
    }

    private fun drawLyric(ms: Long) {
        val line = Subtitles.lineAt(project.lines, ms)
        if (line != lastLine) {
            lastLine = line
            lyricLayout = line?.let {
                StaticLayout.Builder.obtain(it.text, 0, it.text.length, lyricPaint, (w * 0.86f).toInt())
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3).build()
            }
        }
        val layout = lyricLayout ?: return
        val top = if (project.orientation == VideoOrientation.PORTRAIT) h * 0.58f else h * 0.62f
        val pad = unit * 0.025f
        canvas.drawRoundRect(RectF(w * 0.05f, top - pad, w * 0.95f, top + layout.height + pad), pad, pad, panelPaint)
        canvas.save()
        canvas.translate(w * 0.07f, top)
        layout.draw(canvas)
        canvas.restore()
    }
}

@OptIn(UnstableApi::class)
class LyricVideoRenderer(private val context: Context) {
    /** Renders an H.264/AAC MP4. Runs entirely on the phone. [onProgress] receives 0..100. */
    suspend fun render(project: VideoProject, output: File, onProgress: (Int) -> Unit): File = withContext(Dispatchers.Main) {
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()
        val image = MediaItem.Builder()
            .setUri(Uri.fromFile(project.background))
            .setImageDurationMs(project.durationMs)
            .build()
        val overlay = LyricVisualizerOverlay(project)
        val video = EditedMediaItem.Builder(image)
            .setFrameRate(30)
            .setEffects(
                Effects(
                    listOf(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            project.orientation.width, project.orientation.height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                        ),
                        OverlayEffect(listOf(overlay)),
                    ),
                )
            )
            .build()
        val audio = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(project.audio))).setRemoveVideo(true).build()
        val composition = Composition.Builder(
            EditedMediaItemSequence.Builder(video).build(),
            EditedMediaItemSequence.Builder(audio).build(),
        ).build()

        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(output)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (cont.isActive) cont.resumeWithException(
                            IllegalStateException("Video export failed: ${exportException.errorCodeName}", exportException)
                        )
                    }
                })
                .build()
            transformer.start(composition, output.absolutePath)
            val progressJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                val holder = ProgressHolder()
                while (cont.isActive) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                    delay(500)
                }
            }
            cont.invokeOnCancellation {
                progressJob.cancel()
                // Transformer must be touched on its own (main) thread.
                android.os.Handler(android.os.Looper.getMainLooper()).post { transformer.cancel(); output.delete() }
            }
        }
    }
}
