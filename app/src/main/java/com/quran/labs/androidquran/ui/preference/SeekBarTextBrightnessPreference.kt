package com.quran.labs.androidquran.ui.preference

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import com.quran.labs.androidquran.util.QuranSettings

class SeekBarTextBrightnessPreference(
  context: Context, attrs: AttributeSet
) : SeekBarPreference(context, attrs) {

  override fun getPreviewBoxVisibility(): Int = View.VISIBLE

  override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
    super.onProgressChanged(seekBar, progress, fromUser)

    val nightModeBackgroundBrightness = QuranSettings.getInstance(context).nightModeBackgroundBrightness
    val boxColor = Color.argb(255, nightModeBackgroundBrightness, nightModeBackgroundBrightness, nightModeBackgroundBrightness)

    val lineColor = Color.argb(progress, 255, 255, 255)

    previewBox.setBackgroundColor(boxColor)
    previewBoxText.setTextColor(lineColor)
  }

  override fun updatePreview() {
    val nightModeBackgroundBrightness = QuranSettings.getInstance(context).nightModeBackgroundBrightness
    val  textBrightness = QuranSettings.getInstance(context).nightModeTextBrightness

    val boxColor = Color.argb(255, nightModeBackgroundBrightness, nightModeBackgroundBrightness, nightModeBackgroundBrightness)
    val lineColor = Color.argb(textBrightness, 255, 255, 255)

    previewBox.setBackgroundColor(boxColor)
    previewBoxText.setTextColor(lineColor)
  }

}
