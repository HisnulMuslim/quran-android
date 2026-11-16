package com.quran.labs.androidquran.ui.shareimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.quran.labs.androidquran.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class RemoteBackgroundRepository(context: Context) {

  private val client = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

  private val primaryCatalogUrl = if (BuildConfig.DEBUG) {
    "https://quran-files.hisnulmuslimdua.com/data/api-test/backgrounds.json"
  } else {
    "https://quran-files.hisnulmuslimdua.com/data/api/backgrounds.json"
  }

  private val fallbackCatalogUrl = if (BuildConfig.DEBUG) {
    "https://quran-files.hisnulmuslimdua.com/data/api/backgrounds.json"
  } else {
    "https://quran-files.hisnulmuslimdua.com/data/api-test/backgrounds.json"
  }

  private val cacheDir = File(context.cacheDir, "remote_ayah_backgrounds").apply { mkdirs() }
  private val catalogCacheFile = File(cacheDir, "catalog_cache.json")

  fun getCachedCatalog(): List<RemoteAyahBackgroundItem>? {
    if (!catalogCacheFile.exists()) return null
    return try {
      parseCatalog(catalogCacheFile.readText())
    } catch (_: Exception) {
      null
    }
  }

  suspend fun fetchCatalog(): List<RemoteAyahBackgroundItem> = withContext(Dispatchers.IO) {
    val body = fetchString(primaryCatalogUrl) ?: fetchString(fallbackCatalogUrl)
      ?: throw IllegalStateException("Unable to load cloud backgrounds catalog")
    val items = parseCatalog(body)
    try {
      catalogCacheFile.writeText(body)
    } catch (_: Exception) {
    }
    items
  }

  suspend fun loadImage(url: String, cacheKey: String): Bitmap? = withContext(Dispatchers.IO) {
    val safeName = cacheKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val cachedFile = File(cacheDir, "$safeName.img")
    if (cachedFile.exists() && cachedFile.length() > 0L) {
      BitmapFactory.decodeFile(cachedFile.absolutePath)?.let { return@withContext it }
    }
    val bytes = fetchBytes(url) ?: return@withContext null
    try {
      cachedFile.writeBytes(bytes)
    } catch (_: Exception) {
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
  }

  private fun fetchString(url: String): String? {
    return try {
      val req = Request.Builder().url(url).build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        resp.body?.string()
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun fetchBytes(url: String): ByteArray? {
    return try {
      val req = Request.Builder().url(url).build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        resp.body?.bytes()
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun parseCatalog(jsonStr: String): List<RemoteAyahBackgroundItem> {
    val root = JSONObject(jsonStr)
    val array = root.optJSONArray("backgrounds") ?: return emptyList()
    val result = ArrayList<RemoteAyahBackgroundItem>(array.length())
    for (i in 0 until array.length()) {
      val obj = array.optJSONObject(i) ?: continue
      val id = obj.optString("id")
      val name = obj.optString("name")
      val thumb = obj.optString("thumbnailUrl")
      val image = obj.optString("imageUrl")
      if (id.isNotEmpty() && image.isNotEmpty()) {
        result.add(
          RemoteAyahBackgroundItem(
            id = id,
            name = name.ifEmpty { id },
            nameKn = obj.optString("nameKn").takeIf { it.isNotEmpty() },
            thumbnailUrl = thumb.ifEmpty { image },
            imageUrl = image
          )
        )
      }
    }
    return result
  }
}
