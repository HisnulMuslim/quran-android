package com.quran.labs.androidquran

import android.app.Application
import android.content.Context
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration
import androidx.work.WorkManager
import com.quran.labs.androidquran.common.ui.LanguageEnforcer
import com.quran.labs.androidquran.core.worker.QuranWorkerFactory
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import com.quran.labs.androidquran.di.component.application.DaggerApplicationComponent
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.RecordingLogTree
import com.quran.labs.androidquran.widget.BookmarksWidgetSubscriber
import com.quran.mobile.di.QuranApplicationComponent
import com.quran.mobile.di.QuranApplicationComponentProvider
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

open class QuranApplication : Application(), QuranApplicationComponentProvider, LanguageEnforcer {
  lateinit var applicationComponent: ApplicationComponent

  @Inject lateinit var quranWorkerFactory: QuranWorkerFactory
  @Inject lateinit var bookmarksWidgetSubscriber: BookmarksWidgetSubscriber

  override fun provideQuranApplicationComponent(): QuranApplicationComponent {
    return applicationComponent
  }

  override fun onCreate() {
    super.onCreate()
    setupTimber()
    applicationComponent = initializeInjector()
    applicationComponent.inject(this)
    initializeWorkManager()
    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()

    // KQACR12 set light mode as the default for now
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

  }

  open fun setupTimber() {
    Timber.plant(RecordingLogTree())
  }

  open fun initializeInjector(): ApplicationComponent {
    return DaggerApplicationComponent.factory()
      .generate(this)
  }

  open fun initializeWorkManager() {
    WorkManager.initialize(
      this,
      Configuration.Builder()
        .setWorkerFactory(quranWorkerFactory)
        .build()
    )
  }



  override fun refreshLocale(
    context: Context,
    force: Boolean
  ) {
    val language = QuranSettings.getInstance(this).interfaceLanguage().lowercase()
    /*KQACR6 start*/
    val locale: Locale = when (language){
      "ar", "en", "kn" -> {
        Locale(language)
      }
      else -> {
        if (force) {
          Resources.getSystem().configuration.locale
        } else {
          return
        }
      }
    }
    /*KQACR6 end*/
    updateLocale(context, locale)
    val appContext = context.applicationContext
    if (context !== appContext) {
      updateLocale(appContext, locale)
    }
  }

  private fun updateLocale(context: Context, locale: Locale) {
    val resources = context.resources
    val config = resources.configuration
    config.setLocale(locale)
    config.setLayoutDirection(config.locale)
    resources.updateConfiguration(config, resources.displayMetrics)
  }
}
