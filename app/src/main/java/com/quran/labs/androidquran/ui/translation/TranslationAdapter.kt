package com.quran.labs.androidquran.ui.translation

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.common.search.SearchTextUtil
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.HighlightType
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.ui.helpers.ExpandFootnoteSpan
import com.quran.labs.androidquran.ui.helpers.ExpandTafseerSpan
import com.quran.labs.androidquran.ui.helpers.HighlightTypes
import com.quran.labs.androidquran.ui.helpers.TactileFeedback
import com.quran.labs.androidquran.ui.helpers.HafsAyahNumberSpan
import com.quran.labs.androidquran.ui.helpers.UthmaniSpan
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.AyahNumberView
import com.quran.labs.androidquran.view.DividerView
import com.quran.mobile.translation.model.LocalTranslation
import kotlin.math.ln1p
import kotlin.math.min

internal class TranslationAdapter(
  private val context: Context,
  private val recyclerView: RecyclerView,
  private val onClickListener: View.OnClickListener,
  private val onVerseSelectedListener: OnVerseSelectedListener,
  private val onJumpToVerseListener: OnJumpToAyahListener
) : RecyclerView.Adapter<TranslationAdapter.RowViewHolder>() {
  private val inflater: LayoutInflater = LayoutInflater.from(context)
  private val data: MutableList<TranslationViewRow> = mutableListOf()

  private var ayahFontSize: Int = 0
  private var translationFontSize: Int = 0
  private var textColor: Int = 0
  private var footnoteColor: Int = 0
  private var inlineAyahColor: Int = 0
  private var dividerColor: Int = 0
  private var arabicTextColor: Int = 0
  private var suraHeaderColor: Int = 0
  private var ayahSelectionColor: Int = 0
  private var isNightMode: Boolean = false
  private var localTranslations: Array<LocalTranslation>? = null
  private var bookmarkedAyahIds: Set<Int> = emptySet()

  fun setLocalTranslations(translations: Array<LocalTranslation>?) {
    this.localTranslations = translations
  }

  fun setBookmarkedAyahs(ids: Set<Int>) {
    if (bookmarkedAyahIds != ids) {
      bookmarkedAyahIds = ids
      notifyDataSetChanged()
    }
  }

  private var highlightedAyah: Int = 0
  private val highlightedAyahs = mutableSetOf<Int>()
  private var highlightedRowCount: Int = 0
  private var highlightedStartPosition: Int = 0
  private var highlightType: HighlightType? = null

  private val expandedTafseerAyahs = mutableSetOf<Pair<Int, Int>>()
  private val expandedHyperlinks = mutableSetOf<Pair<Int, Int>>()
  private val expandedFootnotes = mutableMapOf<QuranAyahInfo, List<Int>>()

  private val defaultClickListener = View.OnClickListener { this.handleClick(it) }
  private val defaultLongClickListener = View.OnLongClickListener { this.selectVerseRows(it) }
  private val expandClickListener = View.OnClickListener { v -> toggleExpandTafseer(v) }
  private val expandHyperlinkClickListener = View.OnClickListener { v -> toggleTafseerJump(v) }

  private fun positionForAyahSpan(holder: RowViewHolder, startOffset: Int, endOffset: Int): IntArray? {
    val textView = holder.text ?: return null
    val layout = textView.layout ?: return null

    val startLine = layout.getLineForOffset(startOffset)
    val endLine = layout.getLineForOffset(endOffset)

    val lineTop = layout.getLineTop(startLine)
    val lineBottom = layout.getLineBottom(endLine)
    val density = holder.wrapperView.resources.displayMetrics.density
    val toolbarHeight = (52 * density).toInt()

    // Place toolbar above the ayah with margin, or below if near the top
    val yAbove = textView.top + lineTop - toolbarHeight - (6 * density).toInt()
    val yBelow = textView.top + lineBottom + (8 * density).toInt()
    val y = if (yAbove > toolbarHeight) yAbove else yBelow

    val startX = layout.getPrimaryHorizontal(startOffset)
    val endX = layout.getPrimaryHorizontal(endOffset)
    val x = textView.left + ((startX + endX) / 2).toInt()

    return intArrayOf(x, y)
  }

  fun getSelectedVersePopupPosition(): IntArray? {
    return if (highlightedStartPosition > -1) {
      val highlightedEndPosition = highlightedStartPosition + highlightedRowCount

      // find the row with the verse content
      val versePosition = data.withIndex().firstOrNull {
        it.index in highlightedStartPosition until highlightedEndPosition &&
            it.value.type != TranslationViewRow.Type.SURA_HEADER &&
            it.value.type != TranslationViewRow.Type.SPACER
      }

      versePosition?.let {
        val holder = recyclerView.findViewHolderForAdapterPosition(it.index) as RowViewHolder?
        val row = it.value
        if (holder != null && row.type == TranslationViewRow.Type.QURAN_TEXT && row.data is android.text.Spanned) {
          val spans = row.data.getSpans(0, row.data.length, TranslationView.AyahIdSpan::class.java)
          val highlightedSpan = spans.firstOrNull { isAyahHighlighted(it.ayahId) }
          if (highlightedSpan != null) {
            val start = row.data.getSpanStart(highlightedSpan)
            val end = row.data.getSpanEnd(highlightedSpan)
            positionForAyahSpan(holder, start, end)
          } else {
            positionForViewHolderIndex(it.index)
          }
        } else {
          positionForViewHolderIndex(it.index)
        }
      }
    } else {
      null
    }
  }

  fun getSelectedVersePopupPosition(sura: Int, ayah: Int): IntArray? {
    val (startPosition, _) = adapterInfoForAyah(sura, ayah)
    return if (startPosition > -1) {
      val viewHolder = recyclerView.findViewHolderForAdapterPosition(startPosition) as RowViewHolder?
      viewHolder?.let { holder ->
        val row = data[startPosition]
        if (row.type == TranslationViewRow.Type.QURAN_TEXT && row.data is android.text.Spanned) {
          val spans = row.data.getSpans(0, row.data.length, TranslationView.AyahIdSpan::class.java)
          val span = spans.firstOrNull { it.sura == sura && it.ayah == ayah }
          if (span != null) {
            val start = row.data.getSpanStart(span)
            val end = row.data.getSpanEnd(span)
            positionForAyahSpan(holder, start, end)
          } else {
            positionForViewHolderIndex(startPosition)
          }
        } else {
          positionForViewHolderIndex(startPosition)
        }
      }
    } else {
      null
    }
  }

  private fun positionForViewHolderIndex(index: Int): IntArray? {
    val viewHolder = recyclerView.findViewHolderForAdapterPosition(index) as RowViewHolder?
    return viewHolder?.let { holder ->
      val ayahNumberView = holder.ayahNumber
      if (ayahNumberView != null && ayahNumberView.visibility == View.VISIBLE) {
        val x = (ayahNumberView.left + ayahNumberView.boxCenterX)
        val y = (ayahNumberView.top + ayahNumberView.boxBottomY)
        intArrayOf(x, y)
      } else {
        holder.text?.let { textView ->
          val x = (textView.left + textView.right) / 2
          val y = (textView.top + textView.bottom) / 2
          intArrayOf(x, y)
        }
      }
    }
  }

  fun setData(data: List<TranslationViewRow>) {
    this.data.clear()
    expandedTafseerAyahs.clear()
    this.data.addAll(data)
    if (highlightedAyah > 0) {
      highlightAyah(highlightedAyah, true, highlightType ?: HighlightTypes.SELECTION, true)
    }
  }

  fun setHighlightedAyah(ayahId: Int, highlightType: HighlightType) {
    highlightAyah(ayahId, true, highlightType)
  }

  fun isAyahHighlighted(ayahId: Int): Boolean {
    return highlightedAyah == ayahId || highlightedAyahs.contains(ayahId)
  }

  fun hasSelection(): Boolean = highlightedAyah != 0 || highlightedAyahs.isNotEmpty()

  fun getSelectionMinMaxIds(): Pair<Int, Int>? {
    return when {
      highlightedAyahs.isNotEmpty() -> {
        val min = highlightedAyahs.minOrNull() ?: return null
        val max = highlightedAyahs.maxOrNull() ?: return null
        min to max
      }
      highlightedAyah != 0 -> highlightedAyah to highlightedAyah
      else -> null
    }
  }

  fun highlightAyat(ayahIds: Set<Int>, highlightedType: HighlightType) {
    val toUpdate = mutableSetOf<Int>()
    data.forEachIndexed { index, row ->
      val containsOld = if (row.type == TranslationViewRow.Type.QURAN_TEXT && row.data is android.text.Spanned) {
        val spans = row.data.getSpans(0, row.data.length, TranslationView.AyahIdSpan::class.java)
        spans.any { span -> highlightedAyahs.contains(span.ayahId) || span.ayahId == highlightedAyah }
      } else {
        highlightedAyahs.contains(row.ayahInfo.ayahId) || row.ayahInfo.ayahId == highlightedAyah
      }
      if (containsOld) {
        toUpdate.add(index)
      }
    }
    highlightedAyah = 0
    highlightedAyahs.clear()
    highlightedAyahs.addAll(ayahIds)
    highlightType = highlightedType
    data.forEachIndexed { index, row ->
      val containsNew = if (row.type == TranslationViewRow.Type.QURAN_TEXT && row.data is android.text.Spanned) {
        val spans = row.data.getSpans(0, row.data.length, TranslationView.AyahIdSpan::class.java)
        spans.any { span -> highlightedAyahs.contains(span.ayahId) }
      } else {
        highlightedAyahs.contains(row.ayahInfo.ayahId)
      }
      if (containsNew) {
        toUpdate.add(index)
      }
    }
    recyclerView.handler.post {
      toUpdate.forEach { index ->
        notifyItemChanged(index, HIGHLIGHT_CHANGE)
      }
    }
  }

  fun highlightedAyahInfo(): QuranAyahInfo? {
    return data.firstOrNull { isAyahHighlighted(it.ayahInfo.ayahId) }?.ayahInfo
  }

  private fun adapterInfoForAyah(sura: Int, ayah: Int): Pair<Int, Int> {
    val matches =
      data.withIndex().filter {
        val row = it.value
        val matchesVerse = if (row.type == TranslationViewRow.Type.QURAN_TEXT && row.data is android.text.Spanned) {
          val spans = row.data.getSpans(0, row.data.length, TranslationView.AyahIdSpan::class.java)
          spans.any { span -> span.sura == sura && span.ayah == ayah }
        } else {
          row.ayahInfo.sura == sura && row.ayahInfo.ayah == ayah
        }
        matchesVerse && row.type > 1
      }
    return (matches.firstOrNull()?.index ?: -1) to matches.size
  }

  private fun highlightAyah(ayahId: Int, notify: Boolean, highlightedType: HighlightType, force: Boolean = false) {
    if (ayahId != highlightedAyah || force) {
      val matches = data.withIndex().filter {
        val row = it.value
        if (row.type == TranslationViewRow.Type.QURAN_TEXT && row.data is android.text.Spanned) {
          val spans = row.data.getSpans(0, row.data.length, TranslationView.AyahIdSpan::class.java)
          spans.any { span -> span.ayahId == ayahId }
        } else {
          row.ayahInfo.ayahId == ayahId
        }
      }
      val (startPosition, count) = (matches.firstOrNull()?.index ?: -1) to matches.size

      // highlight the newly highlighted ayah
      if (count > 0 && notify) {
        var startChangeCount = count
        var startChangeRange = startPosition
        if (highlightedRowCount > 0) {
          when {
            // merge the requests for notifyItemRangeChanged when we're either the next ayah
            highlightedStartPosition + highlightedRowCount + 1 == startPosition -> {
              startChangeRange = highlightedStartPosition
              startChangeCount += highlightedRowCount
            }
            // ... or when we're the previous ayah
            highlightedStartPosition - 1 == startPosition + count ->
              startChangeCount += highlightedRowCount
            else -> {
              // otherwise, unhighlight
              val start = highlightedStartPosition
              val changeCount = highlightedRowCount
              recyclerView.handler.post {
                notifyItemRangeChanged(start, changeCount, HIGHLIGHT_CHANGE)
              }
            }
          }
        }

        // and update rows to be highlighted
        recyclerView.handler.post {
          notifyItemRangeChanged(startChangeRange, startChangeCount, HIGHLIGHT_CHANGE)
          val layoutManager = recyclerView.layoutManager
          if (highlightedType == HighlightTypes.AUDIO) {
            if (layoutManager is LinearLayoutManager) {
              layoutManager.scrollToPositionWithOffset(startPosition, 64)
            } else {
              recyclerView.smoothScrollToPosition(startPosition)
            }
          } else if (force) {
            recyclerView.smoothScrollToPosition(startPosition)
          }
        }
      }

      highlightedAyah = ayahId
      highlightedStartPosition = startPosition
      highlightedRowCount = count
      highlightType = highlightedType
    }
  }

  fun unhighlight() {
    val toUpdate = mutableSetOf<Int>()
    data.forEachIndexed { index, row ->
      val containsOld = if (row.type == TranslationViewRow.Type.QURAN_TEXT && row.data is android.text.Spanned) {
        val spans = row.data.getSpans(0, row.data.length, TranslationView.AyahIdSpan::class.java)
        spans.any { span -> span.ayahId == highlightedAyah || highlightedAyahs.contains(span.ayahId) }
      } else {
        highlightedAyah == row.ayahInfo.ayahId || highlightedAyahs.contains(row.ayahInfo.ayahId)
      }
      if (containsOld) {
        toUpdate.add(index)
      }
    }
    highlightedAyah = 0
    highlightedAyahs.clear()
    highlightedRowCount = 0
    highlightedStartPosition = -1
    recyclerView.handler.post {
      toUpdate.forEach { index ->
        notifyItemChanged(index, HIGHLIGHT_CHANGE)
      }
    }
  }

  fun refresh(quranSettings: QuranSettings) {
    this.ayahFontSize = quranSettings.ayahTextSize
    this.translationFontSize = quranSettings.translationTextSize
    isNightMode = quranSettings.isNightMode
    if (isNightMode) {
      val originalTextBrightness = quranSettings.nightModeTextBrightness
      val backgroundBrightness = quranSettings.nightModeBackgroundBrightness
      // avoid damaging the looks of the Quran page
      val adjustedBrightness = (50 * ln1p(backgroundBrightness.toDouble()) + originalTextBrightness).toInt()
      val textBrightness = min(adjustedBrightness.toFloat(), 255f).toInt()

      this.textColor = Color.rgb(textBrightness, textBrightness, textBrightness)
      this.footnoteColor = ContextCompat.getColor(context, R.color.translation_footnote_color)
      this.arabicTextColor = textColor
      this.dividerColor = textColor
      this.suraHeaderColor = ContextCompat.getColor(context, R.color.translation_sura_header_night)
      this.ayahSelectionColor = Color.parseColor("#4D80CBC4") // 30% teal in dark mode
    } else {
      val theme = quranSettings.readingTheme
      when (theme) {
        QuranSettings.THEME_LIGHT -> {
          this.textColor = ContextCompat.getColor(context, R.color.translation_text_color)
          this.footnoteColor = ContextCompat.getColor(context, R.color.translation_footnote_color)
          this.dividerColor = ContextCompat.getColor(context, R.color.translation_divider_color)
          this.arabicTextColor = Color.BLACK
          this.suraHeaderColor = ContextCompat.getColor(context, R.color.translation_sura_header)
          this.ayahSelectionColor = Color.parseColor("#47007A5E") // 28% rich emerald teal in light mode
        }
        QuranSettings.THEME_SAGE -> {
          this.textColor = Color.parseColor("#1B3022")
          this.footnoteColor = Color.parseColor("#4A6B55")
          this.dividerColor = Color.parseColor("#D4E4D8")
          this.arabicTextColor = Color.parseColor("#1B3022")
          this.suraHeaderColor = Color.parseColor("#DCEBE0")
          this.ayahSelectionColor = Color.parseColor("#472E7D32") // 28% forest green in sage mode
        }
        QuranSettings.THEME_SLATE -> {
          this.textColor = Color.parseColor("#E2E8F0")
          this.footnoteColor = Color.parseColor("#94A3B8")
          this.dividerColor = Color.parseColor("#2E3744")
          this.arabicTextColor = Color.parseColor("#F1F5F9")
          this.suraHeaderColor = Color.parseColor("#28323E")
          this.ayahSelectionColor = Color.parseColor("#4D64B5F6") // 30% sky blue in slate mode
        }
        else -> {
          this.textColor = Color.parseColor("#3E2723")
          this.footnoteColor = ContextCompat.getColor(context, R.color.translation_footnote_color)
          this.dividerColor = Color.parseColor("#EFE5D9")
          this.arabicTextColor = Color.parseColor("#3E2723")
          this.suraHeaderColor = Color.parseColor("#F5EAD4")
          this.ayahSelectionColor = Color.parseColor("#47A67C52") // 28% warm gold in sepia mode
        }
      }
    }
    this.inlineAyahColor = ContextCompat.getColor(context, R.color.translation_translator_color)

    if (this.data.isNotEmpty()) {
      notifyDataSetChanged()
    }
  }

  private fun handleClick(view: View) {
    val hasSelection = highlightedAyah != 0 || highlightedAyahs.isNotEmpty()
    if (hasSelection) {
      onVerseSelectedListener.onDeselectAndToggleActionBar()
    } else {
      TactileFeedback.click(view)
      onClickListener.onClick(view)
    }
  }

  private fun selectVerseRows(view: View): Boolean {
    val position = recyclerView.getChildAdapterPosition(view)
    if (position != RecyclerView.NO_POSITION) {
      val row = data[position]
      if (row.type == TranslationViewRow.Type.QURAN_TEXT && row.data != null) {
        // Long-clicks on continuous blocks should not override targeted ClickableSpans
        return false
      }
      val ayahInfo = row.ayahInfo
      onVerseSelectedListener.onVerseLongPressed(ayahInfo)
      return true
    }
    return false
  }

  private fun toggleExpandTafseer(view: View) {
    val position = recyclerView.getChildAdapterPosition(view)
    if (position != RecyclerView.NO_POSITION) {
      val data = data[position]
      val what = data.ayahInfo.ayahId to data.translationIndex
      if (expandedTafseerAyahs.contains(what)) {
        expandedTafseerAyahs.remove(what)
      } else {
        expandedTafseerAyahs.add(what)
      }
      notifyItemChanged(position)
    }
  }

  private fun toggleTafseerJump(view: View) {
    val position = recyclerView.getChildAdapterPosition(view)
    if (position != RecyclerView.NO_POSITION) {
      val item = data[position]
      val targetAyah = item.link
      val targetPage = item.linkPage
      if (targetAyah != null && targetPage != null) {
        val match = data.indexOfFirst { it.ayahInfo.asSuraAyah() == targetAyah }
        if (match > -1) {
          recyclerView.smoothScrollToPosition(match)
        } else {
          // it's not on this page...
          onJumpToVerseListener.onJumpToAyah(targetAyah, targetPage)
        }
      }
    }
  }

  override fun getItemViewType(position: Int): Int {
    return data[position].type
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
    @LayoutRes val layout = when (viewType) {
      TranslationViewRow.Type.SURA_HEADER -> R.layout.quran_translation_header_row
      TranslationViewRow.Type.BASMALLAH, TranslationViewRow.Type.QURAN_TEXT ->
        R.layout.quran_translation_arabic_row
      TranslationViewRow.Type.SPACER -> R.layout.quran_translation_spacer_row
      TranslationViewRow.Type.FOOTER_ORNAMENT -> R.layout.quran_translation_footer_ornament
      TranslationViewRow.Type.VERSE_NUMBER -> R.layout.quran_translation_verse_number_row
      TranslationViewRow.Type.TRANSLATOR -> R.layout.quran_translation_translator_row
      else -> R.layout.quran_translation_text_row
    }

    val view = inflater.inflate(layout, parent, false)
    return RowViewHolder(view)
  }

  override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
    val row = data[position]
    when {
      // a row with text
      holder.text != null -> {
        // reset click listener on the text
        holder.text.setOnClickListener(defaultClickListener)
        if (row.type != TranslationViewRow.Type.QURAN_TEXT || row.data == null) {
          holder.text.setOnLongClickListener(defaultLongClickListener)
        }

        val text: CharSequence?
        if (row.type == TranslationViewRow.Type.SURA_HEADER) {
          text = row.data
          holder.text.background = null
          if (row.suraMetadata.isNullOrEmpty()) {
            holder.suraMetadata?.visibility = View.GONE
          } else {
            holder.suraMetadata?.text = row.suraMetadata
            holder.suraMetadata?.visibility = View.VISIBLE
          }
        } else if (row.type == TranslationViewRow.Type.BASMALLAH || row.type == TranslationViewRow.Type.QURAN_TEXT) {
          if (row.type == TranslationViewRow.Type.QURAN_TEXT && row.data != null) {
            bindContinuousQuranText(row, holder)
            text = null
          } else {
            val textStr = if (row.type == TranslationViewRow.Type.BASMALLAH) {
              ArabicDatabaseUtils.AR_BASMALLAH
            } else {
              ArabicDatabaseUtils.getAyahWithoutBasmallah(
                row.ayahInfo.sura, row.ayahInfo.ayah, row.ayahInfo.arabicText!!
              )
            }

            if (row.type == TranslationViewRow.Type.BASMALLAH) {
              val str = SpannableString(textStr)
              str.setSpan(UthmaniSpan(context), 0, str.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
              text = str
              val density = context.resources.displayMetrics.density
              holder.text.setPadding((20 * density).toInt(), (14 * density).toInt(), (20 * density).toInt(), (10 * density).toInt())
              holder.text.textAlignment = View.TEXT_ALIGNMENT_GRAVITY
              holder.text.gravity = android.view.Gravity.CENTER
            } else {
              val builder = SpannableStringBuilder()
              val arabicFont = QuranSettings.getInstance(context).arabicFont
              val needsHafsNumberSpan = arabicFont != QuranSettings.FONT_HAFS_BOLD && arabicFont != QuranSettings.FONT_HAFS_CLASSIC
              val ayahNum = toArabicDigits(row.ayahInfo.ayah)
              builder.append(textStr.trim())
              builder.append("\u00A0")
              val numStart = builder.length
              builder.append(ayahNum)
              builder.setSpan(UthmaniSpan(context), 0, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
              if (needsHafsNumberSpan) {
                builder.setSpan(HafsAyahNumberSpan(context), numStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
              }
              text = builder
              val density = context.resources.displayMetrics.density
              holder.text.setPadding((20 * density).toInt(), (4 * density).toInt(), (20 * density).toInt(), (0 * density).toInt())
              holder.text.layoutDirection = View.LAYOUT_DIRECTION_RTL
              holder.text.textDirection = View.TEXT_DIRECTION_RTL
              holder.text.textAlignment = View.TEXT_ALIGNMENT_GRAVITY
              holder.text.gravity = android.view.Gravity.RIGHT or android.view.Gravity.CENTER_VERTICAL
            }
            holder.text.movementMethod = null
          }

          holder.text.setTextColor(arabicTextColor)
          holder.text.textSize = ARABIC_MULTIPLIER * ayahFontSize
        } else {
          if (row.type == TranslationViewRow.Type.TRANSLATOR) {
            text = row.data
          } else {
            // translation
            text = row.data?.let { rowText ->
              val length = rowText.length
              val expandHyperlink =
                expandedHyperlinks.contains(row.ayahInfo.ayahId to row.translationIndex)

              if (row.link != null && !expandHyperlink) {
                holder.text.setOnClickListener(expandHyperlinkClickListener)
              }

              val originalBuilder = SpannableStringBuilder(row.data)
              row.ayat.forEach { range ->
                val span = ForegroundColorSpan(inlineAyahColor)
                originalBuilder.setSpan(span, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
              }

              val expandedFootnotes = expandedFootnotes[row.ayahInfo] ?: emptyList()
              val translationContent = row.footnoteCognizantText(
                originalBuilder,
                expandedFootnotes,
                ::collapsedFootnoteSpan,
                ::expandedFootnote
              )

              val settings = QuranSettings.getInstance(context)
              val spannableBuilder = SpannableStringBuilder()
              
              if (!settings.wantArabicInTranslationView() && row.translationIndex == 0) {
                val numberPlaceholder = " "
                spannableBuilder.append(numberPlaceholder)
                val isMadani1439 = settings.pageType?.contains("1439") == true
                spannableBuilder.setSpan(
                  AyahMarkerSpan(toArabicDigits(row.ayahInfo.ayah), row.ayahInfo.ayahId, isMadani1439),
                  0,
                  1,
                  Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannableBuilder.append("  ")
              }

              if (row.translationIndex == 0 && bookmarkedAyahIds.contains(row.ayahInfo.ayahId)) {
                val bookmarkDrawable = ContextCompat.getDrawable(context, R.drawable.ic_bookmark_filled)?.mutate()
                if (bookmarkDrawable != null) {
                  val iconSize = (translationFontSize * context.resources.displayMetrics.scaledDensity * 0.95f).toInt()
                  bookmarkDrawable.setBounds(0, 0, iconSize, iconSize)
                  val tintColor = ContextCompat.getColor(context, R.color.translation_bookmark_tint)
                  bookmarkDrawable.setTint(tintColor)
                  val start = spannableBuilder.length
                  spannableBuilder.append(" ")
                  spannableBuilder.setSpan(
                    android.text.style.ImageSpan(bookmarkDrawable, android.text.style.ImageSpan.ALIGN_BASELINE),
                    start,
                    start + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                  )
                  spannableBuilder.append(" ")
                }
              }

              spannableBuilder.append(translationContent)
              val spannable = SpannableString(spannableBuilder)

              when {
                row.link != null && !expandHyperlink -> getAyahLink(row.link)
                length > MAX_TAFSEER_LENGTH ->
                  truncateTextIfNeeded(spannable, row.ayahInfo.ayahId, row.translationIndex)
                else -> spannable
              }
            }

            // determine text directionality
            val isRtl = when {
              row.isArabic -> true
              text != null -> SearchTextUtil.isRtl(text.toString())
              else -> false
            }

            // reset the typeface
            holder.text.typeface = null

            val settings = QuranSettings.getInstance(context)

            if (isRtl) {
              // rtl tafseer, style it
              holder.text.layoutDirection = View.LAYOUT_DIRECTION_RTL

              // only allow this for Arabic though since the Arabic font isn't compatible
              // with other RTL languages that share some Arabic characters.
              if (row.isArabic) {
                holder.text.typeface = TypefaceManager.getTafseerTypeface(context)
              }
            } else {
              holder.text.layoutDirection = View.LAYOUT_DIRECTION_INHERIT
              holder.text.typeface = TypefaceManager.getTranslationTypeface(context)
            }

            holder.text.movementMethod = LinkMovementMethod.getInstance()
            holder.text.setTextColor(textColor)
            holder.text.textSize = translationFontSize.toFloat()
            holder.text.setLineSpacing(0f, settings.translationLineSpacing)
          }
        }
        if (text != null) {
          holder.text.text = text
        }
      }
      // a divider row
      holder.divider != null -> {
        var showLine = true
        if (position + 1 < data.size) {
          val nextRow = data[position + 1]
          if (nextRow.ayahInfo.sura != row.ayahInfo.sura) {
            showLine = false
          }
        } else {
          showLine = false
        }
        holder.divider.toggleLine(showLine)
        holder.divider.setDividerColor(dividerColor)
      }
      // ayah number row
      holder.ayahNumber != null -> {
        val text = context.getString(R.string.sura_ayah, row.ayahInfo.sura, row.ayahInfo.ayah)
        holder.ayahNumber.setAyahString(text)
        holder.ayahNumber.setTextColor(textColor)
        holder.ayahNumber.setNightMode(isNightMode)
      }
    }
    updateHighlight(row, holder)
  }

  private fun collapsedFootnoteSpan(number: Int): SpannableString {
    val text = QuranUtils.getLocalizedNumber(context, number)
    val spannable = SpannableString(text)
    spannable.setSpan(SuperscriptSpan(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    spannable.setSpan(RelativeSizeSpan(0.7f), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    spannable.setSpan(ExpandFootnoteSpan(number, ::expandFootnote), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    spannable.setSpan(ForegroundColorSpan(inlineAyahColor), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return spannable
  }

  private fun expandedFootnote(
    spannableStringBuilder: SpannableStringBuilder,
    start: Int,
    end: Int
  ): SpannableStringBuilder {
    val span = RelativeSizeSpan(0.7f)
    val colorSpan = ForegroundColorSpan(footnoteColor)
    spannableStringBuilder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    spannableStringBuilder.setSpan(colorSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return spannableStringBuilder
  }

  private fun expandFootnote(view: View, number: Int) {
    val position = recyclerView.getChildAdapterPosition(view)
    if (position != RecyclerView.NO_POSITION) {
      val data = data[position]
      val expanded = expandedFootnotes[data.ayahInfo] ?: listOf()
      expandedFootnotes[data.ayahInfo] = expanded + number
      notifyItemChanged(position)
    }
  }

  private fun getAyahLink(link: SuraAyah): CharSequence {
    return context.getString(R.string.see_tafseer_of_verse, link.ayah)
  }

  private fun truncateTextIfNeeded(
    text: CharSequence,
    ayahId: Int,
    translationIndex: Int
  ): CharSequence {
    if (text.length > MAX_TAFSEER_LENGTH &&
      !expandedTafseerAyahs.contains(ayahId to translationIndex)
    ) {
      // let's truncate
      val lastSpace = text.indexOf(' ', MAX_TAFSEER_LENGTH)
      if (lastSpace != -1) {
        return SpannableStringBuilder(text.subSequence(0, lastSpace + 1)).apply {
          append(context.getString(R.string.more_arabic))
          setSpan(
            ExpandTafseerSpan(expandClickListener),
            lastSpace + 1,
            this.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
          )
        }
      }
    }
    return text
  }

  override fun onBindViewHolder(holder: RowViewHolder, position: Int, payloads: List<Any>) {
    if (payloads.contains(HIGHLIGHT_CHANGE)) {
      updateHighlight(data[position], holder)
    } else {
      super.onBindViewHolder(holder, position, payloads)
    }
  }

  private fun findAyahAtOffset(textView: TextView, row: TranslationViewRow, touchX: Float, touchY: Float): QuranAyahInfo? {
    val layout = textView.layout ?: return null
    val spanned = (textView.text as? Spanned) ?: (row.data as? Spanned) ?: return null

    val adjX = touchX - textView.totalPaddingLeft
    val adjY = touchY - textView.totalPaddingTop

    val clampedY = adjY.coerceIn(0f, (layout.height - 1).coerceAtLeast(0).toFloat())
    val line = layout.getLineForVertical(clampedY.toInt())
    val offset = layout.getOffsetForHorizontal(line, adjX)

    val spans = spanned.getSpans(0, spanned.length, TranslationView.AyahIdSpan::class.java)
    for (span in spans) {
      val start = spanned.getSpanStart(span)
      val end = spanned.getSpanEnd(span)
      if (offset in start..end) {
        return QuranAyahInfo(span.sura, span.ayah, null, emptyList(), span.ayahId)
      }
    }

    if (offset > 0) {
      for (span in spans) {
        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)
        if ((offset - 1) in start..end) {
          return QuranAyahInfo(span.sura, span.ayah, null, emptyList(), span.ayahId)
        }
      }
    }

    if (spans.isNotEmpty()) {
      val closest = spans.minByOrNull { span ->
        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)
        when {
          offset < start -> start - offset
          offset > end -> offset - end
          else -> 0
        }
      }
      if (closest != null) {
        return QuranAyahInfo(closest.sura, closest.ayah, null, emptyList(), closest.ayahId)
      }
    }

    return null
  }

  private fun highlightContinuousRange(holder: RowViewHolder, startAyahId: Int, currentAyahId: Int) {
    val spannable = holder.text?.text as? Spannable ?: return
    val row = holder.activeRow ?: return
    val spanned = (holder.text?.text as? Spanned) ?: (row.data as? Spanned) ?: return
    val spans = spanned.getSpans(0, spanned.length, TranslationView.AyahIdSpan::class.java)

    val minId = minOf(startAyahId, currentAyahId)
    val maxId = maxOf(startAyahId, currentAyahId)

    // Clear all existing BackgroundColorSpans
    val existing = spannable.getSpans(0, spannable.length, BackgroundColorSpan::class.java)
    for (s in existing) {
      spannable.removeSpan(s)
    }

    // Apply BackgroundColorSpan for all ayahs in [minId, maxId]
    for (span in spans) {
      if (span.ayahId in minId..maxId) {
        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)
        spannable.setSpan(
          BackgroundColorSpan(ayahSelectionColor),
          start,
          end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
      }
    }
  }

  private fun findAyahInfoForId(row: TranslationViewRow, ayahId: Int): QuranAyahInfo? {
    val spanned = row.data as? Spanned ?: return null
    val spans = spanned.getSpans(0, spanned.length, TranslationView.AyahIdSpan::class.java)
    val span = spans.firstOrNull { it.ayahId == ayahId } ?: return null
    return QuranAyahInfo(span.sura, span.ayah, null, emptyList(), span.ayahId)
  }

  fun findAyahInfo(ayahId: Int): QuranAyahInfo? {
    if (ayahId <= 0) return null
    for (row in data) {
      if (row.type == TranslationViewRow.Type.QURAN_TEXT && row.data is Spanned) {
        val ayahInfo = findAyahInfoForId(row, ayahId)
        if (ayahInfo != null) return ayahInfo
      } else if (row.ayahInfo.ayahId == ayahId) {
        return row.ayahInfo
      }
    }
    return null
  }

  private fun disallowParentIntercept(view: View, disallow: Boolean) {
    var parent = view.parent
    while (parent != null) {
      parent.requestDisallowInterceptTouchEvent(disallow)
      parent = parent.parent
    }
  }

  private fun updateContinuousSpans(row: TranslationViewRow, holder: RowViewHolder) {
    if (holder.text == null || row.data !is Spanned) return
    val builder = SpannableStringBuilder(row.data)
    builder.setSpan(UthmaniSpan(context), 0, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    val arabicFont = QuranSettings.getInstance(context).arabicFont
    if (arabicFont != QuranSettings.FONT_HAFS_BOLD && arabicFont != QuranSettings.FONT_HAFS_CLASSIC) {
      val matches = Regex("\u00A0([٠-٩]+)").findAll(builder.toString())
      for (match in matches) {
        val group = match.groups[1] ?: continue
        builder.setSpan(
          HafsAyahNumberSpan(context),
          group.range.first,
          group.range.last + 1,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
      }
    }

    val spans = builder.getSpans(0, builder.length, TranslationView.AyahIdSpan::class.java)

    for (span in spans) {
      val start = builder.getSpanStart(span)
      val end = builder.getSpanEnd(span)

      if (isAyahHighlighted(span.ayahId)) {
        builder.setSpan(
          android.text.style.BackgroundColorSpan(ayahSelectionColor),
          start,
          end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
      }
    }
    holder.text.setText(builder, TextView.BufferType.SPANNABLE)
  }

  private fun bindContinuousQuranText(row: TranslationViewRow, holder: RowViewHolder) {
    if (holder.text == null) return
    holder.activeRow = row

    updateContinuousSpans(row, holder)

    val density = context.resources.displayMetrics.density
    holder.text.setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
    holder.text.movementMethod = null
    holder.text.isFocusable = false
    holder.text.isFocusableInTouchMode = false
    holder.text.setLineSpacing(0f, 1.0f)

    if (holder.longPressRunnable == null) {
      val longPressRunnable = Runnable {
        val active = holder.activeRow ?: return@Runnable
        val ayah = findAyahAtOffset(holder.text, active, holder.touchDownX, holder.touchDownY)
        if (ayah != null) {
          holder.isDragging = true
          val existingRange = getSelectionMinMaxIds()
          val baseMin = existingRange?.first ?: ayah.ayahId
          val baseMax = existingRange?.second ?: ayah.ayahId
          holder.dragBaseMinId = baseMin
          holder.dragBaseMaxId = baseMax
          holder.dragStartAyah = ayah
          holder.dragLastAyah = ayah
          disallowParentIntercept(holder.text, true)
          TactileFeedback.longPress(holder.text)
          if (existingRange == null) {
            onVerseSelectedListener.onVerseDragStarted(ayah)
          }
          highlightContinuousRange(holder, minOf(baseMin, ayah.ayahId), maxOf(baseMax, ayah.ayahId))
        }
      }
      holder.longPressRunnable = longPressRunnable

      val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
      val longPressTimeout = android.view.ViewConfiguration.getLongPressTimeout().toLong()

      holder.text.setOnTouchListener { _, event ->
        val runnable = holder.longPressRunnable ?: return@setOnTouchListener false
        when (event.actionMasked) {
          MotionEvent.ACTION_DOWN -> {
            holder.touchDownX = event.x
            holder.touchDownY = event.y
            holder.isDragging = false
            holder.dragStartAyah = null
            holder.dragLastAyah = null
            holder.text.removeCallbacks(runnable)
            holder.text.postDelayed(runnable, longPressTimeout)
            true
          }
          MotionEvent.ACTION_MOVE -> {
            if (holder.isDragging) {
              disallowParentIntercept(holder.text, true)
              val active = holder.activeRow
              if (active != null) {
                val currentAyah = findAyahAtOffset(holder.text, active, event.x, event.y)
                if (currentAyah != null && currentAyah.ayahId != holder.dragLastAyah?.ayahId) {
                  holder.dragLastAyah = currentAyah
                  TactileFeedback.dragTick(holder.text)
                  val startId = holder.dragStartAyah?.ayahId ?: currentAyah.ayahId
                  val minId = minOf(holder.dragBaseMinId, minOf(startId, currentAyah.ayahId))
                  val maxId = maxOf(holder.dragBaseMaxId, maxOf(startId, currentAyah.ayahId))
                  highlightContinuousRange(holder, minId, maxId)
                }
              }
              true
            } else {
              val dx = Math.abs(event.x - holder.touchDownX)
              val dy = Math.abs(event.y - holder.touchDownY)
              if (dx > touchSlop || dy > touchSlop) {
                holder.text.removeCallbacks(runnable)
                disallowParentIntercept(holder.text, false)
              }
              false
            }
          }
          MotionEvent.ACTION_UP -> {
            holder.text.removeCallbacks(runnable)
            if (holder.isDragging) {
              holder.isDragging = false
              disallowParentIntercept(holder.text, false)
              val start = holder.dragStartAyah
              val last = holder.dragLastAyah
              holder.dragStartAyah = null
              holder.dragLastAyah = null

              if (start != null && last != null) {
                TactileFeedback.selectionEnd(holder.text)
                val minId = minOf(holder.dragBaseMinId, minOf(start.ayahId, last.ayahId))
                val maxId = maxOf(holder.dragBaseMaxId, maxOf(start.ayahId, last.ayahId))
                val active = holder.activeRow
                val startInfo = (if (active != null) findAyahInfoForId(active, minId) else null)
                  ?: findAyahInfo(minId) ?: start
                val endInfo = (if (active != null) findAyahInfoForId(active, maxId) else null)
                  ?: findAyahInfo(maxId) ?: last
                if (minId == maxId) {
                  onVerseSelectedListener.onVerseSelected(startInfo)
                } else {
                  onVerseSelectedListener.onVerseRangeSelected(startInfo, endInfo)
                }
              }
              true
            } else {
              disallowParentIntercept(holder.text, false)
              val hasSelection = highlightedAyah != 0 || highlightedAyahs.isNotEmpty()
              if (hasSelection) {
                onVerseSelectedListener.onDeselectAndToggleActionBar()
              } else {
                TactileFeedback.click(holder.text)
                defaultClickListener.onClick(holder.text)
              }
              true
            }
          }
          MotionEvent.ACTION_CANCEL -> {
            holder.text.removeCallbacks(runnable)
            if (holder.isDragging) {
              holder.isDragging = false
              holder.dragStartAyah = null
              holder.dragLastAyah = null
              disallowParentIntercept(holder.text, false)
              holder.activeRow?.let { active ->
                updateContinuousSpans(active, holder)
              }
            }
            false
          }
          else -> false
        }
      }
    }

    val isFrontispiece = row.ayahInfo.sura == 1 || (row.ayahInfo.sura == 2 && row.ayahInfo.ayah <= 5)

    holder.text.setTextColor(arabicTextColor)
    holder.text.textSize = ARABIC_MULTIPLIER * ayahFontSize
    holder.text.layoutDirection = View.LAYOUT_DIRECTION_RTL
    holder.text.textDirection = View.TEXT_DIRECTION_RTL

    if (isFrontispiece) {
      holder.text.textAlignment = View.TEXT_ALIGNMENT_CENTER
      holder.text.gravity = android.view.Gravity.CENTER
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        holder.text.justificationMode = android.graphics.text.LineBreaker.JUSTIFICATION_MODE_NONE
      }
    } else {
      holder.text.textAlignment = View.TEXT_ALIGNMENT_GRAVITY
      holder.text.gravity = android.view.Gravity.RIGHT or android.view.Gravity.TOP
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        holder.text.justificationMode = android.graphics.text.LineBreaker.JUSTIFICATION_MODE_INTER_WORD
      }
    }
  }

  private fun updateHighlight(row: TranslationViewRow, holder: RowViewHolder) {
    if (row.type == TranslationViewRow.Type.QURAN_TEXT && row.data != null) {
      holder.wrapperView.setBackgroundColor(0)
      holder.activeRow = row
      updateContinuousSpans(row, holder)
      return
    }
    // toggle highlighting of the ayah, but not for sura headers and basmallah
    val isHighlighted = isAyahHighlighted(row.ayahInfo.ayahId)
    if (row.type != TranslationViewRow.Type.SURA_HEADER &&
      row.type != TranslationViewRow.Type.BASMALLAH &&
      row.type != TranslationViewRow.Type.SPACER &&
      row.type != TranslationViewRow.Type.FOOTER_ORNAMENT
    ) {
      holder.wrapperView.setBackgroundColor(
        if (isHighlighted) ayahSelectionColor else 0
      )
    } else if (holder.divider != null) { // SPACER type
      if (isHighlighted) {
        holder.divider.highlight(ayahSelectionColor)
      } else {
        holder.divider.unhighlight()
      }
    }
  }

  fun getItemAt(position: Int): TranslationViewRow? = data.getOrNull(position)

  override fun getItemCount(): Int = data.size

  internal inner class RowViewHolder(val wrapperView: View) : RecyclerView.ViewHolder(wrapperView) {
    val text: TextView? = wrapperView.findViewById(R.id.text)
    val suraMetadata: TextView? = wrapperView.findViewById(R.id.sura_metadata)
    val divider: DividerView? = wrapperView.findViewById(R.id.divider)
    val ayahNumber: AyahNumberView? = wrapperView.findViewById(R.id.ayah_number)

    var isDragging = false
    var dragBaseMinId = 0
    var dragBaseMaxId = 0
    var dragStartAyah: QuranAyahInfo? = null
    var dragLastAyah: QuranAyahInfo? = null
    var activeRow: TranslationViewRow? = null
    var touchDownX = 0f
    var touchDownY = 0f
    var longPressRunnable: Runnable? = null

    init {
      wrapperView.setOnClickListener(defaultClickListener)
      wrapperView.setOnLongClickListener(defaultLongClickListener)
      text?.setOnLongClickListener(defaultLongClickListener)
    }
  }

  internal interface OnVerseSelectedListener {
    fun onVerseSelected(ayahInfo: QuranAyahInfo)
    fun onVerseLongPressed(ayahInfo: QuranAyahInfo)
    fun onVerseDragStarted(ayahInfo: QuranAyahInfo)
    fun onVerseRangeSelected(start: QuranAyahInfo, end: QuranAyahInfo)
    fun onDeselectAndToggleActionBar()
  }

  internal interface OnJumpToAyahListener {
    fun onJumpToAyah(target: SuraAyah, page: Int)
  }

  private val arabicNumberFormat = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("ar-u-nu-arab"))

  private fun toArabicDigits(number: Int): String {
    return arabicNumberFormat.format(number)
  }

  inner class AyahMarkerSpan(
    private val text: String,
    private val ayahId: Int,
    private val isMadani1439: Boolean
  ) : android.text.style.ReplacementSpan() {

    override fun getSize(
      paint: android.graphics.Paint,
      text: CharSequence?,
      start: Int,
      end: Int,
      fm: android.graphics.Paint.FontMetricsInt?
    ): Int {
      val diameter = (paint.textSize * 0.7f).toInt()
      return (diameter + paint.textSize * 0.25f).toInt()
    }

    override fun draw(
      canvas: android.graphics.Canvas,
      text: CharSequence?,
      start: Int,
      end: Int,
      x: Float,
      top: Int,
      y: Int,
      bottom: Int,
      paint: android.graphics.Paint
    ) {
      val originalColor = paint.color
      val originalTypeface = paint.typeface

      val ringColor: Int
      val innerColor: Int
      val textColor: Int

      if (isMadani1439) {
        if (isNightMode) {
          ringColor = android.graphics.Color.rgb(0x73, 0xaf, 0xfa)
          innerColor = android.graphics.Color.rgb(0x17, 0x25, 0x54)
          textColor = android.graphics.Color.rgb(0x73, 0xaf, 0xfa)
        } else {
          ringColor = android.graphics.Color.rgb(0x1d, 0x4e, 0xd8)
          innerColor = android.graphics.Color.rgb(0xef, 0xf6, 0xff)
          textColor = android.graphics.Color.rgb(0x1e, 0x3a, 0x8a)
        }
      } else {
        if (isNightMode) {
          ringColor = android.graphics.Color.rgb(0x04, 0x78, 0x57)
          innerColor = android.graphics.Color.rgb(0x02, 0x2c, 0x22)
          textColor = android.graphics.Color.rgb(0x34, 0xd3, 0x99)
        } else {
          ringColor = android.graphics.Color.rgb(0x04, 0x78, 0x57)
          innerColor = android.graphics.Color.rgb(0xec, 0xfd, 0xf5)
          textColor = android.graphics.Color.rgb(0x04, 0x78, 0x57)
        }
      }

      val textSize = paint.textSize
      val diameter = textSize * 0.7f
      val radius = diameter / 2f

      val originalStyle = paint.style

      // Draw the selection background color if this verse is selected/highlighted!
      if (isAyahHighlighted(ayahId)) {
        paint.color = ayahSelectionColor
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRect(
          x,
          top.toFloat(),
          x + diameter + textSize * 0.25f,
          bottom.toFloat(),
          paint
        )
      }

      paint.typeface = android.graphics.Typeface.DEFAULT_BOLD

      val xCenter = x + (textSize * 0.125f) + radius
      val yCenter = y - (textSize * 0.25f)

      // 1. Draw inner circle
      paint.color = innerColor
      paint.style = android.graphics.Paint.Style.FILL
      canvas.drawCircle(xCenter, yCenter, radius, paint)

      // 2. Draw ring/border
      paint.color = ringColor
      paint.style = android.graphics.Paint.Style.STROKE
      paint.strokeWidth = diameter * 0.06f
      canvas.drawCircle(xCenter, yCenter, radius, paint)

      // 3. Draw text in the middle
      paint.color = textColor
      paint.style = android.graphics.Paint.Style.FILL
      paint.strokeWidth = 0f
      paint.textAlign = android.graphics.Paint.Align.CENTER

      // Adjust text size dynamically using exact ratios from AyahMarkerDrawer.kt
      paint.textSize = diameter * if (this.text.length > 2) 0.60f else 0.75f

      // Center text vertically using exact offset formula from AyahMarkerDrawer.kt
      val yOffset = (paint.descent() - paint.ascent()) / 2.0f - paint.descent()
      val yText = yCenter + yOffset

      canvas.drawText(this.text, xCenter, yText, paint)

      // Restore paint state
      paint.color = originalColor
      paint.typeface = originalTypeface
      paint.textSize = textSize
      paint.style = originalStyle
    }
  }

  companion object {
    const val ARABIC_MULTIPLIER = 1.4f
    private const val MAX_TAFSEER_LENGTH = 750
    private const val HIGHLIGHT_CHANGE = 1
  }
}
