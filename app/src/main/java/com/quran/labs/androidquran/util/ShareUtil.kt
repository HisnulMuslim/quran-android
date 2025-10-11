package com.quran.labs.androidquran.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.add
import com.quran.data.model.QuranText
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.presenter.translation.BaseTranslationPresenter
import com.quran.labs.androidquran.ui.util.ToastCompat
import com.quran.mobile.translation.model.LocalTranslation
import dagger.Reusable
import timber.log.Timber
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

@Reusable
class ShareUtil @Inject internal constructor(private val quranDisplayData: QuranDisplayData) {

  /*KQACR7 start*/
  fun copyVersesKQA(activity: Activity,  result: BaseTranslationPresenter.ResultHolder,verseRange: VerseRange,activeTranslations: List<String>) {
    val text = getShareTextKQA(activity, result,verseRange,activeTranslations)
    copyToClipboard(activity, text)
  }
  private fun getShareTextKQA(activity: Activity, result: BaseTranslationPresenter.ResultHolder,verseRange: VerseRange,activeTranslations: List<String>): String {
    val quranSettings = QuranSettings.getInstance(activity)

    // Process the result, e.g., update UI or share text
    val  quranAyahInfosList = result.ayahInformation
    val translationsList = result.translations

    //val size = verseRange.versesInRange
    val wantInlineAyahNumbers = quranSettings.isIncludeInlineAyahNumber
    //val isEnglish = quranSettings.interfaceLanguage().lowercase() == "en"
    val locale =Locale("ar")
    val numberFormat = NumberFormat.getNumberInstance(locale)
    val footnoteRegex = "\\[\\[(.+?)]]".toRegex()
    val footnotes = mutableListOf<String>()
    val groupTranslations = mutableListOf<String>()

    return buildString {
      append("\n\n")
      quranAyahInfosList.forEachIndexed { i, ayahInfo ->

        if (wantInlineAyahNumbers && quranSettings.isIncludeTranslation && quranSettings.isInlineTranslation) {
          append("${numberFormat.format(ayahInfo.ayah)}. ")
        }

        /*if (ayahInfo.ayah == 1 && ayahInfo.sura != 1 && ayahInfo.sura != 9){
          append(ArabicDatabaseUtils.AR_BASMALLAH_IN_TEXT)
          append("\n")
          append(ArabicDatabaseUtils.AR_BASMALLAH_IN_TEXT_KN)
          append("\n\n")
        }*/

        ayahInfo.arabicText?.let {
          append(
            ArabicDatabaseUtils.getAyahWithoutBasmallah(
              ayahInfo.sura,
              ayahInfo.ayah,
              ayahInfo.arabicText.trim()
            )
          )
        }
        if (quranSettings.isIncludeTranslation && quranSettings.isInlineTranslation) {
          append("\n")
        } else {
          append(" ۩ ")
        }
        if (quranSettings.isIncludeTranslation) {
          ayahInfo.texts.forEachIndexed { i, translation ->
            val text = translation.text
            if (text.isNotEmpty()) {
              if (ayahInfo.texts.size > 1) {
                if(quranSettings.isInlineTranslation) {
                  append("\n")
                  append(translationsList[i].resolveTranslatorName())
                  append(":\n")
                }else{
                  groupTranslations.add("\n")
                  groupTranslations.add(translationsList[i].resolveTranslatorName())
                  groupTranslations.add(":\n")
                }
              }

              // Replace [[...]] with placeholders and collect footnotes
              val processedText = if (quranSettings.isIncludeNotes) {
                footnoteRegex.replace(text) { match ->
                  val note = match.groupValues[1].trim()
                  footnotes.add(note)
                  "[${footnotes.size}]" // Customize placeholder style here
                }
              } else {
                // Remove footnote brackets and content entirely
                footnoteRegex.replace(text, "")
              }
              if(quranSettings.isInlineTranslation) {
                append(processedText)
                append("\n\n")
              }else{
                groupTranslations.add("\n\n")
                groupTranslations.add(processedText)

              }
            }
          }
        }
      }
      if(quranSettings.isInlineTranslation) {
        append("\n")
      }else{
        groupTranslations.forEach { append(it) }
        append("\n")
      }
      footnotes.forEachIndexed { index, note ->
        if(index == 0){
          append("ಟಿಪ್ಪಣಿಗಳು : \n")
        }
        append("${index + 1}. $note")
        append("\n")
      }

      append("\n[")
      append(quranDisplayData.getSuraName(activity, verseRange.startSura, true))
      append(": ")

      append(verseRange.startAyah)
      if (verseRange.versesInRange > 1) {
        if (verseRange.startSura != verseRange.endingSura) {
          append(" - ")
          append(quranDisplayData.getSuraName(activity, verseRange.endingSura, true))
          append(": ")
        } else {
          append("-")
        }
        append(verseRange.endingAyah)
      }
      append("]\n\n")
      append(activity.getString(R.string.download_text)+"\nhttps://download.pavitra-quraan.com/")
      }
    }



  fun shareVersesKQA(activity: Activity, result: BaseTranslationPresenter.ResultHolder,verseRange: VerseRange,activeTranslations: List<String>) {
    val text = getShareTextKQA(activity, result, verseRange,activeTranslations )
    shareViaIntent(activity, text, com.quran.labs.androidquran.common.toolbar.R.string.share_ayah_text)
  }
  /*KQACR7 end*/

  fun copyVerses(activity: Activity, verses: List<QuranText>) {
    val text = getShareText(activity, verses)
    copyToClipboard(activity, text)
  }

  fun copyToClipboard(activity: Activity, text: String?) {
    val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(activity.getString(R.string.app_name), text)
    cm.setPrimaryClip(clip)
    ToastCompat.makeText(
      activity, activity.getString(R.string.ayah_copied_popup),
      Toast.LENGTH_SHORT
    ).show()
  }

  fun shareVerses(activity: Activity, verses: List<QuranText>) {
    val text = getShareText(activity, verses)
    shareViaIntent(activity, text, com.quran.labs.androidquran.common.toolbar.R.string.share_ayah_text)
  }

  fun shareViaIntent(activity: Activity, text: String?, @StringRes titleResId: Int) {
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
    }
    activity.startActivity(Intent.createChooser(intent, activity.getString(titleResId)))
    /*KQACR7 start*/
    text?.toByteArray()?.size?.let {
          if(it >=4000) {
              Toast.makeText(
                  activity,
                  "For whatsapp, Use the copy option due to size limitation",/*TODO language translation*/
                  Toast.LENGTH_LONG
              ).show()
          }
      }
    /*KQACR7 end*/
  }

  fun getShareText(
    context: Context,
    ayahInfo: QuranAyahInfo,
    translationNames: Array<LocalTranslation>
  ): String {
    return buildString {
      ayahInfo.arabicText?.let {
        //append("{ ")
        append(ArabicDatabaseUtils.getAyahWithoutBasmallah(ayahInfo.sura, ayahInfo.ayah, ayahInfo.arabicText.trim()))
        //append(" }")
        append("\n")
        append("[")
        append(quranDisplayData.getSuraAyahString(context, ayahInfo.sura, ayahInfo.ayah, R.string.sura_ayah_sharing_str))
        append("]")
      }

      ayahInfo.texts.forEachIndexed { i, translation ->
        val text = translation.text
        if (text.isNotEmpty()) {
          append("\n\n")
          if (i < translationNames.size) {
            append(translationNames[i].resolveTranslatorName())
            append(":\n")
          }

          // remove footnotes for now
          val spannableStringBuilder = SpannableStringBuilder(text)
          translation.footnoteCognizantText(
            spannableStringBuilder,
            listOf(),
            { _ -> SpannableString("") },
            { builder, _, _ -> builder }
          )
          append(spannableStringBuilder)
        }
      }
      if (ayahInfo.arabicText == null) {
        append("\n")
        append("-")
        append(quranDisplayData.getSuraAyahString(context, ayahInfo.sura, ayahInfo.ayah, R.string.sura_ayah_notification_str))
      }
    }
  }

  private fun getShareText(activity: Activity, verses: List<QuranText>): String {
    val size = verses.size
    val wantInlineAyahNumbers = size > 1
    val isArabicNames = QuranSettings.getInstance(activity).isArabicNames
    val locale = if (isArabicNames) Locale("ar") else Locale.getDefault()
    val numberFormat = NumberFormat.getNumberInstance(locale)
    return buildString {
      append("{ ")
      for (i in 0 until size) {
        if (i > 0) {
          append(" ")
        }

        append(ArabicDatabaseUtils.getAyahWithoutBasmallah(verses[i].sura, verses[i].ayah, verses[i].text.trim()))
        if (wantInlineAyahNumbers) {
          append(" (")
          append(numberFormat.format(verses[i].ayah))
          append(")")
        }
      }

      // append } and a new line after last ayah
      append(" }\n")
      // append [ before sura label
      append("[")
      val (sura, ayah) = verses[0]
      append(quranDisplayData.getSuraName(activity, sura, true))
      append(": ")
      append(numberFormat.format(ayah))
      if (size > 1) {
        val (sura1, ayah1) = verses[size - 1]
        if (sura != sura1) {
          append(" - ")
          append(quranDisplayData.getSuraName(activity, sura1, true))
          append(": ")
        } else {
          append("-")
        }
        append(numberFormat.format(ayah1))
      }
      // close sura label and append two new lines
      append("]")
    }
  }
}
