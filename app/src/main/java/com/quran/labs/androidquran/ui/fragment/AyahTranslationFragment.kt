package com.quran.labs.androidquran.ui.fragment

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.quran.data.core.QuranInfo
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.presenter.translation.InlineTranslationPresenter
import com.quran.labs.androidquran.presenter.translation.InlineTranslationPresenter.TranslationScreen
import com.quran.labs.androidquran.presenter.translationlist.TranslationListPresenter
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter
import com.quran.labs.androidquran.ui.util.TranslationsSpinnerAdapter
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.view.InlineTranslationView
import com.quran.labs.androidquran.view.QuranSpinner
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

class AyahTranslationFragment : AyahActionFragment(), TranslationScreen {
  private lateinit var progressBar: ProgressBar
  private lateinit var translationView: InlineTranslationView
  private lateinit var emptyState: View
  private lateinit var translationControls: View
  private lateinit var translator: QuranSpinner
  private lateinit var compareScroll: View
  private lateinit var compareBar: LinearLayout

  private var translationAdapter: TranslationsSpinnerAdapter? = null
  private var selectedCompareFilterId: Int = -1
  private var availableTranslations: List<LocalTranslation> = emptyList()

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranSettings: QuranSettings

  @Inject
  lateinit var translationPresenter: InlineTranslationPresenter

  private val scope = MainScope()

  object Provider : AyahActionFragmentProvider {
    override val order = SlidingPagerAdapter.TRANSLATION_PAGE
    override val iconResId = R.drawable.ic_globe
    override fun newAyahActionFragment() = AyahTranslationFragment()
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (activity as? PagerActivity)?.pagerActivityComponent?.inject(this)
  }

  override fun onDetach() {
    scope.cancel()
    super.onDetach()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(
      R.layout.translation_panel, container, false
    )
    translator = view.findViewById(R.id.translator)
    compareScroll = view.findViewById(R.id.compare_scroll)
    compareBar = view.findViewById(R.id.compare_bar)
    translationView = view.findViewById(R.id.translation_view)
    progressBar = view.findViewById(R.id.progress)
    emptyState = view.findViewById(R.id.empty_state)
    translationControls = view.findViewById(R.id.controls)

    val next = translationControls.findViewById<View>(R.id.next_ayah)
    next.setOnClickListener(onClickListener)
    val prev = translationControls.findViewById<View>(R.id.previous_ayah)
    prev.setOnClickListener(onClickListener)
    val getTranslations = view.findViewById<Button>(R.id.get_translations_button)
    getTranslations.setOnClickListener(onClickListener)
    return view
  }

  override fun onResume() {
    // currently needs to be before we call super.onResume
    translationPresenter.bind(this)
    super.onResume()
  }

  override fun onPause() {
    translationPresenter.unbind(this)
    super.onPause()
  }

  private val onClickListener = View.OnClickListener { v: View ->
    val activity: Activity? = activity
    if (activity is PagerActivity) {
      when (v.id) {
        R.id.get_translations_button -> activity.startTranslationManager()
        R.id.next_ayah -> readingEventPresenter.selectNextAyah()
        R.id.previous_ayah -> readingEventPresenter.selectPreviousAyah()
      }
    }
  }

  override fun onTranslationsUpdated(translations: List<LocalTranslation>) {
    availableTranslations = translations
    if (translations.isEmpty()) {
      progressBar.visibility = View.GONE
      emptyState.visibility = View.VISIBLE
      translationControls.visibility = View.GONE
      translator.visibility = View.GONE
      compareScroll.visibility = View.GONE
      translationView.visibility = View.GONE
    } else {
      val activeTranslationsFilesNames = quranSettings.activeTranslations

      val adapter = translationAdapter
      if (adapter == null) {
        translationAdapter = TranslationsSpinnerAdapter(
          activity,
          R.layout.translation_ab_spinner_item,
          translations.map { it.resolveTranslatorName() }.toTypedArray(),
          translations,
          activeTranslationsFilesNames,
        ) { selectedItems: Set<String?>? ->
          quranSettings.activeTranslations = selectedItems
          // this is the refresh for when a translation is selected from the spinner
          refreshView()
        }
        translator.adapter = translationAdapter
      } else {
        adapter.updateItems(
          translations.map { it.resolveTranslatorName() }.toTypedArray(),
          translations,
          activeTranslationsFilesNames
        )
      }
      updateCompareBar(translations)
      refreshView()
    }
  }

  private fun updateCompareBar(translations: List<LocalTranslation>) {
    if (translations.size <= 1) {
      compareScroll.visibility = View.GONE
      translationPresenter.setCustomTranslations(null)
      return
    }

    if (selectedCompareFilterId >= translations.size) {
      selectedCompareFilterId = -1
    }

    compareScroll.visibility = View.VISIBLE
    compareBar.removeAllViews()

    val ctx = context ?: return
    val density = resources.displayMetrics.density
    val accentColor = ContextCompat.getColor(ctx, R.color.accent_color)
    val isNight = quranSettings.isNightMode
    val unselectedBgColor = if (isNight) Color.parseColor("#2C2C2E") else Color.parseColor("#1A007A5E")
    val unselectedTextColor = if (isNight) Color.WHITE else accentColor

    fun addPill(tagId: Int, label: String) {
      val isSelected = selectedCompareFilterId == tagId
      val pill = TextView(ctx).apply {
        text = label
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
        setTextColor(if (isSelected) Color.WHITE else unselectedTextColor)
        background = GradientDrawable().apply {
          shape = GradientDrawable.RECTANGLE
          cornerRadius = 16 * density
          setColor(if (isSelected) accentColor else unselectedBgColor)
        }
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
          marginEnd = (8 * density).toInt()
        }
        setOnClickListener {
          selectedCompareFilterId = tagId
          applyCompareFilter(translations)
          updateCompareBar(translations)
          refreshView()
        }
      }
      compareBar.addView(pill)
    }

    addPill(-1, getString(R.string.compare_all))
    translations.forEachIndexed { index, trans ->
      addPill(index, shortNameForTranslation(trans))
    }
    applyCompareFilter(translations)
  }

  private fun applyCompareFilter(translations: List<LocalTranslation>) {
    if (selectedCompareFilterId == -1) {
      translationPresenter.setCustomTranslations(translations.map { it.filename })
    } else if (selectedCompareFilterId in translations.indices) {
      translationPresenter.setCustomTranslations(listOf(translations[selectedCompareFilterId].filename))
    } else {
      translationPresenter.setCustomTranslations(null)
    }
  }

  private fun shortNameForTranslation(translation: LocalTranslation): String {
    return when (translation.languageCode) {
      "kn" -> "ಕನ್ನಡ"
      "en" -> "English"
      "ta" -> "தமிழ்"
      "ml" -> "മലയാളം"
      "te" -> "తెలుగు"
      "ur" -> "اردو"
      "hi" -> "हिन्दी"
      "ar" -> "العربية"
      else -> translation.resolveTranslatorName()
    }
  }

  public override fun refreshView() {
    val start = start
    val end = end
    if (start == null || end == null) {
      return
    }

    val verses = 1 + abs(
      quranInfo.getAyahId(start.sura, start.ayah) - quranInfo.getAyahId(end.sura, end.ayah)
    )
    val verseRange = VerseRange(start.sura, start.ayah, end.sura, end.ayah, verses)
    scope.launch {
      translationPresenter.refresh(verseRange)
    }
  }

  override fun setVerses(translations: Array<LocalTranslation>, verses: List<QuranAyahInfo>, ayahHasBeenChanged: Boolean) {
    progressBar.visibility = View.GONE
    if (verses.isNotEmpty()) {
      emptyState.visibility = View.GONE
      translationControls.visibility = View.VISIBLE
      translator.visibility = View.VISIBLE
      translationView.visibility = View.VISIBLE
      translationView.setAyahs(translations, verses)
      if (ayahHasBeenChanged) {
        translationView.resetScroll()
      }
    } else {
      emptyState.visibility = View.VISIBLE
    }
  }
}
