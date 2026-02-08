package com.quran.labs.androidquran.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.util.UpdateInfo
import androidx.core.net.toUri
import timber.log.Timber

/**
 * Dialog to show force update or optional update prompt
 */
class ForceUpdateDialog(
  context: Context,
  private val updateInfo: UpdateInfo,
  private val onPostpone: (() -> Unit)? = null,
  private val onUpdateClicked: (() -> Unit)? = null
) : AlertDialog(context) {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.dialog_force_update)
    setCancelable(!updateInfo.isForced)

    setupViews()
  }

  private fun setupViews() {
    val titleView = findViewById<TextView>(R.id.update_title)
    val messageView = findViewById<TextView>(R.id.update_message)
    val versionInfoView = findViewById<TextView>(R.id.update_version_info)
    val postponeButton = findViewById<Button>(R.id.btn_postpone)
    val updateButton = findViewById<Button>(R.id.btn_update)

    // Set title
    titleView?.text = context.getString(R.string.update_available_title)

    // Set message based on an update type
    messageView?.text = if (updateInfo.isForced) {
      context.getString(R.string.update_forced_message)
    } else {
      context.getString(R.string.update_available_optional_message)
    }

    // Set version info
    val currentVersionName = BuildConfig.VERSION_NAME
    val latestVersionName = getVersionName(updateInfo.latestVersion)
    versionInfoView?.text = context.getString(
      R.string.update_version_info,
      currentVersionName,
      latestVersionName
    )

    // Configure buttons
    if (updateInfo.isForced) {
      // Hide the Postpone button for forced updates
      postponeButton?.visibility = View.GONE
    } else {
      // Show a Postpone button for optional updates
      postponeButton?.visibility = View.VISIBLE
      postponeButton?.setOnClickListener {
        onPostpone?.invoke()
        dismiss()
      }
    }

    // Update button opens Play Store
    updateButton?.setOnClickListener {
      openPlayStore()

      // Invoke callback to track that user clicked update
      onUpdateClicked?.invoke()

      // Dismiss dialog - it will be shown again if user returns without updating
      dismiss()
    }
  }

  private fun openPlayStore() {
    val packageName = context.packageName
    try {
      // Try to open the Play Store app
      val intent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
      // If Play Store app not available, open in a browser
      val intent = Intent(
        Intent.ACTION_VIEW,
        "https://play.google.com/store/apps/details?id=$packageName".toUri()
      )
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    }
  }

  private fun getVersionName(versionCode: Int): String {
    // Convert version code to version name
    // Assuming version format like 1.0.17 for version code 1017
    val major = versionCode / 1000
    val minor = (versionCode % 1000) / 100
    val patch = versionCode % 100
    return "$major.$minor.$patch"
  }
}
