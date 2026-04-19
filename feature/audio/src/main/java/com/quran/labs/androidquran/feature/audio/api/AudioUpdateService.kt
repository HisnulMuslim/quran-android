package com.quran.labs.androidquran.feature.audio.api

import retrofit2.http.GET
import retrofit2.http.Path

interface AudioUpdateService {
  @GET("/data/api/audio_updates/audio_updates_v{revision}.json")
  suspend fun getUpdates(@Path("revision") revision: Int): AudioUpdates?
}
