package com.quran.labs.androidquran.ui.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.R
import java.util.Locale

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

    private fun openSupportEmail() {
        val ctx = requireContext()
        val pkg = ctx.packageName
        val pm = ctx.packageManager
        val pi = pm.getPackageInfo(pkg, 0)

        val versionName = pi.versionName ?: BuildConfig.VERSION_NAME
        val versionCode = PackageInfoCompat.getLongVersionCode(pi)


        val device = "${Build.MANUFACTURER} ${Build.MODEL}"
        val androidInfo = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        val locale = Locale.getDefault().toString()

        val toEmail = getString(R.string.about_us_email).trim()
        val subject = "${getString(R.string.support_email_subject)} | #${(100000..999999).random()}"


        val body = """
(Write your message here)

--- Debug info ---
App: $versionName ($versionCode)
Package: $pkg
OS: $androidInfo
Device: $device
Locale: $locale 
    """.trim()

        val uriString =
            "mailto:$toEmail" + "?subject=${Uri.encode(subject)}" + "&body=${Uri.encode(body)}"

        val uri = uriString.toUri()
        val intent = Intent(Intent.ACTION_SENDTO).apply { data = uri }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.no_email_app_found, Toast.LENGTH_LONG).show()
        }
    }

  companion object {
    private val imagePrefKeys = arrayOf("madaniImages", "naskhImages", "qaloonImages")
  }
}
