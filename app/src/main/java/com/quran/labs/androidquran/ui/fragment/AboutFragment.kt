package com.quran.labs.androidquran.ui.fragment

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.util.openSupportEmail

class AboutFragment : PreferenceFragmentCompat() {

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    addPreferencesFromResource(R.xml.about)

    val flavor = BuildConfig.FLAVOR + "Images"
    val parent = findPreference("aboutDataSources") as PreferenceCategory?
    imagePrefKeys.filter { it != flavor }.map {
      val pref: Preference? = findPreference(it)
      if (pref != null) {
        parent?.removePreference(pref)
      }
    }
      // Email click -> add subject + device/app info automatically
      findPreference<Preference>("about_us_email")?.setOnPreferenceClickListener {
          openSupportEmail()
          true
      }
  }

  companion object {
    private val imagePrefKeys = arrayOf("madaniImages", "naskhImages", "qaloonImages")
  }
}
