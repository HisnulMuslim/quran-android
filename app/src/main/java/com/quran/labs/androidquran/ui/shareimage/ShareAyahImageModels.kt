package com.quran.labs.androidquran.ui.shareimage

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.QuranSettings

data class AyahShareCardData(
  val referenceText: String,
  val arabicText: String,
  val translationText: String,
  val footnotes: List<String> = emptyList(),
  val isKannada: Boolean = false,
  val startSura: Int = 0,
  val startAyah: Int = 0,
  val endSura: Int = 0,
  val endAyah: Int = 0
)

enum class AyahCardAspectRatio(
  val id: String,
  val shortLabel: String,
  val labelEn: String,
  val labelKn: String,
  val heightRatio: Float
) {
  SQUARE("1:1", "1:1", "1:1 Square", "ಚೌಕ (1:1)", 1.0f),
  PORTRAIT("4:5", "4:5", "4:5 Portrait", "ಭಾವಚಿತ್ರ (4:5)", 1.25f),
  STORY("9:16", "9:16", "9:16 Story", "ಕಥೆ (9:16)", 16f / 9f);

  fun displayName(isKannada: Boolean): String = if (isKannada) labelKn else labelEn

  fun heightFor(width: Float): Float = width * heightRatio
}

enum class AyahShareCaptionMode(
  val id: String,
  val labelEn: String,
  val labelKn: String
) {
  IMAGE_ONLY("image_only", "Image Only", "ಚಿತ್ರ ಮಾತ್ರ"),
  LINK_ONLY("link_only", "Link Only", "ಲಿಂಕ್ ಮಾತ್ರ"),
  FULL_TEXT("full_text", "Full Text", "ಸಂಪೂರ್ಣ ಪಠ್ಯ");

  fun displayName(isKannada: Boolean): String = if (isKannada) labelKn else labelEn
}

enum class AyahCardTextColorTarget(
  val id: String,
  val labelEn: String,
  val labelKn: String
) {
  ARABIC("arabic", "Arabic", "ಅರೇಬಿಕ್"),
  TRANSLATION("translation", "Translation", "ಅನುವಾದ");

  fun displayName(isKannada: Boolean): String = if (isKannada) labelKn else labelEn
}

enum class AyahCardTranslationFont(
  val id: String,
  val labelEn: String,
  val labelKn: String
) {
  SERIF("noto_serif", "Serif", "ಸೀರಿಫ್"),
  SANS("noto_sans", "Sans", "ಸಾನ್ಸ್"),
  BOLD("bold", "Bold", "ದಪ್ಪ"),
  SYSTEM("system", "System", "ಸಿಸ್ಟಮ್");

  fun displayName(isKannada: Boolean): String = if (isKannada) labelKn else labelEn

  fun typeface(): Typeface {
    return when (this) {
      SERIF -> Typeface.SERIF
      SANS -> Typeface.SANS_SERIF
      BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      SYSTEM -> Typeface.DEFAULT
    }
  }
}

enum class AyahCardPhotoPreset(
  val id: String,
  val labelEn: String,
  val labelKn: String,
  val drawableResName: String
) {
  MOSQUE_DUSK("mosque_dusk", "Mosque", "ಮಸೀದಿ", "bg_mosque"),
  MAKKAH_NIGHT("makkah_night", "Makkah", "ಮಕ್ಕಾ", "bg_makkah"),
  NABAWI_EVENING("nabawi_evening", "Madinah", "ಮದೀನಾ", "bg_nabawi"),
  DESERT_SUNSET("desert_sunset", "Desert", "ಮರುಭೂಮಿ", "bg_desert"),
  STARRY_NIGHT("starry_night", "Stars", "ನಕ್ಷತ್ರಗಳು", "bg_stars"),
  MISTY_MOUNTAINS("misty_mountains", "Mountains", "ಪರ್ವತಗಳು", "bg_mountains"),
  SACRED_ARCHES("sacred_arches", "Arches", "ಕಮಾನುಗಳು", "bg_arches"),
  OCEAN_SUNSET("ocean_sunset", "Ocean", "ಸಾಗರ", "bg_ocean");

  fun displayName(isKannada: Boolean): String = if (isKannada) labelKn else labelEn
}

enum class AyahCardArabicFont(
  val id: String,
  val labelEn: String,
  val labelKn: String
) {
  UTHMANIC("uthmanic", "Hafs Bold", "ಉಸ್ಮಾನಿ"),
  CLASSIC("classic", "Hafs Classic", "ಕ್ಲಾಸಿಕ್"),
  KITAB("kitab", "Kitab", "ಕಿತಾಬ್"),
  NASKH("naskh", "Naskh", "ನಸ್ಖ್"),
  SYSTEM("system", "System", "ಸಿಸ್ಟಮ್");

  fun displayName(isKannada: Boolean): String = if (isKannada) labelKn else labelEn

  fun formatArabicSpannable(context: Context, text: String): CharSequence {
    if (this == UTHMANIC || this == CLASSIC) return text
    val builder = android.text.SpannableStringBuilder(text)
    for (match in Regex("\u00A0([٠-٩]+)").findAll(text)) {
      val group = match.groups[1] ?: continue
      builder.setSpan(
        com.quran.labs.androidquran.ui.helpers.HafsAyahNumberSpan(context),
        group.range.first,
        group.range.last + 1,
        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
      )
    }
    return builder
  }

  fun typeface(context: Context): Typeface {
    return when (this) {
      UTHMANIC -> try {
        Typeface.createFromAsset(context.assets, QuranSettings.FONT_HAFS_BOLD)
      } catch (_: Exception) {
        TypefaceManager.getUthmaniTypeface(context)
      }
      CLASSIC -> try {
        Typeface.createFromAsset(context.assets, QuranSettings.FONT_HAFS_CLASSIC)
      } catch (_: Exception) {
        TypefaceManager.getUthmaniTypeface(context)
      }
      KITAB -> TypefaceManager.getTafseerTypeface(context)
      NASKH -> try {
        Typeface.createFromAsset(context.assets, QuranSettings.FONT_NASKH)
      } catch (_: Exception) {
        TypefaceManager.getTafseerTypeface(context)
      }
      SYSTEM -> Typeface.DEFAULT
    }
  }
}

enum class AyahCardTextColorScheme(
  val id: String,
  val labelEn: String,
  val labelKn: String,
  val colorInt: Int?
) {
  THEME_DEFAULT("theme_default", "Theme", "ಥೀಮ್", null),
  CLASSIC_GOLD("classic_gold", "Gold", "ಚಿನ್ನ", Color.rgb(255, 235, 179)),
  PURE_WHITE("pure_white", "White", "ಬಿಳಿ", Color.WHITE),
  ICE_CYAN("ice_cyan", "Cyan", "ನೀಲಿ", Color.rgb(166, 235, 255)),
  EMERALD_MINT("emerald_mint", "Mint", "ಪಚ್ಚೆ", Color.rgb(153, 245, 204)),
  SUNSET_AMBER("sunset_amber", "Amber", "ಕಿತ್ತಳೆ", Color.rgb(255, 204, 122)),
  ROSE_GOLD("rose_gold", "Rose", "ಗುಲಾಬಿ", Color.rgb(255, 199, 209)),
  ROYAL_LAVENDER("royal_lavender", "Lavender", "ನೇರಳೆ", Color.rgb(224, 214, 255)),
  CUSTOM("custom", "Custom", "ಕಸ್ಟಮ್", null);

  fun displayName(isKannada: Boolean): String = if (isKannada) labelKn else labelEn

  companion object {
    val presets: List<AyahCardTextColorScheme> = listOf(
      THEME_DEFAULT,
      CLASSIC_GOLD,
      PURE_WHITE,
      ICE_CYAN,
      EMERALD_MINT,
      SUNSET_AMBER,
      ROSE_GOLD,
      ROYAL_LAVENDER
    )
  }
}

enum class AyahCardTheme(
  val id: String,
  val labelEn: String,
  val labelKn: String,
  val iconColor: Int,
  val gradientStart: Int,
  val gradientEnd: Int,
  val borderColor: Int,
  val innerBorderColor: Int,
  val badgeBackground: Int,
  val badgeTextColor: Int,
  val arabicTextColor: Int,
  val translationTextColor: Int,
  val dividerColor: Int,
  val footerTextColor: Int,
  val isLight: Boolean = false
) {
  EMERALD(
    id = "emerald",
    labelEn = "Emerald",
    labelKn = "ಪಚ್ಚೆ ಹಸಿರು",
    iconColor = Color.rgb(5, 89, 64),
    gradientStart = Color.rgb(8, 71, 51),
    gradientEnd = Color.rgb(3, 38, 28),
    borderColor = Color.argb(102, 217, 179, 89),
    innerBorderColor = Color.argb(64, 217, 179, 89),
    badgeBackground = Color.argb(46, 255, 255, 255),
    badgeTextColor = Color.rgb(245, 226, 173),
    arabicTextColor = Color.WHITE,
    translationTextColor = Color.argb(235, 255, 255, 255),
    dividerColor = Color.argb(140, 217, 179, 89),
    footerTextColor = Color.argb(220, 245, 232, 195)
  ),
  MIDNIGHT(
    id = "midnight",
    labelEn = "Midnight",
    labelKn = "ಮಧ್ಯರಾತ್ರಿ",
    iconColor = Color.rgb(20, 31, 56),
    gradientStart = Color.rgb(18, 26, 46),
    gradientEnd = Color.rgb(5, 8, 18),
    borderColor = Color.argb(40, 255, 255, 255),
    innerBorderColor = Color.argb(30, 255, 255, 255),
    badgeBackground = Color.argb(38, 255, 255, 255),
    badgeTextColor = Color.rgb(190, 215, 255),
    arabicTextColor = Color.WHITE,
    translationTextColor = Color.argb(230, 235, 240, 255),
    dividerColor = Color.argb(120, 160, 195, 255),
    footerTextColor = Color.argb(215, 200, 220, 255)
  ),
  ROYAL(
    id = "royal",
    labelEn = "Royal Blue",
    labelKn = "ರಾಜ ನೀಲಿ",
    iconColor = Color.rgb(20, 51, 115),
    gradientStart = Color.rgb(15, 41, 92),
    gradientEnd = Color.rgb(5, 15, 46),
    borderColor = Color.argb(90, 166, 204, 250),
    innerBorderColor = Color.argb(60, 166, 204, 250),
    badgeBackground = Color.argb(40, 255, 255, 255),
    badgeTextColor = Color.rgb(195, 225, 255),
    arabicTextColor = Color.WHITE,
    translationTextColor = Color.argb(235, 240, 248, 255),
    dividerColor = Color.argb(130, 166, 204, 250),
    footerTextColor = Color.argb(220, 205, 230, 255)
  ),
  SUNSET(
    id = "sunset",
    labelEn = "Sunset",
    labelKn = "ಸಂಧ್ಯಾ ಕೆಂಪು",
    iconColor = Color.rgb(115, 31, 46),
    gradientStart = Color.rgb(102, 26, 46),
    gradientEnd = Color.rgb(46, 10, 20),
    borderColor = Color.argb(115, 242, 191, 102),
    innerBorderColor = Color.argb(75, 242, 191, 102),
    badgeBackground = Color.argb(40, 255, 255, 255),
    badgeTextColor = Color.rgb(255, 222, 166),
    arabicTextColor = Color.WHITE,
    translationTextColor = Color.argb(235, 255, 242, 242),
    dividerColor = Color.argb(140, 242, 191, 102),
    footerTextColor = Color.argb(225, 255, 224, 178)
  ),
  OLIVE(
    id = "olive",
    labelEn = "Olive",
    labelKn = "ಆಲಿವ್ ಹಸಿರು",
    iconColor = Color.rgb(56, 77, 46),
    gradientStart = Color.rgb(51, 71, 46),
    gradientEnd = Color.rgb(23, 36, 20),
    borderColor = Color.argb(90, 209, 191, 115),
    innerBorderColor = Color.argb(60, 209, 191, 115),
    badgeBackground = Color.argb(40, 255, 255, 255),
    badgeTextColor = Color.rgb(236, 224, 176),
    arabicTextColor = Color.WHITE,
    translationTextColor = Color.argb(235, 245, 248, 240),
    dividerColor = Color.argb(130, 209, 191, 115),
    footerTextColor = Color.argb(220, 236, 228, 185)
  ),
  PARCHMENT(
    id = "parchment",
    labelEn = "Parchment",
    labelKn = "ಕಾಗದ",
    iconColor = Color.rgb(242, 232, 209),
    gradientStart = Color.rgb(250, 245, 230),
    gradientEnd = Color.rgb(237, 227, 201),
    borderColor = Color.argb(77, 153, 115, 64),
    innerBorderColor = Color.argb(55, 153, 115, 64),
    badgeBackground = Color.argb(28, 140, 95, 45),
    badgeTextColor = Color.rgb(102, 68, 28),
    arabicTextColor = Color.rgb(42, 28, 16),
    translationTextColor = Color.rgb(74, 52, 32),
    dividerColor = Color.argb(120, 153, 115, 64),
    footerTextColor = Color.rgb(112, 78, 38),
    isLight = true
  ),
  MINIMAL(
    id = "minimal",
    labelEn = "Minimal",
    labelKn = "ಶುಭ್ರ ಬಿಳಿ",
    iconColor = Color.WHITE,
    gradientStart = Color.WHITE,
    gradientEnd = Color.rgb(247, 247, 250),
    borderColor = Color.argb(35, 0, 0, 0),
    innerBorderColor = Color.argb(25, 0, 0, 0),
    badgeBackground = Color.argb(16, 0, 122, 94),
    badgeTextColor = Color.rgb(0, 105, 82),
    arabicTextColor = Color.rgb(18, 24, 30),
    translationTextColor = Color.rgb(55, 65, 81),
    dividerColor = Color.argb(90, 0, 122, 94),
    footerTextColor = Color.rgb(75, 85, 99),
    isLight = true
  );

  fun displayName(isKannada: Boolean): String = if (isKannada) labelKn else labelEn
}

data class RemoteAyahBackgroundItem(
  val id: String,
  val name: String,
  val nameKn: String?,
  val thumbnailUrl: String,
  val imageUrl: String
) {
  fun localizedName(isKannada: Boolean): String {
    return if (isKannada && !nameKn.isNullOrBlank()) nameKn else name
  }
}
