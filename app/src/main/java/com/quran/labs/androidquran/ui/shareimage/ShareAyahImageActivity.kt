package com.quran.labs.androidquran.ui.shareimage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.quran.labs.androidquran.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt

class ShareAyahImageActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val cardData = AyahShareCardData(
      referenceText = intent.getStringExtra(EXTRA_REFERENCE).orEmpty(),
      arabicText = intent.getStringExtra(EXTRA_ARABIC).orEmpty(),
      translationText = intent.getStringExtra(EXTRA_TRANSLATION).orEmpty(),
      footnotes = intent.getStringArrayListExtra(EXTRA_FOOTNOTES)?.toList().orEmpty(),
      isKannada = intent.getBooleanExtra(EXTRA_IS_KANNADA, false),
      startSura = intent.getIntExtra(EXTRA_START_SURA, 0),
      startAyah = intent.getIntExtra(EXTRA_START_AYAH, 0),
      endSura = intent.getIntExtra(EXTRA_END_SURA, 0),
      endAyah = intent.getIntExtra(EXTRA_END_AYAH, 0)
    )
    val isVideoMode = intent.getBooleanExtra(EXTRA_IS_VIDEO_MODE, false)

    setContent {
      MaterialTheme(
        colorScheme = lightColorScheme(
          primary = Color(0xFF0E5A47),
          secondary = Color(0xFF1F7A63),
          surface = Color.White,
          background = Color(0xFFF2F2F7)
        )
      ) {
        ShareAyahImageScreen(
          data = cardData,
          isVideoMode = isVideoMode,
          onClose = { finish() }
        )
      }
    }
  }

  companion object {
    private const val EXTRA_REFERENCE = "extra_reference"
    private const val EXTRA_ARABIC = "extra_arabic"
    private const val EXTRA_TRANSLATION = "extra_translation"
    private const val EXTRA_FOOTNOTES = "extra_footnotes"
    private const val EXTRA_IS_KANNADA = "extra_is_kannada"
    private const val EXTRA_IS_VIDEO_MODE = "extra_is_video_mode"
    private const val EXTRA_START_SURA = "extra_start_sura"
    private const val EXTRA_START_AYAH = "extra_start_ayah"
    private const val EXTRA_END_SURA = "extra_end_sura"
    private const val EXTRA_END_AYAH = "extra_end_ayah"

    fun createIntent(
      context: Context,
      data: AyahShareCardData,
      isVideoMode: Boolean = false
    ): Intent {
      return Intent(context, ShareAyahImageActivity::class.java).apply {
        putExtra(EXTRA_REFERENCE, data.referenceText)
        putExtra(EXTRA_ARABIC, data.arabicText)
        putExtra(EXTRA_TRANSLATION, data.translationText)
        putStringArrayListExtra(EXTRA_FOOTNOTES, ArrayList(data.footnotes))
        putExtra(EXTRA_IS_KANNADA, data.isKannada)
        putExtra(EXTRA_IS_VIDEO_MODE, isVideoMode)
        putExtra(EXTRA_START_SURA, data.startSura)
        putExtra(EXTRA_START_AYAH, data.startAyah)
        putExtra(EXTRA_END_SURA, data.endSura)
        putExtra(EXTRA_END_AYAH, data.endAyah)
      }
    }
  }
}

private fun loadPresetBitmap(context: Context, preset: AyahCardPhotoPreset): Bitmap? {
  val resId = context.resources.getIdentifier(preset.drawableResName, "drawable", context.packageName)
  if (resId == 0) return null
  return BitmapFactory.decodeResource(context.resources, resId)
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
  val filename = "Quran_Ayah_${System.currentTimeMillis()}.png"
  return try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Quran")
        put(MediaStore.Images.Media.IS_PENDING, 1)
      }
      val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
      context.contentResolver.openOutputStream(uri)?.use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      }
      values.clear()
      values.put(MediaStore.Images.Media.IS_PENDING, 0)
      context.contentResolver.update(uri, values, null, null)
      true
    } else {
      @Suppress("DEPRECATION")
      val inserted = MediaStore.Images.Media.insertImage(
        context.contentResolver,
        bitmap,
        filename,
        "Quran Ayah Card"
      )
      inserted != null
    }
  } catch (e: Exception) {
    Timber.e(e, "Failed to save image to gallery")
    false
  }
}

private fun saveVideoToGallery(context: Context, videoFile: java.io.File): Boolean {
  val filename = videoFile.name
  return try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, filename)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Quran")
        put(MediaStore.Video.Media.IS_PENDING, 1)
      }
      val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
      val stream = context.contentResolver.openOutputStream(uri) ?: return false
      stream.use { out ->
        videoFile.inputStream().use { input -> input.copyTo(out) }
      }
      values.clear()
      values.put(MediaStore.Video.Media.IS_PENDING, 0)
      context.contentResolver.update(uri, values, null, null)
      true
    } else {
      false
    }
  } catch (e: Exception) {
    Timber.e(e, "Failed to save video to gallery")
    false
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareAyahImageScreen(
  data: AyahShareCardData,
  isVideoMode: Boolean = false,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val isKn = data.isKannada

  // State matching iOS defaults
  var selectedTheme by remember { mutableStateOf(AyahCardTheme.EMERALD) }
  var selectedAspectRatio by remember { mutableStateOf(AyahCardAspectRatio.STORY) }
  var backgroundMode by remember { mutableIntStateOf(0) } // 0 = Colors, 1 = Photos
  var selectedPhotoPreset by remember { mutableStateOf<AyahCardPhotoPreset?>(null) }
  var downloadedCloudPhoto by remember { mutableStateOf<Bitmap?>(null) }
  var selectedRemoteItem by remember { mutableStateOf<RemoteAyahBackgroundItem?>(null) }
  var isUsingCloudPhoto by remember { mutableStateOf(false) }

  var selectedArabicFont by remember { mutableStateOf(AyahCardArabicFont.UTHMANIC) }
  var selectedTranslationFont by remember { mutableStateOf(AyahCardTranslationFont.SERIF) }
  var colorTarget by remember { mutableStateOf(AyahCardTextColorTarget.ARABIC) }
  var arabicColorScheme by remember { mutableStateOf(AyahCardTextColorScheme.THEME_DEFAULT) }
  var customArabicColorInt by remember { mutableIntStateOf(android.graphics.Color.rgb(255, 217, 128)) }
  var translationColorScheme by remember { mutableStateOf(AyahCardTextColorScheme.THEME_DEFAULT) }
  var customTranslationColorInt by remember { mutableIntStateOf(android.graphics.Color.WHITE) }

  var fontSizeScale by remember { mutableFloatStateOf(1.0f) }
  var photoScale by remember { mutableFloatStateOf(1.0f) }
  var showArabic by remember { mutableStateOf(data.arabicText.isNotBlank()) }
  var showTranslation by remember { mutableStateOf(data.translationText.isNotBlank()) }
  var showFootnotes by remember { mutableStateOf(false) }
  var showQRCode by remember { mutableStateOf(true) }
  var captionMode by remember { mutableStateOf(AyahShareCaptionMode.LINK_ONLY) }

  val repository = remember { RemoteBackgroundRepository(context) }
  var catalogItems by remember {
    mutableStateOf(repository.getCachedCatalog().orEmpty())
  }
  var isLoadingCatalog by remember { mutableStateOf(false) }
  var isLoadingRemoteImage by remember { mutableStateOf(false) }
  var showCloudSheet by remember { mutableStateOf(false) }
  var showColorPickerDialog by remember { mutableStateOf(false) }
  var isExporting by remember { mutableStateOf(false) }
  var exportActionIsSave by remember { mutableStateOf(false) }
  var exportStatusText by remember { mutableStateOf<String?>(null) }
  var exportProgressPercent by remember { mutableStateOf<Int?>(null) }
  val mainHandler = remember { Handler(Looper.getMainLooper()) }
  fun postExportProgress(progress: AyahVideoExporter.VideoExportProgress) {
    mainHandler.post {
      exportStatusText = progress.statusText
      exportProgressPercent = progress.percent
    }
  }
  var isPlayingAudioPreview by remember { mutableStateOf(false) }
  var isLoadingAudioPreview by remember { mutableStateOf(false) }
  var previewAudioStatusText by remember { mutableStateOf<String?>(null) }
  var previewAudioPercent by remember { mutableStateOf<Int?>(null) }
  var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
  val previewHandler = remember { Handler(Looper.getMainLooper()) }
  val availableQaris = remember {
    if (isVideoMode) AyahVideoExporter(context).getAvailableQaris() else emptyList()
  }
  var selectedQariId by remember {
    mutableIntStateOf(if (isVideoMode) AyahVideoExporter(context).getDefaultQariId() else 0)
  }
  val selectedQariName = availableQaris.firstOrNull { it.first == selectedQariId }?.second
  var previewQariName by remember { mutableStateOf<String?>(selectedQariName) }
  var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
  val targetEndAyah = if (data.endSura == data.startSura && data.endAyah >= data.startAyah) {
    data.endAyah
  } else {
    data.startAyah
  }

  fun stopAudioPreview() {
    previewJob?.cancel()
    previewJob = null
    previewHandler.removeCallbacksAndMessages(null)
    try {
      mediaPlayer?.stop()
    } catch (_: Exception) {
    }
    mediaPlayer?.release()
    mediaPlayer = null
    isPlayingAudioPreview = false
    isLoadingAudioPreview = false
    previewAudioStatusText = null
    previewAudioPercent = null
  }

  DisposableEffect(Unit) {
    onDispose {
      stopAudioPreview()
    }
  }

  fun toggleAudioPreview() {
    if (isPlayingAudioPreview || isLoadingAudioPreview) {
      stopAudioPreview()
      return
    }
    if (data.startSura <= 0 || data.startAyah <= 0) return
    isLoadingAudioPreview = true
    previewAudioPercent = null
    previewAudioStatusText = if (isKn) "ಆಡಿಯೋ ಲೋಡ್..." else "Loading audio..."
    previewJob = scope.launch {
      try {
        val segment = try {
          withContext(Dispatchers.IO) {
            AyahVideoExporter(context).resolveAudioForAyah(
              data.startSura,
              data.startAyah,
              targetEndAyah,
              selectedQariId
            ) { p ->
              previewHandler.post {
                previewAudioStatusText = p.statusText
                previewAudioPercent = p.percent
              }
            }
          }
        } finally {
          previewHandler.removeCallbacksAndMessages(null)
          isLoadingAudioPreview = false
          previewAudioStatusText = null
          previewAudioPercent = null
        }

        previewQariName = segment.qariName
        val player = android.media.MediaPlayer().apply {
          setDataSource(segment.file.absolutePath)
          prepare()
          if (segment.startMs > 0) {
            seekTo(segment.startMs.toInt())
          }
          setOnCompletionListener {
            stopAudioPreview()
          }
          start()
        }
        mediaPlayer?.release()
        mediaPlayer = player
        isPlayingAudioPreview = true
        if (segment.endMs != Long.MAX_VALUE && segment.endMs > segment.startMs) {
          kotlinx.coroutines.delay(segment.endMs - segment.startMs)
          if (mediaPlayer === player) {
            stopAudioPreview()
          }
        }
      } catch (_: kotlinx.coroutines.CancellationException) {
        // Ignore user-initiated cancellation
      } catch (e: Exception) {
        Timber.e(e, "Failed to load audio preview")
        snackbarHostState.showSnackbar(
          if (isKn) "ಆಡಿಯೋ ಲೋಡ್ ಮಾಡಲು ಸಾಧ್ಯವಾಗಲಿಲ್ಲ" else "Could not load recitation: ${e.message ?: "error"}"
        )
      }
    }
  }

  // Load thumbnails for the 8 curated photo presets once
  val presetBitmaps = remember {
    AyahCardPhotoPreset.entries.associateWith { preset ->
      loadPresetBitmap(context, preset)
    }
  }

  val activePhotoBitmap: Bitmap? = when {
    backgroundMode == 0 -> null
    isUsingCloudPhoto && downloadedCloudPhoto != null -> downloadedCloudPhoto
    selectedPhotoPreset != null -> presetBitmaps[selectedPhotoPreset]
    else -> null
  }

  var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

  // Fetch cloud background catalog in background
  LaunchedEffect(Unit) {
    isLoadingCatalog = true
    val fetched = runCatching { repository.fetchCatalog() }.getOrDefault(emptyList())
    if (fetched.isNotEmpty()) {
      catalogItems = fetched
    }
    isLoadingCatalog = false
  }

  // Re-render preview whenever any customization state changes
  LaunchedEffect(
    selectedTheme,
    selectedAspectRatio,
    backgroundMode,
    selectedPhotoPreset,
    isUsingCloudPhoto,
    downloadedCloudPhoto,
    selectedArabicFont,
    selectedTranslationFont,
    arabicColorScheme,
    customArabicColorInt,
    translationColorScheme,
    customTranslationColorInt,
    fontSizeScale,
    showArabic,
    showTranslation,
    showFootnotes,
    showQRCode,
    photoScale
  ) {
    val rendered = withContext(Dispatchers.Default) {
      AyahCardBitmapRenderer.render(
        context = context,
        data = data,
        theme = selectedTheme,
        aspectRatio = selectedAspectRatio,
        arabicFont = selectedArabicFont,
        translationFont = selectedTranslationFont,
        textColorScheme = arabicColorScheme,
        customArabicColor = customArabicColorInt,
        translationTextColorScheme = translationColorScheme,
        customTranslationColor = customTranslationColorInt,
        fontSizeScale = fontSizeScale,
        showArabic = showArabic,
        showTranslation = showTranslation,
        showFootnotes = showFootnotes,
        showQRCode = showQRCode,
        photoBackground = activePhotoBitmap,
        photoScale = photoScale,
        cardWidthPx = 540
      )
    }
    previewBitmap = rendered
  }

  fun renderHighResBitmap(): Bitmap {
    return AyahCardBitmapRenderer.render(
      context = context,
      data = data,
      theme = selectedTheme,
      aspectRatio = selectedAspectRatio,
      arabicFont = selectedArabicFont,
      translationFont = selectedTranslationFont,
      textColorScheme = arabicColorScheme,
      customArabicColor = customArabicColorInt,
      translationTextColorScheme = translationColorScheme,
      customTranslationColor = customTranslationColorInt,
      fontSizeScale = fontSizeScale,
      showArabic = showArabic,
      showTranslation = showTranslation,
      showFootnotes = showFootnotes,
      showQRCode = showQRCode,
      photoBackground = activePhotoBitmap,
      photoScale = photoScale,
      cardWidthPx = 1080
    )
  }

  fun shareCard() {
    if (isExporting) return
    isExporting = true
    exportActionIsSave = false
    exportStatusText = if (isKn) "ತಯಾರಿಸಲಾಗುತ್ತಿದೆ..." else "Preparing..."
    exportProgressPercent = null
    scope.launch {
      try {
        val shareUri = withContext(Dispatchers.Default) {
          val highRes = renderHighResBitmap()
          val file = withContext(Dispatchers.IO) {
            if (isVideoMode && data.startSura > 0 && data.startAyah > 0) {
              val exporter = AyahVideoExporter(context)
              val segment = exporter.resolveAudioForAyah(
                data.startSura,
                data.startAyah,
                targetEndAyah,
                selectedQariId
              ) { p -> postExportProgress(p) }
              exporter.exportMp4(highRes, segment) { p -> postExportProgress(p) }
            } else {
              AyahCardBitmapRenderer.savePngToShareCache(context, highRes)
            }
          }
          FileProvider.getUriForFile(
            context,
            context.getString(R.string.file_authority),
            file
          )
        }

        val captionText = when (captionMode) {
          AyahShareCaptionMode.IMAGE_ONLY -> null
          AyahShareCaptionMode.LINK_ONLY -> AyahCardBitmapRenderer.SHARE_URL
          AyahShareCaptionMode.FULL_TEXT -> buildString {
            if (showArabic && data.arabicText.isNotBlank()) {
              append(data.arabicText)
              append("\n\n")
            }
            if (showTranslation && data.translationText.isNotBlank()) {
              val trans = if (showFootnotes && data.footnotes.isNotEmpty()) {
                data.translationText
              } else {
                AyahCardBitmapRenderer.stripFootnoteMarkers(data.translationText)
              }
              append(trans)
              append("\n\n")
            }
            if (showFootnotes && data.footnotes.isNotEmpty()) {
              append(AyahCardBitmapRenderer.formatNumberedFootnotes(data.footnotes))
              append("\n\n")
            }
            if (data.referenceText.isNotBlank()) {
              append("[${data.referenceText}]\n")
            }
            append(AyahCardBitmapRenderer.SHARE_URL)
          }
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
          type = if (isVideoMode && data.startSura > 0 && data.startAyah > 0) "video/mp4" else "image/png"
          putExtra(Intent.EXTRA_STREAM, shareUri)
          if (!captionText.isNullOrBlank()) {
            putExtra(Intent.EXTRA_TEXT, captionText)
          }
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
          Intent.createChooser(
            sendIntent,
            if (isVideoMode) {
              if (isKn) "ವೀಡಿಯೊ ಹಂಚಿಕೆ" else "Share Ayah Video"
            } else {
              if (isKn) "ಚಿತ್ರ ಹಂಚಿಕೆ" else "Share Ayah"
            }
          )
        )
      } catch (e: Exception) {
        Timber.e(e, "Failed to share card or video")
        val reason = e.message?.takeIf { it.isNotBlank() } ?: "error"
        snackbarHostState.showSnackbar(
          if (isVideoMode) {
            if (isKn) "ವೀಡಿಯೊ ರಚಿಸಲು ಸಾಧ್ಯವಾಗಲಿಲ್ಲ: $reason" else "Could not create video: $reason"
          } else {
            if (isKn) "ಹಂಚಿಕೊಳ್ಳಲು ಸಾಧ್ಯವಾಗಲಿಲ್ಲ: $reason" else "Could not share: $reason"
          }
        )
      } finally {
        mainHandler.removeCallbacksAndMessages(null)
        isExporting = false
        exportActionIsSave = false
        exportStatusText = null
        exportProgressPercent = null
      }
    }
  }

  fun saveToPhotos() {
    if (isExporting) return
    isExporting = true
    exportActionIsSave = true
    exportStatusText = if (isKn) "ತಯಾರಿಸಲಾಗುತ್ತಿದೆ..." else "Preparing..."
    exportProgressPercent = null
    scope.launch {
      try {
        val saved = withContext(Dispatchers.Default) {
          val highRes = renderHighResBitmap()
          withContext(Dispatchers.IO) {
            if (isVideoMode && data.startSura > 0 && data.startAyah > 0) {
              val exporter = AyahVideoExporter(context)
              val segment = exporter.resolveAudioForAyah(
                data.startSura,
                data.startAyah,
                targetEndAyah,
                selectedQariId
              ) { p -> postExportProgress(p) }
              val mp4 = exporter.exportMp4(highRes, segment) { p -> postExportProgress(p) }
              saveVideoToGallery(context, mp4)
            } else {
              saveBitmapToGallery(context, highRes)
            }
          }
        }
        snackbarHostState.showSnackbar(
          if (saved) {
            if (isVideoMode) {
              if (isKn) "ವೀಡಿಯೊ ಗ್ಯಾಲರಿಗೆ ಉಳಿಸಲಾಗಿದೆ" else "Saved video to Gallery"
            } else {
              if (isKn) "ಫೋಟೋಗಳಿಗೆ ಉಳಿಸಲಾಗಿದೆ" else "Saved to Photos"
            }
          } else {
            if (isKn) "ಉಳಿಸಲು ಸಾಧ್ಯವಾಗಲಿಲ್ಲ" else "Could not save"
          }
        )
      } catch (e: Exception) {
        Timber.e(e, "Failed to save card or video")
        val reason = e.message?.takeIf { it.isNotBlank() } ?: "error"
        snackbarHostState.showSnackbar(
          if (isKn) "ಉಳಿಸಲು ಸಾಧ್ಯವಾಗಲಿಲ್ಲ: $reason" else "Could not save: $reason"
        )
      } finally {
        mainHandler.removeCallbacksAndMessages(null)
        isExporting = false
        exportActionIsSave = false
        exportStatusText = null
        exportProgressPercent = null
      }
    }
  }

  val activeColorScheme = if (colorTarget == AyahCardTextColorTarget.ARABIC) {
    arabicColorScheme
  } else {
    translationColorScheme
  }

  Scaffold(
    containerColor = Color(0xFFF2F2F7),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close"
            )
          }
        },
        title = {
          Text(
            text = if (isVideoMode) {
              if (isKn) "ವೀಡಿಯೊ ಹಂಚಿಕೆ" else "Share Ayah Video"
            } else {
              if (isKn) "ಚಿತ್ರ ಹಂಚಿಕೆ" else "Share Ayah"
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Live Card Preview
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(320.dp)
          .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
      ) {
        val bmp = previewBitmap
        if (bmp != null) {
          Box(
            modifier = Modifier
              .shadow(10.dp, RoundedCornerShape(16.dp))
              .clip(RoundedCornerShape(16.dp))
              .aspectRatio(1f / selectedAspectRatio.heightRatio, matchHeightConstraintsFirst = true)
          ) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = "Ayah Card Preview",
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize()
            )
            if (isVideoMode) {
              Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(10.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "MP4",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }

              Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                  .align(Alignment.BottomCenter)
                  .padding(bottom = 12.dp)
                  .clickable { toggleAudioPreview() }
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  if (isLoadingAudioPreview) {
                    CircularProgressIndicator(
                      color = Color.White,
                      strokeWidth = 2.dp,
                      modifier = Modifier.size(16.dp)
                    )
                  } else {
                    Icon(
                      imageVector = if (isPlayingAudioPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = when {
                      isLoadingAudioPreview -> if (isKn) "ಡೌನ್‌ಲೋಡ್..." else "Downloading..."
                      isPlayingAudioPreview -> previewQariName ?: (if (isKn) "ನಿಲ್ಲಿಸಿ" else "Stop preview")
                      else -> previewQariName ?: (if (isKn) "ಪಠಣ ಕೇಳಿ" else "Preview recitation")
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }
        } else {
          CircularProgressIndicator(color = Color(0xFF0E5A47))
        }

        if (isLoadingRemoteImage) {
          Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(20.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (isKn) "ಲೋಡ್ ಆಗುತ್ತಿದೆ..." else "Loading background...",
                color = Color.White,
                fontSize = 12.sp
              )
            }
          }
        }
      }

      if (isVideoMode && isLoadingAudioPreview) {
        VideoProgressBanner(
          statusText = previewAudioStatusText ?: (if (isKn) "ಆಡಿಯೋ ಡೌನ್‌ಲೋಡ್..." else "Downloading audio..."),
          percent = previewAudioPercent,
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }

      if (isVideoMode && availableQaris.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = if (isKn) "ವಾಚಕರು" else "Reciter",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF495057),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            availableQaris.forEach { (qId, qName) ->
              val selected = selectedQariId == qId
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) Color(0xFF0E5A47) else Color.White,
                border = if (selected) null else BorderStroke(1.dp, Color(0xFFDEE2E6)),
                modifier = Modifier.clickable {
                  if (selectedQariId != qId) {
                    stopAudioPreview()
                    selectedQariId = qId
                    previewQariName = qName
                  }
                }
              ) {
                Text(
                  text = qName,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                  color = if (selected) Color.White else Color(0xFF212529),
                  maxLines = 1,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
              }
            }
          }
        }
      }

      // 1. Aspect Ratio Selector (1:1, 4:5, 9:16)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        AyahCardAspectRatio.entries.forEach { ratio ->
          val selected = selectedAspectRatio == ratio
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (selected) Color(0xFF0E5A47) else Color.White,
            modifier = Modifier
              .weight(1f)
              .clickable { selectedAspectRatio = ratio }
          ) {
            Text(
              text = ratio.displayName(isKn),
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = if (selected) Color.White else Color(0xFF212529),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
            )
          }
        }
      }

      // 2. Background Mode Segmented Control: Colors vs Photos
      SegmentedControlRow(
        options = listOf(
          if (isKn) "ಬಣ್ಣಗಳು" else "Colors",
          if (isKn) "ಫೋಟೋಗಳು" else "Photos"
        ),
        selectedIndex = backgroundMode,
        onSelect = { newMode ->
          backgroundMode = newMode
          if (newMode == 1 && !isUsingCloudPhoto && selectedPhotoPreset == null) {
            selectedPhotoPreset = AyahCardPhotoPreset.MOSQUE_DUSK
          }
        },
        modifier = Modifier.padding(horizontal = 16.dp)
      )

      if (backgroundMode == 0) {
        // 7 Color Swatches
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          AyahCardTheme.entries.forEach { theme ->
            val isSelected = selectedTheme == theme
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(
                    listOf(Color(theme.gradientStart), Color(theme.gradientEnd))
                  )
                )
                .border(
                  width = if (isSelected) 3.dp else 1.dp,
                  color = if (isSelected) Color(0xFF0E5A47) else Color(0xFFCED4DA),
                  shape = CircleShape
                )
                .clickable { selectedTheme = theme },
              contentAlignment = Alignment.Center
            ) {
              if (isSelected) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = if (theme.isLight) Color.Black else Color.White,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      } else {
        // Photo Swatches: More (Cloud) + Downloaded Cloud Photo (if any) + 8 Curated Presets
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.Top
        ) {
          // 1. More Cloud Backgrounds button
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .width(56.dp)
              .clickable { showCloudSheet = true }
          ) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF0E5A47).copy(alpha = 0.1f))
                .border(1.5.dp, Color(0xFF0E5A47).copy(alpha = 0.6f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "More Cloud Backgrounds",
                tint = Color(0xFF0E5A47),
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = if (isKn) "ಇನ್ನಷ್ಟು" else "More",
              fontSize = 11.sp,
              color = Color(0xFF495057),
              maxLines = 1
            )
          }

          // 2. Selected Cloud Background (only if one was downloaded from Cloud)
          val cloudBmp = downloadedCloudPhoto
          if (cloudBmp != null) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .width(56.dp)
                .clickable { isUsingCloudPhoto = true }
            ) {
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .border(
                    width = if (isUsingCloudPhoto) 3.dp else 1.dp,
                    color = if (isUsingCloudPhoto) Color(0xFF0E5A47) else Color(0xFFCED4DA),
                    shape = CircleShape
                  ),
                contentAlignment = Alignment.Center
              ) {
                Image(
                  bitmap = cloudBmp.asImageBitmap(),
                  contentDescription = "Cloud Photo",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
                if (isUsingCloudPhoto) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = selectedRemoteItem?.localizedName(isKn) ?: if (isKn) "ಕ್ಲೌಡ್" else "Cloud",
                fontSize = 11.sp,
                color = if (isUsingCloudPhoto) Color(0xFF0E5A47) else Color(0xFF495057),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          // 3. All 8 Curated Photo Presets
          AyahCardPhotoPreset.entries.forEach { preset ->
            val isSelected = !isUsingCloudPhoto && selectedPhotoPreset == preset
            val thumb = presetBitmaps[preset]
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .width(56.dp)
                .clickable {
                  isUsingCloudPhoto = false
                  selectedPhotoPreset = preset
                }
            ) {
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF212529))
                  .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) Color(0xFF0E5A47) else Color(0xFFCED4DA),
                    shape = CircleShape
                  ),
                contentAlignment = Alignment.Center
              ) {
                if (thumb != null) {
                  Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = preset.displayName(isKn),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                }
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = preset.displayName(isKn),
                fontSize = 11.sp,
                color = if (isSelected) Color(0xFF0E5A47) else Color(0xFF495057),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        // Photo Zoom Stepper (100% to 200%, matching iOS)
        StepperCard(
          label = if (isKn) "ಫೋಟೋ ಜೂಮ್" else "Photo Zoom",
          valueText = "${(photoScale * 100).roundToInt()}%",
          canDecrease = photoScale > 1.05f,
          canIncrease = photoScale < 1.95f,
          onDecrease = { photoScale = (photoScale - 0.1f).coerceAtLeast(1.0f) },
          onIncrease = { photoScale = (photoScale + 0.1f).coerceAtMost(2.0f) },
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }

      // 3. Text Size Stepper (20% to 200%)
      StepperCard(
        label = if (isKn) "ಪಠ್ಯದ ಗಾತ್ರ" else "Text Size",
        valueText = "${(fontSizeScale * 100).roundToInt()}%",
        canDecrease = fontSizeScale > 0.25f,
        canIncrease = fontSizeScale < 1.95f,
        onDecrease = { fontSizeScale = (fontSizeScale - 0.1f).coerceAtLeast(0.2f) },
        onIncrease = { fontSizeScale = (fontSizeScale + 0.1f).coerceAtMost(2.0f) },
        modifier = Modifier.padding(horizontal = 16.dp)
      )

      // 4. Font Style Selector (switches between Arabic fonts and Translation fonts based on colorTarget)
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = if (isKn) "ಫಾಂಟ್ ಶೈಲಿ" else "Font Style",
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium
            )
            Text(
              text = if (colorTarget == AyahCardTextColorTarget.ARABIC) {
                selectedArabicFont.displayName(isKn)
              } else {
                selectedTranslationFont.displayName(isKn)
              },
              fontSize = 12.sp,
              color = Color(0xFF6C757D)
            )
          }

          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            if (colorTarget == AyahCardTextColorTarget.ARABIC) {
              AyahCardArabicFont.entries.forEach { fontOption ->
                val selected = selectedArabicFont == fontOption
                PillChip(
                  text = fontOption.displayName(isKn),
                  selected = selected,
                  onClick = { selectedArabicFont = fontOption }
                )
              }
            } else {
              AyahCardTranslationFont.entries.forEach { fontOption ->
                val selected = selectedTranslationFont == fontOption
                PillChip(
                  text = fontOption.displayName(isKn),
                  selected = selected,
                  onClick = { selectedTranslationFont = fontOption }
                )
              }
            }
          }
        }
      }

      // 5. Text Color Selector (Arabic vs Translation Segmented Control + Presets + Custom Picker)
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = if (isKn) "ಪಠ್ಯದ ಬಣ್ಣ" else "Text Color",
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (arabicColorScheme != AyahCardTextColorScheme.THEME_DEFAULT ||
                translationColorScheme != AyahCardTextColorScheme.THEME_DEFAULT
              ) {
                Row(
                  modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF0E5A47).copy(alpha = 0.12f))
                    .clickable {
                      arabicColorScheme = AyahCardTextColorScheme.THEME_DEFAULT
                      translationColorScheme = AyahCardTextColorScheme.THEME_DEFAULT
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                  horizontalArrangement = Arrangement.spacedBy(3.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = if (isKn) "ಮರುಹೊಂದಿಸಿ" else "Reset",
                    tint = Color(0xFF0E5A47),
                    modifier = Modifier.size(12.dp)
                  )
                  Text(
                    text = if (isKn) "ಮರುಹೊಂದಿಸಿ" else "Reset",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0E5A47)
                  )
                }
              }
              Text(
                text = activeColorScheme.displayName(isKn),
                fontSize = 12.sp,
                color = Color(0xFF6C757D)
              )
            }
          }

          SegmentedControlRow(
            options = AyahCardTextColorTarget.entries.map { it.displayName(isKn) },
            selectedIndex = if (colorTarget == AyahCardTextColorTarget.ARABIC) 0 else 1,
            onSelect = { idx ->
              colorTarget = if (idx == 0) AyahCardTextColorTarget.ARABIC else AyahCardTextColorTarget.TRANSLATION
            }
          )

          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            AyahCardTextColorScheme.presets.forEach { scheme ->
              val isSelected = activeColorScheme == scheme
              val previewColor = scheme.colorInt?.let { Color(it) } ?: Color(0xFF8C94A6)
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(previewColor)
                  .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) Color(0xFF0E5A47) else Color(0xFFCED4DA),
                    shape = CircleShape
                  )
                  .clickable {
                    if (colorTarget == AyahCardTextColorTarget.ARABIC) {
                      arabicColorScheme = scheme
                    } else {
                      translationColorScheme = scheme
                    }
                  },
                contentAlignment = Alignment.Center
              ) {
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (scheme == AyahCardTextColorScheme.PURE_WHITE || scheme == AyahCardTextColorScheme.CLASSIC_GOLD) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
            }

            // Custom Color Picker button
            val isCustomSelected = activeColorScheme == AyahCardTextColorScheme.CUSTOM
            val customPreview = Color(
              if (colorTarget == AyahCardTextColorTarget.ARABIC) customArabicColorInt else customTranslationColorInt
            )
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(customPreview)
                .border(
                  width = if (isCustomSelected) 2.5.dp else 1.dp,
                  color = if (isCustomSelected) Color(0xFF0E5A47) else Color(0xFFCED4DA),
                  shape = CircleShape
                )
                .clickable { showColorPickerDialog = true },
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "Custom Color",
                tint = Color(0xFF212529),
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      // 6. Content Toggles (matching iOS)
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
      ) {
        Column {
          ToggleRow(
            label = if (isKn) "ಅರೇಬಿಕ್ ಪಠ್ಯ ತೋರಿಸಿ" else "Show Arabic Text",
            checked = showArabic,
            onCheckedChange = { showArabic = it }
          )
          HorizontalDivider(color = Color(0xFFE9ECEF), modifier = Modifier.padding(start = 16.dp))
          ToggleRow(
            label = if (isKn) "ಅನುವಾದ ತೋರಿಸಿ" else "Show Translation",
            checked = showTranslation,
            onCheckedChange = { showTranslation = it }
          )
          if (data.footnotes.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFFE9ECEF), modifier = Modifier.padding(start = 16.dp))
            ToggleRow(
              label = if (isKn) "ಟಿಪ್ಪಣಿಗಳನ್ನು ಸೇರಿಸಿ" else "Include Footnotes",
              checked = showFootnotes,
              onCheckedChange = { showFootnotes = it }
            )
          }
          HorizontalDivider(color = Color(0xFFE9ECEF), modifier = Modifier.padding(start = 16.dp))
          ToggleRow(
            label = if (isKn) "QR ಕೋಡ್ ಸೇರಿಸಿ" else "Include QR Code",
            checked = showQRCode,
            onCheckedChange = { showQRCode = it }
          )
        }
      }

      // 7. Accompanying Text Mode
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = if (isKn) "ಹಂಚಿಕೊಳ್ಳುವ ಸಂದೇಶ" else "Accompanying Text",
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF6C757D),
          modifier = Modifier.padding(start = 4.dp)
        )
        SegmentedControlRow(
          options = AyahShareCaptionMode.entries.map { it.displayName(isKn) },
          selectedIndex = AyahShareCaptionMode.entries.indexOf(captionMode),
          onSelect = { idx -> captionMode = AyahShareCaptionMode.entries[idx] }
        )
      }

      // 8. Action Buttons: Share + Save to Photos (matching iOS)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (isExporting && isVideoMode) {
          VideoProgressBanner(
            statusText = exportStatusText ?: if (isKn) "ವೀಡಿಯೊ ರಚಿಸಲಾಗುತ್ತಿದೆ..." else "Creating Video...",
            percent = exportProgressPercent
          )
        }

        Button(
          onClick = { shareCard() },
          enabled = !isExporting,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1FA661)),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
        ) {
          if (isExporting && !exportActionIsSave) {
            CircularProgressIndicator(
              color = Color.White,
              strokeWidth = 2.dp,
              modifier = Modifier.size(18.dp)
            )
            if (isVideoMode) {
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (isKn) "ವೀಡಿಯೊ ಹಂಚಿಕೊಳ್ಳಲಾಗುತ್ತಿದೆ..." else "Preparing Video...",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          } else {
            Icon(
              imageVector = if (isVideoMode) Icons.Default.Videocam else Icons.Default.Share,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isVideoMode) {
                if (isKn) "ವೀಡಿಯೊ ಹಂಚಿಕೊಳ್ಳಿ" else "Share Video"
              } else {
                if (isKn) "ಹಂಚಿಕೊಳ್ಳಿ" else "Share"
              },
              fontSize = 16.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }

        OutlinedButton(
          onClick = { saveToPhotos() },
          enabled = !isExporting,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, Color(0xFFCED4DA)),
          colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
        ) {
          if (isExporting && exportActionIsSave) {
            CircularProgressIndicator(
              color = Color(0xFF0E5A47),
              strokeWidth = 2.dp,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isKn) "ವೀಡಿಯೊ ಉಳಿಸಲಾಗುತ್ತಿದೆ..." else if (isVideoMode) "Saving Video..." else "Saving...",
              fontSize = 15.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF0E5A47),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          } else {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isVideoMode) {
                if (isKn) "ವೀಡಿಯೊ ಉಳಿಸಿ" else "Save Video"
              } else {
                if (isKn) "ಫೋಟೋಗಳಿಗೆ ಉಳಿಸಿ" else "Save to Photos"
              },
              fontSize = 15.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF212529)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  // Cloud Backgrounds Modal Sheet
  if (showCloudSheet) {
    ModalBottomSheet(
      onDismissRequest = { showCloudSheet = false }
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .padding(bottom = 24.dp)
      ) {
        Text(
          text = if (isKn) "ಹಿನ್ನೆಲೆ ಚಿತ್ರಗಳು" else "Cloud Backgrounds",
          fontSize = 18.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(bottom = 12.dp)
        )

        if (isLoadingCatalog && catalogItems.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(220.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = Color(0xFF0E5A47))
          }
        } else if (catalogItems.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(160.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = if (isKn) "ಹಿನ್ನೆಲೆ ಚಿತ್ರಗಳು ಲಭ್ಯವಿಲ್ಲ" else "No cloud backgrounds available right now.",
              color = Color.Gray
            )
          }
        } else {
          LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(380.dp)
          ) {
            items(catalogItems, key = { it.id }) { item ->
              val isSelected = isUsingCloudPhoto && selectedRemoteItem?.id == item.id
              RemoteBackgroundGridCell(
                item = item,
                isKannada = isKn,
                repository = repository,
                isSelected = isSelected,
                onClick = {
                  showCloudSheet = false
                  isLoadingRemoteImage = true
                  scope.launch {
                    try {
                      val fullBmp = repository.loadImage(
                        url = item.imageUrl,
                        cacheKey = "full_${item.id}"
                      )
                      if (fullBmp != null) {
                        downloadedCloudPhoto = fullBmp
                        selectedRemoteItem = item
                        isUsingCloudPhoto = true
                        backgroundMode = 1
                        photoScale = 1.0f
                      }
                    } finally {
                      isLoadingRemoteImage = false
                    }
                  }
                }
              )
            }
          }
        }
      }
    }
  }

  // Custom Color Picker Dialog
  if (showColorPickerDialog) {
    val initialColor = if (colorTarget == AyahCardTextColorTarget.ARABIC) {
      customArabicColorInt
    } else {
      customTranslationColorInt
    }
    CustomColorPickerDialog(
      title = if (isKn) "ಕಸ್ಟಮ್ ಬಣ್ಣ" else "Custom Color",
      initialColorInt = initialColor,
      onDismiss = { showColorPickerDialog = false },
      onConfirm = { chosenInt ->
        if (colorTarget == AyahCardTextColorTarget.ARABIC) {
          customArabicColorInt = chosenInt
          arabicColorScheme = AyahCardTextColorScheme.CUSTOM
        } else {
          customTranslationColorInt = chosenInt
          translationColorScheme = AyahCardTextColorScheme.CUSTOM
        }
        showColorPickerDialog = false
      }
    )
  }
}

@Composable
private fun SegmentedControlRow(
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = Color(0xFFE5E5EA),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(3.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      options.forEachIndexed { idx, label ->
        val selected = idx == selectedIndex
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (selected) Color.White else Color.Transparent,
          shadowElevation = if (selected) 1.dp else 0.dp,
          modifier = Modifier
            .weight(1f)
            .clickable { onSelect(idx) }
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 6.dp)
          ) {
            Text(
              text = label,
              fontSize = 13.sp,
              fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
              color = Color(0xFF212529),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StepperCard(
  label: String,
  valueText: String,
  canDecrease: Boolean,
  canIncrease: Boolean,
  onDecrease: () -> Unit,
  onIncrease: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color.White,
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFFF1F3F5),
          modifier = Modifier
            .size(32.dp)
            .clickable(enabled = canDecrease, onClick = onDecrease)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Default.Remove,
              contentDescription = "Decrease",
              tint = if (canDecrease) Color(0xFF212529) else Color(0xFFADB5BD),
              modifier = Modifier.size(16.dp)
            )
          }
        }
        Text(
          text = valueText,
          fontWeight = FontWeight.SemiBold,
          fontSize = 13.sp,
          modifier = Modifier.width(46.dp)
        )
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFFF1F3F5),
          modifier = Modifier
            .size(32.dp)
            .clickable(enabled = canIncrease, onClick = onIncrease)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Default.Add,
              contentDescription = "Increase",
              tint = if (canIncrease) Color(0xFF212529) else Color(0xFFADB5BD),
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PillChip(
  text: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(50),
    color = if (selected) Color(0xFF0E5A47) else Color(0xFFF1F3F5),
    modifier = Modifier.clickable(onClick = onClick)
  ) {
    Text(
      text = text,
      fontSize = 12.5.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      color = if (selected) Color.White else Color(0xFF212529),
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
    )
  }
}

@Composable
private fun ToggleRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onCheckedChange(!checked) }
      .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      fontSize = 14.sp,
      color = Color(0xFF212529)
    )
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange
    )
  }
}

@Composable
private fun CustomColorPickerDialog(
  title: String,
  initialColorInt: Int,
  onDismiss: () -> Unit,
  onConfirm: (Int) -> Unit
) {
  var r by remember { mutableFloatStateOf(android.graphics.Color.red(initialColorInt) / 255f) }
  var g by remember { mutableFloatStateOf(android.graphics.Color.green(initialColorInt) / 255f) }
  var b by remember { mutableFloatStateOf(android.graphics.Color.blue(initialColorInt) / 255f) }
  val previewColor = Color(r, g, b)

  val quickSwatches = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFF5E2AD),
    Color(0xFFFFD54F),
    Color(0xFFFF8A65),
    Color(0xFFF06292),
    Color(0xFFBA68C8),
    Color(0xFF64B5F6),
    Color(0xFF4DB6AC),
    Color(0xFF81C784),
    Color(0xFF1F2937)
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, fontWeight = FontWeight.SemiBold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(previewColor)
            .border(1.dp, Color(0xFFCED4DA), RoundedCornerShape(10.dp))
        )

        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          quickSwatches.forEach { c ->
            Box(
              modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(c)
                .border(1.dp, Color(0xFFCED4DA), CircleShape)
                .clickable {
                  r = c.red
                  g = c.green
                  b = c.blue
                }
            )
          }
        }

        Column {
          Text("R: ${(r * 255).roundToInt()}", fontSize = 12.sp)
          Slider(
            value = r,
            onValueChange = { r = it },
            colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
          )
          Text("G: ${(g * 255).roundToInt()}", fontSize = 12.sp)
          Slider(
            value = g,
            onValueChange = { g = it },
            colors = SliderDefaults.colors(thumbColor = Color(0xFF2E7D32), activeTrackColor = Color(0xFF2E7D32))
          )
          Text("B: ${(b * 255).roundToInt()}", fontSize = 12.sp)
          Slider(
            value = b,
            onValueChange = { b = it },
            colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue)
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(previewColor.toArgb()) }) {
        Text("OK")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
private fun RemoteBackgroundGridCell(
  item: RemoteAyahBackgroundItem,
  isKannada: Boolean,
  repository: RemoteBackgroundRepository,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  var thumbBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
  var isLoadingThumb by remember(item.id) { mutableStateOf(true) }

  LaunchedEffect(item.id) {
    isLoadingThumb = true
    thumbBitmap = try {
      repository.loadImage(
        url = item.thumbnailUrl,
        cacheKey = "thumb_${item.id}"
      )
    } finally {
      isLoadingThumb = false
    }
  }

  Box(
    modifier = Modifier
      .aspectRatio(0.8f)
      .clip(RoundedCornerShape(10.dp))
      .background(Color(0xFFE9ECEF))
      .border(
        width = if (isSelected) 3.dp else 0.5.dp,
        color = if (isSelected) Color(0xFF0E5A47) else Color(0xFFDEE2E6),
        shape = RoundedCornerShape(10.dp)
      )
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    val bmp = thumbBitmap
    if (bmp != null) {
      Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = item.localizedName(isKannada),
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else if (isLoadingThumb) {
      CircularProgressIndicator(
        strokeWidth = 2.dp,
        color = Color(0xFF0E5A47),
        modifier = Modifier.size(20.dp)
      )
    } else {
      Text(
        text = item.localizedName(isKannada),
        fontSize = 11.sp,
        color = Color(0xFF6C757D),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(6.dp)
      )
    }
  }
}

@Composable
private fun VideoProgressBanner(
  statusText: String,
  percent: Int?,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = Color.White,
    border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = statusText,
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF0E5A47),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        if (percent != null) {
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "$percent%",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0E5A47)
          )
        }
      }
      if (percent != null) {
        LinearProgressIndicator(
          progress = { percent / 100f },
          color = Color(0xFF1FA661),
          trackColor = Color(0xFFE9ECEF),
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
        )
      } else {
        LinearProgressIndicator(
          color = Color(0xFF1FA661),
          trackColor = Color(0xFFE9ECEF),
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
        )
      }
    }
  }
}
