package com.quran.data.page.provider.tajweed

import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.common.tajweed.size.DefaultPageSizeCalculator
import com.quran.labs.androidquran.pages.data.tajweed.TajweedDataSource
import com.quran.labs.androidquran.pages.tajweed.R
import com.quran.labs.androidquran.common.audio.defaultQaris

class TajweedPageProvider : PageProvider {

  override fun getDataSource() = dataSource

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      DefaultPageSizeCalculator(displaySize)

  override fun getImageVersion() = 6

  override fun getImagesBaseUrl() = "$tajweedBaseUrl/"

  override fun getImagesZipBaseUrl() = "$tajweedBaseUrl/zips/"

  override fun getPatchBaseUrl() = "$tajweedBaseUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$tajweedBaseUrl/databases/ayahinfo/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "tajweed/databases"

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() =  getDatabasesBaseUrl() + "audio/"

  override fun getImagesDirectoryName() = "tajweed"

  override fun getPreviewTitle() = R.string.tajweed_title

  override fun getPreviewDescription() = R.string.tajweed_description

  override fun getDefaultQariId(): Int = 0

  override fun getQaris(): List<Qari> = defaultQaris

  companion object {
    private const val baseUrl = "https://quran-files.hisnulmuslimdua.com/data"
    private const val tajweedBaseUrl = "$baseUrl/tajweed"
    private val dataSource by lazy { TajweedDataSource() }
  }
}
