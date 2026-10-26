package com.swarai.studio.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import kotlin.math.max
import kotlin.random.Random

/**
 * Cover artwork. "Design" covers are drawn procedurally on the phone (gradients + typography): no AI image model
 * is used and the UI says so. Users can also import their own image through the system photo picker.
 */
object CoverArt {
    val palettes = listOf(
        intArrayOf(0xFF3B1C6E.toInt(), 0xFFE8559B.toInt(), 0xFFFFB547.toInt()),
        intArrayOf(0xFF0B3D2E.toInt(), 0xFF1E8C6E.toInt(), 0xFFF4D35E.toInt()),
        intArrayOf(0xFF2B0F0F.toInt(), 0xFFB23A48.toInt(), 0xFFFFB547.toInt()),
        intArrayOf(0xFF0E1A40.toInt(), 0xFF3F6CDF.toInt(), 0xFF9AD0F5.toInt()),
        intArrayOf(0xFF1A1A1A.toInt(), 0xFF5B5B5B.toInt(), 0xFFE0C097.toInt()),
    )

    fun design(title: String, subtitle: String, paletteIndex: Int, seed: Int, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val colors = palettes[paletteIndex.mod(palettes.size)]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        // Concentric "sound rings" with a deterministic layout per seed.
        val rnd = Random(seed)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        val cx = width * (0.25f + rnd.nextFloat() * 0.5f)
        val cy = height * (0.3f + rnd.nextFloat() * 0.3f)
        for (i in 1..9) {
            paint.color = colors[2]
            paint.alpha = (110 - i * 10).coerceAtLeast(12)
            paint.strokeWidth = max(width, height) * 0.004f * (10 - i)
            canvas.drawCircle(cx, cy, max(width, height) * 0.07f * i, paint)
        }
        paint.style = Paint.Style.FILL
        drawText(canvas, title, subtitle, width, height)
        return bmp
    }

    /** Fits an imported image into the target frame (center crop) and adds the title. */
    fun fromImage(context: Context, uri: Uri, title: String, subtitle: String, width: Int, height: Int, withText: Boolean): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("That file is not an image this phone can open.")
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        val source = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("That file is not an image this phone can open.")
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val scale = max(width / source.width.toFloat(), height / source.height.toFloat())
        val w = source.width * scale
        val h = source.height * scale
        canvas.drawBitmap(source, null, RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        if (withText) {
            val shade = Paint().apply {
                shader = LinearGradient(0f, height * 0.5f, 0f, height.toFloat(), Color.TRANSPARENT, 0xCC000000.toInt(), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, height * 0.5f, width.toFloat(), height.toFloat(), shade)
            drawText(canvas, title, subtitle, width, height)
        }
        return bmp
    }

    private fun drawText(canvas: Canvas, title: String, subtitle: String, width: Int, height: Int) {
        val unit = minOf(width, height)
        val margin = (unit * 0.07f).toInt()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = unit * 0.09f
            setShadowLayer(unit * 0.01f, 0f, unit * 0.004f, 0x88000000.toInt())
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xDDFFFFFF.toInt()
            textSize = unit * 0.045f
        }
        val textWidth = width - margin * 2
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(3).build()
        val subLayout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length, subPaint, textWidth).setMaxLines(2).build()
        val top = height - margin - titleLayout.height - subLayout.height - (unit * 0.02f).toInt()
        canvas.save()
        canvas.translate(margin.toFloat(), top.toFloat())
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height + unit * 0.02f)
        subLayout.draw(canvas)
        canvas.restore()
    }

    fun save(bitmap: Bitmap, file: File): File {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
