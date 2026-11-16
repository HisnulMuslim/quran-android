package com.quran.labs.androidquran.ui.util

import android.content.Context
import android.graphics.Typeface
import com.quran.labs.androidquran.data.QuranFileConstants
import com.quran.labs.androidquran.util.QuranSettings

object TypefaceManager {

  const val TYPE_UTHMANI_HAFS = 1
  const val TYPE_NOOR_HAYAH = 2
  const val TYPE_UTHMANIC_WARSH = 3
  const val TYPE_UTHMANIC_QALOON = 4

  private var typeface: Typeface? = null
  private var cachedFontName: String? = null
  private var hafsAyahTypeface: Typeface? = null
  private var arabicTafseerTypeface: Typeface? = null
  private var arabicHeaderFooterTypeface: Typeface? = null
  private var dyslexicTypeface: Typeface? = null

  @JvmStatic
  fun getUthmaniTypeface(context: Context): Typeface {
    val fontName = when (QuranFileConstants.FONT_TYPE) {
      TYPE_NOOR_HAYAH -> "noorehira.ttf"
      TYPE_UTHMANIC_WARSH -> "uthmanic_warsh_ver09.ttf"
      TYPE_UTHMANIC_QALOON -> "uthmanic_qaloon_ver21.ttf"
      else -> QuranSettings.getInstance(context).arabicFont
    }
    if (typeface != null && cachedFontName == fontName) {
      return typeface!!
    }
    val instance = if (fontName == QuranSettings.FONT_SYSTEM) {
      Typeface.DEFAULT
    } else {
      try {
        Typeface.createFromAsset(context.assets, fontName)
      } catch (_: Exception) {
        Typeface.createFromAsset(context.assets, QuranSettings.FONT_HAFS_BOLD)
      }
    }
    typeface = instance
    cachedFontName = fontName
    return instance
  }

  @JvmStatic
  fun getHafsAyahNumberTypeface(context: Context): Typeface {
    hafsAyahTypeface?.let { return it }
    val instance = Typeface.createFromAsset(context.assets, QuranSettings.FONT_HAFS_BOLD)
    hafsAyahTypeface = instance
    return instance
  }

  @JvmStatic
  fun clearUthmaniTypefaceCache() {
    typeface = null
    cachedFontName = null
  }

  fun getTafseerTypeface(context: Context): Typeface {
    return arabicTafseerTypeface ?: run {
      val instance = Typeface.createFromAsset(context.assets, "kitab.ttf")
      arabicTafseerTypeface = instance
      instance
    }
  }

  fun getDyslexicTypeface(context: Context): Typeface {
    return dyslexicTypeface ?: run {
      val instance = Typeface.createFromAsset(context.assets, "OpenDyslexic.otf")
      dyslexicTypeface = instance
      instance
    }
  }

  fun getHeaderFooterTypeface(context: Context): Typeface {
    return arabicHeaderFooterTypeface ?: run {
      val instance = Typeface.createFromAsset(context.assets, "UthmanTN1Ver10.otf")
      arabicHeaderFooterTypeface = instance
      instance
    }
  }

  fun getTranslationTypeface(context: Context): Typeface {
    val settings = QuranSettings.getInstance(context)
    if (settings.wantDyslexicFontInTranslationView()) {
      return getDyslexicTypeface(context)
    }
    return when (settings.translationFont) {
      QuranSettings.TRANSLATION_FONT_SERIF -> Typeface.SERIF
      QuranSettings.TRANSLATION_FONT_SANS -> Typeface.SANS_SERIF
      QuranSettings.TRANSLATION_FONT_BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      else -> Typeface.DEFAULT
    }
  }

}
