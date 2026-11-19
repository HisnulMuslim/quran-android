package com.quran.labs.androidquran.ui.shareimage

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class ShareAyahImageSafetyTest {

  private lateinit var context: Context

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
  }

  @After
  fun cleanup() {
    File(context.cacheDir, "pending_share_card.json").delete()
    File(context.cacheDir, "outside_secret.json").delete()
  }

  @Test
  fun smallPayloadUsesInlineIntentAndRoundTrips() {
    val slides = listOf(
      AyahSlideData(
        sura = 1,
        ayah = 1,
        referenceText = "Al-Fatihah 1:1",
        arabicText = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
        translationText = "In the name of Allah, the Entirely Merciful, the Especially Merciful.",
        footnotes = listOf("Opening verse footnote")
      )
    )
    val data = AyahShareCardData(
      referenceText = "Al-Fatihah 1:1",
      arabicText = slides.first().arabicText,
      translationText = slides.first().translationText,
      footnotes = slides.first().footnotes,
      isKannada = false,
      startSura = 1,
      startAyah = 1,
      endSura = 1,
      endAyah = 1,
      ayahSlides = slides
    )

    val intent = ShareAyahImageActivity.createIntent(context, data, isVideoMode = true)
    assertThat(intent.hasExtra("extra_payload_file")).isFalse()
    assertThat(intent.getBooleanExtra("extra_is_video_mode", false)).isTrue()

    val decoded = ShareAyahImageActivity.readCardDataFromIntent(context, intent)
    assertThat(decoded).isEqualTo(data)
  }

  @Test
  fun largeMultiAyahPayloadSpoolsToCacheFileWithoutBloatingIntentExtras() {
    val longArabic = "بِسْمِ اللَّهِ ".repeat(250)
    val longTranslation = "Detailed verse translation sentence with commentary [1]. ".repeat(40)
    val slides = (1..25).map { ayahNum ->
      AyahSlideData(
        sura = 2,
        ayah = ayahNum,
        referenceText = "Al-Baqarah 2:$ayahNum",
        arabicText = longArabic,
        translationText = longTranslation,
        footnotes = listOf("Footnote for ayah $ayahNum")
      )
    }
    val data = AyahShareCardData(
      referenceText = "Al-Baqarah 2:1-25",
      arabicText = slides.joinToString(" ") { it.arabicText },
      translationText = slides.joinToString("\n") { it.translationText },
      footnotes = slides.flatMap { it.footnotes },
      isKannada = true,
      startSura = 2,
      startAyah = 1,
      endSura = 2,
      endAyah = 25,
      ayahSlides = slides
    )

    val intent = ShareAyahImageActivity.createIntent(context, data, isVideoMode = false)
    val payloadPath = intent.getStringExtra("extra_payload_file")

    assertThat(payloadPath).isNotNull()
    assertThat(File(payloadPath!!).exists()).isTrue()
    assertThat(intent.hasExtra("extra_arabic")).isFalse()
    assertThat(intent.hasExtra("extra_translation")).isFalse()
    assertThat(intent.hasExtra("extra_ayah_slides_json")).isFalse()

    val decoded = ShareAyahImageActivity.readCardDataFromIntent(context, intent)
    assertThat(decoded).isEqualTo(data)
    assertThat(decoded.ayahSlides).hasSize(25)
  }

  @Test
  fun externalPayloadPathOutsideCacheDirIsIgnored() {
    val externalFile = File(context.filesDir, "outside_secret.json").apply {
      parentFile?.mkdirs()
      writeText(
        ShareAyahImageActivity.encodeCardDataJson(
          AyahShareCardData(
            referenceText = "Should Not Load",
            arabicText = "hacked",
            translationText = "hacked",
            footnotes = emptyList()
          )
        )
      )
    }
    try {
      val intent = Intent(context, ShareAyahImageActivity::class.java).apply {
        putExtra("extra_payload_file", externalFile.absolutePath)
        putExtra("extra_reference", "Fallback Ref")
      }
      val decoded = ShareAyahImageActivity.readCardDataFromIntent(context, intent)
      assertThat(decoded.referenceText).isEqualTo("Fallback Ref")
      assertThat(decoded.arabicText).isEmpty()
    } finally {
      externalFile.delete()
    }
  }

  @Test
  fun qrBitmapCacheReusesInstancesAndRegeneratesAfterRecycle() {
    val url = AyahCardBitmapRenderer.SHARE_URL
    val first = AyahCardBitmapRenderer.getCachedQrBitmap(url, 180)
    val second = AyahCardBitmapRenderer.getCachedQrBitmap(url, 180)
    val differentSize = AyahCardBitmapRenderer.getCachedQrBitmap(url, 240)

    assertThat(first).isNotNull()
    assertThat(second).isSameInstanceAs(first)
    assertThat(differentSize).isNotNull()
    assertThat(differentSize).isNotSameInstanceAs(first)

    first!!.recycle()
    val regenerated = AyahCardBitmapRenderer.getCachedQrBitmap(url, 180)
    assertThat(regenerated).isNotNull()
    assertThat(regenerated!!.isRecycled).isFalse()
    assertThat(regenerated).isNotSameInstanceAs(first)
  }

  @Test
  fun multilineFootnotesAreCapturedWithDotMatchesAllRegex() {
    val regex = "\\[\\[(.+?)]]".toRegex(RegexOption.DOT_MATCHES_ALL)
    val input = "Some verse text [[first line\nsecond line]] and more [[single]]."
    val extracted = mutableListOf<String>()
    val cleaned = regex.replace(input) { match ->
      extracted += match.groupValues[1].trim()
      " [${extracted.size}]"
    }

    assertThat(extracted).containsExactly("first line\nsecond line", "single").inOrder()
    assertThat(cleaned).isEqualTo("Some verse text  [1] and more  [2].")
    assertThat(AyahCardBitmapRenderer.stripFootnoteMarkers("Text [1] here [2]")).isEqualTo("Text here")
  }

  @Test
  fun remoteBackgroundRepositoryCachedCatalogReadsSafelyWithEmptyCache() = runTest {
    val repo = RemoteBackgroundRepository(context)
    assertThat(repo.cachedCatalogItems()).isNotNull()
    assertThat(repo.hasCachedImage("non_existent_item")).isFalse()
  }

  @Test
  fun renderedCardsUseSquareEdgesForBothImageAndVideoModes() {
    val data = AyahShareCardData(
      referenceText = "Al-Fatihah 1:1",
      arabicText = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
      translationText = "In the name of Allah.",
      footnotes = emptyList()
    )

    val solidCard = AyahCardBitmapRenderer.render(
      context = context,
      data = data,
      theme = AyahCardTheme.EMERALD,
      aspectRatio = AyahCardAspectRatio.SQUARE,
      arabicFont = AyahCardArabicFont.SYSTEM,
      textColorScheme = AyahCardTextColorScheme.THEME_DEFAULT,
      fontSizeScale = 1.0f,
      showArabic = true,
      showTranslation = true,
      showFootnotes = false,
      transparentBackground = false,
      cardWidthPx = 360
    )
    try {
      assertThat(android.graphics.Color.alpha(solidCard.getPixel(0, 0))).isEqualTo(255)
      assertThat(android.graphics.Color.alpha(solidCard.getPixel(359, 0))).isEqualTo(255)
      assertThat(android.graphics.Color.alpha(solidCard.getPixel(0, 359))).isEqualTo(255)
      assertThat(android.graphics.Color.alpha(solidCard.getPixel(359, 359))).isEqualTo(255)
    } finally {
      solidCard.recycle()
    }

    val videoOverlay = AyahCardBitmapRenderer.render(
      context = context,
      data = data,
      theme = AyahCardTheme.EMERALD,
      aspectRatio = AyahCardAspectRatio.SQUARE,
      arabicFont = AyahCardArabicFont.SYSTEM,
      textColorScheme = AyahCardTextColorScheme.THEME_DEFAULT,
      fontSizeScale = 1.0f,
      showArabic = true,
      showTranslation = true,
      showFootnotes = false,
      transparentBackground = true,
      cardWidthPx = 360
    )
    try {
      // Inner border at x=6, y=6 must draw a sharp 90-degree corner (differing from scrim at x=2, y=6)
      assertThat(videoOverlay.getPixel(6, 6)).isNotEqualTo(videoOverlay.getPixel(2, 6))
    } finally {
      videoOverlay.recycle()
    }
  }

  @Test
  fun adaptiveVideoProfilesSelect1080pForCapableHighRamAndBuildFallbackChain() {
    val highCaps = AyahVideoExporter.AvcEncoderCapabilities(
      codecName = "c2.qti.avc.encoder",
      widthAlignment = 2,
      heightAlignment = 2,
      supports1080p30 = true,
      supports720p30 = true,
      maxHighProfileLevel = android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel41
    )

    val flagshipChain = AyahVideoExporter.buildExportProfileChain(
      hasVideoBg = true,
      isMultiSlide = true,
      durationUs = 25_000_000L,
      totalRamBytes = 8_000_000_000L,
      isLowRamDevice = false,
      sdkInt = 34,
      capabilities = highCaps
    )
    assertThat(flagshipChain).hasSize(3)
    assertThat(flagshipChain[0].maxWidth).isEqualTo(1080)
    assertThat(flagshipChain[0].fps).isEqualTo(30)
    assertThat(flagshipChain[0].bitRate).isEqualTo(6_200_000)
    assertThat(flagshipChain[0].preferHighProfile).isTrue()
    assertThat(flagshipChain[1].maxWidth).isEqualTo(720)
    assertThat(flagshipChain[1].fps).isEqualTo(30)
    assertThat(flagshipChain[1].bitRate).isEqualTo(4_500_000)
    assertThat(flagshipChain[2].maxWidth).isEqualTo(720)
    assertThat(flagshipChain[2].fps).isEqualTo(24)
    assertThat(flagshipChain[2].preferHighProfile).isFalse()

    val cappedLevel = AyahVideoExporter.selectHighProfileLevel(
      width = 1080,
      height = 1920,
      maxSupportedLevel = android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel62
    )
    assertThat(cappedLevel).isEqualTo(android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel42)

    val exactSized = AyahCardBitmapRenderer.render(
      context = context,
      data = AyahShareCardData(
        referenceText = "Al-Fatihah 1:1",
        arabicText = "بِسْمِ اللَّهِ",
        translationText = "In the name of Allah."
      ),
      theme = AyahCardTheme.EMERALD,
      aspectRatio = AyahCardAspectRatio.PORTRAIT,
      arabicFont = AyahCardArabicFont.SYSTEM,
      textColorScheme = AyahCardTextColorScheme.THEME_DEFAULT,
      fontSizeScale = 1.0f,
      showArabic = true,
      showTranslation = true,
      showFootnotes = false,
      transparentBackground = true,
      cardWidthPx = 1080,
      cardHeightPxOverride = 1344
    )
    try {
      assertThat(exactSized.width).isEqualTo(1080)
      assertThat(exactSized.height).isEqualTo(1344)
    } finally {
      exactSized.recycle()
    }

    // Mid-range 4 GB device gets 720p @ 30fps -> 720p @ 24fps fallback
    val midChain = AyahVideoExporter.buildExportProfileChain(
      hasVideoBg = true,
      isMultiSlide = false,
      durationUs = 15_000_000L,
      totalRamBytes = 3_800_000_000L,
      isLowRamDevice = false,
      sdkInt = 33,
      capabilities = highCaps
    )
    assertThat(midChain.map { it.maxWidth to it.fps }).containsExactly(720 to 30, 720 to 24).inOrder()

    // Low-RAM device gets conservative 720p @ 24fps only
    val lowRamChain = AyahVideoExporter.buildExportProfileChain(
      hasVideoBg = true,
      isMultiSlide = false,
      durationUs = 15_000_000L,
      totalRamBytes = 2_000_000_000L,
      isLowRamDevice = true,
      sdkInt = 30,
      capabilities = highCaps
    )
    assertThat(lowRamChain.map { it.maxWidth to it.fps }).containsExactly(720 to 24)

    // Ultra-long 120s clip caps bitrate safely below slow-network 55 MB guard (~3.5 Mbps -> 720p@30)
    val longClipChain = AyahVideoExporter.buildExportProfileChain(
      hasVideoBg = true,
      isMultiSlide = true,
      durationUs = 120_000_000L,
      totalRamBytes = 12_000_000_000L,
      isLowRamDevice = false,
      sdkInt = 34,
      capabilities = highCaps
    )
    assertThat(longClipChain[0].maxWidth).isEqualTo(720)
    assertThat(longClipChain[0].bitRate).isAtMost(3_600_000)

    // Strict 16px codec alignment floors cleanly
    val aligned16 = AyahVideoExporter.alignVideoDimensions(1080, 1920, 1080, 16, 16)
    assertThat(aligned16.first % 16).isEqualTo(0)
    assertThat(aligned16.second % 16).isEqualTo(0)

    // Explicit Standard 720p override skips 1080p tier on flagship phones
    val forced720p = AyahVideoExporter.buildExportProfileChain(
      hasVideoBg = true,
      isMultiSlide = false,
      durationUs = 20_000_000L,
      totalRamBytes = 12_000_000_000L,
      isLowRamDevice = false,
      sdkInt = 34,
      capabilities = highCaps,
      requestedQuality = AyahVideoExportQuality.STANDARD_720P
    )
    assertThat(forced720p.map { it.maxWidth to it.fps }).containsExactly(720 to 30, 720 to 24).inOrder()

    // Explicit High 1080p unlocks 1080p on mid-range phones whose encoder supports 1080p@30, while keeping 720p fallback
    val explicitHighMid = AyahVideoExporter.buildExportProfileChain(
      hasVideoBg = true,
      isMultiSlide = false,
      durationUs = 20_000_000L,
      totalRamBytes = 4_000_000_000L,
      isLowRamDevice = false,
      sdkInt = 33,
      capabilities = highCaps,
      requestedQuality = AyahVideoExportQuality.HIGH_1080P
    )
    assertThat(explicitHighMid.map { it.maxWidth to it.fps })
      .containsExactly(1080 to 30, 720 to 30, 720 to 24)
      .inOrder()
  }

  @Test
  fun remoteVideoHdAndSizeLabelsParseCleanly() {
    val hdByResolution = RemoteAyahBackgroundItem(
      id = "hd_1",
      name = "HD Skyline",
      nameKn = null,
      thumbnailUrl = "https://example.com/t.jpg",
      videoUrl = "https://example.com/hd.mp4",
      durationSec = 12.5,
      sizeBytes = 5_400_000L,
      resolution = "1080p"
    )
    val standardItem = RemoteAyahBackgroundItem(
      id = "sd_1",
      name = "Classic Clouds",
      nameKn = null,
      thumbnailUrl = "https://example.com/t2.jpg",
      videoUrl = "https://example.com/sd.mp4",
      durationSec = 12.0,
      sizeBytes = 2_650_000L
    )

    assertThat(hdByResolution.isHdVideo).isTrue()
    assertThat(hdByResolution.formattedSizeLabel()).isEqualTo("5.4 MB")
    assertThat(standardItem.isHdVideo).isFalse()
    assertThat(standardItem.formattedSizeLabel()).isEqualTo("2.7 MB")
  }

  @Test
  fun gaplessTimingResolutionIncludesBismillahAtSuraStartExceptFatihahAndTawbah() {
    val surah112RowsNoZero = listOf(
      1 to 4200L,
      2 to 7000L,
      3 to 9800L,
      4 to 12500L,
      999 to 16000L
    )
    val full112 = AyahVideoExporter.resolveGaplessAyahBounds(112, 1, 4, surah112RowsNoZero)
    assertThat(full112).containsExactly(
      1 to (0L to 7000L),
      2 to (7000L to 9800L),
      3 to (9800L to 12500L),
      4 to (12500L to 16000L)
    ).inOrder()

    val surah112RowsWithZero = listOf(0 to 150L) + surah112RowsNoZero
    val full112WithZero = AyahVideoExporter.resolveGaplessAyahBounds(112, 1, 4, surah112RowsWithZero)
    assertThat(full112WithZero?.first()).isEqualTo(1 to (150L to 7000L))

    val mid112 = AyahVideoExporter.resolveGaplessAyahBounds(112, 2, 4, surah112RowsWithZero)
    assertThat(mid112?.first()).isEqualTo(2 to (7000L to 9800L))

    val tawbahRows = listOf(1 to 3500L, 2 to 9000L, 999 to 0L)
    val tawbah = AyahVideoExporter.resolveGaplessAyahBounds(9, 1, 2, tawbahRows)
    assertThat(tawbah).containsExactly(
      1 to (3500L to 9000L),
      2 to (9000L to Long.MAX_VALUE)
    ).inOrder()

    val fatihahRows = listOf(0 to 0L, 1 to 2800L, 2 to 6500L)
    val fatihah = AyahVideoExporter.resolveGaplessAyahBounds(1, 1, 2, fatihahRows)
    assertThat(fatihah?.first()).isEqualTo(1 to (2800L to 6500L))
  }
}
