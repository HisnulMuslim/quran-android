package com.quran.labs.androidquran.ui.shareimage

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.preference.PreferenceManager
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.database.SuraTimingDatabaseHandler
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QuranFileUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Exports a rendered Ayah card [Bitmap] + reciter audio segment into an MP4 video file
 * using built-in Android MediaExtractor / MediaCodec / MediaMuxer and the app's official Qari sources.
 */
class AyahVideoExporter(private val context: Context) {

  @Inject lateinit var audioUtils: AudioUtils
  @Inject lateinit var quranFileUtils: QuranFileUtils
  @Inject lateinit var okHttpClient: OkHttpClient

  init {
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
  }

  data class VideoExportProgress(
    val statusText: String,
    val percent: Int? = null
  )

  data class AudioTrackPart(
    val file: File,
    val startMs: Long,
    val endMs: Long
  )

  data class AyahAudioCue(
    val sura: Int,
    val ayah: Int,
    val relativeStartMs: Long,
    val relativeEndMs: Long,
    val partIndex: Int = 0
  )

  data class AudioSegment(
    val file: File,
    val startMs: Long,
    val endMs: Long,
    val qariName: String,
    val parts: List<AudioTrackPart> = listOf(AudioTrackPart(file, startMs, endMs)),
    val ayahCues: List<AyahAudioCue> = emptyList()
  )

  data class TimedSlideSource(
    val startMs: Long,
    val endMs: Long,
    val renderBitmap: (targetWidth: Int, targetHeight: Int) -> Bitmap
  )

  fun getAvailableQaris(): List<Pair<Int, String>> {
    return audioUtils.getQariList(context).map { it.id to it.name }
  }

  fun getDefaultQariId(): Int {
    val qaris = audioUtils.getQariList(context)
    val prefId = PreferenceManager.getDefaultSharedPreferences(context)
      .getInt(Constants.PREF_DEFAULT_QARI, 0)
    return qaris.firstOrNull { it.id == prefId }?.id ?: qaris.firstOrNull()?.id ?: 0
  }

  fun resolveAudioForAyah(
    sura: Int,
    startAyah: Int,
    endAyah: Int = startAyah,
    qariId: Int? = null,
    ayahList: List<Pair<Int, Int>> = emptyList(),
    onProgress: ((VideoExportProgress) -> Unit)? = null
  ): AudioSegment {
    val qaris = audioUtils.getQariList(context)
    val targetId = qariId ?: getDefaultQariId()
    val qari = qaris.firstOrNull { it.id == targetId } ?: qaris.firstOrNull()
      ?: throw IllegalStateException("No reciters available")

    val localDirPath = audioUtils.getLocalQariUrl(qari)
      ?: context.cacheDir.resolve("share_audio/${qari.path}").absolutePath + File.separator
    val localDir = File(localDirPath).apply { mkdirs() }

    val targetAyahs = if (ayahList.isNotEmpty()) {
      ayahList
    } else {
      val clampedEnd = maxOf(startAyah, endAyah)
      (startAyah..clampedEnd).map { sura to it }
    }

    if (qari.isGapless) {
      val dbPath = audioUtils.getQariDatabasePathIfGapless(qari)
        ?: File(localDir, "${qari.databaseName}.db").absolutePath
      val dbFile = File(dbPath)
      if (!dbFile.exists() || dbFile.length() == 0L) {
        val timingsPlain = context.getString(R.string.share_downloading_timings)
        onProgress?.invoke(VideoExportProgress(timingsPlain))
        val dbName = qari.databaseName
          ?: throw IllegalStateException("Missing timing database for ${qari.name}")
        val zipUrl = quranFileUtils.gaplessDatabaseRootUrl + dbName + AudioUtils.ZIP_EXTENSION
        val zipTmp = File(localDir, "$dbName.zip.tmp")
        downloadUrlToFile(zipUrl, zipTmp) { downloaded, total ->
          onProgress?.invoke(
            formatDownloadProgress(
              plainLabel = timingsPlain,
              detailResId = R.string.share_downloading_timings_progress,
              downloadedBytes = downloaded,
              totalBytes = total
            )
          )
        } ?: throw IllegalStateException("Failed to download timing database for ${qari.name}")
        unzipDbFile(zipTmp, localDir)
        zipTmp.delete()
        SuraTimingDatabaseHandler.clearDatabaseHandlerIfExists(dbPath)
      }

      // Group consecutive ayahs by sura so single-sura and cross-sura ranges both work cleanly.
      val suraRuns = mutableListOf<Pair<Int, List<Int>>>()
      for ((itemSura, itemAyah) in targetAyahs) {
        val last = suraRuns.lastOrNull()
        if (last != null && last.first == itemSura) {
          suraRuns[suraRuns.lastIndex] = itemSura to (last.second + itemAyah)
        } else {
          suraRuns.add(itemSura to listOf(itemAyah))
        }
      }

      val parts = mutableListOf<AudioTrackPart>()
      val cues = mutableListOf<AyahAudioCue>()
      var cumulativeOffsetMs = 0L

      suraRuns.forEachIndexed { runIdx, (runSura, ayahsInRun) ->
        val suraFileName = String.format(Locale.US, "%03d%s", runSura, ".mp3")
        val suraFile = File(localDir, suraFileName)
        if (!suraFile.exists() || suraFile.length() == 0L) {
          val plainLabel = if (suraRuns.size > 1) {
            context.getString(R.string.share_downloading_surah_item, runIdx + 1, suraRuns.size)
          } else {
            context.getString(R.string.share_downloading_surah)
          }
          val detailResId = if (suraRuns.size == 1) R.string.share_downloading_surah_progress else null
          onProgress?.invoke(VideoExportProgress(plainLabel, 0))
          val suraUrl = String.format(Locale.US, audioUtils.getQariUrl(qari), runSura)
          downloadUrlToFile(suraUrl, suraFile) { downloaded, total ->
            onProgress?.invoke(
              formatDownloadProgress(
                plainLabel = plainLabel,
                detailResId = detailResId,
                downloadedBytes = downloaded,
                totalBytes = total
              )
            )
          } ?: throw IllegalStateException("Failed to download surah audio for ${qari.name}")
        }

        val runStartAyah = ayahsInRun.first()
        val runEndAyah = ayahsInRun.last()
        val ayahBounds = queryAyahCues(dbPath, runSura, runStartAyah, runEndAyah)
          ?: throw IllegalStateException("Timings unavailable for $runSura:$runStartAyah")
        val partStartMs = ayahBounds.first().second.first
        val partEndMs = ayahBounds.last().second.second
        val partIndex = parts.size
        parts.add(AudioTrackPart(suraFile, partStartMs, partEndMs))

        val resolvedPartEndMs = if (partEndMs == Long.MAX_VALUE && runIdx < suraRuns.lastIndex) {
          readAudioDurationMs(suraFile)?.takeIf { it > partStartMs } ?: Long.MAX_VALUE
        } else {
          partEndMs
        }

        for ((ayahNum, bounds) in ayahBounds) {
          val relStart = cumulativeOffsetMs + maxOf(0L, bounds.first - partStartMs)
          val relEnd = if (bounds.second == Long.MAX_VALUE) {
            if (resolvedPartEndMs == Long.MAX_VALUE) Long.MAX_VALUE
            else cumulativeOffsetMs + (resolvedPartEndMs - partStartMs)
          } else {
            cumulativeOffsetMs + maxOf(0L, bounds.second - partStartMs)
          }
          cues.add(
            AyahAudioCue(
              sura = runSura,
              ayah = ayahNum,
              relativeStartMs = relStart,
              relativeEndMs = relEnd,
              partIndex = partIndex
            )
          )
        }

        if (resolvedPartEndMs != Long.MAX_VALUE) {
          cumulativeOffsetMs += maxOf(0L, resolvedPartEndMs - partStartMs)
        }
      }

      val firstPart = parts.first()
      return AudioSegment(
        file = firstPart.file,
        startMs = firstPart.startMs,
        endMs = firstPart.endMs,
        qariName = qari.name,
        parts = parts,
        ayahCues = cues
      )
    } else {
      val parts = mutableListOf<AudioTrackPart>()
      val cues = mutableListOf<AyahAudioCue>()
      var cumulativeMs = 0L

      targetAyahs.forEachIndexed { idx, (itemSura, itemAyah) ->
        val prevSura = targetAyahs.getOrNull(idx - 1)?.first
        if (itemAyah == 1 && itemSura != 1 && itemSura != 9 && prevSura != itemSura) {
          val basmalaFile = File(localDir, String.format(Locale.US, "%03d%03d.mp3", 1, 1))
          if (!basmalaFile.exists() || basmalaFile.length() == 0L) {
            val basmalaUrl = String.format(Locale.US, audioUtils.getQariUrl(qari), 1, 1)
            runCatching { downloadUrlToFile(basmalaUrl, basmalaFile, null) }
          }
          if (basmalaFile.exists() && basmalaFile.length() > 0L) {
            val basmalaDurMs = readAudioDurationMs(basmalaFile) ?: -1L
            val basmalaPartIdx = parts.size
            parts.add(AudioTrackPart(basmalaFile, 0L, Long.MAX_VALUE))
            val basmalaEndMs = if (basmalaDurMs > 0L) cumulativeMs + basmalaDurMs else Long.MAX_VALUE
            cues.add(
              AyahAudioCue(
                sura = itemSura,
                ayah = itemAyah,
                relativeStartMs = cumulativeMs,
                relativeEndMs = basmalaEndMs,
                partIndex = basmalaPartIdx
              )
            )
            if (basmalaDurMs > 0L) {
              cumulativeMs += basmalaDurMs
            }
          }
        }

        val ayahFileName = String.format(Locale.US, "%03d%03d.mp3", itemSura, itemAyah)
        val ayahFile = File(localDir, ayahFileName)
        if (!ayahFile.exists() || ayahFile.length() == 0L) {
          val plainLabel = if (targetAyahs.size > 1) {
            context.getString(R.string.share_downloading_audio_item, idx + 1, targetAyahs.size)
          } else {
            context.getString(R.string.share_downloading_audio)
          }
          val detailResId = if (targetAyahs.size == 1) R.string.share_downloading_audio_progress else null
          onProgress?.invoke(VideoExportProgress(plainLabel, 0))
          val ayahUrl = String.format(Locale.US, audioUtils.getQariUrl(qari), itemSura, itemAyah)
          downloadUrlToFile(ayahUrl, ayahFile) { downloaded, total ->
            onProgress?.invoke(
              formatDownloadProgress(
                plainLabel = plainLabel,
                detailResId = detailResId,
                downloadedBytes = downloaded,
                totalBytes = total
              )
            )
          } ?: throw IllegalStateException("Failed to download ayah audio for ${qari.name}")
        }

        val partDurationMs = readAudioDurationMs(ayahFile) ?: -1L
        val ayahPartIdx = parts.size
        parts.add(AudioTrackPart(ayahFile, 0L, Long.MAX_VALUE))
        val endOffsetMs = if (partDurationMs > 0L) cumulativeMs + partDurationMs else Long.MAX_VALUE
        cues.add(
          AyahAudioCue(
            sura = itemSura,
            ayah = itemAyah,
            relativeStartMs = cumulativeMs,
            relativeEndMs = endOffsetMs,
            partIndex = ayahPartIdx
          )
        )
        if (partDurationMs > 0L) {
          cumulativeMs += partDurationMs
        }
      }

      val firstPart = parts.first()
      return AudioSegment(
        file = firstPart.file,
        startMs = firstPart.startMs,
        endMs = firstPart.endMs,
        qariName = qari.name,
        parts = parts,
        ayahCues = cues
      )
    }
  }

  private fun formatDownloadProgress(
    plainLabel: String,
    detailResId: Int?,
    downloadedBytes: Long,
    totalBytes: Long
  ): VideoExportProgress {
    fun formatWithDetail(detail: String): String {
      return if (detailResId != null) {
        context.getString(detailResId, detail)
      } else {
        "$plainLabel ($detail)"
      }
    }

    return if (totalBytes > 0L) {
      val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
      val label = if (totalBytes >= 1_048_576L) {
        val dlMb = downloadedBytes / (1024f * 1024f)
        val totMb = totalBytes / (1024f * 1024f)
        formatWithDetail(String.format(Locale.US, "%.1f / %.1f MB", dlMb, totMb))
      } else {
        plainLabel
      }
      VideoExportProgress(label, pct)
    } else {
      val mb = downloadedBytes / (1024f * 1024f)
      val detail = if (mb >= 0.1f) {
        String.format(Locale.US, "%.1f MB", mb)
      } else {
        "${downloadedBytes / 1024L} KB"
      }
      VideoExportProgress(formatWithDetail(detail), null)
    }
  }

  private fun unzipDbFile(zipFile: File, destDir: File) {
    ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          val outName = File(entry.name).name
          val outFile = File(destDir, outName)
          val tmpFile = File(destDir, "$outName.tmp")
          tmpFile.outputStream().use { out ->
            zis.copyTo(out)
          }
          tmpFile.renameTo(outFile)
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }
  }

  private fun queryAyahCues(
    dbPath: String,
    sura: Int,
    startAyah: Int,
    endAyah: Int
  ): List<Pair<Int, Pair<Long, Long>>>? {
    val handler = SuraTimingDatabaseHandler.getDatabaseHandler(dbPath)
    val cursor = handler.getAyahTimings(sura) ?: return null
    val rows = mutableListOf<Pair<Int, Long>>()
    cursor.use { c ->
      while (c.moveToNext()) {
        val rowAyah = c.getInt(1)
        val rowTime = c.getLong(2)
        rows.add(rowAyah to rowTime)
      }
    }
    if (rows.isEmpty()) return null
    return resolveGaplessAyahBounds(sura, startAyah, endAyah, rows)
  }

  private fun readAudioDurationMs(file: File): Long? {
    val extractor = MediaExtractor()
    return try {
      extractor.setDataSource(file.absolutePath)
      for (i in 0 until extractor.trackCount) {
        val fmt = extractor.getTrackFormat(i)
        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/") && fmt.containsKey(MediaFormat.KEY_DURATION)) {
          val durUs = fmt.getLong(MediaFormat.KEY_DURATION)
          if (durUs > 0L) return durUs / 1000L
        }
      }
      null
    } catch (_: Exception) {
      null
    } finally {
      runCatching { extractor.release() }
    }
  }

  private fun downloadUrlToFile(
    url: String,
    destFile: File,
    onProgress: ((Long, Long) -> Unit)?
  ): File? {
    return try {
      destFile.parentFile?.mkdirs()
      val request = Request.Builder()
        .url(url)
        .header("Accept-Encoding", "identity")
        .build()
      okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Timber.e("Audio HTTP %d for %s", response.code, url)
          return null
        }
        val body = response.body ?: return null
        val totalBytes = body.contentLength()
        val tmp = File(destFile.parentFile, "${destFile.name}.part")
        body.byteStream().use { input ->
          tmp.outputStream().use { output ->
            val buf = ByteArray(16 * 1024)
            var downloaded = 0L
            var lastPct = -1
            var lastReportBytes = 0L
            while (true) {
              val read = input.read(buf)
              if (read < 0) break
              output.write(buf, 0, read)
              downloaded += read
              if (onProgress != null) {
                if (totalBytes > 0L) {
                  val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                  if (pct != lastPct || downloaded - lastReportBytes >= 256 * 1024L) {
                    lastPct = pct
                    lastReportBytes = downloaded
                    onProgress(downloaded, totalBytes)
                  }
                } else if (downloaded - lastReportBytes >= 32 * 1024L) {
                  lastReportBytes = downloaded
                  onProgress(downloaded, -1L)
                }
              }
            }
          }
        }
        if (tmp.length() <= 0) {
          tmp.delete()
          return null
        }
        if (destFile.exists()) destFile.delete()
        tmp.renameTo(destFile)
        destFile
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to download audio: %s", url)
      null
    }
  }

  fun exportMp4(
    cardBitmap: Bitmap,
    segment: AudioSegment,
    onProgress: ((VideoExportProgress) -> Unit)? = null
  ): File {
    return exportMp4WithSlides(
      baseWidth = cardBitmap.width,
      baseHeight = cardBitmap.height,
      segment = segment,
      backgroundVideoFile = null,
      backgroundVideoScale = 1.0f,
      slideFactory = { _, _, _ ->
        listOf(TimedSlideSource(0L, Long.MAX_VALUE) { _, _ -> cardBitmap })
      },
      recycleProducedBitmaps = false,
      onProgress = onProgress
    )
  }

  fun recommendedExportQuality(): AyahVideoExportQuality {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(memInfo)
    val isLowRam = am?.isLowRamDevice == true
    val caps = probeAvcEncoderCapabilities(1080, 1920)
    val can1080p = !isLowRam &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
      memInfo.totalMem >= HIGH_TIER_MIN_RAM_BYTES &&
      caps.supports1080p30
    return if (can1080p) AyahVideoExportQuality.HIGH_1080P else AyahVideoExportQuality.STANDARD_720P
  }

  fun exportMp4WithSlides(
    baseWidth: Int,
    baseHeight: Int,
    segment: AudioSegment,
    backgroundVideoFile: File? = null,
    backgroundVideoScale: Float = 1.0f,
    slideFactory: (resolvedCues: List<AyahAudioCue>, targetWidth: Int, targetHeight: Int) -> List<TimedSlideSource>,
    recycleProducedBitmaps: Boolean = true,
    requestedQuality: AyahVideoExportQuality? = null,
    onProgress: ((VideoExportProgress) -> Unit)? = null
  ): File {
    // Use filesDir rather than cacheDir so Android's low-storage cache reaper never deletes the
    // newly exported MP4 before MediaStore / Share Intent finishes copying it.
    val shareDir = File(context.filesDir, "shared_exports").apply { mkdirs() }
    val staleCutoffMs = System.currentTimeMillis() - 10 * 60_000L
    shareDir.listFiles()?.forEach { oldFile ->
      if (oldFile.isFile && oldFile.name.endsWith(".mp4") && oldFile.lastModified() < staleCutoffMs) {
        oldFile.delete()
      }
    }
    // Remove legacy cacheDir/shared_cards MP4s left by older builds as well.
    File(context.cacheDir, "shared_cards").listFiles()?.forEach { oldFile ->
      if (oldFile.isFile && oldFile.name.endsWith(".mp4")) oldFile.delete()
    }
    val outFile = File(shareDir, "Quran_Ayah_${System.currentTimeMillis()}.mp4")

    onProgress?.invoke(VideoExportProgress(context.getString(R.string.share_encoding_audio), 0))
    val encoded = transcodeAudioToAac(segment, onProgress)
    val safeDurationUs = maxOf(encoded.durationUs, 1_000_000L)

    onProgress?.invoke(VideoExportProgress(context.getString(R.string.share_creating_video), 0))
    val hasVideoBg = backgroundVideoFile != null
    val encoderCaps = probeAvcEncoderCapabilities(baseWidth, baseHeight)
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(memInfo)
    val isLowRam = am?.isLowRamDevice == true
    val totalRam = memInfo.totalMem

    val provisionalSlides = slideFactory(encoded.ayahCues, 720, 720)
    val isMultiSlide = provisionalSlides.size > 1
    val profiles = buildExportProfileChain(
      hasVideoBg = hasVideoBg,
      isMultiSlide = isMultiSlide,
      durationUs = safeDurationUs,
      totalRamBytes = totalRam,
      isLowRamDevice = isLowRam,
      sdkInt = Build.VERSION.SDK_INT,
      capabilities = encoderCaps,
      requestedQuality = requestedQuality
    )

    var lastError: Throwable? = null
    for ((attemptIndex, profile) in profiles.withIndex()) {
      val (targetWidth, targetHeight) = alignVideoDimensions(
        baseWidth = baseWidth,
        baseHeight = baseHeight,
        maxWidth = profile.maxWidth,
        widthAlignment = profile.widthAlignment,
        heightAlignment = profile.heightAlignment
      )
      val slides = slideFactory(encoded.ayahCues, targetWidth, targetHeight)
      require(slides.isNotEmpty()) { "At least one slide is required for video export" }

      try {
        muxVideoAndAudio(
          slides = slides,
          recycleProducedBitmaps = recycleProducedBitmaps,
          width = targetWidth,
          height = targetHeight,
          durationUs = safeDurationUs,
          backgroundVideoFile = backgroundVideoFile,
          backgroundVideoScale = backgroundVideoScale,
          aacFormat = encoded.format,
          aacFrames = encoded.frames,
          outFile = outFile,
          profile = profile,
          preferredCodecName = encoderCaps.codecName,
          maxHighProfileLevel = encoderCaps.maxHighProfileLevel,
          onProgress = onProgress
        )
        return outFile
      } catch (t: Throwable) {
        if (t is InterruptedException) throw t
        lastError = t
        outFile.delete()
        val hasFallback = attemptIndex < profiles.lastIndex
        Timber.w(
          t,
          "Video export failed at %dx%d @ %dfps (%d bps)%s",
          targetWidth,
          targetHeight,
          profile.fps,
          profile.bitRate,
          if (hasFallback) "; retrying lower tier" else ""
        )
        if (!hasFallback) throw t
      }
    }
    throw (lastError ?: IllegalStateException("Video export failed"))
  }

  private data class EncodedAudio(
    val format: MediaFormat,
    val frames: List<Pair<ByteArray, MediaCodec.BufferInfo>>,
    val durationUs: Long,
    val ayahCues: List<AyahAudioCue>
  )

  private fun transcodeAudioToAac(
    segment: AudioSegment,
    onProgress: ((VideoExportProgress) -> Unit)? = null
  ): EncodedAudio {
    val parts = segment.parts.ifEmpty {
      listOf(AudioTrackPart(segment.file, segment.startMs, segment.endMs))
    }

    var totalExpectedSpanUs = 0L
    var allSpansKnown = true
    for (part in parts) {
      val sUs = part.startMs * 1000L
      val eUs = if (part.endMs == Long.MAX_VALUE) {
        (readAudioDurationMs(part.file) ?: -1L).let { if (it > 0L) it * 1000L else -1L }
      } else {
        part.endMs * 1000L
      }
      if (eUs > sUs) {
        totalExpectedSpanUs += (eUs - sUs)
      } else {
        allSpansKnown = false
      }
    }
    val expectedTotalUs = if (allSpansKnown && totalExpectedSpanUs > 0L) totalExpectedSpanUs else -1L

    var encoder: MediaCodec? = null
    var aacFormatReq: MediaFormat? = null
    var outputFormat: MediaFormat? = null
    val frames = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
    var lastPtsUs = 0L
    var lastAudioPct = 0
    var encSampleRate = 44100
    var encChannelCount = 1
    var totalPcmBytes = 0L

    val encInfo = MediaCodec.BufferInfo()
    val resolvedCues = mutableListOf<AyahAudioCue>()

    fun bytesPerSec(): Long = (encSampleRate * encChannelCount * 2).toLong().coerceAtLeast(1L)

    fun drainAvailableEncoder(waitForEos: Boolean) {
      val activeEncoder = encoder ?: return
      while (true) {
        val encOutIdx = activeEncoder.dequeueOutputBuffer(encInfo, if (waitForEos) 10_000L else 0L)
        if (encOutIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
          if (waitForEos) continue else break
        } else if (encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          outputFormat = activeEncoder.outputFormat
        } else if (encOutIdx >= 0) {
          val encOutBuf = activeEncoder.getOutputBuffer(encOutIdx)!!
          if ((encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && encInfo.size > 0) {
            val bytes = ByteArray(encInfo.size)
            encOutBuf.position(encInfo.offset)
            encOutBuf.get(bytes)
            val copyInfo = MediaCodec.BufferInfo().apply {
              set(0, encInfo.size, encInfo.presentationTimeUs, encInfo.flags)
            }
            frames.add(bytes to copyInfo)
            if (encInfo.presentationTimeUs > lastPtsUs) {
              lastPtsUs = encInfo.presentationTimeUs
            }
          }
          val isEos = (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
          activeEncoder.releaseOutputBuffer(encOutIdx, false)
          if (isEos) break
        }
      }
    }

    try {
      parts.forEachIndexed { partIdx, part ->
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
          extractor.setDataSource(part.file.absolutePath)
          var audioTrackIndex = -1
          var inputFormat: MediaFormat? = null
          for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
              audioTrackIndex = i
              inputFormat = fmt
              break
            }
          }
          if (audioTrackIndex < 0 || inputFormat == null) {
            throw IllegalStateException("No audio track found in ${part.file.name}")
          }

          extractor.selectTrack(audioTrackIndex)
          val startUs = part.startMs * 1000L
          val endUs = if (part.endMs == Long.MAX_VALUE) Long.MAX_VALUE else part.endMs * 1000L
          if (startUs > 0L) {
            extractor.seekTo(maxOf(0L, startUs - 500_000L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
          }

          val inputMime = inputFormat.getString(MediaFormat.KEY_MIME)!!
          var partSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
          } else 44100
          var partChannelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
          } else 1

          decoder = MediaCodec.createDecoderByType(inputMime).apply {
            configure(inputFormat, null, null, 0)
            start()
          }

          val decInfo = MediaCodec.BufferInfo()
          var extractorDone = false
          var decoderDone = false
          var pendingPcm: ByteArray? = null
          var pendingOffset = 0
          var pendingPtsUs = 0L

          val partStartMs = (totalPcmBytes * 1000L) / bytesPerSec()

          while (!decoderDone || pendingPcm != null) {
            if (!extractorDone) {
              val inIdx = decoder.dequeueInputBuffer(0)
              if (inIdx >= 0) {
                val buf = decoder.getInputBuffer(inIdx)!!
                val size = extractor.readSampleData(buf, 0)
                val sampleTime = extractor.sampleTime
                if (size < 0 || (endUs != Long.MAX_VALUE && sampleTime > endUs + 200_000L)) {
                  decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                  extractorDone = true
                } else {
                  decoder.queueInputBuffer(inIdx, 0, size, sampleTime, 0)
                  extractor.advance()
                }
              }
            }

            if (!decoderDone && pendingPcm == null) {
              val outIdx = decoder.dequeueOutputBuffer(decInfo, 5_000)
              when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                  val decFmt = decoder.outputFormat
                  if (decFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    partSampleRate = decFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                  }
                  if (decFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    partChannelCount = decFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                  }
                }
                outIdx >= 0 -> {
                  val isEos = (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                  val keep = decInfo.size > 0 &&
                    decInfo.presentationTimeUs >= startUs &&
                    (endUs == Long.MAX_VALUE || decInfo.presentationTimeUs <= endUs)

                  if (keep) {
                    val outBuf = decoder.getOutputBuffer(outIdx)!!
                    val rawBytes = ByteArray(decInfo.size)
                    outBuf.position(decInfo.offset)
                    outBuf.get(rawBytes)

                    if (encoder == null) {
                      encSampleRate = partSampleRate
                      encChannelCount = partChannelCount
                      aacFormatReq = createAacFormat(encSampleRate, encChannelCount)
                      encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                        configure(aacFormatReq, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        start()
                      }
                    }

                    val normalizedBytes = normalizeChannelCount(rawBytes, partChannelCount, encChannelCount)
                    val samplePtsUs = (totalPcmBytes * 1_000_000L) / bytesPerSec()
                    totalPcmBytes += normalizedBytes.size
                    pendingPcm = normalizedBytes
                    pendingOffset = 0
                    pendingPtsUs = samplePtsUs

                    if (expectedTotalUs > 0L && onProgress != null) {
                      val pct = ((samplePtsUs * 100L) / expectedTotalUs).toInt().coerceIn(0, 99)
                      if (pct != lastAudioPct) {
                        lastAudioPct = pct
                        onProgress(VideoExportProgress(context.getString(R.string.share_encoding_audio), pct))
                      }
                    }
                  }
                  decoder.releaseOutputBuffer(outIdx, false)
                  if (isEos) {
                    decoderDone = true
                  }
                }
              }
            }

            val activeEncoder = encoder
            if (activeEncoder != null) {
              if (pendingPcm != null) {
                val encInIdx = activeEncoder.dequeueInputBuffer(5_000)
                if (encInIdx >= 0) {
                  val encInBuf = activeEncoder.getInputBuffer(encInIdx)!!
                  val remaining = pendingPcm.size - pendingOffset
                  val toCopy = minOf(remaining, encInBuf.remaining())
                  encInBuf.put(pendingPcm, pendingOffset, toCopy)
                  activeEncoder.queueInputBuffer(encInIdx, 0, toCopy, pendingPtsUs, 0)
                  pendingOffset += toCopy
                  pendingPtsUs += (toCopy.toLong() * 1_000_000L) / bytesPerSec()
                  if (pendingOffset >= pendingPcm.size) {
                    pendingPcm = null
                    pendingOffset = 0
                  }
                }
              }
              drainAvailableEncoder(waitForEos = false)
            }
          }

          val partEndMs = (totalPcmBytes * 1000L) / bytesPerSec()
          val rawPartCues = segment.ayahCues.filter { it.partIndex == partIdx }
          if (rawPartCues.size == 1 && part.startMs == 0L && part.endMs == Long.MAX_VALUE) {
            val cue = rawPartCues[0]
            resolvedCues.add(
              cue.copy(
                relativeStartMs = partStartMs,
                relativeEndMs = partEndMs
              )
            )
          } else if (rawPartCues.isNotEmpty()) {
            val origStart = rawPartCues.first().relativeStartMs
            val partSpanMs = maxOf(0L, partEndMs - partStartMs)
            for (cue in rawPartCues) {
              val relInPartStart = maxOf(0L, cue.relativeStartMs - origStart)
              val relInPartEnd = if (cue.relativeEndMs == Long.MAX_VALUE) {
                partSpanMs
              } else {
                minOf(partSpanMs, maxOf(relInPartStart, cue.relativeEndMs - origStart))
              }
              resolvedCues.add(
                cue.copy(
                  relativeStartMs = partStartMs + relInPartStart,
                  relativeEndMs = partStartMs + relInPartEnd
                )
              )
            }
          }
        } finally {
          runCatching { decoder?.stop() }
          runCatching { decoder?.release() }
          runCatching { extractor.release() }
        }
      }

      val activeEncoder = encoder
      if (activeEncoder != null) {
        val encInIdx = activeEncoder.dequeueInputBuffer(10_000)
        if (encInIdx >= 0) {
          activeEncoder.queueInputBuffer(encInIdx, 0, 0, lastPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drainAvailableEncoder(waitForEos = true)
      }
    } finally {
      runCatching { encoder?.stop() }
      runCatching { encoder?.release() }
    }

    if (frames.isEmpty()) {
      throw IllegalStateException("Audio segment contained no playable frames")
    }

    onProgress?.invoke(VideoExportProgress(context.getString(R.string.share_encoding_audio), 100))

    val encodedDurationUs = maxOf(
      lastPtsUs + 23_220L,
      (totalPcmBytes * 1_000_000L) / bytesPerSec()
    )
    return EncodedAudio(
      format = outputFormat ?: aacFormatReq ?: createAacFormat(44100, 1),
      frames = frames,
      durationUs = encodedDurationUs,
      ayahCues = resolvedCues
    )
  }

  private fun normalizeChannelCount(pcm: ByteArray, srcChannels: Int, dstChannels: Int): ByteArray {
    if (srcChannels == dstChannels) return pcm
    if (srcChannels == 1 && dstChannels == 2) {
      val out = ByteArray(pcm.size * 2)
      var i = 0
      var j = 0
      while (i + 1 < pcm.size) {
        val lo = pcm[i]
        val hi = pcm[i + 1]
        out[j] = lo
        out[j + 1] = hi
        out[j + 2] = lo
        out[j + 3] = hi
        i += 2
        j += 4
      }
      return out
    }
    if (srcChannels == 2 && dstChannels == 1) {
      val out = ByteArray(pcm.size / 2)
      var i = 0
      var j = 0
      while (i + 3 < pcm.size) {
        val left = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
        val right = (pcm[i + 2].toInt() and 0xFF) or (pcm[i + 3].toInt() shl 8)
        val mono = ((left.toShort().toInt() + right.toShort().toInt()) / 2).toShort()
        out[j] = (mono.toInt() and 0xFF).toByte()
        out[j + 1] = ((mono.toInt() shr 8) and 0xFF).toByte()
        i += 4
        j += 2
      }
      return out
    }
    return pcm
  }

  private fun createAacFormat(sampleRate: Int, channelCount: Int): MediaFormat {
    return MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
      setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
      setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
      setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)
    }
  }

  private fun muxVideoAndAudio(
    slides: List<TimedSlideSource>,
    recycleProducedBitmaps: Boolean,
    width: Int,
    height: Int,
    durationUs: Long,
    backgroundVideoFile: File?,
    backgroundVideoScale: Float,
    aacFormat: MediaFormat,
    aacFrames: List<Pair<ByteArray, MediaCodec.BufferInfo>>,
    outFile: File,
    profile: VideoExportProfile,
    preferredCodecName: String?,
    maxHighProfileLevel: Int?,
    onProgress: ((VideoExportProgress) -> Unit)? = null
  ) {
    val hasVideoBg = backgroundVideoFile != null
    val fps = profile.fps
    val bitRate = profile.bitRate
    val frameTimestampsUs = buildFrameTimestampsUs(durationUs, fps, slides, hasVideoBg)
    val totalFrames = frameTimestampsUs.size

    fun createAndConfigureEncoder(useHighProfile: Boolean): MediaCodec {
      val codec = preferredCodecName?.let {
        runCatching { MediaCodec.createByCodecName(it) }.getOrNull()
      } ?: MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
      val targetHighLevel = selectHighProfileLevel(width, height, maxHighProfileLevel)
      val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          // Non-realtime offline priority lets hardware encoders allocate full quality lookahead.
          setInteger(MediaFormat.KEY_PRIORITY, 1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && maxOf(width, height) >= 720) {
          setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
          setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
          setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
        }
        if (useHighProfile && targetHighLevel != null) {
          setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
          setInteger(MediaFormat.KEY_LEVEL, targetHighLevel)
        }
        val supportsVbr = runCatching {
          codec.codecInfo
            .getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            .encoderCapabilities
            ?.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR) == true
        }.getOrDefault(false)
        if (supportsVbr) {
          setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }
      }
      try {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return codec
      } catch (t: Throwable) {
        runCatching { codec.release() }
        throw t
      }
    }

    val videoEncoder = if (profile.preferHighProfile && maxHighProfileLevel != null) {
      try {
        createAndConfigureEncoder(useHighProfile = true)
      } catch (t: Throwable) {
        Timber.w(t, "High profile encoder configure failed; falling back to default AVC profile")
        createAndConfigureEncoder(useHighProfile = false)
      }
    } else {
      createAndConfigureEncoder(useHighProfile = false)
    }
    val inputSurface = videoEncoder.createInputSurface()
    videoEncoder.start()

    val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val eglRenderer = EglCompositorSurfaceRenderer(inputSurface, hasVideoBg)
    val videoBgDecoder = if (backgroundVideoFile != null) {
      LoopingVideoDecoder(
        videoFile = backgroundVideoFile,
        outputSurface = eglRenderer.createBackgroundSurface(),
        surfaceTexture = eglRenderer.backgroundSurfaceTexture!!,
        outputWidth = width,
        outputHeight = height,
        scaleZoom = backgroundVideoScale,
        frameLock = eglRenderer.bgFrameLock,
        consumeFrameAvailable = { eglRenderer.consumeBgFrameAvailable() }
      )
    } else null

    var videoTrack = -1
    var audioTrack = -1
    var muxerStarted = false
    var nextVideoPtsUs = 0L

    val bufInfo = MediaCodec.BufferInfo()
    var lastVideoPct = 0
    var activeSlideIdx = -1

    fun drainEncoder(waitForEos: Boolean) {
      val timeoutUs = if (waitForEos) 10_000L else 0L
      while (true) {
        val idx = videoEncoder.dequeueOutputBuffer(bufInfo, timeoutUs)
        if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
          if (waitForEos) continue else break
        } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          if (!muxerStarted) {
            videoTrack = muxer.addTrack(videoEncoder.outputFormat)
            audioTrack = muxer.addTrack(aacFormat)
            muxer.start()
            muxerStarted = true
          }
        } else if (idx >= 0) {
          val isEos = (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
          val isCodecConfig = (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
          val outBuf = videoEncoder.getOutputBuffer(idx)!!
          if (!isCodecConfig && !isEos && bufInfo.size > 0 && muxerStarted) {
            val pts = maxOf(nextVideoPtsUs, bufInfo.presentationTimeUs)
            nextVideoPtsUs = pts + 1L
            bufInfo.presentationTimeUs = pts
            outBuf.position(bufInfo.offset)
            outBuf.limit(bufInfo.offset + bufInfo.size)
            muxer.writeSampleData(videoTrack, outBuf, bufInfo)
          }
          videoEncoder.releaseOutputBuffer(idx, false)
          if (isEos) break
        }
      }
    }

    try {
      for (frameIdx in 0 until totalFrames) {
        val ptsUs = frameTimestampsUs[frameIdx]
        val ptsMs = ptsUs / 1000L
        val targetSlideIdx = findSlideIndexForTime(slides, ptsMs)
        if (targetSlideIdx != activeSlideIdx) {
          activeSlideIdx = targetSlideIdx
          val rawBmp = slides[targetSlideIdx].renderBitmap(width, height)
          val uploadBmp = if (rawBmp.width == width && rawBmp.height == height) {
            rawBmp
          } else {
            Bitmap.createScaledBitmap(rawBmp, width, height, true)
          }
          try {
            eglRenderer.uploadOverlayBitmap(uploadBmp)
          } finally {
            if (uploadBmp !== rawBmp) uploadBmp.recycle()
            if (recycleProducedBitmaps) rawBmp.recycle()
          }
        }

        if (videoBgDecoder != null) {
          videoBgDecoder.advanceTo(ptsUs)
          eglRenderer.renderCompositeFrame(
            width = width,
            height = height,
            ptsUs = ptsUs,
            stMatrix = videoBgDecoder.stMatrix,
            cropScale = videoBgDecoder.cropScale
          )
        } else {
          eglRenderer.renderOverlayOnlyFrame(width, height, ptsUs)
        }

        val pct = (((frameIdx + 1) * 90) / totalFrames).coerceIn(0, 90)
        if (pct != lastVideoPct) {
          lastVideoPct = pct
          onProgress?.invoke(VideoExportProgress(context.getString(R.string.share_creating_video), pct))
        }
        drainEncoder(waitForEos = false)
      }

      onProgress?.invoke(VideoExportProgress(context.getString(R.string.share_creating_video), 95))
      videoEncoder.signalEndOfInputStream()
      drainEncoder(waitForEos = true)

      if (muxerStarted && audioTrack >= 0) {
        for ((bytes, info) in aacFrames) {
          val buf = ByteBuffer.wrap(bytes)
          muxer.writeSampleData(audioTrack, buf, info)
        }
      }
      onProgress?.invoke(VideoExportProgress(context.getString(R.string.share_creating_video), 100))
    } finally {
      runCatching { videoBgDecoder?.release() }
      runCatching { eglRenderer.release() }
      runCatching { videoEncoder.stop() }
      runCatching { videoEncoder.release() }
      runCatching { inputSurface.release() }
      if (muxerStarted) {
        runCatching { muxer.stop() }
      }
      runCatching { muxer.release() }
    }
  }

  private fun findSlideIndexForTime(slides: List<TimedSlideSource>, ptsMs: Long): Int {
    for (i in slides.indices.reversed()) {
      if (ptsMs >= slides[i].startMs) return i
    }
    return 0
  }

  private fun buildFrameTimestampsUs(
    durationUs: Long,
    fps: Int,
    slides: List<TimedSlideSource>,
    hasVideoBg: Boolean
  ): List<Long> {
    if (hasVideoBg || slides.size <= 1) {
      val totalFrames = maxOf(2, ((durationUs * fps + 999_999L) / 1_000_000L).toInt())
      return (0 until totalFrames).map { (it.toLong() * 1_000_000L) / fps }
    }

    val stepUs = 1_000_000L / fps
    val cueSet = sortedSetOf<Long>()
    for (slide in slides) {
      val boundaryUs = slide.startMs * 1000L
      if (boundaryUs in 0L..durationUs) cueSet.add(boundaryUs)
    }
    cueSet.add(0L)

    val merged = sortedSetOf<Long>()
    merged.addAll(cueSet)
    var t = 0L
    while (t < durationUs) {
      val nearCue = cueSet.any { kotlin.math.abs(it - t) < 20_000L }
      if (!nearCue) merged.add(t)
      t += stepUs
    }
    if (merged.size < 2) merged.add(durationUs)
    return merged.toList()
  }

  /**
   * Decodes a looping muted MP4 background clip onto a [SurfaceTexture] for GL compositing.
   */
  private class LoopingVideoDecoder(
    videoFile: File,
    private val outputSurface: Surface,
    private val surfaceTexture: SurfaceTexture,
    outputWidth: Int,
    outputHeight: Int,
    scaleZoom: Float,
    private val frameLock: Object,
    private val consumeFrameAvailable: () -> Boolean
  ) {
    private val extractor = MediaExtractor()
    private val decoder: MediaCodec
    private val decInfo = MediaCodec.BufferInfo()
    val stMatrix = FloatArray(16)
    val cropScale: FloatArray

    private var inputEos = false
    private var loopBasePtsUs = 0L
    private var lastFramePtsUs = 0L
    private var currentUnwrappedPtsUs = -1L

    init {
      extractor.setDataSource(videoFile.absolutePath)
      var trackIdx = -1
      var trackFormat: MediaFormat? = null
      for (i in 0 until extractor.trackCount) {
        val fmt = extractor.getTrackFormat(i)
        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("video/")) {
          trackIdx = i
          trackFormat = fmt
          break
        }
      }
      check(trackIdx >= 0 && trackFormat != null) {
        "No video track found in ${videoFile.name}"
      }
      extractor.selectTrack(trackIdx)

      val rawW = trackFormat.getInteger(MediaFormat.KEY_WIDTH)
      val rawH = trackFormat.getInteger(MediaFormat.KEY_HEIGHT)
      val rotation = if (Build.VERSION.SDK_INT >= 23 && trackFormat.containsKey(MediaFormat.KEY_ROTATION)) {
        trackFormat.getInteger(MediaFormat.KEY_ROTATION)
      } else {
        0
      }
      val displayW = if (rotation % 180 != 0) rawH else rawW
      val displayH = if (rotation % 180 != 0) rawW else rawH

      val vidAspect = displayW.toFloat() / displayH.coerceAtLeast(1)
      val outAspect = outputWidth.toFloat() / outputHeight.coerceAtLeast(1)
      val zoom = maxOf(1f, scaleZoom)
      val cropW = if (vidAspect > outAspect) (outAspect / vidAspect) / zoom else 1f / zoom
      val cropH = if (vidAspect > outAspect) 1f / zoom else (vidAspect / outAspect) / zoom
      cropScale = floatArrayOf(cropW.coerceIn(0.05f, 1f), cropH.coerceIn(0.05f, 1f))

      val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
      decoder = MediaCodec.createDecoderByType(mime).apply {
        configure(trackFormat, outputSurface, null, 0)
        start()
      }
    }

    fun advanceTo(targetPtsUs: Long) {
      var iterations = 0
      while (currentUnwrappedPtsUs < targetPtsUs && iterations < 6000) {
        iterations++
        if (!inputEos) {
          val inIdx = decoder.dequeueInputBuffer(5_000)
          if (inIdx >= 0) {
            val buf = decoder.getInputBuffer(inIdx)!!
            val size = extractor.readSampleData(buf, 0)
            val sampleTime = extractor.sampleTime
            if (size < 0) {
              decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputEos = true
            } else {
              decoder.queueInputBuffer(inIdx, 0, size, sampleTime, 0)
              extractor.advance()
            }
          }
        }

        val outIdx = decoder.dequeueOutputBuffer(decInfo, 10_000)
        if (outIdx >= 0) {
          val isEos = (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
          if (decInfo.size > 0) {
            lastFramePtsUs = decInfo.presentationTimeUs
            val unwrapped = loopBasePtsUs + lastFramePtsUs
            val shouldRender = unwrapped >= targetPtsUs - 45_000L || currentUnwrappedPtsUs < 0L
            if (shouldRender) {
              synchronized(frameLock) {
                consumeFrameAvailable()
              }
              decoder.releaseOutputBuffer(outIdx, true)
              awaitAndUpdateTexImage()
            } else {
              decoder.releaseOutputBuffer(outIdx, false)
            }
            currentUnwrappedPtsUs = unwrapped
          } else {
            decoder.releaseOutputBuffer(outIdx, false)
          }

          if (isEos) {
            loopBasePtsUs = maxOf(
              loopBasePtsUs + lastFramePtsUs + 33_333L,
              currentUnwrappedPtsUs + 33_333L
            )
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            decoder.flush()
            inputEos = false
            lastFramePtsUs = 0L
          }
        }
      }
    }

    private fun awaitAndUpdateTexImage() {
      synchronized(frameLock) {
        val deadline = System.currentTimeMillis() + 2000L
        while (!consumeFrameAvailable() && System.currentTimeMillis() < deadline) {
          frameLock.wait(200L)
        }
      }
      surfaceTexture.updateTexImage()
      surfaceTexture.getTransformMatrix(stMatrix)
    }

    fun release() {
      runCatching { decoder.stop() }
      runCatching { decoder.release() }
      runCatching { extractor.release() }
      runCatching { outputSurface.release() }
    }
  }

  /**
   * Renders overlay slide textures (with optional OES looping background video beneath) onto a
   * MediaCodec input [Surface] with explicit [EGLExt.eglPresentationTimeANDROID] timestamps.
   */
  private class EglCompositorSurfaceRenderer(
    surface: Surface,
    private val hasVideoBackground: Boolean
  ) {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var overlayProgram = 0
    private var overlayTextureId = 0
    private var overlayPosHandle = 0
    private var overlayTexHandle = 0

    private var oesProgram = 0
    private var oesTextureId = 0
    private var oesPosHandle = 0
    private var oesTexHandle = 0
    private var oesStMatrixHandle = 0
    private var oesCropScaleHandle = 0
    private var bgHandlerThread: HandlerThread? = null
    var backgroundSurfaceTexture: SurfaceTexture? = null
      private set
    val bgFrameLock = Object()
    private var bgFrameAvailable = false

    private val vertexCoords = ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
      put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
      position(0)
    }
    private val texCoords = ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
      put(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
      position(0)
    }

    init {
      eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
      val version = IntArray(2)
      check(eglDisplay != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
        "Unable to initialize EGL14"
      }

      val config = chooseEglConfig(eglDisplay)
      eglContext = EGL14.eglCreateContext(
        eglDisplay,
        config,
        EGL14.EGL_NO_CONTEXT,
        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
        0
      )
      check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }

      eglSurface = EGL14.eglCreateWindowSurface(
        eglDisplay,
        config,
        surface,
        intArrayOf(EGL14.EGL_NONE),
        0
      )
      check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }
      check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
        "Unable to make EGL context current"
      }

      val textures = IntArray(1)
      GLES20.glGenTextures(1, textures, 0)
      overlayTextureId = textures[0]
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

      overlayProgram = createOverlayProgram()
      overlayPosHandle = GLES20.glGetAttribLocation(overlayProgram, "aPosition")
      overlayTexHandle = GLES20.glGetAttribLocation(overlayProgram, "aTexCoord")

      if (hasVideoBackground) {
        val oesTex = IntArray(1)
        GLES20.glGenTextures(1, oesTex, 0)
        oesTextureId = oesTex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val thread = HandlerThread("VideoBgTexThread").apply { start() }
        bgHandlerThread = thread
        val st = SurfaceTexture(oesTextureId)
        st.setOnFrameAvailableListener({ markBgFrameAvailable() }, Handler(thread.looper))
        backgroundSurfaceTexture = st

        oesProgram = createOesProgram()
        oesPosHandle = GLES20.glGetAttribLocation(oesProgram, "aPosition")
        oesTexHandle = GLES20.glGetAttribLocation(oesProgram, "aTexCoord")
        oesStMatrixHandle = GLES20.glGetUniformLocation(oesProgram, "uSTMatrix")
        oesCropScaleHandle = GLES20.glGetUniformLocation(oesProgram, "uCropScale")
      }
    }

    fun createBackgroundSurface(): Surface {
      return Surface(requireNotNull(backgroundSurfaceTexture))
    }

    fun markBgFrameAvailable() {
      synchronized(bgFrameLock) {
        bgFrameAvailable = true
        bgFrameLock.notifyAll()
      }
    }

    fun consumeBgFrameAvailable(): Boolean {
      val was = bgFrameAvailable
      bgFrameAvailable = false
      return was
    }

    fun uploadOverlayBitmap(bitmap: Bitmap) {
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }

    fun renderOverlayOnlyFrame(width: Int, height: Int, ptsUs: Long) {
      GLES20.glViewport(0, 0, width, height)
      GLES20.glDisable(GLES20.GL_BLEND)
      drawOverlayQuad()
      EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsUs * 1000L)
      EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun renderCompositeFrame(
      width: Int,
      height: Int,
      ptsUs: Long,
      stMatrix: FloatArray,
      cropScale: FloatArray
    ) {
      GLES20.glViewport(0, 0, width, height)
      GLES20.glClearColor(0f, 0f, 0f, 1f)
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

      // 1. Opaque moving background video
      GLES20.glDisable(GLES20.GL_BLEND)
      GLES20.glUseProgram(oesProgram)
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
      GLES20.glUniformMatrix4fv(oesStMatrixHandle, 1, false, stMatrix, 0)
      GLES20.glUniform2f(oesCropScaleHandle, cropScale[0], cropScale[1])

      vertexCoords.position(0)
      GLES20.glEnableVertexAttribArray(oesPosHandle)
      GLES20.glVertexAttribPointer(oesPosHandle, 2, GLES20.GL_FLOAT, false, 0, vertexCoords)

      texCoords.position(0)
      GLES20.glEnableVertexAttribArray(oesTexHandle)
      GLES20.glVertexAttribPointer(oesTexHandle, 2, GLES20.GL_FLOAT, false, 0, texCoords)

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
      GLES20.glDisableVertexAttribArray(oesPosHandle)
      GLES20.glDisableVertexAttribArray(oesTexHandle)

      // 2. Premultiplied-alpha Ayah card scrim + text overlay
      GLES20.glEnable(GLES20.GL_BLEND)
      GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
      drawOverlayQuad()
      GLES20.glDisable(GLES20.GL_BLEND)

      EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsUs * 1000L)
      EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun drawOverlayQuad() {
      GLES20.glUseProgram(overlayProgram)
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)

      vertexCoords.position(0)
      GLES20.glEnableVertexAttribArray(overlayPosHandle)
      GLES20.glVertexAttribPointer(overlayPosHandle, 2, GLES20.GL_FLOAT, false, 0, vertexCoords)

      texCoords.position(0)
      GLES20.glEnableVertexAttribArray(overlayTexHandle)
      GLES20.glVertexAttribPointer(overlayTexHandle, 2, GLES20.GL_FLOAT, false, 0, texCoords)

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

      GLES20.glDisableVertexAttribArray(overlayPosHandle)
      GLES20.glDisableVertexAttribArray(overlayTexHandle)
    }

    fun release() {
      runCatching { backgroundSurfaceTexture?.release() }
      backgroundSurfaceTexture = null
      runCatching { bgHandlerThread?.quitSafely() }
      bgHandlerThread = null

      if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
        if (eglSurface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
          EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
          if (overlayTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(overlayTextureId), 0)
            overlayTextureId = 0
          }
          if (oesTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
            oesTextureId = 0
          }
          if (overlayProgram != 0) {
            GLES20.glDeleteProgram(overlayProgram)
            overlayProgram = 0
          }
          if (oesProgram != 0) {
            GLES20.glDeleteProgram(oesProgram)
            oesProgram = 0
          }
        }
        EGL14.eglMakeCurrent(
          eglDisplay,
          EGL14.EGL_NO_SURFACE,
          EGL14.EGL_NO_SURFACE,
          EGL14.EGL_NO_CONTEXT
        )
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(eglDisplay)
      }
      eglDisplay = EGL14.EGL_NO_DISPLAY
      eglSurface = EGL14.EGL_NO_SURFACE
      eglContext = EGL14.EGL_NO_CONTEXT
    }

    private fun chooseEglConfig(display: EGLDisplay): EGLConfig {
      val eglRecordableAndroid = 0x3142
      val attribsWithRecordable = intArrayOf(
        EGL14.EGL_RED_SIZE, 8,
        EGL14.EGL_GREEN_SIZE, 8,
        EGL14.EGL_BLUE_SIZE, 8,
        EGL14.EGL_ALPHA_SIZE, 8,
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        eglRecordableAndroid, 1,
        EGL14.EGL_NONE
      )
      val configs = arrayOfNulls<EGLConfig>(1)
      val numConfigs = IntArray(1)
      if (EGL14.eglChooseConfig(display, attribsWithRecordable, 0, configs, 0, 1, numConfigs, 0) &&
        numConfigs[0] > 0 && configs[0] != null
      ) {
        return configs[0]!!
      }
      val fallbackAttribs = intArrayOf(
        EGL14.EGL_RED_SIZE, 8,
        EGL14.EGL_GREEN_SIZE, 8,
        EGL14.EGL_BLUE_SIZE, 8,
        EGL14.EGL_ALPHA_SIZE, 8,
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_NONE
      )
      check(
        EGL14.eglChooseConfig(display, fallbackAttribs, 0, configs, 0, 1, numConfigs, 0) &&
          numConfigs[0] > 0 && configs[0] != null
      ) {
        "Unable to choose EGL2 config"
      }
      return configs[0]!!
    }

    private fun createOverlayProgram(): Int {
      return linkProgram(
        """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
          gl_Position = aPosition;
          vTexCoord = aTexCoord;
        }
        """.trimIndent(),
        """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D sTexture;
        void main() {
          gl_FragColor = texture2D(sTexture, vTexCoord);
        }
        """.trimIndent()
      )
    }

    private fun createOesProgram(): Int {
      return linkProgram(
        """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uSTMatrix;
        uniform vec2 uCropScale;
        varying vec2 vTexCoord;
        void main() {
          gl_Position = aPosition;
          vec2 centered = (vec2(aTexCoord.x, 1.0 - aTexCoord.y) - vec2(0.5)) * uCropScale + vec2(0.5);
          vTexCoord = (uSTMatrix * vec4(centered, 0.0, 1.0)).xy;
        }
        """.trimIndent(),
        """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTexCoord;
        uniform samplerExternalOES sVideoTexture;
        void main() {
          gl_FragColor = texture2D(sVideoTexture, vTexCoord);
        }
        """.trimIndent()
      )
    }

    private fun linkProgram(vsSrc: String, fsSrc: String): Int {
      val vs = loadShader(GLES20.GL_VERTEX_SHADER, vsSrc)
      val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
      val prog = GLES20.glCreateProgram()
      GLES20.glAttachShader(prog, vs)
      GLES20.glAttachShader(prog, fs)
      GLES20.glLinkProgram(prog)
      GLES20.glDeleteShader(vs)
      GLES20.glDeleteShader(fs)
      return prog
    }

    private fun loadShader(type: Int, src: String): Int {
      val shader = GLES20.glCreateShader(type)
      GLES20.glShaderSource(shader, src)
      GLES20.glCompileShader(shader)
      return shader
    }
  }

  internal data class VideoExportProfile(
    val maxWidth: Int,
    val fps: Int,
    val bitRate: Int,
    val preferHighProfile: Boolean,
    val widthAlignment: Int = 2,
    val heightAlignment: Int = 2
  )

  internal data class AvcEncoderCapabilities(
    val codecName: String?,
    val widthAlignment: Int,
    val heightAlignment: Int,
    val supports1080p30: Boolean,
    val supports720p30: Boolean,
    val maxHighProfileLevel: Int?
  )

  companion object {
    internal const val WHATSAPP_SLOW_SAFE_MAX_BYTES = 55_000_000L
    private const val AUDIO_AND_MUX_OVERHEAD_BPS = 160_000L
    private const val MIN_VIDEO_BITRATE_BPS = 1_500_000L
    private const val MIN_1080P_VIDEO_BITRATE_BPS = 3_600_000L
    internal const val HIGH_TIER_MIN_RAM_BYTES = 5_500_000_000L
    internal const val MID_TIER_MIN_RAM_BYTES = 3_400_000_000L

    /**
     * Resolves gapless ayah `[startMs, endMs]` pairs from DB timing rows while preserving the
     * surah's opening Bismillah (`ayah = 0` or `0 ms`) whenever starting at Ayah 1 (except
     * Al-Fatihah 1 and At-Tawbah 9).
     */
    internal fun resolveGaplessAyahBounds(
      sura: Int,
      startAyah: Int,
      endAyah: Int,
      rawRows: List<Pair<Int, Long>>
    ): List<Pair<Int, Pair<Long, Long>>>? {
      if (rawRows.isEmpty()) return null
      val sorted = rawRows.sortedWith(compareBy<Pair<Int, Long>> { it.first }.thenBy { it.second })
      val includesLeadingBasmala = startAyah == 1 && sura != 1 && sura != 9

      val result = mutableListOf<Pair<Int, Pair<Long, Long>>>()
      for (ayahNum in startAyah..endAyah) {
        val row = sorted.firstOrNull { it.first == ayahNum } ?: continue
        val verseStart = row.second
        val ayahStart = if (ayahNum == 1 && includesLeadingBasmala) {
          sorted.firstOrNull { it.first == 0 }?.second?.coerceAtMost(verseStart) ?: 0L
        } else {
          verseStart
        }
        val ayahEnd = sorted.firstOrNull { it.first > ayahNum }?.second
          ?.takeIf { it > verseStart } ?: Long.MAX_VALUE
        result.add(ayahNum to (ayahStart to ayahEnd))
      }
      return result.takeIf { it.isNotEmpty() }
    }

    internal fun alignVideoDimensions(
      baseWidth: Int,
      baseHeight: Int,
      maxWidth: Int,
      widthAlignment: Int = 2,
      heightAlignment: Int = 2
    ): Pair<Int, Int> {
      val wAlign = widthAlignment.coerceIn(2, 16)
      val hAlign = heightAlignment.coerceIn(2, 16)
      val clampedW = minOf(baseWidth, maxWidth).coerceAtLeast(wAlign)
      val targetW = ((clampedW / wAlign) * wAlign).coerceAtLeast(wAlign)
      val aspectHeight = (baseHeight.toFloat() / baseWidth.coerceAtLeast(1) * targetW).toInt()
      val targetH = (((aspectHeight + hAlign / 2) / hAlign) * hAlign).coerceAtLeast(hAlign)
      return targetW to targetH
    }

    internal fun selectHighProfileLevel(
      width: Int,
      height: Int,
      maxSupportedLevel: Int?
    ): Int? {
      val maxLevel = maxSupportedLevel ?: return null
      val ceiling = if (maxOf(width, height) > 1280) {
        MediaCodecInfo.CodecProfileLevel.AVCLevel42
      } else {
        MediaCodecInfo.CodecProfileLevel.AVCLevel4
      }
      return minOf(maxLevel, ceiling)
    }

    internal fun probeAvcEncoderCapabilities(baseWidth: Int, baseHeight: Int): AvcEncoderCapabilities {
      return try {
        val encoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
          info.isEncoder && info.supportedTypes.any {
            it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)
          }
        }
        fun isSoftware(info: MediaCodecInfo): Boolean {
          return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isSoftwareOnly
          } else {
            info.name.startsWith("OMX.google.", ignoreCase = true) ||
              info.name.startsWith("c2.android.", ignoreCase = true)
          }
        }
        fun isSecondaryVariant(info: MediaCodecInfo): Boolean {
          val lower = info.name.lowercase(Locale.US)
          return lower.contains("low_latency") || lower.endsWith(".cq")
        }
        val defaultEncoderName = runCatching {
          val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
          try {
            codec.name
          } finally {
            runCatching { codec.release() }
          }
        }.getOrNull()
        val selected = encoders.firstOrNull {
          !isSoftware(it) && !isSecondaryVariant(it) && it.name == defaultEncoderName
        } ?: encoders.firstOrNull { !isSoftware(it) && !isSecondaryVariant(it) }
          ?: encoders.firstOrNull { !isSoftware(it) }
          ?: encoders.firstOrNull()
        val caps = selected?.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val vc = caps?.videoCapabilities
        val wAlign = (vc?.widthAlignment ?: 2).coerceIn(2, 16)
        val hAlign = (vc?.heightAlignment ?: 2).coerceIn(2, 16)
        val (w1080, h1080) = alignVideoDimensions(baseWidth, baseHeight, 1080, wAlign, hAlign)
        val (w720, h720) = alignVideoDimensions(baseWidth, baseHeight, 720, wAlign, hAlign)
        val supports1080p30 = vc?.let {
          it.areSizeAndRateSupported(w1080, h1080, 30.0) ||
            it.areSizeAndRateSupported(h1080, w1080, 30.0)
        } == true
        val supports720p30 = vc?.let {
          it.areSizeAndRateSupported(w720, h720, 30.0) ||
            it.areSizeAndRateSupported(h720, w720, 30.0)
        } == true
        val maxHighLevel = caps?.profileLevels
          ?.filter { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh }
          ?.maxOfOrNull { it.level }
        AvcEncoderCapabilities(
          codecName = selected?.name,
          widthAlignment = wAlign,
          heightAlignment = hAlign,
          supports1080p30 = supports1080p30,
          supports720p30 = supports720p30,
          maxHighProfileLevel = maxHighLevel
        )
      } catch (e: Exception) {
        Timber.w(e, "Unable to probe AVC encoder capabilities; using safe baseline")
        AvcEncoderCapabilities(
          codecName = null,
          widthAlignment = 2,
          heightAlignment = 2,
          supports1080p30 = false,
          supports720p30 = false,
          maxHighProfileLevel = null
        )
      }
    }

    internal fun buildExportProfileChain(
      hasVideoBg: Boolean,
      isMultiSlide: Boolean,
      durationUs: Long,
      totalRamBytes: Long,
      isLowRamDevice: Boolean,
      sdkInt: Int,
      capabilities: AvcEncoderCapabilities,
      requestedQuality: AyahVideoExportQuality? = null
    ): List<VideoExportProfile> {
      val durationSec = maxOf(1.0, durationUs / 1_000_000.0)
      val totalBudgetBps = ((WHATSAPP_SLOW_SAFE_MAX_BYTES * 8.0) / durationSec).toLong()
      val videoBudgetBps = maxOf(MIN_VIDEO_BITRATE_BPS, totalBudgetBps - AUDIO_AND_MUX_OVERHEAD_BPS)
      val preferHigh = capabilities.maxHighProfileLevel != null

      if (!hasVideoBg) {
        val staticFps = 12
        val static1080 = VideoExportProfile(
          maxWidth = 1080,
          fps = staticFps,
          bitRate = minOf(6_000_000L, videoBudgetBps).toInt(),
          preferHighProfile = preferHigh,
          widthAlignment = capabilities.widthAlignment,
          heightAlignment = capabilities.heightAlignment
        )
        val static720 = VideoExportProfile(
          maxWidth = 720,
          fps = staticFps,
          bitRate = minOf(4_000_000L, videoBudgetBps).toInt(),
          preferHighProfile = preferHigh,
          widthAlignment = capabilities.widthAlignment,
          heightAlignment = capabilities.heightAlignment
        )
        return if (requestedQuality == AyahVideoExportQuality.STANDARD_720P) {
          listOf(static720)
        } else {
          listOf(static1080, static720)
        }
      }

      val chain = mutableListOf<VideoExportProfile>()
      val ramAllows1080p = if (requestedQuality == AyahVideoExportQuality.HIGH_1080P) {
        !isLowRamDevice
      } else {
        !isLowRamDevice && totalRamBytes >= HIGH_TIER_MIN_RAM_BYTES
      }
      val allow1080p = requestedQuality != AyahVideoExportQuality.STANDARD_720P &&
        ramAllows1080p &&
        sdkInt >= Build.VERSION_CODES.Q &&
        capabilities.supports1080p30 &&
        videoBudgetBps >= MIN_1080P_VIDEO_BITRATE_BPS

      if (allow1080p) {
        chain += VideoExportProfile(
          maxWidth = 1080,
          fps = 30,
          bitRate = minOf(6_200_000L, videoBudgetBps).toInt(),
          preferHighProfile = preferHigh,
          widthAlignment = capabilities.widthAlignment,
          heightAlignment = capabilities.heightAlignment
        )
      }

      if (!isLowRamDevice && totalRamBytes >= MID_TIER_MIN_RAM_BYTES && capabilities.supports720p30) {
        chain += VideoExportProfile(
          maxWidth = 720,
          fps = 30,
          bitRate = minOf(4_500_000L, videoBudgetBps).toInt(),
          preferHighProfile = preferHigh,
          widthAlignment = capabilities.widthAlignment,
          heightAlignment = capabilities.heightAlignment
        )
      }

      chain += VideoExportProfile(
        maxWidth = 720,
        fps = 24,
        bitRate = minOf(3_200_000L, videoBudgetBps).toInt(),
        preferHighProfile = false,
        widthAlignment = capabilities.widthAlignment,
        heightAlignment = capabilities.heightAlignment
      )

      return chain.distinctBy { Triple(it.maxWidth, it.fps, it.preferHighProfile) }
    }
  }
}
