package com.quran.labs.androidquran.util

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.quran.labs.androidquran.R

/**
 * Enables edge-to-edge with white status bar icons and a green status bar scrim
 * that remains solid across all Android versions and OEM skins (including One UI).
 */
fun ComponentActivity.enableGreenStatusBarEdgeToEdge() {
  enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
  )
}

fun ComponentActivity.applyGreenStatusBarInsets(root: ViewGroup) {
  val green = ContextCompat.getColor(this, R.color.toolbar_background_main)
  val contentFrame = findViewById<FrameLayout>(android.R.id.content)
  val statusBarScrim = View(this).apply {
    setBackgroundColor(green)
  }
  contentFrame.addView(
    statusBarScrim,
    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP)
  )

  ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
    val insets = windowInsets.getInsets(
      WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    )
    statusBarScrim.updateLayoutParams {
      height = insets.top
    }
    root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
      topMargin = insets.top
      bottomMargin = insets.bottom
      leftMargin = insets.left
      rightMargin = insets.right
    }
    windowInsets
  }
}
