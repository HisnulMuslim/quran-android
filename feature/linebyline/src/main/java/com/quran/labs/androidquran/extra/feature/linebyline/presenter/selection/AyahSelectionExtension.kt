package com.quran.labs.androidquran.extra.feature.linebyline.presenter.selection

import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection

fun AyahSelection.mergeWith(
  ayahSelection: AyahSelection.Ayah,
  selectionStartAyah: SuraAyah
): AyahSelection {
  return when (this) {
    is AyahSelection.Ayah -> mergeAyahWithAyah(this, ayahSelection)
    is AyahSelection.AyahRange -> mergeAyahIntoAyahRange(
      ayahSelection,
      this
    )
    AyahSelection.None -> ayahSelection
  }
}

private fun mergeAyahWithAyah(firstAyah: AyahSelection.Ayah, secondAyah: AyahSelection.Ayah): AyahSelection {
  return if (firstAyah.suraAyah == secondAyah.suraAyah) {
    firstAyah
  } else {
    if (firstAyah.suraAyah > secondAyah.suraAyah) {
      AyahSelection.AyahRange(secondAyah.suraAyah, firstAyah.suraAyah, firstAyah.selectionIndicator)
    } else {
      AyahSelection.AyahRange(
        firstAyah.suraAyah,
        secondAyah.suraAyah,
        secondAyah.selectionIndicator
      )
    }
  }
}

private fun mergeAyahIntoAyahRange(
  ayah: AyahSelection.Ayah,
  ayahRange: AyahSelection.AyahRange
): AyahSelection {
  val suraAyah = ayah.suraAyah
  val newStart = if (suraAyah < ayahRange.startSuraAyah) suraAyah else ayahRange.startSuraAyah
  val newEnd = if (suraAyah > ayahRange.endSuraAyah) suraAyah else ayahRange.endSuraAyah
  return ayahRange.copy(startSuraAyah = newStart, endSuraAyah = newEnd)
}
