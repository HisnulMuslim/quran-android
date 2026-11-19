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
  private val qrBitmapCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
  @Volatile private var cachedLogoBitmap: Bitmap? = null

  private fun createTextPaint(
    color: Int,
    textSize: Float,
    typeface: Typeface? = null,
    addShadow: Boolean = false,
    scale: Float = 1f
  ): TextPaint {
    return TextPaint(
      Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
    ).apply {
      this.color = color
      this.textSize = textSize
      if (typeface != null) {
        this.typeface = typeface
      }
      if (addShadow) {
        setShadowLayer(1.6f * scale, 0f, 0.8f * scale, Color.argb(135, 0, 0, 0))
      }
    }
  }

  private fun getLogoBitmap(context: Context): Bitmap? {
    cachedLogoBitmap?.takeIf { !it.isRecycled }?.let { return it }
    return try {
      val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
      val bmp = if (drawable != null) {
        val size = 192
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { target ->
          val c = Canvas(target)
          drawable.setBounds(0, 0, size, size)
          drawable.draw(c)
        }
      } else {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
      }
      if (bmp != null) {
        cachedLogoBitmap = bmp
      }
      bmp
    } catch (_: Exception) {
      null
    }
  }

  internal fun getCachedQrBitmap(content: String, sizePx: Int): Bitmap? {
    val safeSize = sizePx.coerceAtLeast(1)
    val key = "$content#$safeSize"
    qrBitmapCache[key]?.takeIf { !it.isRecycled }?.let { return it }
    return generateQrBitmap(content, safeSize)?.also { bmp ->
      if (qrBitmapCache.size >= 16) {
        qrBitmapCache.clear()
      }
      qrBitmapCache[key] = bmp
    }
  }

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
    photoBackground: Bitmap? = null,
    photoScale: Float = 1.0f,
    transparentBackground: Boolean = false,
    cardWidthPx: Int = 1080,
    cardHeightPxOverride: Int? = null
  ): Bitmap {
    val cardHeightPx = (cardHeightPxOverride ?: aspectRatio.heightFor(cardWidthPx.toFloat()).toInt())
      .coerceAtLeast(1)
    val scale = cardWidthPx / BASE_WIDTH
    val useDarkScrimOverlay = photoBackground != null || transparentBackground

    val bitmap = Bitmap.createBitmap(cardWidthPx, cardHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. Background (photo + scrim, scrim-only overlay over video, or theme gradient)
    drawBackground(
      canvas = canvas,
      width = cardWidthPx.toFloat(),
      height = cardHeightPx.toFloat(),
      theme = theme,
      photo = photoBackground,
      photoScale = photoScale,
      transparentBackground = transparentBackground
    )

    // 2. Inner ornate border + outer border (square-edged across both image & video exports)
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1f * scale
      color = theme.innerBorderColor
    }
    val innerMargin = 6f * scale
    canvas.drawRect(
      innerMargin,
      innerMargin,
      cardWidthPx - innerMargin,
      cardHeightPx - innerMargin,
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
      hasPhoto = useDarkScrimOverlay,
      centerX = cardWidthPx / 2f,
      topY = outerPadding + 2f * scale,
      scale = scale
    )

    // 4. Footer: App Logo + Permanent App Store / Google Play Badges & URL + Permanent QR Code
    val footerTop = drawFooter(
      context = context,
      canvas = canvas,
      theme = theme,
      hasPhoto = useDarkScrimOverlay,
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
      hasPhoto = useDarkScrimOverlay,
      left = contentLeft,
      width = contentWidth,
      middleTop = middleTop,
      middleHeight = middleHeight,
      scale = scale
    )

    canvas.restore()

    // Outer card border
    if (!transparentBackground) {
      val outerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * scale
        color = theme.borderColor
      }
      val halfStroke = 0.75f * scale
      canvas.drawRect(
        halfStroke,
        halfStroke,
        cardWidthPx - halfStroke,
        cardHeightPx - halfStroke,
        outerStrokePaint
      )
    }

    return bitmap
  }

  private fun drawBackground(
    canvas: Canvas,
    width: Float,
    height: Float,
    theme: AyahCardTheme,
    photo: Bitmap?,
    photoScale: Float,
    transparentBackground: Boolean
  ) {
    if (transparentBackground || (photo != null && !photo.isRecycled)) {
      if (photo != null && !photo.isRecycled) {
        val baseScale = max(width / photo.width, height / photo.height) * photoScale
        val drawW = photo.width * baseScale
        val drawH = photo.height * baseScale
        val left = (width - drawW) / 2f
        val top = (height - drawH) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(photo, null, RectF(left, top, left + drawW, top + drawH), paint)
      }

      // Dark gradient scrim for crisp text legibility over photo/video
      val scrimPaint = Paint(Paint.DITHER_FLAG).apply {
        shader = LinearGradient(
          0f, 0f, 0f, height,
          intArrayOf(
            Color.argb(156, 0, 0, 0),
            Color.argb(124, 0, 0, 0),
            Color.argb(184, 0, 0, 0)
          ),
          floatArrayOf(0f, 0.5f, 1f),
          Shader.TileMode.CLAMP
        )
      }
      canvas.drawRect(0f, 0f, width, height, scrimPaint)
    } else {
      val bgPaint = Paint(Paint.DITHER_FLAG).apply {
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
    val textPaint = createTextPaint(
      color = textColor,
      textSize = 13f * scale,
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
      addShadow = hasPhoto,
      scale = scale
    )
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
      val arabicPaint = createTextPaint(
        color = arabicColor,
        textSize = (if (arabicFont == AyahCardArabicFont.UTHMANIC) 24f else 22f) * scale * fontSizeScale,
        typeface = arabicFont.typeface(context),
        addShadow = false,
        scale = scale
      )
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
      val transPaint = createTextPaint(
        color = translationColor,
        textSize = 15f * scale * fontSizeScale,
        typeface = translationFont.typeface(),
        addShadow = hasPhoto,
        scale = scale
      )
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
      val notePaint = createTextPaint(
        color = Color.argb(
          220,
          Color.red(translationColor),
          Color.green(translationColor),
          Color.blue(translationColor)
        ),
        textSize = 10.5f * scale * fontSizeScale,
        typeface = Typeface.DEFAULT,
        addShadow = hasPhoto,
        scale = scale
      )
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
    left: Float,
    right: Float,
    bottomY: Float,
    scale: Float
  ): Float {
    val footerColor = if (hasPhoto) Color.argb(242, 255, 255, 255) else theme.footerTextColor
    val isDarkFooterText =
      (Color.red(footerColor) + Color.green(footerColor) + Color.blue(footerColor)) < 384

    val logoBitmap = getLogoBitmap(context)

    val logoSize = 25f * scale
    val qrSize = 33f * scale
    val qrPad = 2.5f * scale
    val qrBoxSize = qrSize + qrPad * 2f

    val rowHeight = max(logoSize, qrBoxSize)
    val rowTop = bottomY - rowHeight
    val qrLeft = right - 4f * scale - qrBoxSize

    // Left: Square App Logo + Store Badge Pill (App Store • Google Play) + Domain Sub-caption
    var cursorX = left + 4f * scale
    if (logoBitmap != null) {
      val logoTop = rowTop + (rowHeight - logoSize) / 2f
      val logoRect = RectF(cursorX, logoTop, cursorX + logoSize, logoTop + logoSize)
      drawRoundedBitmap(canvas, logoBitmap, logoRect, 5f * scale)
      val logoBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.75f * scale
        color = if (isDarkFooterText) Color.argb(45, 0, 0, 0) else Color.argb(90, 255, 255, 255)
      }
      canvas.drawRoundRect(logoRect, 5f * scale, 5f * scale, logoBorder)
      cursorX += logoSize + 7f * scale
    }

    val maxLeftWidth = (qrLeft - 8f * scale - cursorX).coerceAtLeast(80f * scale)

    val pillColorPrimary = if (isDarkFooterText) footerColor else Color.WHITE
    val storeTextPaint = createTextPaint(
      color = pillColorPrimary,
      textSize = 7.4f * scale,
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    )
    val sepPaint = createTextPaint(
      color = Color.argb(
        140,
        Color.red(pillColorPrimary),
        Color.green(pillColorPrimary),
        Color.blue(pillColorPrimary)
      ),
      textSize = 7.4f * scale,
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    )
    val urlPaint = createTextPaint(
      color = Color.argb(
        225,
        Color.red(footerColor),
        Color.green(footerColor),
        Color.blue(footerColor)
      ),
      textSize = 7.2f * scale,
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
      addShadow = hasPhoto,
      scale = scale
    )
    val measuredUrlW = urlPaint.measureText(DISPLAY_URL)
    if (measuredUrlW > maxLeftWidth) {
      urlPaint.textSize *= (maxLeftWidth / measuredUrlW)
    }

    val iconSize = 8.8f * scale
    val iconLabelGap = 3f * scale
    val dividerStr = "  |  "
    val appStoreLabel = "App Store"
    val playStoreLabel = "Google Play"

    val appStoreTextW = storeTextPaint.measureText(appStoreLabel)
    val playStoreTextW = storeTextPaint.measureText(playStoreLabel)
    val dividerW = sepPaint.measureText(dividerStr)
    val pillPadH = 6.5f * scale
    val pillPadV = 3.2f * scale

    val pillContentW = iconSize + iconLabelGap + appStoreTextW + dividerW + iconSize + iconLabelGap + playStoreTextW
    val rawPillW = pillContentW + pillPadH * 2f
    val pillScale = if (rawPillW > maxLeftWidth) (maxLeftWidth / rawPillW) else 1f

    val pillHeight = (iconSize + pillPadV * 2f) * pillScale + 2.5f * scale
    val urlFm = urlPaint.fontMetrics
    val urlHeight = urlFm.descent - urlFm.ascent
    val stackGap = 3.5f * scale
    val totalStackH = pillHeight + stackGap + urlHeight
    val stackTop = rowTop + (rowHeight - totalStackH) / 2f

    // Draw Store Pill
    val drawnPillW = rawPillW * pillScale
    val pillRect = RectF(cursorX, stackTop, cursorX + drawnPillW, stackTop + pillHeight)
    val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = if (isDarkFooterText) Color.argb(22, 15, 23, 42) else Color.argb(115, 0, 0, 0)
    }
    val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 0.7f * scale
      color = if (isDarkFooterText) Color.argb(48, 15, 23, 42) else Color.argb(72, 255, 255, 255)
    }
    val pillRadius = pillHeight / 2f
    canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillBgPaint)
    canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillStrokePaint)

    canvas.save()
    if (pillScale < 1f) {
      canvas.translate(cursorX, stackTop)
      canvas.scale(pillScale, pillScale)
      canvas.translate(-cursorX, -stackTop)
    }
    var innerX = cursorX + pillPadH
    val pillCenterY = stackTop + pillHeight / pillScale / 2f

    // Apple Icon + "App Store"
    drawAppleIcon(canvas, innerX, pillCenterY - iconSize / 2f - 0.3f * scale, iconSize, pillColorPrimary)
    innerX += iconSize + iconLabelGap
    val storeFm = storeTextPaint.fontMetrics
    val storeTextY = pillCenterY - (storeFm.ascent + storeFm.descent) / 2f
    canvas.drawText(appStoreLabel, innerX, storeTextY, storeTextPaint)
    innerX += appStoreTextW

    // Hairline divider
    canvas.drawText(dividerStr, innerX, storeTextY, sepPaint)
    innerX += dividerW

    // Google Play 4-color Icon + "Google Play"
    drawGooglePlayIcon(canvas, innerX, pillCenterY - iconSize / 2f, iconSize)
    innerX += iconSize + iconLabelGap
    canvas.drawText(playStoreLabel, innerX, storeTextY, storeTextPaint)
    canvas.restore()

    // Draw clean domain sub-caption underneath the store pill
    val urlY = stackTop + pillHeight + stackGap - urlFm.ascent
    canvas.drawText(DISPLAY_URL, cursorX + 1.5f * scale, urlY, urlPaint)

    // Right: Permanent white padded QR Code (nearest-neighbor to keep square modules razor-sharp)
    val qrBitmap = getCachedQrBitmap(SHARE_URL, (qrSize * 3).toInt().coerceAtLeast(180))
    if (qrBitmap != null) {
      val qrBoxRect = RectF(qrLeft, rowTop, qrLeft + qrBoxSize, rowTop + qrBoxSize)
      val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
      canvas.drawRoundRect(qrBoxRect, 5f * scale, 5f * scale, whitePaint)
      if (isDarkFooterText) {
        val qrBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 0.65f * scale
          color = Color.argb(42, 15, 23, 42)
        }
        canvas.drawRoundRect(qrBoxRect, 5f * scale, 5f * scale, qrBorderPaint)
      }
      val qrDrawRect = Rect(
        (qrLeft + qrPad).toInt(),
        (rowTop + qrPad).toInt(),
        (qrLeft + qrPad + qrSize).toInt(),
        (rowTop + qrPad + qrSize).toInt()
      )
      val qrDrawPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
      }
      canvas.drawBitmap(qrBitmap, null, qrDrawRect, qrDrawPaint)
    }

    return rowTop
  }

  private fun drawAppleIcon(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
    val s = size / 24f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      style = Paint.Style.FILL
    }
    canvas.save()
    canvas.translate(x, y)
    canvas.scale(s, s)

    // Tilted leaf
    val leaf = Path().apply {
      moveTo(15.6f, 1.4f)
      cubicTo(14.15f, 1.55f, 12.65f, 2.55f, 12.15f, 4.95f)
      cubicTo(13.8f, 5.05f, 15.35f, 3.85f, 15.6f, 1.4f)
      close()
    }
    canvas.drawPath(leaf, paint)

    // Bitten apple silhouette
    val body = Path().apply {
      moveTo(12.1f, 6.2f)
      cubicTo(13.5f, 6.2f, 14.95f, 5.25f, 16.5f, 5.35f)
      cubicTo(17.95f, 5.45f, 19.35f, 6.2f, 20.15f, 7.5f)
      cubicTo(17.1f, 9.35f, 17.6f, 13.75f, 20.6f, 15.0f)
      cubicTo(19.95f, 16.75f, 18.8f, 18.95f, 17.35f, 20.4f)
      cubicTo(16.15f, 21.6f, 15.05f, 21.65f, 13.65f, 21.05f)
      cubicTo(12.6f, 20.6f, 11.75f, 20.6f, 10.6f, 21.05f)
      cubicTo(9.15f, 21.65f, 8.2f, 21.55f, 7.05f, 20.35f)
      cubicTo(4.65f, 17.75f, 3.35f, 13.3f, 4.75f, 9.5f)
      cubicTo(5.65f, 7.0f, 7.7f, 5.55f, 9.8f, 5.55f)
      cubicTo(11.05f, 5.55f, 11.45f, 6.2f, 12.1f, 6.2f)
      close()
    }
    canvas.drawPath(body, paint)
    canvas.restore()
  }

  private fun drawGooglePlayIcon(canvas: Canvas, x: Float, y: Float, size: Float) {
    val s = size / 24f
    canvas.save()
    canvas.translate(x, y)
    canvas.scale(s, s)

    // Outer slightly-rounded play triangle clip
    val clip = Path().apply {
      moveTo(3.5f, 3.0f)
      quadTo(3.5f, 1.5f, 4.8f, 2.2f)
      lineTo(20.6f, 11.2f)
      quadTo(21.9f, 12.0f, 20.6f, 12.8f)
      lineTo(4.8f, 21.8f)
      quadTo(3.5f, 22.5f, 3.5f, 21.0f)
      close()
    }
    canvas.clipPath(clip)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Left blue/cyan facet
    paint.color = Color.rgb(0, 210, 255)
    canvas.drawPath(Path().apply {
      moveTo(3.5f, 1.5f)
      lineTo(13.5f, 12.0f)
      lineTo(3.5f, 22.5f)
      close()
    }, paint)

    // Top green facet
    paint.color = Color.rgb(0, 240, 118)
    canvas.drawPath(Path().apply {
      moveTo(3.5f, 1.5f)
      lineTo(16.6f, 9.1f)
      lineTo(13.5f, 12.0f)
      close()
    }, paint)

    // Right amber/yellow facet
    paint.color = Color.rgb(255, 196, 0)
    canvas.drawPath(Path().apply {
      moveTo(13.5f, 12.0f)
      lineTo(16.6f, 9.1f)
      lineTo(22.0f, 12.0f)
      lineTo(16.6f, 14.9f)
      close()
    }, paint)

    // Bottom red/coral facet
    paint.color = Color.rgb(255, 58, 68)
    canvas.drawPath(Path().apply {
      moveTo(3.5f, 22.5f)
      lineTo(13.5f, 12.0f)
      lineTo(16.6f, 14.9f)
      close()
    }, paint)

    canvas.restore()
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
