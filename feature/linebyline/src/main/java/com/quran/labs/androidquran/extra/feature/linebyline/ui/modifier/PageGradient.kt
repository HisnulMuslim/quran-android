package com.quran.labs.androidquran.extra.feature.linebyline.ui.modifier

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

fun Modifier.pageGradient(startWithWidth: Boolean): Modifier {
  return background(Color(0xff, 0xfd, 0xf9))
}
