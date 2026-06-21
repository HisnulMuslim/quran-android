package com.quran.data.page.provider.madani_1439

import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageContentType
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.common.audio.defaultQaris
import com.quran.labs.androidquran.pages.common.madani_1439.size.DefaultPageSizeCalculator
import com.quran.labs.androidquran.pages.data.madani_1439.Madani1439DataSource
import com.quran.labs.androidquran.pages.madani_1439.R

class MadaniPageProvider1439 : PageProvider {

  override fun getDataSource() = dataSource

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      DefaultPageSizeCalculator(displaySize)

  override fun getImageVersion() = 1

  override fun getImagesBaseUrl() = "$baseUrl/madani_1439/"

  override fun getImagesZipBaseUrl() = "$baseUrl/madani_1439/zips/"

  override fun getPatchBaseUrl() = "$baseUrl/madani_1439/patches/v"

  override fun getAyahInfoBaseUrl() = "$baseUrl/madani_1439/databases/ayahinfo/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "new_madani/databases"

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() =  getDatabasesBaseUrl() + "audio/"

  override fun getImagesDirectoryName() = "new_madani"

  override fun getPreviewTitle() = R.string.madani_1439_title

  override fun getPreviewDescription() = R.string.madani_1439_description

  override fun getPageContentType(): PageContentType =
    PageContentType.Line(ratio = 0.161f, lineHeight = 174, allowOverlapOfLines = false)

  override fun pageType(): String = "madani_1439"

  override fun getDefaultQariId(): Int = 4

  override fun getQaris(): List<Qari> = defaultQaris

  override fun getFallbackPageType()  = "tajweed";

  companion object {
    private const val baseUrl = "https://quran-files.hisnulmuslimdua.com/data"
    private val dataSource by lazy { Madani1439DataSource() }
  }
}
