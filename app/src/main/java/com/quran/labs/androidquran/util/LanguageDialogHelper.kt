package com.quran.labs.androidquran.util

import android.preference.PreferenceManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.ui.QuranActivity

object LanguageDialogHelper {
  fun show(activity: AppCompatActivity, settings: QuranSettings) {
    val languages = activity.resources.getStringArray(R.array.preferred_language)
    val languageValues = activity.resources.getStringArray(R.array.preferred_language_values)
    val currentLanguage = settings.interfaceLanguage()
    val currentIndex = languageValues.indexOf(currentLanguage)

    AlertDialog.Builder(activity)
      .setTitle(R.string.prefs_language_title)
      .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
        val selectedLanguage = languageValues[which]
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit()
          .putString(Constants.PREF_INTERFACE_LANGUAGE, selectedLanguage)
          .apply()
        dialog.dismiss()
        // Set flag to recreate QuranActivity when it resumes
        QuranActivity.shouldRecreate = true
        // Restart app to apply language change
        (activity.application as QuranApplication).refreshLocale(activity, true)
        activity.recreate()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
