package com.quran.labs.androidquran.view;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.NonNull;

import com.quran.labs.androidquran.ui.translation.TranslationView;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranSettings;

public class QuranTranslationPageLayout extends QuranPageLayout {
  private TranslationView translationView;

  public QuranTranslationPageLayout(Context context) {
    super(context);
    isFullWidth = true;
    initialize();
  }

  @Override
  protected View generateContentView(@NonNull Context context, boolean isLandscape) {
    translationView = new TranslationView(context);
    return translationView;
  }

  @Override
  public void setPageController(PageController controller, int pageNumber, int skips) {
    super.setPageController(controller, pageNumber, skips);
    translationView.setPageController(controller);
  }

  @Override
  public void updateView(@NonNull QuranSettings quranSettings) {
    super.updateView(quranSettings);
    translationView.refresh(quranSettings);
  }

  @Override
  protected void updateBackground(boolean nightMode, QuranSettings quranSettings) {
    if (nightMode) {
      final int backgroundBrightness = quranSettings.getNightModeBackgroundBrightness();
      setBackgroundColor(Color.rgb(backgroundBrightness, backgroundBrightness, backgroundBrightness));
    } else {
      int theme = quranSettings.getReadingTheme();
      if (theme == QuranSettings.THEME_LIGHT) {
        setBackgroundColor(0xFFFFFFFF);
      } else if (theme == QuranSettings.THEME_SAGE) {
        setBackgroundColor(0xFFEEF5F0);
      } else if (theme == QuranSettings.THEME_SLATE) {
        setBackgroundColor(0xFF1E242B);
      } else {
        setBackgroundColor(0xFFFFFDF9);
      }
    }
  }

  @Override
  protected boolean shouldWrapWithScrollView() {
    return false;
  }

  public TranslationView getTranslationView() {
    return translationView;
  }
}
