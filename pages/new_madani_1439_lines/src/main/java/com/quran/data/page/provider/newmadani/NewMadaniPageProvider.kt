package com.quran.data.page.provider.newmadani

import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageContentType
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.common.newmadani.size.DefaultPageSizeCalculator
import com.quran.labs.androidquran.pages.data.newmadani.NewMadaniDataSource
import com.quran.labs.androidquran.pages.newmadani.R
import com.quran.labs.androidquran.common.audio.R as audioR

class NewMadaniPageProvider : PageProvider {

  override fun getDataSource() = dataSource

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      DefaultPageSizeCalculator(displaySize)

  override fun getImageVersion() = 1

  override fun getImagesBaseUrl() = "$baseUrl/new_madani/"

  override fun getImagesZipBaseUrl() = "$baseUrl/new_madani/zips/"

  override fun getPatchBaseUrl() = "$baseUrl/new_madani/patches/v"

  override fun getAyahInfoBaseUrl() = "$baseUrl/new_madani/databases/ayahinfo/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = "new_madani/databases"

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() =  "https://quran-files.hisnulmuslimdua.com/data/databases/audio"

  override fun getImagesDirectoryName() = "new_madani"

  override fun getPreviewTitle() = R.string.new_madani_title

  override fun getPreviewDescription() = R.string.new_madani_description

  override fun getPageContentType(): PageContentType =
    PageContentType.Line(ratio = 0.161f, lineHeight = 174, allowOverlapOfLines = false)

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
        2,
        audioR.string.qari_basfar,
        "https://download.quranicaudio.com/quran/abdullaah_basfar/",
        "abdullah_basfar",
        false,
        "abdullah_basfar"
      ),Qari(
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
        "mishari_alafasy"),

      Qari(
        9,
        audioR.string.qari_abdulaziz_zahrani_gapless,
        "https://download.quranicaudio.com/quran/abdulaziz_bin_saleh_alzahrani/",
        "abdulaziz_zahrani",
        false,
        "abdulaziz_zahrani"),

      Qari(
        10,
        audioR.string.qari_qatami_gapless,
        "https://download.quranicaudio.com/quran/nasser_bin_ali_alqatami/",
        "qatami",
        false,
        "qatami"),

      Qari(
        11,
        audioR.string.qari_idrees_abkar_gapless,
        "https://download.quranicaudio.com/quran/idrees_abkar/",
        "idrees_abkar",
        false,
        "idrees_abkar")


    )
  }

  companion object {
    private const val baseUrl = "https://quran-files.hisnulmuslimdua.com/data"
    private val dataSource by lazy { NewMadaniDataSource() }
  }
}
