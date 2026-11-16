package com.quran.labs.androidquran.ui.helpers

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

object TactileFeedback {

  @JvmStatic
  fun click(view: View?) {
    view?.performHapticFeedback(
      HapticFeedbackConstants.KEYBOARD_TAP,
      HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )
  }

  @JvmStatic
  fun longPress(view: View?) {
    view?.performHapticFeedback(
      HapticFeedbackConstants.LONG_PRESS,
      HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )
  }

  @JvmStatic
  fun dragTick(view: View?) {
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      HapticFeedbackConstants.SEGMENT_TICK
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      HapticFeedbackConstants.TEXT_HANDLE_MOVE
    } else {
      HapticFeedbackConstants.CLOCK_TICK
    }
    view?.performHapticFeedback(
      constant,
      HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )
  }

  @JvmStatic
  fun selectionEnd(view: View?) {
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      HapticFeedbackConstants.CONFIRM
    } else {
      HapticFeedbackConstants.KEYBOARD_TAP
    }
    view?.performHapticFeedback(
      constant,
      HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )
  }
}

