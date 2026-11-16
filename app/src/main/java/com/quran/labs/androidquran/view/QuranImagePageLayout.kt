package com.quran.labs.androidquran.view

import android.content.Context
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.View.OnTouchListener
import android.view.animation.DecelerateInterpolator
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.util.PageController
import com.quran.labs.androidquran.util.QuranSettings
import kotlin.math.abs

/**
 * Layout class for a single Arabic page of the Quran, with margins/background.
 * <p>
 * Note that the image of the Quran page to be displayed is set by users of this class by calling
 * {@link #getImageView()} and calling the appropriate methods on that view.
 */
open class QuranImagePageLayout(context: Context) : QuranPageLayout(context) {

  private lateinit var imageView: HighlightingImageView
  private var scaleFactor = 1.0f
  private var lastTouchX = 0f
  private var lastTouchY = 0f
  private var isDragging = false
  private var isLongPressDragging = false
  private var lastHapticScale = 1.0f

  init {
    this.initialize()
  }

  override fun generateContentView(context: Context, isLandscape: Boolean): View {
    imageView = HighlightingImageView(context).apply {
      adjustViewBounds = true
      setIsScrollable(isLandscape && shouldWrapWithScrollView(), isLandscape)
    }
    return imageView
  }

  override fun updateView(quranSettings: QuranSettings) {
    super.updateView(quranSettings)
    val isDark = quranSettings.isNightMode || quranSettings.readingTheme == QuranSettings.THEME_SLATE
    val textBrightness = if (quranSettings.isNightMode) quranSettings.nightModeTextBrightness else 235
    val backgroundBrightness = if (quranSettings.isNightMode) quranSettings.nightModeBackgroundBrightness else 0
    imageView.setNightMode(isDark, textBrightness, backgroundBrightness)
  }

  override fun setPageController(controller: PageController?, pageNumber: Int, skips: Int) {
    super.setPageController(controller, pageNumber, skips)
    
    val gestureDetector = GestureDetector(context, PageGestureDetector())
    val scaleDetector = ScaleGestureDetector(context, PageScaleListener())
    
    val gestureListener = OnTouchListener { _, event ->
      scaleDetector.onTouchEvent(event)
      gestureDetector.onTouchEvent(event)

      if (scaleDetector.isInProgress) {
        parent?.requestDisallowInterceptTouchEvent(true)
        return@OnTouchListener true
      }

      val action = event.actionMasked
      if (isLongPressDragging) {
        parent?.requestDisallowInterceptTouchEvent(true)
        when (action) {
          MotionEvent.ACTION_MOVE -> {
            if (event.pointerCount == 1) {
              pageController.handleTouchEvent(
                event,
                AyahSelectedListener.EventType.LONG_PRESS,
                pageNumber
              )
            }
          }
          MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            isLongPressDragging = false
            parent?.requestDisallowInterceptTouchEvent(false)
          }
        }
        return@OnTouchListener true
      }

      if (scaleFactor > 1.05f) {
        parent?.requestDisallowInterceptTouchEvent(true)
        when (action) {
          MotionEvent.ACTION_DOWN -> {
            lastTouchX = event.rawX
            lastTouchY = event.rawY
            isDragging = true
          }
          MotionEvent.ACTION_MOVE -> {
            if (isDragging && !scaleDetector.isInProgress && event.pointerCount == 1) {
              val dx = event.rawX - lastTouchX
              val dy = event.rawY - lastTouchY

              val maxDx = (imageView.width * scaleFactor - imageView.width) / 2f
              val maxDy = (imageView.height * scaleFactor - imageView.height) / 2f

              imageView.translationX = (imageView.translationX + dx).coerceIn(-maxDx, maxDx)
              imageView.translationY = (imageView.translationY + dy).coerceIn(-maxDy, maxDy)

              lastTouchX = event.rawX
              lastTouchY = event.rawY
            }
          }
          MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            isDragging = false
          }
        }
        return@OnTouchListener true
      } else {
        if (action == MotionEvent.ACTION_DOWN) {
          parent?.requestDisallowInterceptTouchEvent(false)
          isDragging = false
          isLongPressDragging = false
        }
      }

      // Allow ViewPager to handle normal horizontal paging swipe when scale == 1.0
      false
    }
    
    imageView.setOnTouchListener(gestureListener)
    imageView.isClickable = true
    imageView.isLongClickable = true
  }

  fun getImageView(): HighlightingImageView = imageView

  private fun animateZoom(targetScale: Float, targetTranslationX: Float, targetTranslationY: Float) {
    imageView.animate()
      .scaleX(targetScale)
      .scaleY(targetScale)
      .translationX(targetTranslationX)
      .translationY(targetTranslationY)
      .setDuration(220)
      .setInterpolator(DecelerateInterpolator())
      .withEndAction {
        scaleFactor = targetScale
        if (targetScale <= 1.05f) {
          scaleFactor = 1.0f
          imageView.translationX = 0f
          imageView.translationY = 0f
          parent?.requestDisallowInterceptTouchEvent(false)
        } else {
          parent?.requestDisallowInterceptTouchEvent(true)
        }
      }
      .start()
  }

  inner class PageScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
      parent?.requestDisallowInterceptTouchEvent(true)
      lastHapticScale = scaleFactor
      return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
      scaleFactor *= detector.scaleFactor
      scaleFactor = scaleFactor.coerceIn(1.0f, 3.0f)

      if (abs(scaleFactor - lastHapticScale) >= 0.15f) {
        imageView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        lastHapticScale = scaleFactor
      }

      imageView.scaleX = scaleFactor
      imageView.scaleY = scaleFactor

      if (scaleFactor > 1.0f) {
        val maxDx = (imageView.width * scaleFactor - imageView.width) / 2f
        val maxDy = (imageView.height * scaleFactor - imageView.height) / 2f

        imageView.translationX = imageView.translationX.coerceIn(-maxDx, maxDx)
        imageView.translationY = imageView.translationY.coerceIn(-maxDy, maxDy)
      } else {
        imageView.translationX = 0f
        imageView.translationY = 0f
      }

      return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
      if (scaleFactor <= 1.05f) {
        animateZoom(1.0f, 0f, 0f)
      } else {
        parent?.requestDisallowInterceptTouchEvent(true)
      }
    }
  }

  inner class PageGestureDetector : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent): Boolean = true

    override fun onSingleTapUp(event: MotionEvent): Boolean {
      return pageController.handleTouchEvent(
        event,
        AyahSelectedListener.EventType.SINGLE_TAP,
        pageNumber
      )
    }

    override fun onDoubleTap(event: MotionEvent): Boolean {
      imageView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
      if (scaleFactor > 1.05f) {
        animateZoom(1.0f, 0f, 0f)
      } else {
        val targetScale = 2.0f
        val focusX = event.x
        val focusY = event.y
        val centerX = imageView.width / 2f
        val centerY = imageView.height / 2f

        val newTranslationX = (centerX - focusX) * (targetScale - 1.0f)
        val newTranslationY = (centerY - focusY) * (targetScale - 1.0f)

        val maxDx = (imageView.width * targetScale - imageView.width) / 2f
        val maxDy = (imageView.height * targetScale - imageView.height) / 2f

        animateZoom(targetScale, newTranslationX.coerceIn(-maxDx, maxDx), newTranslationY.coerceIn(-maxDy, maxDy))
      }
      pageController.handleTouchEvent(
        event,
        AyahSelectedListener.EventType.DOUBLE_TAP,
        pageNumber
      )
      return true
    }

    override fun onLongPress(event: MotionEvent) {
      isLongPressDragging = true
      parent?.requestDisallowInterceptTouchEvent(true)
      imageView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
      pageController.handleTouchEvent(
        event,
        AyahSelectedListener.EventType.LONG_PRESS,
        pageNumber
      )
    }
  }
}
