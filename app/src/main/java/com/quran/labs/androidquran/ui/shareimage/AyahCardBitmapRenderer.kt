package com.quran.labs.androidquran.ui.shareimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.quran.labs.androidquran.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object AyahCardBitmapRenderer {

  private const val BASE_WIDTH = 360f
  const val SHARE_URL = "https://quran.hisnulmuslimdua.com/"
  const val DISPLAY_URL = "https://quran.hisnulmuslimdua.com"

  private val FOOTNOTE_MARKER_REGEX = Regex(" *\\[\\d+\\]")
  private val MULTI_SPACE_REGEX = Regex(" {2,}")

  fun stripFootnoteMarkers(text: String): String {
    return text
      .replace(FOOTNOTE_MARKER_REGEX, "")
      .replace(MULTI_SPACE_REGEX, " ")
      .trim()
  }

  fun formatNumberedFootnotes(footnotes: List<String>): String {
    return footnotes.mapIndexed { index, note ->
      val trimmed = note.trim()
      val prefix = "[${index + 1}]"
      if (trimmed.startsWith(prefix) || trimmed.startsWith("${index + 1}.")) {
        trimmed
      } else {
        "$prefix $trimmed"
      }
    }.joinToString("\n")
  }

  fun render(
    context: Context,
    data: AyahShareCardData,
    theme: AyahCardTheme,
    aspectRatio: AyahCardAspectRatio,
    arabicFont: AyahCardArabicFont,
    translationFont: AyahCardTranslationFont = AyahCardTranslationFont.SERIF,
    textColorScheme: AyahCardTextColorScheme,
    customArabicColor: Int? = null,
    translationTextColorScheme: AyahCardTextColorScheme = AyahCardTextColorScheme.THEME_DEFAULT,
    customTranslationColor: Int? = null,
    fontSizeScale: Float,
    showArabic: Boolean,
    showTranslation: Boolean,
    showFootnotes: Boolean,
    showQRCode: Boolean,
    photoBackground: Bitmap? = null,
    photoScale: Float = 1.0f,
    cardWidthPx: Int = 1080
  ): Bitmap {
    val cardHeightPx = aspectRatio.heightFor(cardWidthPx.toFloat()).toInt().coerceAtLeast(1)
    val scale = cardWidthPx / BASE_WIDTH

    val bitmap = Bitmap.createBitmap(cardWidthPx, cardHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cornerRadius = 22f * scale
    val outerClipPath = Path().apply {
      addRoundRect(
        RectF(0f, 0f, cardWidthPx.toFloat(), cardHeightPx.toFloat()),
        cornerRadius,
        cornerRadius,
        Path.Direction.CW
      )
    }

    canvas.save()
    canvas.clipPath(outerClipPath)

    // 1. Background (photo + scrim or theme gradient)
    drawBackground(canvas, cardWidthPx.toFloat(), cardHeightPx.toFloat(), theme, photoBackground, photoScale)

    // 2. Inner ornate border + outer border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1f * scale
      color = theme.innerBorderColor
    }
    val innerMargin = 6f * scale
    canvas.drawRoundRect(
      RectF(innerMargin, innerMargin, cardWidthPx - innerMargin, cardHeightPx - innerMargin),
      cornerRadius - 4f * scale,
      cornerRadius - 4f * scale,
      borderPaint
    )

    val outerPadding = when (aspectRatio) {
      AyahCardAspectRatio.STORY -> 28f * scale
      AyahCardAspectRatio.PORTRAIT -> 24f * scale
      AyahCardAspectRatio.SQUARE -> 22f * scale
    }
    val bottomPadding = when (aspectRatio) {
      AyahCardAspectRatio.STORY -> 16f * scale
      AyahCardAspectRatio.PORTRAIT -> 13f * scale
      AyahCardAspectRatio.SQUARE -> 10f * scale
    }

    val contentLeft = outerPadding
    val contentRight = cardWidthPx - outerPadding
    val contentWidth = (contentRight - contentLeft).toInt().coerceAtLeast(10)

    // 3. Header: Citation Pill Badge (pinned at top)
    val headerBottom = drawHeaderBadge(
      canvas = canvas,
      text = data.referenceText,
      theme = theme,
      textColorScheme = textColorScheme,
      hasPhoto = photoBackground != null,
      centerX = cardWidthPx / 2f,
      topY = outerPadding + 2f * scale,
      scale = scale
    )

    // 4. Footer: App Logo + URL + optional QR Code (pinned at bottom)
    val footerTop = drawFooter(
      context = context,
      canvas = canvas,
      theme = theme,
      hasPhoto = photoBackground != null,
      showQRCode = showQRCode,
      left = contentLeft,
      right = contentRight,
      bottomY = cardHeightPx - bottomPadding,
      scale = scale
    )

    // 5. Middle Content (strictly clipped between headerBottom and footerTop)
    val middleTop = headerBottom + 4f * scale
    val middleBottom = (footerTop - 4f * scale).coerceAtLeast(middleTop + 10f * scale)
    val middleHeight = middleBottom - middleTop

    canvas.save()
    canvas.clipRect(contentLeft, middleTop, contentRight, middleBottom)

    drawMiddleContent(
      context = context,
      canvas = canvas,
      data = data,
      theme = theme,
      arabicFont = arabicFont,
      translationFont = translationFont,
      textColorScheme = textColorScheme,
      customArabicColor = customArabicColor,
      translationTextColorScheme = translationTextColorScheme,
      customTranslationColor = customTranslationColor,
      fontSizeScale = fontSizeScale,
      showArabic = showArabic,
      showTranslation = showTranslation,
      showFootnotes = showFootnotes,
      hasPhoto = photoBackground != null,
      left = contentLeft,
      width = contentWidth,
      middleTop = middleTop,
      middleHeight = middleHeight,
      scale = scale
    )

    canvas.restore()

    // Outer card border
    val outerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1.5f * scale
      color = theme.borderColor
    }
    canvas.drawRoundRect(
      RectF(0.75f * scale, 0.75f * scale, cardWidthPx - 0.75f * scale, cardHeightPx - 0.75f * scale),
      cornerRadius,
      cornerRadius,
      outerStrokePaint
    )

    canvas.restore()
    return bitmap
  }

  private fun drawBackground(
    canvas: Canvas,
    width: Float,
    height: Float,
    theme: AyahCardTheme,
    photo: Bitmap?,
    photoScale: Float
  ) {
    if (photo != null && !photo.isRecycled) {
      val baseScale = max(width / photo.width, height / photo.height) * photoScale
      val drawW = photo.width * baseScale
      val drawH = photo.height * baseScale
      val left = (width - drawW) / 2f
      val top = (height - drawH) / 2f
      val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
      canvas.drawBitmap(photo, null, RectF(left, top, left + drawW, top + drawH), paint)

      // Dark gradient scrim for crisp text legibility
      val scrimPaint = Paint().apply {
        shader = LinearGradient(
          0f, 0f, 0f, height,
          intArrayOf(
            Color.argb(140, 0, 0, 0),
            Color.argb(102, 0, 0, 0),
            Color.argb(166, 0, 0, 0)
          ),
          floatArrayOf(0f, 0.5f, 1f),
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRect(0f, 0f, width, height, scrimPaint)
    } else {
      val bgPaint = Paint().apply {
        shader = LinearGradient(
          0f, 0f, width, height,
          theme.gradientStart,
          theme.gradientEnd,
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRect(0f, 0f, width, height, bgPaint)
    }
  }

  private fun drawHeaderBadge(
    canvas: Canvas,
    text: String,
    theme: AyahCardTheme,
    textColorScheme: AyahCardTextColorScheme,
    hasPhoto: Boolean,
    centerX: Float,
    topY: Float,
    scale: Float
  ): Float {
    val textColor = when {
      textColorScheme.colorInt != null -> textColorScheme.colorInt
      hasPhoto -> Color.WHITE
      else -> theme.badgeTextColor
    }
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = textColor
      textSize = 13f * scale
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val textWidth = textPaint.measureText(text)
    val fm = textPaint.fontMetrics
    val textHeight = fm.descent - fm.ascent

    val padH = 14f * scale
    val padV = 6f * scale
    val pillW = textWidth + padH * 2f
    val pillH = textHeight + padV * 2f
    val pillRect = RectF(centerX - pillW / 2f, topY, centerX + pillW / 2f, topY + pillH)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = if (hasPhoto) Color.argb(70, 0, 0, 0) else theme.badgeBackground
    }
    canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, bgPaint)

    val baselineY = pillRect.centerY() - (fm.ascent + fm.descent) / 2f
    canvas.drawText(text, centerX - textWidth / 2f, baselineY, textPaint)
    return pillRect.bottom
  }

  private fun resolveSchemeColor(
    scheme: AyahCardTextColorScheme,
    customColor: Int?
  ): Int? {
    return when {
      scheme == AyahCardTextColorScheme.CUSTOM && customColor != null -> customColor
      scheme.colorInt != null -> scheme.colorInt
      else -> null
    }
  }

  private fun drawMiddleContent(
    context: Context,
    canvas: Canvas,
    data: AyahShareCardData,
    theme: AyahCardTheme,
    arabicFont: AyahCardArabicFont,
    translationFont: AyahCardTranslationFont,
    textColorScheme: AyahCardTextColorScheme,
    customArabicColor: Int?,
    translationTextColorScheme: AyahCardTextColorScheme,
    customTranslationColor: Int?,
    fontSizeScale: Float,
    showArabic: Boolean,
    showTranslation: Boolean,
    showFootnotes: Boolean,
    hasPhoto: Boolean,
    left: Float,
    width: Int,
    middleTop: Float,
    middleHeight: Float,
    scale: Float
  ) {
    val resolvedArabicOverride = resolveSchemeColor(textColorScheme, customArabicColor)
    val arabicColor = when {
      resolvedArabicOverride != null -> resolvedArabicOverride
      hasPhoto -> Color.WHITE
      else -> theme.arabicTextColor
    }
    val resolvedTransOverride = resolveSchemeColor(translationTextColorScheme, customTranslationColor)
    val translationColor = when {
      resolvedTransOverride != null -> Color.argb(
        242,
        Color.red(resolvedTransOverride),
        Color.green(resolvedTransOverride),
        Color.blue(resolvedTransOverride)
      )
      hasPhoto -> Color.argb(242, 255, 255, 255)
      else -> theme.translationTextColor
    }
    val dividerColor = when {
      resolvedArabicOverride != null -> Color.argb(
        120,
        Color.red(resolvedArabicOverride),
        Color.green(resolvedArabicOverride),
        Color.blue(resolvedArabicOverride)
      )
      hasPhoto -> Color.argb(140, 255, 255, 255)
      else -> theme.dividerColor
    }

    val blocks = mutableListOf<Pair<Float, (Canvas, Float) -> Unit>>()

    if (showArabic && data.arabicText.isNotBlank()) {
      val arabicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = arabicColor
        textSize = (if (arabicFont == AyahCardArabicFont.UTHMANIC) 24f else 22f) * scale * fontSizeScale
        typeface = arabicFont.typeface(context)
      }
      val formattedArabicText = arabicFont.formatArabicSpannable(context, data.arabicText)
      val layout = StaticLayout.Builder
        .obtain(formattedArabicText, 0, formattedArabicText.length, arabicPaint, (width - (12f * scale).toInt()).coerceAtLeast(10))
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(6f * scale * fontSizeScale, 1.15f)
        .build()
      val h = layout.height.toFloat()
      blocks.add(h to { c, y ->
        c.save()
        c.translate(left + 6f * scale, y)
        layout.draw(c)
        c.restore()
      })
    }

    if (showArabic && showTranslation && data.arabicText.isNotBlank() && data.translationText.isNotBlank()) {
      val divH = 18f * scale
      blocks.add(divH to { c, y ->
        drawDecorativeDivider(c, left + width / 2f, y + divH / 2f, width * 0.45f, dividerColor, scale)
      })
    }

    if (showTranslation && data.translationText.isNotBlank()) {
      val displayTranslation = if (showFootnotes && data.footnotes.isNotEmpty()) {
        data.translationText
      } else {
        stripFootnoteMarkers(data.translationText)
      }
      val transPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = translationColor
        textSize = 15f * scale * fontSizeScale
        typeface = translationFont.typeface()
      }
      val layout = StaticLayout.Builder
        .obtain(displayTranslation, 0, displayTranslation.length, transPaint, (width - (12f * scale).toInt()).coerceAtLeast(10))
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(4f * scale * fontSizeScale, 1.12f)
        .build()
      val h = layout.height.toFloat()
      blocks.add(h to { c, y ->
        c.save()
        c.translate(left + 6f * scale, y)
        layout.draw(c)
        c.restore()
      })
    }

    if (showFootnotes && data.footnotes.isNotEmpty()) {
      val gap = 8f * scale
      blocks.add(gap to { _, _ -> })

      val notesText = formatNumberedFootnotes(data.footnotes)
      val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(
          220,
          Color.red(translationColor),
          Color.green(translationColor),
          Color.blue(translationColor)
        )
        textSize = 10.5f * scale * fontSizeScale
        typeface = Typeface.DEFAULT
      }
      val boxPad = 9f * scale
      val innerW = (width - (boxPad * 2 + 12f * scale).toInt()).coerceAtLeast(10)
      val layout = StaticLayout.Builder
        .obtain(notesText, 0, notesText.length, notePaint, innerW)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(2f * scale, 1.05f)
        .build()
      val boxH = layout.height + boxPad * 2f
      blocks.add(boxH to { c, y ->
        val rect = RectF(left + 6f * scale, y, left + width - 6f * scale, y + boxH)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = if (hasPhoto) Color.argb(80, 0, 0, 0) else theme.badgeBackground
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 1f * scale
          color = theme.innerBorderColor
        }
        c.drawRoundRect(rect, 10f * scale, 10f * scale, fillPaint)
        c.drawRoundRect(rect, 10f * scale, 10f * scale, strokePaint)
        c.save()
        c.translate(rect.left + boxPad, rect.top + boxPad)
        layout.draw(c)
        c.restore()
      })
    }

    val totalHeight = blocks.sumOf { it.first.toDouble() }.toFloat()
    var currentY = middleTop + max(0f, (middleHeight - totalHeight) / 2f)
    for ((blockH, drawer) in blocks) {
      drawer(canvas, currentY)
      currentY += blockH
    }
  }

  private fun drawDecorativeDivider(
    canvas: Canvas,
    centerX: Float,
    centerY: Float,
    lineSpan: Float,
    color: Int,
    scale: Float
  ) {
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      strokeWidth = 1f * scale
    }
    val halfSpan = lineSpan / 2f
    val diamondRadius = 3.5f * scale
    canvas.drawLine(centerX - halfSpan, centerY, centerX - diamondRadius * 2.2f, centerY, linePaint)
    canvas.drawLine(centerX + diamondRadius * 2.2f, centerY, centerX + halfSpan, centerY, linePaint)

    val diamond = Path().apply {
      moveTo(centerX, centerY - diamondRadius)
      lineTo(centerX + diamondRadius, centerY)
      lineTo(centerX, centerY + diamondRadius)
      lineTo(centerX - diamondRadius, centerY)
      close()
    }
    val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      style = Paint.Style.FILL
    }
    canvas.drawPath(diamond, diamondPaint)
  }

  private fun drawFooter(
    context: Context,
    canvas: Canvas,
    theme: AyahCardTheme,
    hasPhoto: Boolean,
    showQRCode: Boolean,
    left: Float,
    right: Float,
    bottomY: Float,
    scale: Float
  ): Float {
    val footerColor = if (hasPhoto) Color.argb(242, 255, 255, 255) else theme.footerTextColor
    val urlPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = footerColor
      textSize = (if (showQRCode) 8.8f else 9.5f) * scale
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val logoBitmap = try {
      val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
      if (drawable != null) {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(c)
        bmp
      } else {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
      }
    } catch (_: Exception) {
      null
    }

    val logoSize = 22f * scale
    val qrSize = 32f * scale
    val qrPad = 2.5f * scale
    val qrBoxSize = qrSize + qrPad * 2f

    val rowHeight = if (showQRCode) max(logoSize, qrBoxSize) else logoSize
    val rowTop = bottomY - rowHeight

    if (showQRCode) {
      // Left: Logo + URL
      var cursorX = left + 4f * scale
      if (logoBitmap != null) {
        val logoTop = rowTop + (rowHeight - logoSize) / 2f
        val logoRect = RectF(cursorX, logoTop, cursorX + logoSize, logoTop + logoSize)
        drawRoundedBitmap(canvas, logoBitmap, logoRect, 4.5f * scale)
        cursorX += logoSize + 6f * scale
      }
      val fm = urlPaint.fontMetrics
      val urlY = rowTop + rowHeight / 2f - (fm.ascent + fm.descent) / 2f
      canvas.drawText(DISPLAY_URL, cursorX, urlY, urlPaint)

      // Right: White padded QR Code
      val qrBitmap = generateQrBitmap(SHARE_URL, (qrSize * 2).toInt().coerceAtLeast(120))
      if (qrBitmap != null) {
        val qrLeft = right - 4f * scale - qrBoxSize
        val qrBoxRect = RectF(qrLeft, rowTop, qrLeft + qrBoxSize, rowTop + qrBoxSize)
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(qrBoxRect, 5f * scale, 5f * scale, whitePaint)
        val qrDrawRect = Rect(
          (qrLeft + qrPad).toInt(),
          (rowTop + qrPad).toInt(),
          (qrLeft + qrPad + qrSize).toInt(),
          (rowTop + qrPad + qrSize).toInt()
        )
        canvas.drawBitmap(qrBitmap, null, qrDrawRect, Paint(Paint.FILTER_BITMAP_FLAG))
      }
    } else {
      // Centered pure watermark
      val urlWidth = urlPaint.measureText(DISPLAY_URL)
      val gap = if (logoBitmap != null) 6f * scale else 0f
      val totalW = (if (logoBitmap != null) logoSize else 0f) + gap + urlWidth
      var cursorX = (left + right) / 2f - totalW / 2f
      if (logoBitmap != null) {
        val logoTop = rowTop + (rowHeight - logoSize) / 2f
        drawRoundedBitmap(canvas, logoBitmap, RectF(cursorX, logoTop, cursorX + logoSize, logoTop + logoSize), 4.5f * scale)
        cursorX += logoSize + gap
      }
      val fm = urlPaint.fontMetrics
      val urlY = rowTop + rowHeight / 2f - (fm.ascent + fm.descent) / 2f
      canvas.drawText(DISPLAY_URL, cursorX, urlY, urlPaint)
    }

    return rowTop
  }

  private fun drawRoundedBitmap(canvas: Canvas, bitmap: Bitmap, rect: RectF, radius: Float) {
    canvas.save()
    val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
    canvas.clipPath(path)
    canvas.drawBitmap(bitmap, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
  }

  fun generateQrBitmap(content: String, sizePx: Int): Bitmap? {
    return try {
      val hints = mapOf(EncodeHintType.MARGIN to 0)
      val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val width = matrix.width
      val height = matrix.height
      val pixels = IntArray(width * height)
      for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
          pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
      }
      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
      }
    } catch (_: Exception) {
      null
    }
  }

  fun savePngToShareCache(context: Context, bitmap: Bitmap): File {
    val dir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
    val outFile = File(dir, "ayah_card_${System.currentTimeMillis()}.png")
    // Clean older files so cache stays tidy
    dir.listFiles()?.filter { it.name.startsWith("ayah_card_") }?.forEach {
      if (it.absolutePath != outFile.absolutePath) it.delete()
    }
    FileOutputStream(outFile).use { fos ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
    }
    return outFile
  }
}
