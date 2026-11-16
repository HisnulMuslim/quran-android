package com.quran.labs.androidquran.ui.fragment

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.quran.mobile.common.ui.core.R as UiCoreR
import java.util.Locale
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.Constants.JUZ2_COUNT
import com.quran.labs.androidquran.data.Constants.SURAS_COUNT
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.observers.DisposableSingleObserver
import javax.inject.Inject

class SuraListFragment : Fragment() {

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  @Inject
  lateinit var quranSettings: QuranSettings

  private lateinit var recyclerView: RecyclerView
  private var continueReadingCard: View? = null
  private var continueReadingTitle: TextView? = null
  private var numberOfPages = 0
  private var showSuraTranslatedName = false
  private var disposable: Disposable? = null

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
    numberOfPages = quranInfo.numberOfPages
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view: View = inflater.inflate(R.layout.quran_list, container, false)
    continueReadingCard = view.findViewById(R.id.continue_reading_card)
    continueReadingTitle = view.findViewById(R.id.continue_reading_title)
    recyclerView = view.findViewById(R.id.recycler_view)
    recyclerView.apply {
      layoutManager = LinearLayoutManager(context)
      itemAnimator = DefaultItemAnimator()
      adapter = QuranListAdapter(requireActivity(), recyclerView, getSuraList(), false)
    }
    return view
  }

  override fun onResume() {
    super.onResume()
    val activity = requireActivity()
    if (activity is QuranActivity) {
      val newValueOfShowSuraTranslatedName = quranSettings.isShowSuraTranslatedName
      if (showSuraTranslatedName != newValueOfShowSuraTranslatedName) {
        showHideSuraTranslatedName()
        showSuraTranslatedName = newValueOfShowSuraTranslatedName
      }
      disposable = (requireActivity() as QuranActivity).latestPageObservable
        .first(Constants.NO_PAGE)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(object : DisposableSingleObserver<Int>() {
          override fun onSuccess(recentPage: Int) {
            if (recentPage != Constants.NO_PAGE) {
              val sura = quranDisplayData.safelyGetSuraOnPage(recentPage)
              val juz = quranInfo.getJuzFromPage(recentPage)
              val suraName = quranDisplayData.getSuraName(activity, sura, false, false)
              val pageAndJuzStr = activity.getString(
                R.string.page_description,
                QuranUtils.getLocalizedNumber(activity, recentPage),
                QuranUtils.getLocalizedNumber(activity, juz)
              )
              continueReadingTitle?.text = "$suraName • $pageAndJuzStr"
              continueReadingCard?.visibility = View.VISIBLE
              continueReadingCard?.setOnClickListener {
                (activity as? QuranActivity)?.jumpTo(recentPage)
              }
              val position = sura + juz - 1
              recyclerView.scrollToPosition(position)
            } else {
              continueReadingCard?.visibility = View.GONE
            }
          }

          override fun onError(e: Throwable) {}
        })

      if (quranSettings.isArabicNames) updateScrollBarPositionHoneycomb()
    }
  }

  override fun onPause() {
    disposable?.dispose()
    super.onPause()
  }

  private fun updateScrollBarPositionHoneycomb() {
    recyclerView.verticalScrollbarPosition = View.SCROLLBAR_POSITION_LEFT
  }

  private fun getSuraList(): Array<QuranRow> {
    var next: Int
    var pos = 0
    var sura = 1
    val elements = arrayOfNulls<QuranRow>(SURAS_COUNT + JUZ2_COUNT)

    val activity: Activity = requireActivity()
    val wantPrefix = activity.resources.getBoolean(R.bool.show_surat_prefix)
    val wantTranslation = quranSettings.isShowSuraTranslatedName
    val showArabicOnRight = !quranSettings.isArabicNames && Locale.getDefault().language != "ar"
    val arabicSuraNames = if (showArabicOnRight) {
      val arConfig = Configuration(activity.resources.configuration).apply {
        setLocale(Locale("ar"))
      }
      activity.createConfigurationContext(arConfig).resources.getStringArray(UiCoreR.array.sura_names)
    } else {
      null
    }
    for (juz in 1..JUZ2_COUNT) {
      val headerTitle = activity.getString(
        R.string.juz2_description,
        QuranUtils.getLocalizedNumber(activity, juz)
      )
      val headerBuilder = QuranRow.Builder()
        .withType(QuranRow.HEADER)
        .withText(headerTitle)
        .withPage(quranInfo.getStartingPageForJuz(juz))
      elements[pos++] = headerBuilder.build()
      next = if (juz == JUZ2_COUNT) {
        numberOfPages + 1
      } else quranInfo.getStartingPageForJuz(juz + 1)

      while (sura <= SURAS_COUNT && quranInfo.getPageNumberForSura(sura) < next) {
        val builder = QuranRow.Builder()
          .withText(quranDisplayData.getSuraName(activity, sura, wantPrefix, wantTranslation))
          .withMetadata(quranDisplayData.getSuraListMetaString(activity, sura))
          .withSura(sura)
          .withPage(quranInfo.getPageNumberForSura(sura))
        if (arabicSuraNames != null && sura in 1..arabicSuraNames.size) {
          builder.withArabicSuraName(arabicSuraNames[sura - 1])
        }
        elements[pos++] = builder.build()
        sura++
      }
    }
    return elements.filterNotNull().toTypedArray()
  }

  private fun showHideSuraTranslatedName() {
    val elements = getSuraList()
    (recyclerView.adapter as QuranListAdapter).setElements(elements)
    recyclerView.adapter?.notifyDataSetChanged()
  }

  companion object {
    fun newInstance(): SuraListFragment = SuraListFragment()
  }
}
