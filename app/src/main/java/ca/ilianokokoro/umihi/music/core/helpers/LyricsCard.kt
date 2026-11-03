package ca.ilianokokoro.umihi.music.core.helpers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap

/**
 * Lyric lines drawn as an image to share: white on a dark panel in every theme, since the image
 * outlives the app and has to read wherever it lands.
 */
object LyricsCard {
    const val WIDTH = 1080
    const val HEIGHT = 1350
    private const val MARGIN = 88f
    private const val ARTWORK = 168f
    private const val MAX_TEXT = 92f
    private const val MIN_TEXT = 44f
    private const val BRAND = "Jelyvo"

    /** The largest size at which the passage still fits, or the smallest, for text that never will. */
    fun fitTextSize(
        minSize: Float,
        maxSize: Float,
        availableHeight: Int,
        heightAt: (size: Float) -> Int,
    ): Float {
        var size = maxSize
        while (size > minSize && heightAt(size) > availableHeight) size -= 4f
        return size.coerceAtLeast(minSize)
    }

    fun render(passage: String, title: String, artist: String, artwork: Bitmap?): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        canvas.drawPaint(
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, HEIGHT.toFloat(),
                    Color.rgb(38, 38, 42),
                    Color.rgb(10, 10, 12),
                    Shader.TileMode.CLAMP,
                )
            }
        )

        val contentWidth = (WIDTH - MARGIN * 2).toInt()
        drawHeader(canvas, title, artist, artwork)

        val top = MARGIN + ARTWORK + 72f
        val footerTop = HEIGHT - MARGIN - 44f
        val available = (footerTop - top - 48f).toInt()
        val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val text = passage.trim()
        lyricPaint.textSize = fitTextSize(MIN_TEXT, MAX_TEXT, available) { size ->
            lyricPaint.textSize = size
            layout(text, lyricPaint, contentWidth).height
        }
        val lyric = layout(text, lyricPaint, contentWidth, maxLines = available / lineHeight(lyricPaint))
        canvas.save()
        canvas.translate(MARGIN, top)
        lyric.draw(canvas)
        canvas.restore()

        canvas.drawText(
            BRAND,
            MARGIN,
            HEIGHT - MARGIN,
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(170, 255, 255, 255)
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            },
        )
        return bitmap
    }

    private fun drawHeader(canvas: Canvas, title: String, artist: String, artwork: Bitmap?) {
        val box = RectF(MARGIN, MARGIN, MARGIN + ARTWORK, MARGIN + ARTWORK)
        canvas.drawRoundRect(box, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 255, 255)
        })
        if (artwork != null) {
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(box, 20f, 20f, Path.Direction.CW) })
            canvas.drawBitmap(artwork, centerSquare(artwork), box, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        }
        val textLeft = box.right + 36f
        val textWidth = WIDTH - MARGIN - textLeft
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 255, 255)
            textSize = 38f
        }
        val middle = box.centerY()
        canvas.drawText(
            TextUtils.ellipsize(title, titlePaint, textWidth, TextUtils.TruncateAt.END).toString(),
            textLeft,
            middle - 8f,
            titlePaint,
        )
        canvas.drawText(
            TextUtils.ellipsize(artist, artistPaint, textWidth, TextUtils.TruncateAt.END).toString(),
            textLeft,
            middle + 46f,
            artistPaint,
        )
    }

    private fun centerSquare(bitmap: Bitmap): Rect {
        val side = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        return Rect(left, top, left + side, top + side)
    }

    private fun lineHeight(paint: TextPaint): Int =
        layout("A", paint, WIDTH).height.coerceAtLeast(1)

    private fun layout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int = Int.MAX_VALUE,
    ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1.12f)
        .setMaxLines(maxLines.coerceAtLeast(1))
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
}
