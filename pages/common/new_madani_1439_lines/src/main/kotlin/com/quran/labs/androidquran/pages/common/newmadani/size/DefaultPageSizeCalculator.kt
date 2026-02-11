package com.quran.labs.androidquran.pages.common.newmadani.size

import com.quran.data.source.DisplaySize
import com.quran.data.source.PageSizeCalculator

open class DefaultPageSizeCalculator(displaySize: DisplaySize) : PageSizeCalculator {
  private val maxWidth: Int = if (displaySize.x > displaySize.y) displaySize.x else displaySize.y

  // override parameter can be null or other width values
  private var overrideParam: String? = null

  override fun getWidthParameter(): String {
    return overrideParam ?: when {
      maxWidth <= 480 -> "480"
      maxWidth <= 800 -> "800"
      maxWidth <= 1280 -> "1080"
      else -> "1080"
    }
  }

  override fun getTabletWidthParameter(): String {
    return getWidthParameter()
  }

  override fun setOverrideParameter(parameter: String) {
    overrideParam = if (parameter.isNotBlank()) {
      parameter
    } else {
      null
    }
  }
}
