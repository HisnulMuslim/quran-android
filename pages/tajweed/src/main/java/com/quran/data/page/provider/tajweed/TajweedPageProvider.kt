package com.quran.data.page.provider.tajweed

import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.common.tajweed.size.DefaultPageSizeCalculator
import com.quran.labs.androidquran.pages.data.tajweed.TajweedDataSource
import com.quran.labs.androidquran.pages.tajweed.R
import com.quran.labs.androidquran.common.audio.R as audioR

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

  override fun getAudioDatabasesBaseUrl() =  "https://android.quran.com/data/databases/audio/"

  override fun getImagesDirectoryName() = "tajweed"

  override fun getPreviewTitle() = R.string.tajweed_title

  override fun getPreviewDescription() = R.string.tajweed_description

  override fun getDefaultQariId(): Int = 4

  override fun getQaris(): List<Qari> {
    return listOf(
      Qari(
        0,
        audioR.string.qari_minshawi_murattal_gapless,
        "https://download.quranicaudio.com/quran/muhammad_siddeeq_al-minshaawee/",
        "minshawi_murattal",
        false,
        "minshawi_murattal"
      ),
      Qari(
        1,
        audioR.string.qari_husary_gapless,
        "https://download.quranicaudio.com/quran/mahmood_khaleel_al-husaree/",
        "husary",
        false,
        "husary"
      ),
      Qari(
        3,
        audioR.string.qari_sudais_gapless,
        "https://download.quranicaudio.com/quran/abdurrahmaan_as-sudays/",
        "sudais_murattal",
        false,
        "sudais_murattal"
      ),
      Qari(
        4,
        audioR.string.qari_shuraym_gapless,
        "https://download.quranicaudio.com/quran/sa3ood_al-shuraym/",
        "shuraym",
        false,
        "shuraym"
      ),
      Qari(
        5,
        audioR.string.qari_muaiqly_haramain_gapless,
        "https://download.quranicaudio.com/quran/maher_256/",
        "maher_al_muaiqly",
        false,
        "maher_al_muaiqly"),
      Qari(
        6,
        audioR.string.qari_saad_al_ghamidi_gapless,
        "https://download.quranicaudio.com/quran/sa3d_al-ghaamidi/complete/",
        "sa3d_alghamidi",
        false,
        "sa3d_alghamidi"),

      Qari(
        7,
        audioR.string.qari_yasser_dussary_gapless,
        "https://download.quranicaudio.com/quran/yasser_ad-dussary/",
        "yasser_dussary",
        false,
        "yasser_dussary"),

      Qari(
        8,
        audioR.string.qari_afasy_gapless,
        "https://download.quranicaudio.com/quran/mishaari_raashid_al_3afaasee/",
        "mishari_alafasy",
        false,
        "mishari_alafasy")
    )
  }

  companion object {
    private const val baseUrl = "https://quran-files.hisnulmuslimdua.com/data"
    private const val tajweedBaseUrl = "$baseUrl/tajweed"
    private val dataSource by lazy { TajweedDataSource() }
  }
}
