package com.quran.labs.androidquran.ui.translation;

import static com.quran.labs.androidquran.ui.PagerActivity.EXTRA_HIGHLIGHT_AYAH;
import static com.quran.labs.androidquran.ui.PagerActivity.EXTRA_HIGHLIGHT_SURA;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.DisplayCutoutCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.quran.data.model.SuraAyah;
import com.quran.data.model.highlight.HighlightType;
import com.quran.data.model.selection.SelectionIndicator;
import com.quran.labs.androidquran.common.LocalTranslationDisplaySort;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.common.TranslationMetadata;
import com.quran.labs.androidquran.data.QuranDisplayData;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.HighlightTypes;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.ui.helpers.TactileFeedback;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.mobile.translation.model.LocalTranslation;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.GestureDetector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.chrisbanes.insetter.Insetter;

public class TranslationView extends FrameLayout implements View.OnClickListener,
    TranslationAdapter.OnVerseSelectedListener, TranslationAdapter.OnJumpToAyahListener {
  private final TranslationAdapter translationAdapter;
  private final RecyclerView translationRecycler;

  private SuraAyah selectedAyah;
  private int selectedAyahId = -1;
  private OnClickListener onClickListener;
  private final LinearLayoutManager layoutManager;
  private PageController pageController;
  private LocalTranslation[] localTranslations;
  private SpacerDecoration spacerDecoration;
  private ScaleGestureDetector scaleGestureDetector;
  private GestureDetector doubleTapDetector;
  private boolean isDraggingRows = false;
  private int rowDragBaseMinId = 0;
  private int rowDragBaseMaxId = 0;
  private QuranAyahInfo rowDragStartAyah = null;
  private QuranAyahInfo rowDragLastAyah = null;

  public TranslationView(Context context) {
    this(context, null);
  }

  public TranslationView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public TranslationView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    translationRecycler = new RecyclerView(context);
    layoutManager = new LinearLayoutManager(context);
    translationRecycler.setLayoutManager(layoutManager);
    translationRecycler.setItemAnimator(new DefaultItemAnimator());
    translationAdapter = new TranslationAdapter(context, translationRecycler, this, this, this);
    translationRecycler.setAdapter(translationAdapter);
    addView(translationRecycler, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

    GestureDetector emptySpaceDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
      @Override
      public boolean onSingleTapUp(MotionEvent e) {
        View child = translationRecycler.findChildViewUnder(e.getX(), e.getY());
        if (child == null) {
          if (hasActiveSelection()) {
            onDeselectAndToggleActionBar();
            return true;
          }
          TactileFeedback.click(TranslationView.this);
          if (onClickListener != null) {
            onClickListener.onClick(TranslationView.this);
            return true;
          }
        }
        return false;
      }
    });

    GestureDetector rowLongPressDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
      @Override
      public boolean onDown(MotionEvent e) {
        return true;
      }

      @Override
      public void onLongPress(MotionEvent e) {
        if (isDraggingRows) return;
        View child = translationRecycler.findChildViewUnder(e.getX(), e.getY());
        if (child != null) {
          int pos = translationRecycler.getChildAdapterPosition(child);
          if (pos != RecyclerView.NO_POSITION && pos < translationAdapter.getItemCount()) {
            TranslationViewRow row = translationAdapter.getItemAt(pos);
            if (row != null && row.getAyahInfo() != null &&
                row.getType() != TranslationViewRow.Type.SURA_HEADER &&
                row.getType() != TranslationViewRow.Type.BASMALLAH &&
                row.getType() != TranslationViewRow.Type.SPACER &&
                row.getType() != TranslationViewRow.Type.FOOTER_ORNAMENT &&
                row.getType() != TranslationViewRow.Type.QURAN_TEXT) {
              onVerseLongPressed(row.getAyahInfo());
            }
          }
        }
      }
    });

    translationRecycler.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
      @Override
      public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        if (!isDraggingRows) {
          emptySpaceDetector.onTouchEvent(e);
          rowLongPressDetector.onTouchEvent(e);
        }
        if (isDraggingRows) {
          if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            handleRowDragMove(e.getX(), e.getY());
            return true;
          } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            finishRowDrag();
            return true;
          }
        }
        return false;
      }

      @Override
      public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        if (isDraggingRows) {
          if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            handleRowDragMove(e.getX(), e.getY());
          } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            finishRowDrag();
          }
        }
      }
    });

    scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
      private float accumulatedScale = 1.0f;
      private long lastStepTime = 0;

      @Override
      public boolean onScaleBegin(ScaleGestureDetector detector) {
        accumulatedScale = 1.0f;
        lastStepTime = System.currentTimeMillis();
        if (getParent() != null) {
          getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
      }

      @Override
      public boolean onScale(ScaleGestureDetector detector) {
        accumulatedScale *= detector.getScaleFactor();
        long now = System.currentTimeMillis();

        if (now - lastStepTime >= 80) {
          if (accumulatedScale >= 1.05f) {
            adjustFontSizes(1);
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            accumulatedScale = 1.0f;
            lastStepTime = now;
          } else if (accumulatedScale <= 0.95f) {
            adjustFontSizes(-1);
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            accumulatedScale = 1.0f;
            lastStepTime = now;
          }
        }
        return true;
      }

      @Override
      public void onScaleEnd(ScaleGestureDetector detector) {
        accumulatedScale = 1.0f;
        if (getParent() != null) {
          getParent().requestDisallowInterceptTouchEvent(false);
        }
      }
    });

    doubleTapDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
      @Override
      public boolean onDoubleTap(MotionEvent e) {
        QuranSettings settings = QuranSettings.getInstance(getContext());
        settings.setAyahTextSize(com.quran.labs.androidquran.data.Constants.DEFAULT_TEXT_SIZE);
        settings.setTranslationTextSize(com.quran.labs.androidquran.data.Constants.DEFAULT_TRANSLATION_TEXT_SIZE);
        performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
        if (getContext() instanceof PagerActivity) {
          ((PagerActivity) getContext()).refreshQuranPages();
        } else if (translationAdapter != null) {
          translationAdapter.refresh(settings);
          translationAdapter.notifyDataSetChanged();
        }
        return true;
      }
    });

    translationRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
      boolean isDragging = false;

      @Override
      public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);

        // do not modify the RecyclerView from this method or any method called from
        // the onScrolled listener, since most modification methods cannot be called
        // while the RecyclerView is computing layout or scrolling
        if (selectedAyah != null && isDragging) {
          updateAyahToolBarPosition();
        }
      }

      @Override
      public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        super.onScrollStateChanged(recyclerView, newState);
        isDragging = newState == RecyclerView.SCROLL_STATE_DRAGGING;
        if (selectedAyah != null && newState == RecyclerView.SCROLL_STATE_IDLE) {
          updateAyahToolBarPosition();
        }
      }
    });

    Insetter.builder()
        .setOnApplyInsetsListener((view, insets, initialState) -> {
          final DisplayCutoutCompat cutout = insets.getDisplayCutout();
          if (cutout != null) {
            final int topSafeOffset = cutout.getSafeInsetTop();
            final int bottomSafeOffset = cutout.getSafeInsetBottom();
            final int horizontalSafeOffset =
                Math.max(cutout.getSafeInsetLeft(), cutout.getSafeInsetRight());

            if (spacerDecoration == null) {
              spacerDecoration = new SpacerDecoration(topSafeOffset, bottomSafeOffset);
              translationRecycler.addItemDecoration(spacerDecoration);
            } else {
              if (spacerDecoration.setOffsets(topSafeOffset, bottomSafeOffset)) {
                translationRecycler.invalidateItemDecorations();
              }
            }

            setPadding(horizontalSafeOffset, 0, horizontalSafeOffset, 0);
          }
        })
        .applyToView(this);
  }

  public void setPageController(PageController controller) {
    this.pageController = controller;
  }

  public void setVerses(@NonNull QuranDisplayData quranDisplayData,
                        @NonNull LocalTranslation[] translations,
                        @NonNull List<QuranAyahInfo> verses) {

    List<TranslationViewRow> rows = new ArrayList<>();
    int currentSura = -1;
    boolean wantTranslationHeaders = translations.length > 1;
    
    boolean onlyArabic = translations.length == 0 || !QuranSettings.getInstance(getContext()).isIncludeTranslation();
    int page = verses.isEmpty() ? 0 : quranDisplayData.getPageFromSuraAyah(verses.get(0).sura, verses.get(0).ayah);
    boolean isFrontispiece = onlyArabic && (page == 1 || page == 2);
    
    setPadding(0, 0, 0, 0);
    setBackground(null);

    if (isFrontispiece) {
      float density = getResources().getDisplayMetrics().density;
      int padH = (int) (14 * density);
      int padV = (int) (16 * density);
      int marginH = (int) (8 * density);
      int marginV = (int) (16 * density);
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
          LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER_VERTICAL);
      lp.setMargins(marginH, marginV, marginH, marginV);
      translationRecycler.setLayoutParams(lp);
      translationRecycler.setPadding(padH, padV, padH, padV);
      translationRecycler.setClipToPadding(false);
      translationRecycler.setBackgroundResource(R.drawable.frontispiece_frame);
    } else {
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
          LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
      translationRecycler.setLayoutParams(lp);
      translationRecycler.setPadding(0, 0, 0, 0);
      translationRecycler.setBackground(null);
    }
    
    if (onlyArabic) {
      SpannableStringBuilder currentSuraText = null;
      QuranAyahInfo suraHeaderVerse = null;
      List<kotlin.ranges.IntRange> ayahRanges = new ArrayList<>();
      List<Integer> ayahIds = new ArrayList<>();
      List<Integer> ayahNumbers = new ArrayList<>();
      
      for (int i = 0, size = verses.size(); i < size; i++) {
        QuranAyahInfo verse = verses.get(i);
        int sura = verse.sura;
        
        if (sura != currentSura) {
          if (currentSuraText != null && currentSuraText.length() > 0) {
            SpannableString finalStr = new SpannableString(currentSuraText);
            for (int k = 0; k < ayahRanges.size(); k++) {
              finalStr.setSpan(new AyahIdSpan(suraHeaderVerse.sura, ayahNumbers.get(k), ayahIds.get(k)), ayahRanges.get(k).getStart(), ayahRanges.get(k).getEndInclusive() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            rows.add(new TranslationViewRow(TranslationViewRow.Type.QURAN_TEXT, suraHeaderVerse, finalStr, -1, null, null, true, ayahRanges));
            rows.add(new TranslationViewRow(TranslationViewRow.Type.SPACER, suraHeaderVerse));
          }
          
          if (verse.ayah == 1 || isFrontispiece) {
            rows.add(new TranslationViewRow(TranslationViewRow.Type.SURA_HEADER, verse,
                quranDisplayData.getSuraName(getContext(), sura, true),
                quranDisplayData.getSuraListMetaString(getContext(), sura)));
          }
          currentSura = sura;
          suraHeaderVerse = verse;
          currentSuraText = new SpannableStringBuilder();
          ayahRanges = new ArrayList<>();
          ayahIds = new ArrayList<>();
          ayahNumbers = new ArrayList<>();
          
          if (verse.arabicText != null && verse.ayah == 1 && sura != 1 && sura != 9) {
            rows.add(new TranslationViewRow(TranslationViewRow.Type.BASMALLAH, verse));
          }
        }
        
        if (verse.arabicText != null) {
          String rawAyahText = ArabicDatabaseUtils.Companion.getAyahWithoutBasmallah(
              verse.sura, verse.ayah, verse.arabicText
          );
          
          int start = currentSuraText.length();
          currentSuraText.append(rawAyahText.trim());
          currentSuraText.append("\u00A0").append(toArabicDigits(verse.ayah));
          currentSuraText.append(" ");
          int end = currentSuraText.length() - 1;
          ayahRanges.add(new kotlin.ranges.IntRange(start, end));
          ayahIds.add(verse.ayahId);
          ayahNumbers.add(verse.ayah);
        }
      }
      
      if (currentSuraText != null && currentSuraText.length() > 0) {
        SpannableString finalStr = new SpannableString(currentSuraText);
        for (int k = 0; k < ayahRanges.size(); k++) {
          finalStr.setSpan(new AyahIdSpan(suraHeaderVerse.sura, ayahNumbers.get(k), ayahIds.get(k)), ayahRanges.get(k).getStart(), ayahRanges.get(k).getEndInclusive() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        rows.add(new TranslationViewRow(TranslationViewRow.Type.QURAN_TEXT, suraHeaderVerse, finalStr, -1, null, null, true, ayahRanges));
        rows.add(new TranslationViewRow(TranslationViewRow.Type.SPACER, suraHeaderVerse));
      }

      if (isFrontispiece && suraHeaderVerse != null) {
        rows.add(new TranslationViewRow(TranslationViewRow.Type.FOOTER_ORNAMENT, suraHeaderVerse));
      }
    } else {
      for (int i = 0, size = verses.size(); i < size; i++) {
        QuranAyahInfo verse = verses.get(i);
        int sura = verse.sura;
        if (sura != currentSura) {
          if (verse.ayah == 1 || isFrontispiece) {
            rows.add(new TranslationViewRow(TranslationViewRow.Type.SURA_HEADER, verse,
                quranDisplayData.getSuraName(getContext(), sura, true),
                quranDisplayData.getSuraListMetaString(getContext(), sura)));
          }
          currentSura = sura;
        }

        if (verse.arabicText != null && verse.ayah == 1 && sura != 1 && sura != 9) {
          rows.add(new TranslationViewRow(TranslationViewRow.Type.BASMALLAH, verse));
        }

        if (verse.arabicText != null) {
          rows.add(new TranslationViewRow(TranslationViewRow.Type.QURAN_TEXT, verse));
        }

        final LocalTranslation[] sortedTranslations = Arrays.copyOf(translations, translations.length);
        Arrays.sort(sortedTranslations, new LocalTranslationDisplaySort());

        for (int j = 0; j < sortedTranslations.length; j++) {
          final TranslationMetadata metadata = findText(verse.texts, (int) sortedTranslations[j].getId());
          CharSequence text = metadata != null ? metadata.getText() : "";
          if (!TextUtils.isEmpty(text)) {
            if (wantTranslationHeaders) {
              rows.add(
                  new TranslationViewRow(TranslationViewRow.Type.TRANSLATOR,
                      verse,
                      sortedTranslations[j].resolveTranslatorName()));
            }
            rows.add(new TranslationViewRow(
                TranslationViewRow.Type.TRANSLATION_TEXT, verse, text, j,
                metadata == null ? null : metadata.getLink(),
                metadata == null ? null : metadata.getLinkPageNumber(),
                "ar".equals(sortedTranslations[j].getLanguageCode()),
                metadata == null ? Collections.emptyList() : metadata.getAyat(),
                metadata == null ? Collections.emptyList() : metadata.getFootnotes()
                ));
          }
        }

        rows.add(new TranslationViewRow(TranslationViewRow.Type.SPACER, verse));
      }
    }

    localTranslations = translations;
    translationAdapter.setLocalTranslations(translations);
    translationAdapter.setData(rows);
    translationAdapter.notifyDataSetChanged();
  }

  public void refresh(@NonNull QuranSettings quranSettings) {
    translationAdapter.refresh(quranSettings);
  }

  public void setBookmarkedAyahs(@NonNull java.util.Set<Integer> bookmarkedAyahIds) {
    translationAdapter.setBookmarkedAyahs(bookmarkedAyahIds);
  }

  public void setTranslationClickedListener(OnClickListener listener) {
    onClickListener = listener;
  }

  @Override
  public void onJumpToAyah(@NonNull SuraAyah target, int page) {
    final Context context = getContext();
    Intent i = new Intent(getContext(), PagerActivity.class);
    i.putExtra("page", page);
    i.putExtra(EXTRA_HIGHLIGHT_SURA, target.sura);
    i.putExtra(EXTRA_HIGHLIGHT_AYAH, target.ayah);
    context.startActivity(i);
  }

  public void highlightAyah(SuraAyah suraAyah, int ayahId, HighlightType highlightType) {
    if (highlightType.equals(HighlightTypes.SELECTION)) {
      selectedAyah = suraAyah;
      selectedAyahId = ayahId;
    } else if (selectedAyah != null) {
      hideMenu();
    }

    if (shouldHandleHighlightType(highlightType)) {
      translationAdapter.setHighlightedAyah(ayahId, highlightType);
    }
  }

  public void highlightAyat(Set<Integer> ayahIds, HighlightType highlightType) {
    if (shouldHandleHighlightType(highlightType)) {
      translationAdapter.highlightAyat(ayahIds, highlightType);
    }
  }

  private boolean shouldHandleHighlightType(HighlightType highlightType) {
    return highlightType.equals(HighlightTypes.AUDIO) || highlightType.equals(HighlightTypes.SELECTION);
  }

  private void hideMenu() {
    pageController.endAyahMode();
  }

  public void unhighlightAyah(HighlightType highlightType) {
    if (highlightType.equals(HighlightTypes.SELECTION)) {
      selectedAyah = null;
      selectedAyahId = -1;
    }

    if (shouldHandleHighlightType(highlightType)) {
      translationAdapter.unhighlight();
      if (selectedAyah != null) {
        // imples that it's not selection, so let's reselect the selected ayah
        translationAdapter.setHighlightedAyah(selectedAyahId, HighlightTypes.SELECTION);
      }
    }
  }

  public void unhighlightAyat(HighlightType highlightType) {
    if (highlightType.equals(HighlightTypes.SELECTION)) {
      selectedAyah = null;
      selectedAyahId = -1;
    }

    if (shouldHandleHighlightType(highlightType)) {
      translationAdapter.unhighlight();
      if (selectedAyah != null) {
        // imples that it's not selection, so let's reselect the selected ayah
        translationAdapter.setHighlightedAyah(selectedAyahId, HighlightTypes.SELECTION);
      }
    }
  }

  @Override
  public void onClick(View v) {
    if (hasActiveSelection()) {
      onDeselectAndToggleActionBar();
      return;
    }

    TactileFeedback.click(this);
    if (onClickListener != null) {
      onClickListener.onClick(v);
    }
  }

  private TranslationMetadata findText(List<TranslationMetadata> texts, Integer translationId) {
    for (TranslationMetadata text : texts) {
      if (translationId.equals(text.getLocalTranslationId())) {
        return text;
      }
    }
    return null;
  }

  @Nullable
  public QuranAyahInfo getQuranAyahInfo(int sura, int ayah) {
    if (selectedAyah != null && selectedAyah.sura == sura && selectedAyah.ayah == ayah) {
      return translationAdapter.highlightedAyahInfo();
    } else {
      return null;
    }
  }

  @Nullable
  public LocalTranslation[] getLocalTranslations() {
    return localTranslations;
  }

  public SelectionIndicator getToolbarPosition(int sura, int ayah) {
    int[] versePopupPosition = translationAdapter.getSelectedVersePopupPosition(sura, ayah);
    if (versePopupPosition != null) {
      return getToolbarPosition(versePopupPosition);
    }
    return SelectionIndicator.None.INSTANCE;
  }

  public SelectionIndicator getToolbarPosition() {
    int[] versePopupPosition = translationAdapter.getSelectedVersePopupPosition();
    if (versePopupPosition != null) {
      return getToolbarPosition(versePopupPosition);
    }
    return SelectionIndicator.None.INSTANCE;
  }

  private SelectionIndicator getToolbarPosition(int[] versePopupPosition) {
    // for dual screen tablet mode, we need to add the view's x (so clicks on the
    // right page properly show on the right page and not on the left one).
    final int[] positionOnScreen = new int[2];
    getLocationOnScreen(positionOnScreen);
    final int xOffset = positionOnScreen[0];

    // Left-align the horizontal action toolbar with safe margin (16dp) so it never clips off-screen
    final int safeLeftX = xOffset + (int) (16 * getResources().getDisplayMetrics().density);

    return new SelectionIndicator.SelectedPointPosition(
        safeLeftX,
        versePopupPosition[1],
        0f,
        0f
    );
  }

  /**
   * This method updates the toolbar position when an ayah is selected
   * This method is called from the onScroll listener, and as thus must make sure not to ask
   * the RecyclerView to change anything (otherwise, it will result in a crash, as methods to
   * update the RecyclerView cannot be called amidst scrolling or computing of a layout).
   */
  private void updateAyahToolBarPosition() {
    pageController.onScrollChanged(0);
  }

  public boolean hasActiveSelection() {
    return selectedAyah != null || translationAdapter.hasSelection();
  }

  @Override
  public void onDeselectAndToggleActionBar() {
    hideMenu();
    selectedAyah = null;
    selectedAyahId = -1;
    translationAdapter.unhighlight();
    if (pageController != null) {
      pageController.endAyahMode();
    }
    TactileFeedback.click(this);
    if (onClickListener != null) {
      onClickListener.onClick(this);
    }
  }

  @Override
  public void onVerseSelected(@NonNull QuranAyahInfo ayahInfo) {
    final SuraAyah suraAyah = new SuraAyah(ayahInfo.sura, ayahInfo.ayah);
    selectedAyah = suraAyah;
    selectedAyahId = ayahInfo.ayahId;
    TactileFeedback.click(this);
    if (pageController != null) {
      pageController.handleSingleTap(suraAyah);
    }
  }

  private void disallowAncestorsIntercept(View view, boolean disallow) {
    android.view.ViewParent parent = view.getParent();
    while (parent != null) {
      parent.requestDisallowInterceptTouchEvent(disallow);
      parent = parent.getParent();
    }
  }

  private QuranAyahInfo findAyahInfoForId(int ayahId) {
    if (ayahId <= 0) return null;
    for (int i = 0; i < translationAdapter.getItemCount(); i++) {
      TranslationViewRow row = translationAdapter.getItemAt(i);
      if (row != null && row.getAyahInfo() != null && row.getAyahInfo().ayahId == ayahId) {
        return row.getAyahInfo();
      }
    }
    return null;
  }

  @Override
  public void onVerseDragStarted(@NonNull QuranAyahInfo ayahInfo) {
    if (selectedAyah != null) {
      hideMenu();
    }
    if (selectedAyahId <= 0) {
      selectedAyah = null;
      selectedAyahId = -1;
      translationAdapter.unhighlight();
      if (pageController != null) {
        pageController.endAyahMode();
      }
    }
  }

  @Override
  public void onVerseLongPressed(@NonNull QuranAyahInfo ayahInfo) {
    if (isDraggingRows) return;

    kotlin.Pair<Integer, Integer> existingRange = translationAdapter.getSelectionMinMaxIds();
    int baseMin = (existingRange != null) ? existingRange.getFirst() : ayahInfo.ayahId;
    int baseMax = (existingRange != null) ? existingRange.getSecond() : ayahInfo.ayahId;

    isDraggingRows = true;
    rowDragBaseMinId = baseMin;
    rowDragBaseMaxId = baseMax;
    rowDragStartAyah = ayahInfo;
    rowDragLastAyah = ayahInfo;
    disallowAncestorsIntercept(translationRecycler, true);
    TactileFeedback.longPress(translationRecycler);

    int minId = Math.min(baseMin, ayahInfo.ayahId);
    int maxId = Math.max(baseMax, ayahInfo.ayahId);
    Set<Integer> range = new HashSet<>();
    for (int id = minId; id <= maxId; id++) {
      range.add(id);
    }
    translationAdapter.highlightAyat(range, HighlightTypes.SELECTION);
  }

  private void handleRowDragMove(float x, float y) {
    if (!isDraggingRows || rowDragStartAyah == null) return;
    View child = translationRecycler.findChildViewUnder(x, y);
    if (child != null) {
      int pos = translationRecycler.getChildAdapterPosition(child);
      if (pos != RecyclerView.NO_POSITION && pos < translationAdapter.getItemCount()) {
        TranslationViewRow row = translationAdapter.getItemAt(pos);
        if (row != null && row.getAyahInfo() != null &&
            row.getType() != TranslationViewRow.Type.SURA_HEADER &&
            row.getType() != TranslationViewRow.Type.BASMALLAH &&
            row.getType() != TranslationViewRow.Type.SPACER &&
            row.getType() != TranslationViewRow.Type.FOOTER_ORNAMENT) {
          QuranAyahInfo current = row.getAyahInfo();
          if (rowDragLastAyah == null || current.ayahId != rowDragLastAyah.ayahId) {
            rowDragLastAyah = current;
            TactileFeedback.dragTick(translationRecycler);
            int minId = Math.min(rowDragBaseMinId, Math.min(rowDragStartAyah.ayahId, current.ayahId));
            int maxId = Math.max(rowDragBaseMaxId, Math.max(rowDragStartAyah.ayahId, current.ayahId));
            Set<Integer> range = new HashSet<>();
            for (int id = minId; id <= maxId; id++) {
              range.add(id);
            }
            translationAdapter.highlightAyat(range, HighlightTypes.SELECTION);
          }
        }
      }
    }
    // Auto-scroll near top or bottom edges
    if (y < 120 && translationRecycler.canScrollVertically(-1)) {
      translationRecycler.scrollBy(0, -25);
    } else if (y > translationRecycler.getHeight() - 120 && translationRecycler.canScrollVertically(1)) {
      translationRecycler.scrollBy(0, 25);
    }
  }

  private void finishRowDrag() {
    if (!isDraggingRows) return;
    isDraggingRows = false;
    disallowAncestorsIntercept(translationRecycler, false);

    if (rowDragStartAyah != null && rowDragLastAyah != null) {
      TactileFeedback.selectionEnd(translationRecycler);
      int minId = Math.min(rowDragBaseMinId, Math.min(rowDragStartAyah.ayahId, rowDragLastAyah.ayahId));
      int maxId = Math.max(rowDragBaseMaxId, Math.max(rowDragStartAyah.ayahId, rowDragLastAyah.ayahId));
      QuranAyahInfo startInfo = findAyahInfoForId(minId);
      QuranAyahInfo endInfo = findAyahInfoForId(maxId);
      if (startInfo == null) startInfo = rowDragStartAyah;
      if (endInfo == null) endInfo = rowDragLastAyah;

      selectedAyah = new SuraAyah(endInfo.sura, endInfo.ayah);
      selectedAyahId = endInfo.ayahId;
      if (minId == maxId) {
        pageController.handleSingleTap(selectedAyah);
      } else {
        pageController.handleAyahRange(
            new SuraAyah(startInfo.sura, startInfo.ayah),
            new SuraAyah(endInfo.sura, endInfo.ayah)
        );
      }
    }
    rowDragStartAyah = null;
    rowDragLastAyah = null;
  }

  @Override
  public void onVerseRangeSelected(@NonNull QuranAyahInfo start, @NonNull QuranAyahInfo end) {
    selectedAyah = new SuraAyah(end.sura, end.ayah);
    selectedAyahId = end.ayahId;
    int minId = Math.min(start.ayahId, end.ayahId);
    int maxId = Math.max(start.ayahId, end.ayahId);
    Set<Integer> range = new HashSet<>();
    for (int id = minId; id <= maxId; id++) {
      range.add(id);
    }
    translationAdapter.highlightAyat(range, HighlightTypes.SELECTION);
    if (start.ayahId == end.ayahId) {
      pageController.handleSingleTap(selectedAyah);
    } else {
      pageController.handleAyahRange(
          new SuraAyah(start.sura, start.ayah),
          new SuraAyah(end.sura, end.ayah)
      );
    }
  }

  public int findFirstCompletelyVisibleItemPosition() {
    return layoutManager.findFirstCompletelyVisibleItemPosition();
  }

  public void setScrollPosition(int position) {
    layoutManager.scrollToPosition(position);
  }

  public static class AyahIdSpan {
    public final int sura;
    public final int ayah;
    public final int ayahId;

    AyahIdSpan(int sura, int ayah, int ayahId) {
      this.sura = sura;
      this.ayah = ayah;
      this.ayahId = ayahId;
    }
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    if (ev.getPointerCount() > 1 && getParent() != null) {
      getParent().requestDisallowInterceptTouchEvent(true);
    }
    if (scaleGestureDetector != null) {
      scaleGestureDetector.onTouchEvent(ev);
      if (scaleGestureDetector.isInProgress()) {
        if (getParent() != null) {
          getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
      }
    }
    if (doubleTapDetector != null) {
      doubleTapDetector.onTouchEvent(ev);
    }
    boolean handled = super.dispatchTouchEvent(ev);
    int action = ev.getActionMasked();
    if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && getParent() != null) {
      getParent().requestDisallowInterceptTouchEvent(false);
    }
    return handled;
  }

  private void adjustFontSizes(int direction) {
    QuranSettings settings = QuranSettings.getInstance(getContext());
    int currentTranslationSize = settings.getTranslationTextSize();
    int currentAyahSize = settings.getAyahTextSize();

    int newTranslationSize = currentTranslationSize + direction;
    int newAyahSize = currentAyahSize + direction;

    newTranslationSize = Math.max(13, Math.min(28, newTranslationSize));
    newAyahSize = Math.max(16, Math.min(34, newAyahSize));

    if (newTranslationSize != currentTranslationSize || newAyahSize != currentAyahSize) {
      settings.setTranslationTextSize(newTranslationSize);
      settings.setAyahTextSize(newAyahSize);

      if (getContext() instanceof PagerActivity) {
        ((PagerActivity) getContext()).refreshQuranPages();
      } else {
        translationAdapter.refresh(settings);
        translationAdapter.notifyDataSetChanged();
      }
    }
  }

  private static final java.text.NumberFormat arabicNumberFormat = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("ar-u-nu-arab"));

  private static String toArabicDigits(int number) {
    return arabicNumberFormat.format(number);
  }
}
