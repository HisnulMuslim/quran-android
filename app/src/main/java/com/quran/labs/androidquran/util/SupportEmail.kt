package com.quran.labs.androidquran.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.R
import java.util.Locale

fun Fragment.openSupportEmail() {
  openSupportEmailInternal(
    context = requireContext(),
    start = { intent -> startActivity(intent) }
  )
}

fun Activity.openSupportEmail() {
  openSupportEmailInternal(
    context = this,
    start = { intent -> startActivity(intent) }
  )
}

private fun openSupportEmailInternal(
  context: android.content.Context,
  start: (Intent) -> Unit
) {
  val pkg = context.packageName
  val pi = context.packageManager.getPackageInfo(pkg, 0)

  val versionName = pi.versionName ?: BuildConfig.VERSION_NAME
  val versionCode = PackageInfoCompat.getLongVersionCode(pi)

  val device = "${Build.MANUFACTURER} ${Build.MODEL}"
  val androidInfo = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
  val locale = Locale.getDefault().toString()

  val toEmail = context.getString(R.string.about_us_email).trim()
  val subject = "${context.getString(R.string.support_email_subject)} | #${(100000..999999).random()}"

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
    "mailto:$toEmail" +
        "?subject=${Uri.encode(subject)}" +
        "&body=${Uri.encode(body)}"

  val intent = Intent(Intent.ACTION_SENDTO).apply { data = uriString.toUri() }

  try {
    start(intent)
  } catch (_: ActivityNotFoundException) {
    Toast.makeText(context, R.string.no_email_app_found, Toast.LENGTH_LONG).show()
  }
}
