package com.quran.labs.androidquran.ui.shareimage

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import androidx.preference.PreferenceManager
import com.quran.labs.androidquran.QuranApplication
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

  data class AudioSegment(
    val file: File,
    val startMs: Long,
    val endMs: Long,
    val qariName: String
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
    onProgress: ((VideoExportProgress) -> Unit)? = null
  ): AudioSegment {
    val qaris = audioUtils.getQariList(context)
    val targetId = qariId ?: getDefaultQariId()
    val qari = qaris.firstOrNull { it.id == targetId } ?: qaris.firstOrNull()
      ?: throw IllegalStateException("No reciters available")

    val localDirPath = audioUtils.getLocalQariUrl(qari)
      ?: context.cacheDir.resolve("share_audio/${qari.path}").absolutePath + File.separator
    val localDir = File(localDirPath).apply { mkdirs() }

    if (qari.isGapless) {
      val dbPath = audioUtils.getQariDatabasePathIfGapless(qari)
        ?: File(localDir, "${qari.databaseName}.db").absolutePath
      val dbFile = File(dbPath)
      if (!dbFile.exists() || dbFile.length() == 0L) {
        onProgress?.invoke(VideoExportProgress("Downloading timings..."))
        val dbName = qari.databaseName
          ?: throw IllegalStateException("Missing timing database for ${qari.name}")
        val zipUrl = quranFileUtils.gaplessDatabaseRootUrl + dbName + AudioUtils.ZIP_EXTENSION
        val zipTmp = File(localDir, "$dbName.zip.tmp")
        downloadUrlToFile(zipUrl, zipTmp) { downloaded, total ->
          onProgress?.invoke(formatDownloadProgress("Downloading timings", downloaded, total))
        } ?: throw IllegalStateException("Failed to download timing database for ${qari.name}")
        unzipDbFile(zipTmp, localDir)
        zipTmp.delete()
        SuraTimingDatabaseHandler.clearDatabaseHandlerIfExists(dbPath)
      }

      val suraFileName = String.format(Locale.US, "%03d%s", sura, ".mp3")
      val suraFile = File(localDir, suraFileName)
      if (!suraFile.exists() || suraFile.length() == 0L) {
        onProgress?.invoke(VideoExportProgress("Downloading surah...", 0))
        val suraUrl = String.format(Locale.US, audioUtils.getQariUrl(qari), sura)
        downloadUrlToFile(suraUrl, suraFile) { downloaded, total ->
          onProgress?.invoke(formatDownloadProgress("Downloading surah", downloaded, total))
        } ?: throw IllegalStateException("Failed to download surah audio for ${qari.name}")
      }

      val targetEnd = maxOf(startAyah, endAyah)
      val timings = queryAyahBounds(dbPath, sura, startAyah, targetEnd)
        ?: throw IllegalStateException("Timings unavailable for $sura:$startAyah")

      return AudioSegment(suraFile, timings.first, timings.second, qari.name)
    } else {
      val ayahFileName = String.format(Locale.US, "%03d%03d.mp3", sura, startAyah)
      val ayahFile = File(localDir, ayahFileName)
      if (!ayahFile.exists() || ayahFile.length() == 0L) {
        onProgress?.invoke(VideoExportProgress("Downloading audio...", 0))
        val ayahUrl = String.format(Locale.US, audioUtils.getQariUrl(qari), sura, startAyah)
        downloadUrlToFile(ayahUrl, ayahFile) { downloaded, total ->
          onProgress?.invoke(formatDownloadProgress("Downloading audio", downloaded, total))
        } ?: throw IllegalStateException("Failed to download ayah audio for ${qari.name}")
      }
      return AudioSegment(ayahFile, 0L, Long.MAX_VALUE, qari.name)
    }
  }

  private fun formatDownloadProgress(
    prefix: String,
    downloadedBytes: Long,
    totalBytes: Long
  ): VideoExportProgress {
    return if (totalBytes > 0L) {
      val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
      val label = if (totalBytes >= 1_048_576L) {
        val dlMb = downloadedBytes / (1024f * 1024f)
        val totMb = totalBytes / (1024f * 1024f)
        String.format(Locale.US, "$prefix (%.1f / %.1f MB)...", dlMb, totMb)
      } else {
        "$prefix..."
      }
      VideoExportProgress(label, pct)
    } else {
      val mb = downloadedBytes / (1024f * 1024f)
      val label = if (mb >= 0.1f) {
        String.format(Locale.US, "$prefix (%.1f MB)...", mb)
      } else {
        "$prefix (${downloadedBytes / 1024L} KB)..."
      }
      VideoExportProgress(label, null)
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

  private fun queryAyahBounds(
    dbPath: String,
    sura: Int,
    startAyah: Int,
    endAyah: Int
  ): Pair<Long, Long>? {
    val handler = SuraTimingDatabaseHandler.getDatabaseHandler(dbPath)
    val cursor = handler.getAyahTimings(sura) ?: return null
    var startMs = -1L
    var endMs = Long.MAX_VALUE
    cursor.use { c ->
      while (c.moveToNext()) {
        val rowAyah = c.getInt(1)
        val rowTime = c.getLong(2)
        if (rowAyah == startAyah && startMs < 0) {
          startMs = rowTime
        } else if (startMs >= 0 && rowAyah > endAyah) {
          endMs = rowTime
          break
        }
      }
    }
    return if (startMs >= 0) startMs to endMs else null
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
    val shareDir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
    val outFile = File(shareDir, "Quran_Ayah_${System.currentTimeMillis()}.mp4")

    onProgress?.invoke(VideoExportProgress("Encoding audio...", 0))
    val (aacFormat, aacFrames, durationUs) = transcodeAudioToAac(segment, onProgress)
    val safeDurationUs = maxOf(durationUs, 1_000_000L)

    onProgress?.invoke(VideoExportProgress("Creating video...", 0))
    val targetWidth = minOf(cardBitmap.width, 1080).let { if (it % 2 == 0) it else it - 1 }
    val rawHeight = (cardBitmap.height.toFloat() / cardBitmap.width * targetWidth).toInt()
    val targetHeight = if (rawHeight % 2 == 0) rawHeight else rawHeight + 1
    val scaledBitmap = if (cardBitmap.width == targetWidth && cardBitmap.height == targetHeight) {
      cardBitmap
    } else {
      Bitmap.createScaledBitmap(cardBitmap, targetWidth, targetHeight, true)
    }

    try {
      muxVideoAndAudio(
        scaledBitmap,
        targetWidth,
        targetHeight,
        safeDurationUs,
        aacFormat,
        aacFrames,
        outFile,
        onProgress
      )
    } finally {
      if (scaledBitmap !== cardBitmap) {
        scaledBitmap.recycle()
      }
    }
    return outFile
  }

  private data class EncodedAudio(
    val format: MediaFormat,
    val frames: List<Pair<ByteArray, MediaCodec.BufferInfo>>,
    val durationUs: Long
  )

  private fun transcodeAudioToAac(
    segment: AudioSegment,
    onProgress: ((VideoExportProgress) -> Unit)? = null
  ): EncodedAudio {
    val extractor = MediaExtractor()
    extractor.setDataSource(segment.file.absolutePath)
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
      extractor.release()
      throw IllegalStateException("No audio track found in ${segment.file.name}")
    }

    extractor.selectTrack(audioTrackIndex)
    val startUs = segment.startMs * 1000L
    val endUs = if (segment.endMs == Long.MAX_VALUE) Long.MAX_VALUE else segment.endMs * 1000L
    val fileDurationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
      inputFormat.getLong(MediaFormat.KEY_DURATION)
    } else -1L
    val expectedSpanUs = when {
      endUs != Long.MAX_VALUE && endUs > startUs -> endUs - startUs
      fileDurationUs > startUs -> fileDurationUs - startUs
      else -> -1L
    }
    if (startUs > 0) {
      extractor.seekTo(maxOf(0L, startUs - 500_000L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    }

    val inputMime = inputFormat.getString(MediaFormat.KEY_MIME)!!
    var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
      inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    } else 44100
    var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
      inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    } else 1

    val decoder = MediaCodec.createDecoderByType(inputMime)
    decoder.configure(inputFormat, null, null, 0)
    decoder.start()

    var encoder: MediaCodec? = null
    var aacFormatReq = createAacFormat(sampleRate, channelCount)
    val frames = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
    var outputFormat: MediaFormat? = null
    var lastPtsUs = 0L
    var lastAudioPct = 0

    val decInfo = MediaCodec.BufferInfo()
    val encInfo = MediaCodec.BufferInfo()
    var extractorDone = false
    var decoderDone = false
    var encoderEosSent = false
    var encoderDone = false
    var firstKeptPtsUs = -1L

    var pendingPcm: ByteArray? = null
    var pendingOffset = 0
    var pendingPtsUs = 0L

    try {
      while (!encoderDone) {
        // 1. Feed extractor -> decoder
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

        // 2. Drain decoder -> pendingPcm (only if no pending PCM left)
        if (!decoderDone && pendingPcm == null) {
          val outIdx = decoder.dequeueOutputBuffer(decInfo, 5_000)
          when {
            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
              val decFmt = decoder.outputFormat
              if (decFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                sampleRate = decFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
              }
              if (decFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                channelCount = decFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
              }
            }
            outIdx >= 0 -> {
              val isEos = (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
              val keep = decInfo.size > 0 &&
                decInfo.presentationTimeUs >= startUs &&
                (endUs == Long.MAX_VALUE || decInfo.presentationTimeUs <= endUs)

              if (keep) {
                if (firstKeptPtsUs < 0) firstKeptPtsUs = decInfo.presentationTimeUs
                val outBuf = decoder.getOutputBuffer(outIdx)!!
                val bytes = ByteArray(decInfo.size)
                outBuf.position(decInfo.offset)
                outBuf.get(bytes)
                pendingPcm = bytes
                pendingOffset = 0
                pendingPtsUs = maxOf(0L, decInfo.presentationTimeUs - firstKeptPtsUs)

                if (expectedSpanUs > 0L && onProgress != null) {
                  val pct = ((pendingPtsUs * 100L) / expectedSpanUs).toInt().coerceIn(0, 99)
                  if (pct != lastAudioPct) {
                    lastAudioPct = pct
                    onProgress(VideoExportProgress("Encoding audio...", pct))
                  }
                }

                if (encoder == null) {
                  aacFormatReq = createAacFormat(sampleRate, channelCount)
                  encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                    configure(aacFormatReq, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
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

        // 3. Feed pendingPcm -> encoder (or EOS if decoder is done & nothing pending)
        val activeEncoder = encoder
        if (activeEncoder != null) {
          if (pendingPcm != null) {
            val encInIdx = activeEncoder.dequeueInputBuffer(0)
            if (encInIdx >= 0) {
              val encInBuf = activeEncoder.getInputBuffer(encInIdx)!!
              val remaining = pendingPcm.size - pendingOffset
              val toCopy = minOf(remaining, encInBuf.remaining())
              encInBuf.put(pendingPcm, pendingOffset, toCopy)
              activeEncoder.queueInputBuffer(encInIdx, 0, toCopy, pendingPtsUs, 0)
              pendingOffset += toCopy
              if (pendingOffset >= pendingPcm.size) {
                pendingPcm = null
                pendingOffset = 0
              }
            }
          } else if (decoderDone && !encoderEosSent) {
            val encInIdx = activeEncoder.dequeueInputBuffer(5_000)
            if (encInIdx >= 0) {
              activeEncoder.queueInputBuffer(encInIdx, 0, 0, lastPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              encoderEosSent = true
            }
          }

          // 4. Drain ALL available output from encoder on every tick
          while (true) {
            val encOutIdx = activeEncoder.dequeueOutputBuffer(encInfo, 0)
            if (encOutIdx == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
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
              if (isEos) {
                encoderDone = true
                break
              }
            }
          }
        } else if (decoderDone) {
          // No PCM frames matched [startUs, endUs]; bail safely
          break
        }
      }
    } finally {
      decoder.stop()
      decoder.release()
      encoder?.stop()
      encoder?.release()
      extractor.release()
    }

    if (frames.isEmpty()) {
      throw IllegalStateException("Audio segment contained no playable frames ($startUs..$endUs)")
    }

    onProgress?.invoke(VideoExportProgress("Encoding audio...", 100))

    return EncodedAudio(
      format = outputFormat ?: aacFormatReq,
      frames = frames,
      durationUs = lastPtsUs + 23_220L
    )
  }

  private fun createAacFormat(sampleRate: Int, channelCount: Int): MediaFormat {
    return MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
      setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
      setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
      setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)
    }
  }

  private fun muxVideoAndAudio(
    bitmap: Bitmap,
    width: Int,
    height: Int,
    durationUs: Long,
    aacFormat: MediaFormat,
    aacFrames: List<Pair<ByteArray, MediaCodec.BufferInfo>>,
    outFile: File,
    onProgress: ((VideoExportProgress) -> Unit)? = null
  ) {
    val fps = 2
    val frameStepUs = 1_000_000L / fps
    val totalFrames = maxOf(2, ((durationUs + frameStepUs - 1) / frameStepUs).toInt())

    val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    val videoFormatReq = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
      setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
      setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
      setInteger(MediaFormat.KEY_FRAME_RATE, fps)
      setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
      val supportsVbr = runCatching {
        videoEncoder.codecInfo
          .getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
          .encoderCapabilities
          ?.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR) == true
      }.getOrDefault(false)
      if (supportsVbr) {
        setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
      }
    }

    videoEncoder.configure(videoFormatReq, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    val inputSurface = videoEncoder.createInputSurface()
    videoEncoder.start()

    val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val eglRenderer = EglBitmapSurfaceRenderer(inputSurface, bitmap)
    var videoTrack = -1
    var audioTrack = -1
    var muxerStarted = false
    var nextVideoPtsUs = 0L

    val bufInfo = MediaCodec.BufferInfo()
    var lastVideoPct = 0

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
        val ptsUs = frameIdx * frameStepUs
        eglRenderer.renderFrame(width, height, ptsUs)
        val pct = (((frameIdx + 1) * 90) / totalFrames).coerceIn(0, 90)
        if (pct != lastVideoPct) {
          lastVideoPct = pct
          onProgress?.invoke(VideoExportProgress("Creating video...", pct))
        }
        drainEncoder(waitForEos = false)
      }

      onProgress?.invoke(VideoExportProgress("Creating video...", 95))
      videoEncoder.signalEndOfInputStream()
      drainEncoder(waitForEos = true)

      if (muxerStarted && audioTrack >= 0) {
        for ((bytes, info) in aacFrames) {
          val buf = ByteBuffer.wrap(bytes)
          muxer.writeSampleData(audioTrack, buf, info)
        }
      }
      onProgress?.invoke(VideoExportProgress("Creating video...", 100))
    } finally {
      eglRenderer.release()
      videoEncoder.stop()
      videoEncoder.release()
      inputSurface.release()
      if (muxerStarted) {
        muxer.stop()
      }
      muxer.release()
    }
  }

  /**
   * Renders a single uploaded GL texture onto a MediaCodec input [Surface] with explicit
   * [EGLExt.eglPresentationTimeANDROID] frame timestamps so hardware H.264 rate control
   * spaces static-image frames across real output time instead of collapsing bitrate.
   */
  private class EglBitmapSurfaceRenderer(
    surface: Surface,
    bitmap: Bitmap
  ) {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var textureId = 0
    private var posHandle = 0
    private var texHandle = 0

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
      textureId = textures[0]
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

      program = createProgram()
      posHandle = GLES20.glGetAttribLocation(program, "aPosition")
      texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
    }

    fun renderFrame(width: Int, height: Int, ptsUs: Long) {
      GLES20.glViewport(0, 0, width, height)
      GLES20.glUseProgram(program)
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

      vertexCoords.position(0)
      GLES20.glEnableVertexAttribArray(posHandle)
      GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, vertexCoords)

      texCoords.position(0)
      GLES20.glEnableVertexAttribArray(texHandle)
      GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texCoords)

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

      GLES20.glDisableVertexAttribArray(posHandle)
      GLES20.glDisableVertexAttribArray(texHandle)

      EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsUs * 1000L)
      EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun release() {
      if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
        if (eglSurface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
          EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
          if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
          }
          if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
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

    private fun createProgram(): Int {
      val vs = loadShader(
        GLES20.GL_VERTEX_SHADER,
        """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
          gl_Position = aPosition;
          vTexCoord = aTexCoord;
        }
        """.trimIndent()
      )
      val fs = loadShader(
        GLES20.GL_FRAGMENT_SHADER,
        """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D sTexture;
        void main() {
          gl_FragColor = texture2D(sTexture, vTexCoord);
        }
        """.trimIndent()
      )
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
}
