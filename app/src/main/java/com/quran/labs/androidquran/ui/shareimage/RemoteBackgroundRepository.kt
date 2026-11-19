package com.quran.labs.androidquran.ui.shareimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.quran.labs.androidquran.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

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

  companion object {
    // Shared across grid cells and recompositions so scrolling back never re-decodes bitmaps from disk.
    private val bitmapMemoryCache: LruCache<String, Bitmap> by lazy {
      val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
      val cacheSizeKb = (maxMemoryKb / 8).coerceIn(8 * 1024, 32 * 1024)
      object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
          return (value.byteCount / 1024).coerceAtLeast(1)
        }
      }
    }
  }

  fun getCachedBitmap(cacheKey: String): Bitmap? {
    val safeName = sanitizeKey(cacheKey)
    val bmp = bitmapMemoryCache.get(safeName)
    return if (bmp != null && !bmp.isRecycled) bmp else null
  }

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
    writeFileAtomically(catalogCacheFile) { it.writeText(body) }
    items
  }

  suspend fun cachedCatalogItems(): List<RemoteAyahBackgroundItem> = withContext(Dispatchers.IO) {
    getCachedCatalog().orEmpty()
  }

  suspend fun filterCachedPhotoIds(items: List<RemoteAyahBackgroundItem>): Set<String> = withContext(Dispatchers.IO) {
    items.asSequence()
      .filter { !it.isVideo && hasCachedImage("full_${it.id}") }
      .map { it.id }
      .toSet()
  }

  suspend fun filterCachedVideoIds(items: List<RemoteAyahBackgroundItem>): Set<String> = withContext(Dispatchers.IO) {
    items.asSequence()
      .filter { it.isVideo && getCachedVideoFile("vid_${it.id}") != null }
      .map { it.id }
      .toSet()
  }

  fun hasCachedImage(cacheKey: String): Boolean {
    val file = File(cacheDir, "${sanitizeKey(cacheKey)}.img")
    return file.exists() && file.length() > 0L
  }

  suspend fun loadImage(url: String, cacheKey: String): Bitmap? = withContext(Dispatchers.IO) {
    val safeName = sanitizeKey(cacheKey)
    getCachedBitmap(cacheKey)?.let { return@withContext it }

    val cachedFile = File(cacheDir, "$safeName.img")
    val maxDim = if (safeName.startsWith("thumb_")) 320 else 1440
    if (cachedFile.exists() && cachedFile.length() > 0L) {
      decodeBitmap(file = cachedFile, maxDimensionPx = maxDim)?.let { bmp ->
        bitmapMemoryCache.put(safeName, bmp)
        return@withContext bmp
      }
    }
    val bytes = fetchBytes(url) ?: return@withContext null
    writeFileAtomically(cachedFile) { it.writeBytes(bytes) }
    decodeBitmap(bytes = bytes, maxDimensionPx = maxDim)?.also { bmp ->
      bitmapMemoryCache.put(safeName, bmp)
    }
  }

  private fun writeFileAtomically(target: File, writeContent: (File) -> Unit) {
    target.parentFile?.mkdirs()
    val tempFile = File(target.parentFile, "${target.name}.${System.nanoTime()}.part")
    try {
      writeContent(tempFile)
      if (target.exists() && !target.delete()) {
        tempFile.copyTo(target, overwrite = true)
        tempFile.delete()
        return
      }
      if (!tempFile.renameTo(target)) {
        tempFile.copyTo(target, overwrite = true)
        tempFile.delete()
      }
    } catch (_: Exception) {
      tempFile.delete()
    }
  }

  private fun sanitizeKey(cacheKey: String): String {
    return cacheKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")
  }

  private fun decodeBitmap(
    file: File? = null,
    bytes: ByteArray? = null,
    maxDimensionPx: Int?
  ): Bitmap? {
    if (maxDimensionPx == null) {
      return when {
        file != null -> BitmapFactory.decodeFile(file.absolutePath)
        bytes != null -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        else -> null
      }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    when {
      file != null -> BitmapFactory.decodeFile(file.absolutePath, bounds)
      bytes != null -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      else -> return null
    }
    var sampleSize = 1
    while ((bounds.outWidth / sampleSize) > maxDimensionPx * 2 ||
      (bounds.outHeight / sampleSize) > maxDimensionPx * 2
    ) {
      sampleSize *= 2
    }
    val opts = BitmapFactory.Options().apply {
      inSampleSize = sampleSize
      inPreferredConfig = Bitmap.Config.RGB_565
    }
    return when {
      file != null -> BitmapFactory.decodeFile(file.absolutePath, opts)
      bytes != null -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
      else -> null
    }
  }

  fun getCachedVideoFile(cacheKey: String): File? {
    val file = videoCacheFile(cacheKey)
    return if (file.exists() && file.length() > 0L) file else null
  }

  suspend fun loadVideo(
    url: String,
    cacheKey: String,
    onProgress: ((Int?) -> Unit)? = null
  ): File? = withContext(Dispatchers.IO) {
    val cachedFile = videoCacheFile(cacheKey)
    if (cachedFile.exists() && cachedFile.length() > 0L) {
      onProgress?.invoke(100)
      return@withContext cachedFile
    }

    val tempFile = File(cacheDir, "${cachedFile.name}.part")
    if (tempFile.exists()) tempFile.delete()

    try {
      val videoClient = client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
      val req = Request.Builder()
        .url(url)
        .header("Accept-Encoding", "identity")
        .build()

      videoClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@withContext null
        val body = resp.body ?: return@withContext null
        val contentLength = body.contentLength()
        var downloaded = 0L
        var lastReportedPct = -1

        if (contentLength <= 0L) {
          onProgress?.invoke(null)
        } else {
          onProgress?.invoke(0)
          lastReportedPct = 0
        }

        body.byteStream().use { input ->
          FileOutputStream(tempFile).use { output ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
              coroutineContext.ensureActive()
              val read = input.read(buffer)
              if (read < 0) break
              output.write(buffer, 0, read)
              downloaded += read
              if (contentLength > 0L) {
                val pct = ((downloaded * 100L) / contentLength).toInt().coerceIn(0, 99)
                if (pct >= lastReportedPct + 2 || (pct == 99 && lastReportedPct < 99)) {
                  lastReportedPct = pct
                  onProgress?.invoke(pct)
                }
              }
            }
            output.flush()
          }
        }

        if (downloaded <= 0L || (contentLength > 0L && downloaded < contentLength)) {
          tempFile.delete()
          return@withContext null
        }
      }

      if (cachedFile.exists()) cachedFile.delete()
      if (!tempFile.renameTo(cachedFile)) {
        tempFile.copyTo(cachedFile, overwrite = true)
        tempFile.delete()
      }
      onProgress?.invoke(100)
      cachedFile
    } catch (ce: CancellationException) {
      tempFile.delete()
      throw ce
    } catch (_: Exception) {
      tempFile.delete()
      null
    }
  }

  private fun videoCacheFile(cacheKey: String): File {
    val safeName = cacheKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    return File(cacheDir, "$safeName.mp4")
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
    val result = ArrayList<RemoteAyahBackgroundItem>()
    appendCatalogItems(root.optJSONArray("backgrounds"), isVideo = false, out = result)
    appendCatalogItems(root.optJSONArray("videos"), isVideo = true, out = result)
    return result
  }

  private fun appendCatalogItems(
    array: JSONArray?,
    isVideo: Boolean,
    out: MutableList<RemoteAyahBackgroundItem>
  ) {
    if (array == null) return
    for (i in 0 until array.length()) {
      val obj = array.optJSONObject(i) ?: continue
      val id = obj.optString("id")
      val name = obj.optString("name")
      val thumb = obj.optString("thumbnailUrl")
      val image = obj.optString("imageUrl")
      val video = obj.optString("videoUrl").takeIf { it.isNotEmpty() }
      if (id.isEmpty()) continue
      if (isVideo && video == null) continue
      if (!isVideo && image.isEmpty()) continue
      val durationSec = obj.optDouble("durationSec").takeIf { !it.isNaN() && it > 0.0 }
      val sizeBytes = obj.optLong("sizeBytes", 0L).takeIf { it > 0L }
      val resolution = obj.optString("resolution").takeIf { it.isNotEmpty() }
      val isHd = obj.optBoolean("isHd", false)
      out.add(
        RemoteAyahBackgroundItem(
          id = id,
          name = name.ifEmpty { id },
          nameKn = obj.optString("nameKn").takeIf { it.isNotEmpty() },
          thumbnailUrl = thumb.ifEmpty { image },
          imageUrl = if (isVideo) "" else image,
          videoUrl = video,
          durationSec = durationSec,
          sizeBytes = sizeBytes,
          resolution = resolution,
          isHd = isHd
        )
      )
    }
  }
}
