package com.quran.labs.androidquran.util

import android.content.Context
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.R
import timber.log.Timber
import androidx.core.content.edit

/**
 * Handles force update logic including version checking and postpone tracking
 */
class ForceUpdateChecker(private val context: Context) {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val remoteConfig: FirebaseRemoteConfig by lazy {
    Firebase.remoteConfig.apply {
      val configSettings = remoteConfigSettings {
        minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 6 * 60 * 60
      }
      setConfigSettingsAsync(configSettings)
      setDefaultsAsync(R.xml.remote_config_defaults)
    }
  }

  /**
   * Fetch remote config values from Firebase
   */
  fun fetchRemoteConfig(onComplete: () -> Unit) {
    try {
      remoteConfig.fetchAndActivate()
        .addOnCompleteListener { task ->
          if (task.isSuccessful) {
            Timber.d(" Remote config fetched successfully")
          } else {
            Timber.e("  Remote config fetch failed")
          }
          onComplete()
        }
    } catch (e: Exception) {
      Timber.e(e, " Error fetching remote config")
      onComplete()
    }
  }

  /**
   * Check if update should be shown
   * @return UpdateInfo if update is required, null otherwise
   */
  fun shouldShowUpdate(): UpdateInfo? {
    // Get current app version
    val currentVersionCode = BuildConfig.VERSION_CODE

    val minimumVersionCode = getMinimumRequiredVersion()
    val latestVersionCode = getLatestVersion()


    // Check if app version is below minimum required
    if (currentVersionCode < minimumVersionCode) {
      return UpdateInfo(
        currentVersion = currentVersionCode,
        requiredVersion = minimumVersionCode,
        latestVersion = latestVersionCode,
        isForced = true
      )
    }

    // Check if the optional update is available and not postponed
    if (currentVersionCode < latestVersionCode && !isUpdatePostponed()) {
      return UpdateInfo(
        currentVersion = currentVersionCode,
        requiredVersion = minimumVersionCode,
        latestVersion = latestVersionCode,
        isForced = false
      )
    }

    return null
  }

  /**
   * Mark update as postponed based on configured days from Remote Config
   */
  fun postponeUpdate() {
    val postponeDays = getPostponeDays()
    val postponeDurationMs = postponeDays * 24L * 60 * 60 * 1000
    val postponeUntil = System.currentTimeMillis() + postponeDurationMs
    prefs.edit {
      putLong(KEY_POSTPONE_UNTIL, postponeUntil)
    }
  }

  /**
   * Get postpone days from Firebase Remote Config
   */
  private fun getPostponeDays(): Long {
    val raw = runCatching { remoteConfig.getLong(KEY_POSTPONE_DAYS_REMOTE) }
      .getOrElse { e ->
        Timber.e(e, "Error getting postpone days from remote config")
        DEFAULT_POSTPONE_DAYS
      }

    // Validate: treat 0/negative/absurd values as invalid
    return when {
      raw <= 0L -> DEFAULT_POSTPONE_DAYS
      raw > 30L -> 30L // optional cap; adjust as you like
      else -> raw
    }
  }

  /**
   * Check if the update is currently postponed
   */
  private fun isUpdatePostponed(): Boolean {
    val postponeUntil = prefs.getLong(KEY_POSTPONE_UNTIL, 0L)
    return System.currentTimeMillis() < postponeUntil
  }

  /**
   * Clear postpone state
   */
  fun clearPostpone() {
    prefs.edit { remove(KEY_POSTPONE_UNTIL) }
  }

  /**
   * Get minimum required version from Firebase Remote Config
   */
  private fun getMinimumRequiredVersion(): Int {
    return try {
      remoteConfig.getLong(KEY_MIN_VERSION_REMOTE).toInt()
    } catch (e: Exception) {
      Timber.e(e, "Error getting minimum version from remote config")
      0 // Return 0 if remote config not available (no forced update)
    }
  }

  /**
   * Get latest available version from Firebase Remote Config
   */
  private fun getLatestVersion(): Int {
    return try {
      remoteConfig.getLong(KEY_LATEST_VERSION_REMOTE).toInt()
    } catch (e: Exception) {
      Timber.e(e, "Error getting latest version from remote config")
      BuildConfig.VERSION_CODE // Return current version if remote config not available
    }
  }

  /**
   * Set minimum required version (for testing or remote config update)
   */
  fun setMinimumRequiredVersion(versionCode: Int) {
    prefs.edit().putInt(KEY_MIN_VERSION, versionCode).apply()
  }

  /**
   * Set latest version (for testing or remote config update)
   */
  fun setLatestVersion(versionCode: Int) {
    prefs.edit().putInt(KEY_LATEST_VERSION, versionCode).apply()
  }

  companion object {
    private const val PREFS_NAME = "force_update_prefs"
    private const val KEY_POSTPONE_UNTIL = "postpone_until"
    private const val KEY_MIN_VERSION = "min_version"
    private const val KEY_LATEST_VERSION = "latest_version"
    private const val KEY_MIN_VERSION_REMOTE = "android_min_version"
    private const val KEY_LATEST_VERSION_REMOTE = "android_latest_version"
    private const val KEY_POSTPONE_DAYS_REMOTE = "android_update_postpone_days"
    private const val DEFAULT_POSTPONE_DAYS = 7L // Default 7 days
  }
}

/**
 * Contains information about available update
 */
data class UpdateInfo(
  val currentVersion: Int,
  val requiredVersion: Int,
  val latestVersion: Int,
  val isForced: Boolean // true if update is mandatory, false if optional
)
