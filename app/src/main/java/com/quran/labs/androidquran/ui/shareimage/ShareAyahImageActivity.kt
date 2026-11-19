package com.quran.labs.androidquran.ui.shareimage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material.icons.filled.Photo
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt

class ShareAyahImageActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    (application as? QuranApplication)?.refreshLocale(this, false)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT
      ),
      navigationBarStyle = SystemBarStyle.light(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
      )
    )
    super.onCreate(savedInstanceState)

    val cardData = readCardDataFromIntent(this, intent)
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
    private const val EXTRA_AYAH_SLIDES_JSON = "extra_ayah_slides_json"
    private const val EXTRA_PAYLOAD_FILE = "extra_payload_file"
    private const val MAX_INLINE_INTENT_CHARS = 32_000
    private const val PENDING_PAYLOAD_FILENAME = "pending_share_card.json"

    internal fun encodeCardDataJson(data: AyahShareCardData): String {
      return JSONObject().apply {
        put("referenceText", data.referenceText)
        put("arabicText", data.arabicText)
        put("translationText", data.translationText)
        put("footnotes", JSONArray(data.footnotes))
        put("isKannada", data.isKannada)
        put("startSura", data.startSura)
        put("startAyah", data.startAyah)
        put("endSura", data.endSura)
        put("endAyah", data.endAyah)
        encodeAyahSlidesJson(data.ayahSlides)?.let { put("ayahSlidesJson", it) }
      }.toString()
    }

    internal fun decodeCardDataJson(json: String?): AyahShareCardData? {
      if (json.isNullOrBlank()) return null
      return try {
        val obj = JSONObject(json)
        val fnArr = obj.optJSONArray("footnotes")
        val footnotes = if (fnArr != null) {
          List(fnArr.length()) { idx -> fnArr.optString(idx) }
        } else {
          emptyList()
        }
        AyahShareCardData(
          referenceText = obj.optString("referenceText"),
          arabicText = obj.optString("arabicText"),
          translationText = obj.optString("translationText"),
          footnotes = footnotes,
          isKannada = obj.optBoolean("isKannada", false),
          startSura = obj.optInt("startSura", 0),
          startAyah = obj.optInt("startAyah", 0),
          endSura = obj.optInt("endSura", 0),
          endAyah = obj.optInt("endAyah", 0),
          ayahSlides = decodeAyahSlidesJson(
            obj.optString("ayahSlidesJson").takeIf { it.isNotEmpty() }
          )
        )
      } catch (_: Exception) {
        null
      }
    }

    internal fun readCardDataFromIntent(context: Context, intent: Intent): AyahShareCardData {
      val payloadPath = intent.getStringExtra(EXTRA_PAYLOAD_FILE)
      if (!payloadPath.isNullOrBlank()) {
        try {
          val file = java.io.File(payloadPath)
          if (file.exists() && file.canonicalFile.parentFile?.canonicalPath == context.cacheDir.canonicalPath) {
            decodeCardDataJson(file.readText())?.let { return it }
          }
        } catch (e: Exception) {
          Timber.e(e, "Failed to read share card payload file")
        }
      }
      return AyahShareCardData(
        referenceText = intent.getStringExtra(EXTRA_REFERENCE).orEmpty(),
        arabicText = intent.getStringExtra(EXTRA_ARABIC).orEmpty(),
        translationText = intent.getStringExtra(EXTRA_TRANSLATION).orEmpty(),
        footnotes = intent.getStringArrayListExtra(EXTRA_FOOTNOTES)?.toList().orEmpty(),
        isKannada = intent.getBooleanExtra(EXTRA_IS_KANNADA, false),
        startSura = intent.getIntExtra(EXTRA_START_SURA, 0),
        startAyah = intent.getIntExtra(EXTRA_START_AYAH, 0),
        endSura = intent.getIntExtra(EXTRA_END_SURA, 0),
        endAyah = intent.getIntExtra(EXTRA_END_AYAH, 0),
        ayahSlides = decodeAyahSlidesJson(intent.getStringExtra(EXTRA_AYAH_SLIDES_JSON))
      )
    }

    private fun encodeAyahSlidesJson(slides: List<AyahSlideData>): String? {
      if (slides.isEmpty()) return null
      val arr = JSONArray()
      for (slide in slides) {
        val obj = JSONObject().apply {
          put("sura", slide.sura)
          put("ayah", slide.ayah)
          put("ref", slide.referenceText)
          put("ar", slide.arabicText)
          put("tr", slide.translationText)
          put("fn", JSONArray(slide.footnotes))
        }
        arr.put(obj)
      }
      return arr.toString()
    }

    private fun decodeAyahSlidesJson(jsonStr: String?): List<AyahSlideData> {
      if (jsonStr.isNullOrBlank()) return emptyList()
      return try {
        val arr = JSONArray(jsonStr)
        List(arr.length()) { idx ->
          val obj = arr.getJSONObject(idx)
          val fnArr = obj.optJSONArray("fn")
          val footnotes = if (fnArr != null) {
            List(fnArr.length()) { fIdx -> fnArr.optString(fIdx) }
          } else {
            emptyList()
          }
          AyahSlideData(
            sura = obj.optInt("sura"),
            ayah = obj.optInt("ayah"),
            referenceText = obj.optString("ref"),
            arabicText = obj.optString("ar"),
            translationText = obj.optString("tr"),
            footnotes = footnotes
          )
        }
      } catch (_: Exception) {
        emptyList()
      }
    }

    fun createIntent(
      context: Context,
      data: AyahShareCardData,
      isVideoMode: Boolean = false
    ): Intent {
      val slidesJson = encodeAyahSlidesJson(data.ayahSlides)
      val estimatedChars = data.referenceText.length +
        data.arabicText.length +
        data.translationText.length +
        data.footnotes.sumOf { it.length } +
        (slidesJson?.length ?: 0)

      val payloadFilePath = if (estimatedChars > MAX_INLINE_INTENT_CHARS) {
        try {
          val target = java.io.File(context.cacheDir, PENDING_PAYLOAD_FILENAME)
          target.parentFile?.mkdirs()
          val temp = java.io.File(context.cacheDir, "$PENDING_PAYLOAD_FILENAME.part")
          temp.writeText(encodeCardDataJson(data))
          if (target.exists() && !target.delete()) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
          } else if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
          }
          target.absolutePath
        } catch (e: Exception) {
          Timber.e(e, "Failed to spool large share card payload to cache")
          null
        }
      } else {
        null
      }

      return Intent(context, ShareAyahImageActivity::class.java).apply {
        putExtra(EXTRA_REFERENCE, data.referenceText)
        putExtra(EXTRA_IS_KANNADA, data.isKannada)
        putExtra(EXTRA_IS_VIDEO_MODE, isVideoMode)
        putExtra(EXTRA_START_SURA, data.startSura)
        putExtra(EXTRA_START_AYAH, data.startAyah)
        putExtra(EXTRA_END_SURA, data.endSura)
        putExtra(EXTRA_END_AYAH, data.endAyah)
        if (payloadFilePath != null) {
          putExtra(EXTRA_PAYLOAD_FILE, payloadFilePath)
        } else {
          putExtra(EXTRA_ARABIC, data.arabicText)
          putExtra(EXTRA_TRANSLATION, data.translationText)
          putStringArrayListExtra(EXTRA_FOOTNOTES, ArrayList(data.footnotes))
          slidesJson?.let { putExtra(EXTRA_AYAH_SLIDES_JSON, it) }
        }
      }
    }
  }
}

private fun saveMediaToGallery(
  context: Context,
  collection: android.net.Uri,
  legacyDirectory: String,
  filename: String,
  mimeType: String,
  relativePath: String,
  logTag: String,
  writePayload: (java.io.OutputStream) -> Boolean
): Boolean {
  return try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val resolver = context.contentResolver
      val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
      }
      val uri = resolver.insert(collection, values) ?: return false
      try {
        val written = resolver.openOutputStream(uri)?.use { out -> writePayload(out) } ?: false
        if (!written) {
          resolver.delete(uri, null, null)
          return false
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
      } catch (e: Exception) {
        try {
          resolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
        throw e
      }
    } else {
      val hasStoragePermission = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
      if (!hasStoragePermission) return false

      @Suppress("DEPRECATION")
      val root = Environment.getExternalStoragePublicDirectory(legacyDirectory)
      val targetDir = java.io.File(root, "Quran")
      if (!targetDir.exists() && !targetDir.mkdirs()) return false
      val targetFile = java.io.File(targetDir, filename)
      val written = java.io.FileOutputStream(targetFile).use { out -> writePayload(out) }
      if (!written) {
        targetFile.delete()
        return false
      }
      android.media.MediaScannerConnection.scanFile(
        context.applicationContext,
        arrayOf(targetFile.absolutePath),
        arrayOf(mimeType),
        null
      )
      true
    }
  } catch (e: Exception) {
    Timber.e(e, "Failed to save %s to gallery", logTag)
    false
  }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
  if (bitmap.isRecycled) return false
  val filename = "Quran_Ayah_${System.currentTimeMillis()}.png"
  return saveMediaToGallery(
    context = context,
    collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    legacyDirectory = Environment.DIRECTORY_PICTURES,
    filename = filename,
    mimeType = "image/png",
    relativePath = "${Environment.DIRECTORY_PICTURES}/Quran",
    logTag = "image"
  ) { out ->
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
  }
}

private fun saveVideoToGallery(context: Context, videoFile: java.io.File): Boolean {
  if (!videoFile.exists() || videoFile.length() <= 0L) return false
  return saveMediaToGallery(
    context = context,
    collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
    legacyDirectory = Environment.DIRECTORY_MOVIES,
    filename = videoFile.name,
    mimeType = "video/mp4",
    relativePath = "${Environment.DIRECTORY_MOVIES}/Quran",
    logTag = "video"
  ) { out ->
    videoFile.inputStream().use { input -> input.copyTo(out) > 0L }
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
  val isKn = data.isKannada || context.resources.configuration.locales[0]?.language == "kn"
  val isLowRamDevice = remember {
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true
  }
  val previewRenderWidthPx = if (isLowRamDevice) 720 else 1080
  val imageExportWidthPx = if (isLowRamDevice) 1080 else 1440

  // Videos tab is default when Share as Video; Photos tab is default when Share as Image
  var selectedTheme by remember { mutableStateOf(AyahCardTheme.EMERALD) }
  var selectedAspectRatio by remember { mutableStateOf(AyahCardAspectRatio.STORY) }
  var backgroundMode by remember { mutableIntStateOf(if (isVideoMode) 2 else 1) } // 0 = Colors, 1 = Photos, 2 = Videos
  var selectedRemotePhotoItem by remember { mutableStateOf<RemoteAyahBackgroundItem?>(null) }
  var downloadedCloudPhoto by remember { mutableStateOf<Bitmap?>(null) }
  var photoDownloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

  var selectedRemoteVideoItem by remember { mutableStateOf<RemoteAyahBackgroundItem?>(null) }
  var downloadedCloudVideoFile by remember { mutableStateOf<File?>(null) }
  var loadingVideoThumbnail by remember { mutableStateOf<Bitmap?>(null) }
  var isLoadingRemoteVideo by remember { mutableStateOf(false) }
  var remoteVideoProgress by remember { mutableStateOf<Int?>(null) }
  var videoDownloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
  var cloudSheetTab by remember { mutableIntStateOf(if (isVideoMode) 1 else 0) } // 0 = Photos, 1 = Videos

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
  var captionMode by remember { mutableStateOf(AyahShareCaptionMode.LINK_ONLY) }

  val hasMultiAyahSlides = data.ayahSlides.size > 1
  var syncAyahWithAudio by remember { mutableStateOf(isVideoMode && hasMultiAyahSlides) }
  var activePreviewSlideIndex by remember { mutableIntStateOf(0) }
  var selectedVideoQuality by remember { mutableStateOf(AyahVideoExportQuality.STANDARD_720P) }
  var videoResolutionFilter by remember { mutableStateOf(RemoteVideoResolutionFilter.ALL) }
  LaunchedEffect(isVideoMode) {
    if (isVideoMode) {
      selectedVideoQuality = withContext(Dispatchers.IO) {
        AyahVideoExporter(context).recommendedExportQuality()
      }
    }
  }

  val repository = remember { RemoteBackgroundRepository(context) }
  var catalogItems by remember { mutableStateOf<List<RemoteAyahBackgroundItem>>(emptyList()) }
  val photoCatalogItems = remember(catalogItems) { catalogItems.filter { !it.isVideo } }
  val videoCatalogItems = remember(catalogItems) { catalogItems.filter { it.isVideo } }
  var isLoadingCatalog by remember { mutableStateOf(false) }
  var isLoadingRemoteImage by remember { mutableStateOf(false) }
  var downloadedPhotoIdSet by remember { mutableStateOf<Set<String>>(emptySet()) }
  var downloadedVideoIdSet by remember { mutableStateOf<Set<String>>(emptySet()) }
  LaunchedEffect(photoCatalogItems, downloadedCloudPhoto, isLoadingRemoteImage) {
    downloadedPhotoIdSet = repository.filterCachedPhotoIds(photoCatalogItems)
  }
  LaunchedEffect(videoCatalogItems, downloadedCloudVideoFile, isLoadingRemoteVideo) {
    downloadedVideoIdSet = repository.filterCachedVideoIds(videoCatalogItems)
  }
  val downloadedPhotoCatalogItems = remember(photoCatalogItems, downloadedPhotoIdSet) {
    photoCatalogItems.filter { it.id in downloadedPhotoIdSet }
  }
  val downloadedVideoCatalogItems = remember(videoCatalogItems, downloadedVideoIdSet) {
    videoCatalogItems.filter { it.id in downloadedVideoIdSet }
  }
  fun buildInlineCatalogItems(
    downloaded: List<RemoteAyahBackgroundItem>,
    all: List<RemoteAyahBackgroundItem>
  ): List<RemoteAyahBackgroundItem> {
    val minimumInlineCount = 4
    if (downloaded.size >= minimumInlineCount) return downloaded
    val downloadedIds = downloaded.map { it.id }.toSet()
    return downloaded + all.filter { it.id !in downloadedIds }.take(minimumInlineCount - downloaded.size)
  }
  val inlinePhotoCatalogItems = remember(downloadedPhotoCatalogItems, photoCatalogItems) {
    buildInlineCatalogItems(downloadedPhotoCatalogItems, photoCatalogItems)
  }
  val inlineVideoCatalogItems = remember(downloadedVideoCatalogItems, videoCatalogItems) {
    buildInlineCatalogItems(downloadedVideoCatalogItems, videoCatalogItems)
  }
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
  var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
  val targetEndAyah = if (data.endSura == data.startSura && data.endAyah >= data.startAyah) {
    data.endAyah
  } else {
    data.startAyah
  }
  val ayahListPairs = remember(data) {
    data.ayahSlides.map { it.sura to it.ayah }
  }

  fun selectCloudPhotoItem(item: RemoteAyahBackgroundItem) {
    if (item.imageUrl.isBlank()) return
    selectedRemotePhotoItem = item
    backgroundMode = 1
    photoScale = 1.0f

    val cacheKey = "full_${item.id}"
    photoDownloadJob?.cancel()
    if (!repository.hasCachedImage(cacheKey)) {
      downloadedCloudPhoto = null
    }
    isLoadingRemoteImage = true
    photoDownloadJob = scope.launch {
      try {
        val fullBmp = repository.loadImage(url = item.imageUrl, cacheKey = cacheKey)
        if (selectedRemotePhotoItem?.id == item.id) {
          if (fullBmp != null) {
            downloadedCloudPhoto = fullBmp
          } else {
            selectedRemotePhotoItem = null
            snackbarHostState.showSnackbar(context.getString(R.string.share_error_bg_photo))
          }
        }
      } catch (_: kotlinx.coroutines.CancellationException) {
        // User selected another photo
      } finally {
        if (selectedRemotePhotoItem?.id == item.id) {
          isLoadingRemoteImage = false
        }
      }
    }
  }

  fun selectCloudVideoItem(item: RemoteAyahBackgroundItem) {
    val videoUrl = item.videoUrl ?: return
    selectedRemoteVideoItem = item
    backgroundMode = 2
    photoScale = 1.0f

    val cacheKey = "vid_${item.id}"
    val existing = repository.getCachedVideoFile(cacheKey)
    videoDownloadJob?.cancel()
    if (existing != null) {
      downloadedCloudVideoFile = existing
      loadingVideoThumbnail = null
      isLoadingRemoteVideo = false
      remoteVideoProgress = null
      return
    }

    downloadedCloudVideoFile = null
    isLoadingRemoteVideo = true
    remoteVideoProgress = 0
    videoDownloadJob = scope.launch {
      try {
        val thumbJob = launch {
          loadingVideoThumbnail = runCatching {
            repository.loadImage(url = item.thumbnailUrl, cacheKey = "thumb_${item.id}")
          }.getOrNull()
        }
        val downloaded = repository.loadVideo(url = videoUrl, cacheKey = cacheKey) { pct ->
          mainHandler.post {
            if (selectedRemoteVideoItem?.id == item.id) {
              remoteVideoProgress = pct
            }
          }
        }
        thumbJob.join()
        if (selectedRemoteVideoItem?.id == item.id) {
          if (downloaded != null) {
            downloadedCloudVideoFile = downloaded
          } else {
            selectedRemoteVideoItem = null
            snackbarHostState.showSnackbar(context.getString(R.string.share_error_bg_video))
          }
        }
      } catch (_: kotlinx.coroutines.CancellationException) {
        // User selected another item
      } finally {
        if (selectedRemoteVideoItem?.id == item.id) {
          isLoadingRemoteVideo = false
          remoteVideoProgress = null
        }
      }
    }
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
    activePreviewSlideIndex = 0
  }

  DisposableEffect(Unit) {
    onDispose {
      photoDownloadJob?.cancel()
      videoDownloadJob?.cancel()
      stopAudioPreview()
    }
  }

  fun findCueIndexAt(cues: List<AyahVideoExporter.AyahAudioCue>, relMs: Long): Int {
    for (i in cues.indices.reversed()) {
      if (relMs >= cues[i].relativeStartMs) return i
    }
    return 0
  }

  suspend fun playPartWithCueTracking(
    partIndex: Int,
    part: AyahVideoExporter.AudioTrackPart,
    cues: List<AyahVideoExporter.AyahAudioCue>,
    partCumulativeOffsetMs: Long
  ) {
    val player = MediaPlayer()
    mediaPlayer?.release()
    mediaPlayer = player

    try {
      val cuesInPart = cues.withIndex().filter { it.value.partIndex == partIndex }
      if (cuesInPart.size == 1 && data.ayahSlides.isNotEmpty()) {
        val cue = cuesInPart[0].value
        val slideIdx = data.ayahSlides.indexOfFirst { it.sura == cue.sura && it.ayah == cue.ayah }
          .takeIf { it >= 0 } ?: cuesInPart[0].index
        activePreviewSlideIndex = slideIdx.coerceIn(0, data.ayahSlides.lastIndex)
      }

      var completed = false
      player.setDataSource(part.file.absolutePath)
      player.prepare()
      if (part.startMs > 0L) {
        player.seekTo(part.startMs.toInt())
      }
      player.setOnCompletionListener { completed = true }
      player.setOnErrorListener { _, _, _ ->
        completed = true
        true
      }
      player.start()

      while (!completed && mediaPlayer === player) {
        val posMs = runCatching { player.currentPosition.toLong() }.getOrDefault(part.startMs)
        if (part.endMs != Long.MAX_VALUE && posMs >= part.endMs) {
          break
        }
        if (cuesInPart.size > 1 && data.ayahSlides.isNotEmpty()) {
          val relMs = partCumulativeOffsetMs + maxOf(0L, posMs - part.startMs)
          val cueIdx = findCueIndexAt(cues, relMs)
          val cue = cues[cueIdx]
          val slideIdx = data.ayahSlides.indexOfFirst { it.sura == cue.sura && it.ayah == cue.ayah }
            .takeIf { it >= 0 } ?: cueIdx
          val clamped = slideIdx.coerceIn(0, data.ayahSlides.lastIndex)
          if (activePreviewSlideIndex != clamped) {
            activePreviewSlideIndex = clamped
          }
        }
        delay(60L)
      }
    } finally {
      if (mediaPlayer === player) {
        runCatching { player.stop() }
        runCatching { player.release() }
        mediaPlayer = null
      } else {
        runCatching { player.release() }
      }
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
    previewAudioStatusText = context.getString(R.string.share_loading_audio)
    activePreviewSlideIndex = 0
    previewJob = scope.launch {
      try {
        val segment = try {
          withContext(Dispatchers.IO) {
            AyahVideoExporter(context).resolveAudioForAyah(
              sura = data.startSura,
              startAyah = data.startAyah,
              endAyah = targetEndAyah,
              qariId = selectedQariId,
              ayahList = ayahListPairs
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
        isPlayingAudioPreview = true
        val parts = segment.parts.ifEmpty {
          listOf(AyahVideoExporter.AudioTrackPart(segment.file, segment.startMs, segment.endMs))
        }
        var partCumulativeOffsetMs = 0L
        for (partIdx in parts.indices) {
          val part = parts[partIdx]
          playPartWithCueTracking(partIdx, part, segment.ayahCues, partCumulativeOffsetMs)
          val cuesInPart = segment.ayahCues.filter { it.partIndex == partIdx }
          val lastEnd = cuesInPart.lastOrNull()?.relativeEndMs
          if (lastEnd != null && lastEnd != Long.MAX_VALUE) {
            partCumulativeOffsetMs = lastEnd
          }
        }
        stopAudioPreview()
      } catch (_: kotlinx.coroutines.CancellationException) {
        // Ignore user-initiated cancellation
      } catch (e: Exception) {
        Timber.e(e, "Failed to load audio preview")
        stopAudioPreview()
        snackbarHostState.showSnackbar(
          context.getString(R.string.share_error_audio_preview_reason, e.message ?: "error")
        )
      }
    }
  }

  val isLiveVideoBackground = isVideoMode && backgroundMode == 2 && downloadedCloudVideoFile != null
  val activePhotoBitmap: Bitmap? = when {
    backgroundMode == 0 -> null
    backgroundMode == 2 -> if (downloadedCloudVideoFile == null) loadingVideoThumbnail else null
    else -> downloadedCloudPhoto
  }
  val displayedCardData = remember(data, isVideoMode, syncAyahWithAudio, activePreviewSlideIndex) {
    if (isVideoMode && syncAyahWithAudio && data.ayahSlides.isNotEmpty()) {
      data.ayahSlides[activePreviewSlideIndex.coerceIn(0, data.ayahSlides.lastIndex)].toCardData(isKn)
    } else {
      data
    }
  }

  var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

  // Fetch cloud background catalog in background and auto-select active cached/starter background
  LaunchedEffect(Unit) {
    suspend fun applyCatalog(items: List<RemoteAyahBackgroundItem>) {
      catalogItems = items
      val cachedPhotos = repository.filterCachedPhotoIds(items)
      val cachedVideos = repository.filterCachedVideoIds(items)
      downloadedPhotoIdSet = cachedPhotos
      downloadedVideoIdSet = cachedVideos
      if (isVideoMode && backgroundMode == 2 && selectedRemoteVideoItem == null) {
        val targetVideo = items.firstOrNull { it.isVideo && it.id in cachedVideos }
          ?: items.firstOrNull { it.isVideo }
        targetVideo?.let { selectCloudVideoItem(it) }
      } else if (backgroundMode == 1 && selectedRemotePhotoItem == null) {
        val targetPhoto = items.firstOrNull { !it.isVideo && it.id in cachedPhotos }
          ?: items.firstOrNull { !it.isVideo }
        targetPhoto?.let { selectCloudPhotoItem(it) }
      }
    }

    isLoadingCatalog = true
    val cached = repository.cachedCatalogItems()
    if (cached.isNotEmpty()) {
      applyCatalog(cached)
    }
    val fetched = runCatching { repository.fetchCatalog() }.getOrDefault(emptyList())
    if (fetched.isNotEmpty()) {
      applyCatalog(fetched)
    }
    isLoadingCatalog = false
  }

  // Re-render preview whenever any customization state changes
  LaunchedEffect(
    displayedCardData,
    selectedTheme,
    selectedAspectRatio,
    backgroundMode,
    isLiveVideoBackground,
    selectedRemotePhotoItem,
    downloadedCloudPhoto,
    loadingVideoThumbnail,
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
    photoScale
  ) {
    val rendered = withContext(Dispatchers.Default) {
      try {
        AyahCardBitmapRenderer.render(
          context = context,
          data = displayedCardData,
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
          photoBackground = activePhotoBitmap?.takeIf { !it.isRecycled },
          photoScale = photoScale,
          transparentBackground = isLiveVideoBackground,
          cardWidthPx = previewRenderWidthPx
        )
      } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        Timber.e(t, "Failed to render preview bitmap")
        null
      }
    }
    if (rendered != null) {
      previewBitmap = rendered
    }
  }

  fun renderCardBitmap(
    cardData: AyahShareCardData,
    widthPx: Int,
    photoBg: Bitmap?,
    transparentBg: Boolean,
    heightPxOverride: Int? = null
  ): Bitmap {
    return AyahCardBitmapRenderer.render(
      context = context,
      data = cardData,
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
      photoBackground = photoBg,
      photoScale = photoScale,
      transparentBackground = transparentBg,
      cardWidthPx = widthPx,
      cardHeightPxOverride = heightPxOverride
    )
  }

  suspend fun ensureSelectedBackgroundVideoFile(): File? {
    if (!isVideoMode || backgroundMode != 2) return null
    downloadedCloudVideoFile?.let { if (it.exists() && it.length() > 0L) return it }
    val item = selectedRemoteVideoItem ?: return null
    val url = item.videoUrl ?: return null
    val dlText = context.getString(R.string.share_downloading_bg_video)
    postExportProgress(AyahVideoExporter.VideoExportProgress(dlText, 0))
    return repository.loadVideo(url = url, cacheKey = "vid_${item.id}") { pct ->
      postExportProgress(AyahVideoExporter.VideoExportProgress(dlText, pct))
    }
  }

  suspend fun exportCurrentMediaFile(): File = withContext(Dispatchers.IO) {
    if (isVideoMode && data.startSura > 0 && data.startAyah > 0) {
      val videoBgFile = ensureSelectedBackgroundVideoFile()
      val useTransparentOverlay = videoBgFile != null
      val staticBg = if (useTransparentOverlay) null else activePhotoBitmap

      val exporter = AyahVideoExporter(context)
      val segment = exporter.resolveAudioForAyah(
        sura = data.startSura,
        startAyah = data.startAyah,
        endAyah = targetEndAyah,
        qariId = selectedQariId,
        ayahList = ayahListPairs
      ) { p -> postExportProgress(p) }

      val baseWidth = 1080
      val baseHeight = selectedAspectRatio.heightFor(baseWidth.toFloat()).roundToInt()
      val shouldSyncSlides = syncAyahWithAudio && data.ayahSlides.size > 1

      exporter.exportMp4WithSlides(
        baseWidth = baseWidth,
        baseHeight = baseHeight,
        segment = segment,
        backgroundVideoFile = videoBgFile,
        backgroundVideoScale = photoScale,
        slideFactory = { resolvedCues, _, _ ->
          if (shouldSyncSlides && resolvedCues.isNotEmpty()) {
            resolvedCues.mapIndexed { idx, cue ->
              val slideData = data.ayahSlides.firstOrNull {
                it.sura == cue.sura && it.ayah == cue.ayah
              } ?: data.ayahSlides.getOrElse(idx) { data.ayahSlides.first() }
              val slideCardData = slideData.toCardData(isKn)
              AyahVideoExporter.TimedSlideSource(
                startMs = cue.relativeStartMs,
                endMs = cue.relativeEndMs
              ) { w, h ->
                renderCardBitmap(
                  cardData = slideCardData,
                  widthPx = w,
                  photoBg = staticBg,
                  transparentBg = useTransparentOverlay,
                  heightPxOverride = h
                )
              }
            }
          } else {
            listOf(
              AyahVideoExporter.TimedSlideSource(0L, Long.MAX_VALUE) { w, h ->
                renderCardBitmap(
                  cardData = data,
                  widthPx = w,
                  photoBg = staticBg,
                  transparentBg = useTransparentOverlay,
                  heightPxOverride = h
                )
              }
            )
          }
        },
        recycleProducedBitmaps = true,
        requestedQuality = selectedVideoQuality,
        onProgress = { p -> postExportProgress(p) }
      )
    } else {
      val highRes = withContext(Dispatchers.Default) {
        renderCardBitmap(
          cardData = data,
          widthPx = imageExportWidthPx,
          photoBg = activePhotoBitmap?.takeIf { !it.isRecycled },
          transparentBg = false
        )
      }
      try {
        AyahCardBitmapRenderer.savePngToShareCache(context, highRes)
      } finally {
        highRes.recycle()
      }
    }
  }

  fun shareCard() {
    if (isExporting) return
    stopAudioPreview()
    isExporting = true
    exportActionIsSave = false
    exportStatusText = context.getString(R.string.share_preparing)
    exportProgressPercent = null
    scope.launch {
      try {
        val file = exportCurrentMediaFile()
        val shareUri = FileProvider.getUriForFile(
          context,
          context.getString(R.string.file_authority),
          file
        )

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
            context.getString(if (isVideoMode) R.string.share_video_title else R.string.share_card_title)
          )
        )
      } catch (e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Timber.e(e, "Failed to share card or video")
        val reason = e.message?.takeIf { it.isNotBlank() } ?: "error"
        val msgRes = if (isVideoMode) R.string.share_error_create_video_reason else R.string.share_error_share_reason
        snackbarHostState.showSnackbar(context.getString(msgRes, reason))
      } finally {
        mainHandler.removeCallbacksAndMessages(null)
        isExporting = false
        exportActionIsSave = false
        exportStatusText = null
        exportProgressPercent = null
      }
    }
  }

  fun performSaveToPhotos() {
    if (isExporting) return
    stopAudioPreview()
    isExporting = true
    exportActionIsSave = true
    exportStatusText = context.getString(R.string.share_preparing)
    exportProgressPercent = null
    scope.launch {
      try {
        val saved = if (isVideoMode && data.startSura > 0 && data.startAyah > 0) {
          val mp4 = exportCurrentMediaFile()
          try {
            withContext(Dispatchers.IO) {
              saveVideoToGallery(context, mp4)
            }
          } finally {
            mp4.delete()
          }
        } else {
          val highRes = withContext(Dispatchers.Default) {
            renderCardBitmap(
              cardData = data,
              widthPx = imageExportWidthPx,
              photoBg = activePhotoBitmap?.takeIf { !it.isRecycled },
              transparentBg = false
            )
          }
          try {
            withContext(Dispatchers.IO) {
              saveBitmapToGallery(context, highRes)
            }
          } finally {
            highRes.recycle()
          }
        }
        val msgRes = when {
          saved && isVideoMode -> R.string.share_saved_video
          saved -> R.string.share_saved_photos
          else -> R.string.share_error_save
        }
        snackbarHostState.showSnackbar(context.getString(msgRes))
      } catch (e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Timber.e(e, "Failed to save card or video")
        val reason = e.message?.takeIf { it.isNotBlank() } ?: "error"
        snackbarHostState.showSnackbar(context.getString(R.string.share_error_save_reason, reason))
      } finally {
        mainHandler.removeCallbacksAndMessages(null)
        isExporting = false
        exportActionIsSave = false
        exportStatusText = null
        exportProgressPercent = null
      }
    }
  }

  val storagePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      performSaveToPhotos()
    } else {
      scope.launch {
        snackbarHostState.showSnackbar(context.getString(R.string.share_error_save))
      }
    }
  }

  fun saveToPhotos() {
    if (isExporting) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
      if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
      ) {
        storagePermissionLauncher.launch(permission)
        return
      }
    }
    performSaveToPhotos()
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
              contentDescription = stringResource(R.string.share_close)
            )
          }
        },
        title = {
          Text(
            text = stringResource(if (isVideoMode) R.string.share_video_title else R.string.share_card_title),
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
        if (bmp != null && !bmp.isRecycled) {
          Box(
            modifier = Modifier
              .shadow(10.dp, RoundedCornerShape(0.dp))
              .clip(RoundedCornerShape(0.dp))
              .aspectRatio(1f / selectedAspectRatio.heightRatio, matchHeightConstraintsFirst = true)
          ) {
            val activeVidFile = downloadedCloudVideoFile
            if (isLiveVideoBackground && activeVidFile != null) {
              LoopingBackgroundVideoView(
                videoFile = activeVidFile,
                zoom = photoScale,
                modifier = Modifier.fillMaxSize()
              )
            }
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = stringResource(R.string.share_card_preview_desc),
              contentScale = ContentScale.Fit,
              filterQuality = FilterQuality.High,
              modifier = Modifier.fillMaxSize()
            )
            if (isVideoMode) {
              Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(8.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (syncAyahWithAudio && hasMultiAyahSlides) {
                      "${activePreviewSlideIndex + 1}/${data.ayahSlides.size}"
                    } else {
                      "MP4"
                    },
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }

              Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                  .align(Alignment.BottomCenter)
                  .padding(bottom = 44.dp)
                  .clickable { toggleAudioPreview() }
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  if (isLoadingAudioPreview) {
                    CircularProgressIndicator(
                      color = Color.White,
                      strokeWidth = 2.dp,
                      modifier = Modifier.size(14.dp)
                    )
                  } else {
                    Icon(
                      imageVector = if (isPlayingAudioPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(15.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = when {
                      isLoadingAudioPreview -> stringResource(R.string.share_downloading)
                      isPlayingAudioPreview -> previewQariName ?: stringResource(R.string.share_stop_preview)
                      else -> previewQariName ?: stringResource(R.string.share_preview_recitation)
                    },
                    color = Color.White,
                    fontSize = 11.sp,
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
                text = stringResource(R.string.share_loading_bg),
                color = Color.White,
                fontSize = 12.sp
              )
            }
          }
        }
      }

      if (isVideoMode && isLoadingAudioPreview) {
        VideoProgressBanner(
          statusText = previewAudioStatusText ?: stringResource(R.string.share_downloading_audio),
          percent = previewAudioPercent,
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }

      if (isVideoMode && isLoadingRemoteVideo) {
        VideoProgressBanner(
          statusText = stringResource(R.string.share_downloading_bg_video),
          percent = remoteVideoProgress,
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }

      if (isVideoMode && availableQaris.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = stringResource(R.string.share_reciter_label),
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

      if (isVideoMode) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = pickLocalizedLabel(context, "Video Quality", "ವೀಡಿಯೊ ಗುಣಮಟ್ಟ", "جودة الفيديو"),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF495057),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
          )
          SegmentedControlRow(
            options = AyahVideoExportQuality.entries.map { it.displayName(context) },
            selectedIndex = AyahVideoExportQuality.entries.indexOf(selectedVideoQuality).coerceAtLeast(0),
            onSelect = { idx ->
              selectedVideoQuality = AyahVideoExportQuality.entries.getOrElse(idx) { AyahVideoExportQuality.HIGH_1080P }
            },
            modifier = Modifier.padding(horizontal = 16.dp)
          )
        }
      }

      if (isVideoMode && hasMultiAyahSlides) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color.White,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          ToggleRow(
            label = stringResource(R.string.share_sync_ayah_audio),
            checked = syncAyahWithAudio,
            onCheckedChange = { checked ->
              syncAyahWithAudio = checked
              if (!checked) activePreviewSlideIndex = 0
            }
          )
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
              text = ratio.displayName(context),
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

      // 2. Background Mode Segmented Control: Colors vs Photos (vs Videos in video mode)
      val bgModeOptions = if (isVideoMode) {
        listOf(
          stringResource(R.string.share_bg_colors),
          stringResource(R.string.share_bg_photos),
          stringResource(R.string.share_bg_videos)
        )
      } else {
        listOf(
          stringResource(R.string.share_bg_colors),
          stringResource(R.string.share_bg_photos)
        )
      }
      SegmentedControlRow(
        options = bgModeOptions,
        selectedIndex = backgroundMode.coerceIn(0, bgModeOptions.lastIndex),
        onSelect = { newMode ->
          backgroundMode = newMode
          if (newMode == 1 && selectedRemotePhotoItem == null) {
            (downloadedPhotoCatalogItems.firstOrNull() ?: photoCatalogItems.firstOrNull())?.let {
              selectCloudPhotoItem(it)
            }
          } else if (newMode == 2 && selectedRemoteVideoItem == null) {
            (downloadedVideoCatalogItems.firstOrNull() ?: videoCatalogItems.firstOrNull())?.let {
              selectCloudVideoItem(it)
            }
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
      } else if (backgroundMode == 2 && isVideoMode) {
        CloudBackgroundSwatchesRow(
          items = inlineVideoCatalogItems,
          isKannada = isKn,
          repository = repository,
          selectedId = selectedRemoteVideoItem?.id,
          downloadedIds = downloadedVideoCatalogItems.map { it.id }.toSet(),
          isLoadingInline = inlineVideoCatalogItems.isEmpty() && (isLoadingRemoteVideo || isLoadingCatalog),
          moreDescription = stringResource(R.string.share_more_cloud_videos_desc),
          browseLabel = stringResource(R.string.share_browse_cloud_videos),
          downloadedBadgeIcon = Icons.Default.Videocam,
          onBrowseMore = {
            cloudSheetTab = 1
            showCloudSheet = true
          },
          onSelectItem = { vidItem -> selectCloudVideoItem(vidItem) }
        )

        if (isLiveVideoBackground) {
          StepperCard(
            label = stringResource(R.string.share_video_zoom),
            valueText = "${(photoScale * 100).roundToInt()}%",
            canDecrease = photoScale > 1.05f,
            canIncrease = photoScale < 1.95f,
            onDecrease = { photoScale = (photoScale - 0.1f).coerceAtLeast(1.0f) },
            onIncrease = { photoScale = (photoScale + 0.1f).coerceAtMost(2.0f) },
            modifier = Modifier.padding(horizontal = 16.dp)
          )
        }
      } else {
        CloudBackgroundSwatchesRow(
          items = inlinePhotoCatalogItems,
          isKannada = isKn,
          repository = repository,
          selectedId = selectedRemotePhotoItem?.id,
          downloadedIds = downloadedPhotoCatalogItems.map { it.id }.toSet(),
          isLoadingInline = inlinePhotoCatalogItems.isEmpty() && (isLoadingRemoteImage || isLoadingCatalog),
          moreDescription = stringResource(R.string.share_more_cloud_backgrounds_desc),
          browseLabel = stringResource(R.string.share_browse_cloud_photos),
          downloadedBadgeIcon = Icons.Default.Photo,
          onBrowseMore = {
            cloudSheetTab = 0
            showCloudSheet = true
          },
          onSelectItem = { photoItem -> selectCloudPhotoItem(photoItem) }
        )

        if (downloadedCloudPhoto != null) {
          StepperCard(
            label = stringResource(R.string.share_photo_zoom),
            valueText = "${(photoScale * 100).roundToInt()}%",
            canDecrease = photoScale > 1.05f,
            canIncrease = photoScale < 1.95f,
            onDecrease = { photoScale = (photoScale - 0.1f).coerceAtLeast(1.0f) },
            onIncrease = { photoScale = (photoScale + 0.1f).coerceAtMost(2.0f) },
            modifier = Modifier.padding(horizontal = 16.dp)
          )
        }
      }

      // 3. Text Size Stepper (20% to 200%)
      StepperCard(
        label = stringResource(R.string.share_text_size),
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
              text = stringResource(R.string.share_font_style),
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium
            )
            Text(
              text = if (colorTarget == AyahCardTextColorTarget.ARABIC) {
                selectedArabicFont.displayName(context)
              } else {
                selectedTranslationFont.displayName(context)
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
                  text = fontOption.displayName(context),
                  selected = selected,
                  onClick = { selectedArabicFont = fontOption }
                )
              }
            } else {
              AyahCardTranslationFont.entries.forEach { fontOption ->
                val selected = selectedTranslationFont == fontOption
                PillChip(
                  text = fontOption.displayName(context),
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
              text = stringResource(R.string.share_text_color),
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
                    contentDescription = stringResource(R.string.share_reset),
                    tint = Color(0xFF0E5A47),
                    modifier = Modifier.size(12.dp)
                  )
                  Text(
                    text = stringResource(R.string.share_reset),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0E5A47)
                  )
                }
              }
              Text(
                text = activeColorScheme.displayName(context),
                fontSize = 12.sp,
                color = Color(0xFF6C757D)
              )
            }
          }

          SegmentedControlRow(
            options = AyahCardTextColorTarget.entries.map { it.displayName(context) },
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
                contentDescription = stringResource(R.string.share_custom_color),
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
            label = stringResource(R.string.share_show_arabic),
            checked = showArabic,
            onCheckedChange = { showArabic = it }
          )
          HorizontalDivider(color = Color(0xFFE9ECEF), modifier = Modifier.padding(start = 16.dp))
          ToggleRow(
            label = stringResource(R.string.share_show_translation),
            checked = showTranslation,
            onCheckedChange = { showTranslation = it }
          )
          if (data.footnotes.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFFE9ECEF), modifier = Modifier.padding(start = 16.dp))
            ToggleRow(
              label = stringResource(R.string.share_include_footnotes),
              checked = showFootnotes,
              onCheckedChange = { showFootnotes = it }
            )
          }
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
          text = stringResource(R.string.share_accompanying_text),
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF6C757D),
          modifier = Modifier.padding(start = 4.dp)
        )
        SegmentedControlRow(
          options = AyahShareCaptionMode.entries.map { it.displayName(context) },
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
            statusText = exportStatusText ?: stringResource(R.string.share_creating_video),
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
                text = stringResource(R.string.share_btn_preparing_video),
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
              text = stringResource(if (isVideoMode) R.string.share_btn_share_video else R.string.share_btn_share),
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
              text = stringResource(if (isVideoMode) R.string.share_btn_saving_video else R.string.share_btn_saving),
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
              text = stringResource(if (isVideoMode) R.string.share_btn_save_video else R.string.share_btn_save_photos),
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gridState = rememberLazyGridState()
    val hdVideoItems = remember(videoCatalogItems) { videoCatalogItems.filter { it.isHdVideo } }
    val standardVideoItems = remember(videoCatalogItems) { videoCatalogItems.filter { !it.isHdVideo } }
    val activeSheetItems = remember(
      isVideoMode,
      cloudSheetTab,
      videoResolutionFilter,
      videoCatalogItems,
      hdVideoItems,
      standardVideoItems,
      photoCatalogItems
    ) {
      when {
        isVideoMode && cloudSheetTab == 1 && videoCatalogItems.isNotEmpty() -> when (videoResolutionFilter) {
          RemoteVideoResolutionFilter.ALL -> videoCatalogItems
          RemoteVideoResolutionFilter.HD_1080P -> hdVideoItems
          RemoteVideoResolutionFilter.STANDARD_720P -> standardVideoItems
        }
        else -> photoCatalogItems
      }
    }
    ModalBottomSheet(
      onDismissRequest = { showCloudSheet = false },
      sheetState = sheetState
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .padding(bottom = 16.dp)
      ) {
        Text(
          text = stringResource(R.string.share_cloud_backgrounds),
          fontSize = 18.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(bottom = 12.dp)
        )

        if (isVideoMode && videoCatalogItems.isNotEmpty()) {
          SegmentedControlRow(
            options = listOf(
              stringResource(R.string.share_bg_photos),
              stringResource(R.string.share_bg_videos)
            ),
            selectedIndex = cloudSheetTab.coerceIn(0, 1),
            onSelect = { cloudSheetTab = it },
            modifier = Modifier.padding(bottom = 8.dp)
          )

          if (cloudSheetTab == 1) {
            SegmentedControlRow(
              options = listOf(
                "${RemoteVideoResolutionFilter.ALL.displayName(context)} (${videoCatalogItems.size})",
                "${RemoteVideoResolutionFilter.HD_1080P.displayName(context)} (${hdVideoItems.size})",
                "${RemoteVideoResolutionFilter.STANDARD_720P.displayName(context)} (${standardVideoItems.size})"
              ),
              selectedIndex = RemoteVideoResolutionFilter.entries.indexOf(videoResolutionFilter).coerceAtLeast(0),
              onSelect = { idx ->
                videoResolutionFilter = RemoteVideoResolutionFilter.entries.getOrElse(idx) {
                  RemoteVideoResolutionFilter.ALL
                }
              },
              modifier = Modifier.padding(bottom = 10.dp)
            )
          }
        }

        if (isLoadingCatalog && catalogItems.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(220.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = Color(0xFF0E5A47))
          }
        } else if (activeSheetItems.isEmpty()) {
          val emptyMessage = if (
            isVideoMode &&
            cloudSheetTab == 1 &&
            videoResolutionFilter == RemoteVideoResolutionFilter.HD_1080P
          ) {
            pickLocalizedLabel(
              context,
              "1080p HD videos coming soon",
              "1080p HD ವೀಡಿಯೊಗಳು ಶೀಘ್ರದಲ್ಲೇ ಬರಲಿವೆ",
              "فيديوهات 1080p HD قريباً"
            )
          } else {
            stringResource(R.string.share_no_cloud_backgrounds)
          }
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(160.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = emptyMessage,
              color = Color.Gray
            )
          }
        } else {
          // Consume leftover downward fling velocity inside the grid so fast scrolling back to the top
          // doesn't accidentally swipe-dismiss the ModalBottomSheet (drag handle / header still dismisses).
          val absorbGridFlingConnection = remember {
            object : NestedScrollConnection {
              override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
              ): Offset {
                return if (source == NestedScrollSource.SideEffect && available.y > 0f) {
                  Offset(0f, available.y)
                } else {
                  Offset.Zero
                }
              }

              override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
              ): Velocity {
                return if (available.y > 0f) Velocity(0f, available.y) else Velocity.Zero
              }
            }
          }
          LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(480.dp)
              .nestedScroll(absorbGridFlingConnection)
          ) {
            items(activeSheetItems, key = { it.id }) { item ->
              val isSelected = if (item.isVideo) {
                backgroundMode == 2 && selectedRemoteVideoItem?.id == item.id
              } else {
                backgroundMode == 1 && selectedRemotePhotoItem?.id == item.id
              }
              val isDownloaded = if (item.isVideo) {
                item.id in downloadedVideoIdSet
              } else {
                item.id in downloadedPhotoIdSet
              }
              RemoteBackgroundGridCell(
                item = item,
                isKannada = isKn,
                repository = repository,
                isSelected = isSelected,
                isDownloaded = isDownloaded,
                onClick = {
                  showCloudSheet = false
                  if (item.isVideo) {
                    selectCloudVideoItem(item)
                  } else {
                    selectCloudPhotoItem(item)
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
      title = stringResource(R.string.share_custom_color),
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
              contentDescription = stringResource(R.string.share_decrease_desc),
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
              contentDescription = stringResource(R.string.share_increase_desc),
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
        Text(stringResource(R.string.share_ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.share_cancel))
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
  isDownloaded: Boolean,
  onClick: () -> Unit
) {
  var thumbBitmap by remember(item.id) {
    mutableStateOf(repository.getCachedBitmap("thumb_${item.id}"))
  }
  var isLoadingThumb by remember(item.id) {
    mutableStateOf(thumbBitmap == null)
  }

  LaunchedEffect(item.id) {
    if (thumbBitmap == null) {
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
    if (bmp != null && !bmp.isRecycled) {
      val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
      Image(
        bitmap = imageBitmap,
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

    if (item.isVideo && item.isHdVideo) {
      Surface(
        color = Color(0xFF0E5A47).copy(alpha = 0.92f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(6.dp)
      ) {
        Text(
          text = "HD",
          color = Color.White,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
      }
    }

    if (item.isVideo) {
      item.formattedSizeLabel()?.let { sizeText ->
        Surface(
          color = Color.Black.copy(alpha = 0.65f),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
        ) {
          Text(
            text = sizeText,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
          )
        }
      }
    }

    if (item.isVideo || isDownloaded) {
      Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = CircleShape,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(6.dp)
      ) {
        Icon(
          imageVector = if (isDownloaded) Icons.Default.Check else Icons.Default.Videocam,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier
            .padding(4.dp)
            .size(12.dp)
        )
      }
    }
  }
}

@Composable
private fun CloudBackgroundSwatchesRow(
  items: List<RemoteAyahBackgroundItem>,
  isKannada: Boolean,
  repository: RemoteBackgroundRepository,
  selectedId: String?,
  downloadedIds: Set<String>,
  isLoadingInline: Boolean,
  moreDescription: String,
  browseLabel: String,
  downloadedBadgeIcon: androidx.compose.ui.graphics.vector.ImageVector,
  onBrowseMore: () -> Unit,
  onSelectItem: (RemoteAyahBackgroundItem) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .width(56.dp)
        .clickable(onClick = onBrowseMore)
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
          contentDescription = moreDescription,
          tint = Color(0xFF0E5A47),
          modifier = Modifier.size(20.dp)
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.share_more),
        fontSize = 11.sp,
        color = Color(0xFF495057),
        maxLines = 1
      )
    }

    if (items.isEmpty()) {
      if (isLoadingInline) {
        Box(
          modifier = Modifier.height(48.dp),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = Color(0xFF0E5A47),
            modifier = Modifier.size(22.dp)
          )
        }
      } else {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFF0E5A47).copy(alpha = 0.1f),
          border = BorderStroke(1.dp, Color(0xFF0E5A47).copy(alpha = 0.3f)),
          modifier = Modifier.clickable(onClick = onBrowseMore)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.CloudDownload,
              contentDescription = null,
              tint = Color(0xFF0E5A47),
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = browseLabel,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF0E5A47)
            )
          }
        }
      }
    } else {
      items.forEach { item ->
        CloudMediaSwatchCell(
          item = item,
          isKannada = isKannada,
          repository = repository,
          isSelected = selectedId == item.id,
          isDownloaded = item.id in downloadedIds,
          downloadedBadgeIcon = downloadedBadgeIcon,
          onClick = { onSelectItem(item) }
        )
      }
    }
  }
}

@Composable
private fun CloudMediaSwatchCell(
  item: RemoteAyahBackgroundItem,
  isKannada: Boolean,
  repository: RemoteBackgroundRepository,
  isSelected: Boolean,
  isDownloaded: Boolean,
  downloadedBadgeIcon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit
) {
  var thumbBitmap by remember(item.id) {
    mutableStateOf(repository.getCachedBitmap("thumb_${item.id}"))
  }

  LaunchedEffect(item.id) {
    if (thumbBitmap == null) {
      thumbBitmap = runCatching {
        repository.loadImage(url = item.thumbnailUrl, cacheKey = "thumb_${item.id}")
      }.getOrNull()
    }
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .width(56.dp)
      .clickable(onClick = onClick)
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
      val bmp = thumbBitmap
      if (bmp != null && !bmp.isRecycled) {
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = item.localizedName(isKannada),
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      }
      if (!isDownloaded) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
          )
        }
      } else if (isSelected) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(18.dp)
        )
      } else {
        Icon(
          imageVector = downloadedBadgeIcon,
          contentDescription = null,
          tint = Color.White.copy(alpha = 0.85f),
          modifier = Modifier.size(14.dp)
        )
      }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = item.localizedName(isKannada),
      fontSize = 11.sp,
      color = if (isSelected) Color(0xFF0E5A47) else Color(0xFF495057),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun LoopingBackgroundVideoView(
  videoFile: File,
  zoom: Float,
  modifier: Modifier = Modifier
) {
  key(videoFile.absolutePath) {
    val playerState = remember {
      object {
        var player: MediaPlayer? = null
        var surface: Surface? = null
        var videoWidth = 0
        var videoHeight = 0
        var currentZoom = zoom

        fun applyTransform(tv: TextureView) {
          val viewW = tv.width.toFloat()
          val viewH = tv.height.toFloat()
          if (viewW <= 0f || viewH <= 0f || videoWidth <= 0 || videoHeight <= 0) return
          val viewRatio = viewW / viewH
          val vidRatio = videoWidth.toFloat() / videoHeight.toFloat()
          val z = maxOf(1f, currentZoom)
          val scaleX: Float
          val scaleY: Float
          if (vidRatio > viewRatio) {
            scaleX = (vidRatio / viewRatio) * z
            scaleY = z
          } else {
            scaleX = z
            scaleY = (viewRatio / vidRatio) * z
          }
          val matrix = Matrix()
          matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
          tv.setTransform(matrix)
        }

        fun release() {
          runCatching { player?.stop() }
          runCatching { player?.release() }
          player = null
          runCatching { surface?.release() }
          surface = null
        }
      }
    }

    DisposableEffect(Unit) {
      onDispose {
        playerState.release()
      }
    }

    AndroidView(
      factory = { ctx ->
        TextureView(ctx).apply {
          addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            playerState.applyTransform(this)
          }
          surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
              playerState.release()
              val s = Surface(st)
              playerState.surface = s
              val mp = MediaPlayer()
              playerState.player = mp
              try {
                mp.setDataSource(videoFile.absolutePath)
                mp.setSurface(s)
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                mp.setOnVideoSizeChangedListener { _, vw, vh ->
                  playerState.videoWidth = vw
                  playerState.videoHeight = vh
                  playerState.applyTransform(this@apply)
                }
                mp.setOnPreparedListener {
                  playerState.videoWidth = it.videoWidth
                  playerState.videoHeight = it.videoHeight
                  playerState.applyTransform(this@apply)
                  it.start()
                }
                mp.prepareAsync()
              } catch (e: Exception) {
                Timber.e(e, "Failed to start preview video player")
              }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
              playerState.applyTransform(this@apply)
            }

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
              playerState.release()
              return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
          }
        }
      },
      update = { tv ->
        playerState.currentZoom = zoom
        playerState.applyTransform(tv)
      },
      modifier = modifier
    )
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
