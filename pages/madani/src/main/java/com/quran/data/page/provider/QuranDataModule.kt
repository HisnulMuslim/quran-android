package com.quran.data.page.provider

import android.util.Log
import androidx.fragment.app.Fragment
import com.quran.common.upgrade.LocalDataUpgrade
import com.quran.common.upgrade.PreferencesUpgrade
import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.page.provider.madani.MadaniPageProvider
import com.quran.data.page.provider.newmadani.NewMadaniPageProvider
import com.quran.data.page.provider.tajweed.TajweedPageProvider
import com.quran.data.pageinfo.mapper.AyahMapper
import com.quran.data.pageinfo.mapper.IdentityAyahMapper
import com.quran.data.source.PageContentType
import com.quran.data.source.PageProvider
import com.quran.page.common.draw.ImageDrawHelper
import com.quran.page.common.factory.PageViewFactory
import com.quran.page.common.factory.PageViewFactoryProvider
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Named

@Module
object QuranDataModule {

  @Provides
  fun providePageViewFactoryProvider(pageProviders: Map<String, @JvmSuppressWildcards PageProvider>): PageViewFactoryProvider {
    return PageViewFactoryProvider { pageType ->
      Log.d("QuranDataModule", "providePageViewFactory called with pageType: $pageType")
      val provider = pageProviders[pageType]
      val contentType = provider?.getPageContentType()
      Log.d("QuranDataModule", "ContentType for $pageType: $contentType")

      if (contentType is PageContentType.Line) {
        Log.d("QuranDataModule", "Creating PageViewFactory for line-by-line page")
        object : PageViewFactory {
          override fun providePage(pageNumber: Int, pageMode: com.quran.page.common.data.PageMode): Fragment? {
            Log.d("QuranDataModule", "providePage called for page: $pageNumber")
            return try {
              val clazz = Class.forName("com.quran.labs.androidquran.extra.feature.linebyline.QuranLineByLineFragment")
              val companionField = clazz.getDeclaredField("Companion")
              val companion = companionField.get(null)
              val method = companion.javaClass.getMethod("newInstance", Int::class.javaPrimitiveType)
              val fragment = method.invoke(companion, pageNumber) as Fragment
              Log.d("QuranDataModule", "Successfully created QuranLineByLineFragment for page: $pageNumber")
              fragment
            } catch (e: Exception) {
              Log.e("QuranDataModule", "Failed to create QuranLineByLineFragment", e)
              null
            }
          }

          override fun providePageView(context: android.content.Context, pageNumber: Int, pageMode: com.quran.page.common.data.PageMode): android.view.View? {
            return null
          }
        }
      } else {
        Log.d("QuranDataModule", "Not a line-by-line page, returning null")
        null
      }
    }
  }

  @Named(DependencyInjectionConstants.FALLBACK_PAGE_TYPE)
  @JvmStatic
  @Provides
  fun provideFallbackPageType(): String = "madani"

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("madani")
  fun provideMadaniPageSet(): PageProvider {
    return MadaniPageProvider()
  }

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("tajweed")
  fun provideTajweedPageSet(): PageProvider {
    return TajweedPageProvider()
  }

  @JvmStatic
  @Provides
  @IntoMap
  @StringKey("new_madani_1439_lines")
  fun provideNewMadaniPageSet(): PageProvider {
    return NewMadaniPageProvider()
  }

  @JvmStatic
  @Provides
  @ElementsIntoSet
  fun provideImageDrawHelpers(): Set<ImageDrawHelper> {
    return emptySet()
  }

  @JvmStatic
  @Provides
  fun provideLocalDataUpgrade(): LocalDataUpgrade = object : LocalDataUpgrade {  }

  @JvmStatic
  @Provides
  fun providePreferencesUpgrade(): PreferencesUpgrade = PreferencesUpgrade { _, _, _ -> true }

  @JvmStatic
  @Reusable
  @Provides
  fun provideAyahMapper(): AyahMapper = IdentityAyahMapper()
}
