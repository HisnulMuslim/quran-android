package com.quran.labs.androidquran.ui

import android.Manifest
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.preference.PreferenceManager
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager.widget.NonRestoringViewPager
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.quran.data.core.QuranInfo
import com.quran.data.dao.BookmarksDao
import com.quran.data.model.QuranText
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.AyahSelection.AyahRange
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.endSuraAyah
import com.quran.data.model.selection.selectionIndicator
import com.quran.data.model.selection.startSuraAyah
import com.quran.data.model.selection.withXScroll
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.HelpActivity
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.TajweedActivity
import com.quran.labs.androidquran.QuranPreferenceActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.model.QariItem.Companion.fromQari
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.di.component.activity.PagerActivityComponent
import com.quran.labs.androidquran.feature.reading.bridge.AudioStatusRepositoryBridge
import com.quran.labs.androidquran.feature.reading.bridge.DownloadBridge
import com.quran.labs.androidquran.feature.reading.bridge.ReadingEventPresenterBridge
import com.quran.labs.androidquran.feature.reading.presenter.AudioPresenter
import com.quran.labs.androidquran.feature.reading.presenter.RecentPagePresenter
import com.quran.labs.androidquran.feature.reading.presenter.recitation.PagerActivityRecitationPresenter
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.presenter.data.QuranEventLogger
import com.quran.labs.androidquran.presenter.translation.InlineTranslationPresenter
import com.quran.labs.androidquran.presenter.translationlist.TranslationListPresenter
import com.quran.labs.androidquran.service.AudioService
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.PermissionUtil.buildPostPermissionDialog
import com.quran.labs.androidquran.service.util.PermissionUtil.canRequestPostNotificationPermission
import com.quran.labs.androidquran.service.util.PermissionUtil.havePostNotificationPermission
import com.quran.labs.androidquran.service.util.ServiceIntentHelper.getDownloadIntent
import com.quran.labs.androidquran.ui.fragment.AddTagDialog
import com.quran.labs.androidquran.ui.fragment.JumpFragment
import com.quran.labs.androidquran.ui.fragment.TabletFragment
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog.OnBookmarkTagsUpdateListener
import com.quran.labs.androidquran.ui.fragment.TranslationFragment
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.helpers.AyahTracker
import com.quran.labs.androidquran.ui.helpers.JumpDestination
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper
import com.quran.labs.androidquran.ui.helpers.QuranPage
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter
import com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter
import com.quran.labs.androidquran.ui.listener.AudioBarListener
import com.quran.labs.androidquran.ui.util.ToastCompat.makeText
import com.quran.labs.androidquran.ui.util.TranslationsSpinnerAdapter
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.LanguageDialogHelper
import com.quran.labs.androidquran.util.QuranAppUtils
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.ShareUtil
import com.quran.labs.androidquran.view.IconPageIndicator
import com.quran.labs.androidquran.view.QuranSpinner
import com.quran.labs.androidquran.view.SlidingUpPanelLayout
import com.quran.mobile.common.download.DownloadInfoStreams
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.di.QuranReadingActivityComponent
import com.quran.mobile.di.QuranReadingActivityComponentProvider
import com.quran.mobile.di.QuranReadingPageComponent
import com.quran.mobile.di.QuranReadingPageComponentProvider
import com.quran.mobile.feature.audiobar.AudioBarInjector
import com.quran.mobile.feature.audiobar.AudioBarWrapper
import com.quran.mobile.feature.audiobar.presenter.AudioBarEventRepository
import com.quran.mobile.feature.qarilist.QariListWrapper
import com.quran.mobile.feature.qarilist.di.QariListWrapperInjector
import com.quran.mobile.translation.model.LocalTranslation
import com.quran.page.common.factory.PageViewFactoryProvider
import com.quran.page.common.toolbar.AyahToolBar
import com.quran.page.common.toolbar.di.AyahToolBarInjector
import com.quran.reading.common.ReadingEventPresenter
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.observers.DisposableSingleObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

/**
 * Activity that displays the Quran (in Arabic or translation mode).
 *
 *
 * Essentially, this activity consists of a [ViewPager] of Quran pages (using [ ]).
 * [AudioService] is used to handle playing audio, and this is synced with
 * the display of the Quran.
 */
class PagerActivity : AppCompatActivity(), AudioBarListener, OnBookmarkTagsUpdateListener,
  AyahSelectedListener, JumpDestination, QuranReadingActivityComponentProvider,
  QuranReadingPageComponentProvider, AyahToolBarInjector, QariListWrapperInjector,
  AudioBarInjector, ActivityCompat.OnRequestPermissionsResultCallback {
  private var lastPopupTime: Long = 0
  private var isActionBarHidden = true
  private var shouldReconnect = false
  private var showingTranslation = true
  private var needsPermissionToDownloadOver3g = true
  private var promptDialog: AlertDialog? = null
  private var isDualPages = false
  private var promptedForExtraDownload = false
  private var progressDialog: ProgressDialog? = null
  private var isFoldableDeviceOpenAndVertical = false

  private var bookmarksMenuItem: MenuItem? = null

  private var translationNames: Array<String> = emptyArray()
  private var translations: List<LocalTranslation>? = null
  private var activeTranslationsFilesNames: Set<String?>? = null
  private var translationsSpinnerAdapter: TranslationsSpinnerAdapter? = null

  private lateinit var audioStatusBar: AudioBarWrapper
  private lateinit var viewPager: ViewPager
  private lateinit var pagerAdapter: QuranPageAdapter
  private lateinit var ayahToolBar: AyahToolBar
  private lateinit var slidingPanel: SlidingUpPanelLayout
  private lateinit var slidingPager: ViewPager
  private lateinit var slidingPagerAdapter: SlidingPagerAdapter
  private lateinit var translationsSpinner: QuranSpinner
  private lateinit var overlay: FrameLayout
  private lateinit var toolBarArea: View

  private var requestPermissionLauncher: ActivityResultLauncher<String>? = null

  private var isSplitScreen = false

  private lateinit var onClearAyahModeBackCallback: OnBackPressedCallback
  private lateinit var onShowingTranslationBackCallback: OnBackPressedCallback
  private lateinit var onEndSessionBackCallback: OnBackPressedCallback
  private lateinit var onCollapseSearchBackCallback: OnBackPressedCallback
  private var searchMenuItem: MenuItem? = null

  private var lastSelectedTranslationAyah: QuranAyahInfo? = null
  private var lastActivatedLocalTranslations: Array<LocalTranslation> = emptyArray()

  @Inject lateinit var bookmarksDao: BookmarksDao
  @Inject lateinit var recentPagePresenter: RecentPagePresenter
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var quranScreenInfo: QuranScreenInfo
  @Inject lateinit var arabicDatabaseUtils: ArabicDatabaseUtils
  @Inject lateinit var quranAppUtils: QuranAppUtils
  @Inject lateinit var shareUtil: ShareUtil
  @Inject lateinit var audioUtils: AudioUtils
  @Inject lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var quranInfo: QuranInfo
  @Inject lateinit var quranFileUtils: QuranFileUtils
  @Inject lateinit var audioPresenter: AudioPresenter
  @Inject lateinit var quranEventLogger: QuranEventLogger
  @Inject lateinit var audioStatusRepository: AudioStatusRepository
  @Inject lateinit var readingEventPresenter: ReadingEventPresenter
  @Inject lateinit var pageProviderFactoryProvider: PageViewFactoryProvider
  @Inject lateinit var quranPageProvider: com.quran.data.source.PageProvider
  @Inject lateinit var additionalAyahPanels: Set<@JvmSuppressWildcards AyahActionFragmentProvider>
  @Inject lateinit var pagerActivityRecitationPresenter: PagerActivityRecitationPresenter
  @Inject lateinit var translationListPresenter: TranslationListPresenter
  @Inject lateinit var audioBarEventRepository: AudioBarEventRepository
  @Inject lateinit var downloadInfoStreams: DownloadInfoStreams
  @Inject lateinit var qariManager: CurrentQariManager
  @Inject lateinit var downloadBridge: DownloadBridge
  //KQACR7 Share and copy logic change
  @Inject lateinit var translationPresenter: InlineTranslationPresenter

  private lateinit var audioStatusRepositoryBridge: AudioStatusRepositoryBridge
  private lateinit var readingEventPresenterBridge: ReadingEventPresenterBridge
  private lateinit var windowInsetsController: WindowInsetsControllerCompat

  private var translationJob: Job? = null
  private var currentBookmarks: List<Bookmark> = listOf()
  private lateinit var compositeDisposable: CompositeDisposable
  private val foregroundDisposable = CompositeDisposable()
  private val scope = MainScope()

  val pagerActivityComponent: PagerActivityComponent by lazy {
    (application as QuranApplication)
      .applicationComponent
      .activityComponentFactory()
      .generate(this)
      .pagerActivityComponentFactory()
      .generate(this)
  }

  private val handler = PagerHandler(this)

  private class PagerHandler(activity: PagerActivity) : Handler(Looper.getMainLooper()) {
    private val activity = WeakReference(activity)

    override fun handleMessage(msg: Message) {
      val activity = activity.get()
      if (activity != null) {
        if (msg.what == MSG_HIDE_ACTIONBAR) {
          activity.toggleActionBarVisibility(false)
        } else {
          super.handleMessage(msg)
        }
      }
    }
  }

  public override fun onCreate(savedInstanceState: Bundle?) {
    val quranApp = application as QuranApplication
    quranApp.refreshLocale(this, false)

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.auto(
        android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b),
        android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
      )
    )
    super.onCreate(savedInstanceState)

    // field injection
    pagerActivityComponent.inject(this)

    isFoldableDeviceOpenAndVertical =
      savedInstanceState?.getBoolean(LAST_FOLDING_STATE, isFoldableDeviceOpenAndVertical)
        ?: isFoldableDeviceOpenAndVertical

    lifecycleScope.launch(scope.coroutineContext) {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        WindowInfoTracker.getOrCreate(this@PagerActivity)
          .windowLayoutInfo(this@PagerActivity)
          .map { it.displayFeatures }
          .collectLatest {
            val foldingFeatures = it.filterIsInstance<FoldingFeature>().firstOrNull()
            if (foldingFeatures != null) {
              val localState = foldingFeatures.state == FoldingFeature.State.FLAT &&
                  foldingFeatures.orientation == FoldingFeature.Orientation.VERTICAL
              if (isFoldableDeviceOpenAndVertical != localState) {
                isFoldableDeviceOpenAndVertical = localState
                updateDualPageMode()
              }
            } else if (isFoldableDeviceOpenAndVertical) {
              // this else case suggests that the device is not open and vertical, otherwise
              // we'd have some information given via the folding features.
              isFoldableDeviceOpenAndVertical = false
              updateDualPageMode()
            }
          }
      }
    }

    setContentView(R.layout.quran_page_activity_slider)

    val lightStatusBar = resources.getBoolean(R.bool.light_navigation_bar)
    windowInsetsController = WindowInsetsControllerCompat(
      window, findViewById<ViewGroup>(R.id.sliding_panel)
    ).apply {
      isAppearanceLightStatusBars = lightStatusBar
      isAppearanceLightNavigationBars = lightStatusBar
    }
    registerBackPressedCallbacks()
    initialize(savedInstanceState)
    requestPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean? ->
        audioPresenter.onPostNotificationsPermissionResponse(
          isGranted!!
        )
      }

    // read the list of translations
    requestTranslationsList()

    downloadBridge.subscribeToDownloads {
      onDownloadSuccess()
    }

    bookmarksDao.pageBookmarksWithoutTags().combine(currentPageFlow) { bookmarks, currentPage ->
      bookmarks to currentPage
    }.onEach { (bookmarks, page) ->
      currentBookmarks = bookmarks

      val isBookmarked = if (isDualPages) {
        bookmarks.any { it.page == page || it.page == page - 1 }
      } else {
        bookmarks.any { it.page == page }
      }
      refreshBookmarksMenu(isBookmarked)
    }.launchIn(scope)
  }

  private fun updateDualPageMode() {
    val lastIsDualPages = isDualPages
    val nextIsDualPages = QuranUtils.isDualPages(this, quranScreenInfo, isFoldableDeviceOpenAndVertical)
    if (lastIsDualPages != nextIsDualPages) {
      // due to the conversion after setting the adapter, we need this page to be
      // the page _before_ we switched from dual to single or vice versa. otherwise,
      // we end up double-converting the page.
      //
      // might be worth trying later to undo this and just remove the conversion below.
      val page = currentPage
      isDualPages = nextIsDualPages

      pagerAdapter = QuranPageAdapter(
        supportFragmentManager,
        isDualPages,
        showingTranslation,
        quranInfo,
        isSplitScreen,
        pageProviderFactoryProvider.providePageViewFactory(quranSettings.pageType)
      )
      viewPager.adapter = pagerAdapter
      // when going from two page per screen to one or vice versa, we adjust the page number,
      // such that the first page is always selected.
      val curPage = if (isDualPageVisible) {
        quranInfo.mapSinglePageToDualPage(page)
      } else {
        quranInfo.mapDualPageToSinglePage(page)
      }

      val pageIndex = quranInfo.getPositionFromPage(curPage, isDualPageVisible)
      viewPager.setCurrentItem(pageIndex)
    }
  }

  private fun initialize(savedInstanceState: Bundle?) {
    var shouldAdjustPageNumber = false
    isDualPages = QuranUtils.isDualPages(this, quranScreenInfo, isFoldableDeviceOpenAndVertical)
    isSplitScreen = quranSettings.isQuranSplitWithTranslation
    audioStatusRepositoryBridge = AudioStatusRepositoryBridge(
      audioStatusRepository,
      audioBarEventRepository,
      { suraAyah: SuraAyah? ->
        onAudioPlaybackAyahChanged(suraAyah)
      },
      this,
      pagerActivityRecitationPresenter
    )
    readingEventPresenterBridge = ReadingEventPresenterBridge(
      readingEventPresenter,
      {
        onPageClicked()
      },
      handleSelection = { ayahSelection: AyahSelection ->
        onAyahSelectionChanged(ayahSelection)
      }
    )

    // remove the window background to avoid overdraw. note that, per Romain's blog, this is
    // acceptable (as long as we don't set the background color to null in the theme, since
    // that is used to generate preview windows).
    window.setBackgroundDrawable(null)

    var page = -1
    isActionBarHidden = true
    if (savedInstanceState != null) {
      Timber.d("non-null saved instance state!")
      page = savedInstanceState.getInt(LAST_READ_PAGE, -1)
      showingTranslation = savedInstanceState
        .getBoolean(LAST_READING_MODE_IS_TRANSLATION, false)
      if (savedInstanceState.containsKey(LAST_ACTIONBAR_STATE)) {
        isActionBarHidden = !savedInstanceState.getBoolean(LAST_ACTIONBAR_STATE)
      }
      val lastWasDualPages = savedInstanceState.getBoolean(LAST_WAS_DUAL_PAGES, isDualPages)
      shouldAdjustPageNumber = (lastWasDualPages != isDualPages)
    } else {
      val intent = intent
      val extras = intent.extras
      if (extras != null) {
        page = extras.getInt("page", Constants.PAGES_FIRST)
        showingTranslation =
          extras.getBoolean(EXTRA_JUMP_TO_TRANSLATION, showingTranslation)
        val highlightedSura = extras.getInt(EXTRA_HIGHLIGHT_SURA, -1)
        val highlightedAyah = extras.getInt(EXTRA_HIGHLIGHT_AYAH, -1)

        if (highlightedSura > -1 && highlightedAyah > -1) {
          readingEventPresenterBridge.setSelection(
            highlightedSura,
            highlightedAyah,
            true
          )
        }
      }
    }
    onShowingTranslationBackCallback.isEnabled = showingTranslation

    compositeDisposable = CompositeDisposable()

    audioStatusBar = findViewById(R.id.audio_area)

    toolBarArea = findViewById(R.id.toolbar_area)
    translationsSpinner = findViewById(R.id.spinner)
    overlay = findViewById(R.id.overlay)

    ViewCompat.setOnApplyWindowInsetsListener(toolBarArea) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.statusBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.navigationBars()
      )
      view.updatePadding(insets.left, insets.top, insets.right, 0)
      windowInsets
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)

    supportActionBar?.setDisplayShowHomeEnabled(true)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    initAyahActionPanel()

    if (showingTranslation && translationNames.isNotEmpty()) {
      updateActionBarSpinner()
    } else {
      updateActionBarTitle(page)
    }

    lastPopupTime = System.currentTimeMillis()
    pagerAdapter = QuranPageAdapter(
      supportFragmentManager,
      isDualPages,
      showingTranslation,
      quranInfo,
      isSplitScreen,
      pageProviderFactoryProvider.providePageViewFactory(quranSettings.pageType)
    )
    ayahToolBar = findViewById(R.id.ayah_toolbar)
    ayahToolBar.flavor = BuildConfig.FLAVOR
    ayahToolBar.longPressLambda = { charSequence: CharSequence? ->
      makeText(this@PagerActivity, charSequence!!, Toast.LENGTH_SHORT).show()
    }

    ViewCompat.setOnApplyWindowInsetsListener(ayahToolBar) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.statusBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.navigationBars()
      )
      ayahToolBar.insets = insets
      windowInsets
    }

    val nonRestoringViewPager = findViewById<NonRestoringViewPager>(R.id.quran_pager)
    nonRestoringViewPager.setIsDualPagesInLandscape(
      QuranUtils.isDualPagesInLandscape(this, quranScreenInfo)
    )

    viewPager = nonRestoringViewPager
    viewPager.setAdapter(pagerAdapter)

    ayahToolBar.setOnItemSelectedListener(AyahMenuItemSelectionHandler())
    val onPageChangeListener: OnPageChangeListener = object : OnPageChangeListener {
      override fun onPageScrollStateChanged(state: Int) {
      }

      override fun onPageScrolled(
        position: Int,
        positionOffset: Float,
        positionOffsetPixels: Int
      ) {
        val currentSelection = readingEventPresenter.currentAyahSelection()
        val selectionIndicator =
          currentSelection.selectionIndicator()
        val suraAyah = currentSelection.startSuraAyah()
        if (selectionIndicator !== SelectionIndicator.None && selectionIndicator !== SelectionIndicator.ScrollOnly && suraAyah != null) {
          val startPage = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
          val barPos = quranInfo.getPositionFromPage(startPage, isDualPageVisible)
          if (position == barPos) {
            // Swiping to next ViewPager page (i.e. prev quran page)
            val updatedSelectionIndicator =
              selectionIndicator.withXScroll(-positionOffsetPixels.toFloat())
            readingEventPresenterBridge.withSelectionIndicator(
              updatedSelectionIndicator
            )
          } else if (position == barPos - 1 || position == barPos + 1) {
            // Swiping to previous or next ViewPager page (i.e. next or previous quran page)
            val updatedSelectionIndicator =
              selectionIndicator.withXScroll((viewPager.width - positionOffsetPixels).toFloat())
            readingEventPresenterBridge.withSelectionIndicator(
              updatedSelectionIndicator
            )
          } else {
            readingEventPresenterBridge.clearSelectedAyah()
          }
        }
      }

      override fun onPageSelected(position: Int) {
        Timber.d("onPageSelected(): %d", position)
        val page = quranInfo.getPageFromPosition(position, isDualPageVisible)

        if (quranSettings.shouldDisplayMarkerPopup()) {
          lastPopupTime = QuranDisplayHelper.displayMarkerPopup(
            this@PagerActivity, quranInfo, page, lastPopupTime
          )
          if (isDualPages) {
            lastPopupTime = QuranDisplayHelper.displayMarkerPopup(
              this@PagerActivity, quranInfo, page - 1, lastPopupTime
            )
          }
        }

        if (!showingTranslation) {
          updateActionBarTitle(page)
        } else {
          refreshActionBarSpinner()
        }

        // If we're more than 1 page away from ayah selection end ayah mode
        val suraAyah: SuraAyah? = selectionStart
        if (suraAyah != null) {
          val startPage = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
          val ayahPos = quranInfo.getPositionFromPage(startPage, isDualPageVisible)
          if (abs((ayahPos - position).toDouble()) > 1) {
            endAyahMode()
          }
        }
      }
    }
    viewPager.addOnPageChangeListener(onPageChangeListener)

    setUiVisibilityListener()
    audioStatusBar.visibility = View.VISIBLE
    toggleActionBarVisibility(true)

    if (shouldAdjustPageNumber) {
      // when going from two page per screen to one or vice versa, we adjust the page number,
      // such that the first page is always selected.
      val curPage = if (isDualPageVisible) {
        quranInfo.mapSinglePageToDualPage(page)
      } else {
        quranInfo.mapDualPageToSinglePage(page)
      }
      page = curPage
    }

    val pageIndex = quranInfo.getPositionFromPage(page, isDualPageVisible)
    viewPager.setCurrentItem(pageIndex)
    if (page == 0) {
      onPageChangeListener.onPageSelected(0)
    }

    // just got created, need to reconnect to service
    shouldReconnect = true

    // enforce orientation lock only on compact screens (large screens handle free resizing)
    if (quranSettings.isLockOrientation && resources.configuration.smallestScreenWidthDp < 600) {
      val current = resources.configuration.orientation
      if (quranSettings.isLandscapeOrientation) {
        if (current == Configuration.ORIENTATION_PORTRAIT) {
          requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
          return
        }
      } else if (current == Configuration.ORIENTATION_LANDSCAPE) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        return
      }
    }

    quranEventLogger.logAnalytics(isDualPages, showingTranslation, isSplitScreen)

    // Setup recitation (if enabled)
    pagerActivityRecitationPresenter.bind(
      this, PagerActivityRecitationPresenter.Bridge(
        { isDualPageVisible },
        { currentPage },
        { ayahToolBar },
        { ayah: SuraAyah -> ensurePage(ayah.sura, ayah.ayah) },
        { sliderPage: Int -> showSlider(slidingPagerAdapter.getPagePosition(sliderPage)) }
      ))
  }

  private fun registerBackPressedCallbacks() {
    val isSessionEnabled = false
    onEndSessionBackCallback = object : OnBackPressedCallback(isSessionEnabled) {
      override fun handleOnBackPressed() {
        onSessionEnd()
      }
    }
    onBackPressedDispatcher.addCallback(this, onEndSessionBackCallback)

    onShowingTranslationBackCallback = object : OnBackPressedCallback(showingTranslation) {
      override fun handleOnBackPressed() {
        switchToQuran()
      }
    }
    onBackPressedDispatcher.addCallback(this, onShowingTranslationBackCallback)

    onClearAyahModeBackCallback = object : OnBackPressedCallback(selectionStart != null) {
      override fun handleOnBackPressed() {
        endAyahMode()
      }
    }
    onBackPressedDispatcher.addCallback(this, onClearAyahModeBackCallback)

    onCollapseSearchBackCallback = object : OnBackPressedCallback(false) {
      override fun handleOnBackPressed() {
        searchMenuItem?.collapseActionView()
      }
    }
    onBackPressedDispatcher.addCallback(this, onCollapseSearchBackCallback)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int, permissions: Array<String>, grantResults: IntArray
  ) {
    pagerActivityRecitationPresenter.onPermissionsResult(requestCode, grantResults)
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

  private val isDualPageVisible: Boolean
    get() = isDualPages && !(isSplitScreen && showingTranslation)

  private fun shouldUpdatePageNumber(): Boolean {
    return isDualPages && isSplitScreen
  }

  private val currentPageFlow: Flow<Int> =
    callbackFlow {
      val pageChangedListener: OnPageChangeListener =
        object : SimpleOnPageChangeListener() {
          override fun onPageSelected(position: Int) {
            val page = quranInfo.getPageFromPosition(position, isDualPageVisible)
            trySend(page)
          }
        }
      viewPager.addOnPageChangeListener(pageChangedListener)
      awaitClose { viewPager.removeOnPageChangeListener(pageChangedListener) }
    }
      .onStart { emit(currentPage) }
      .buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST)
      .shareIn(scope, SharingStarted.Eagerly, 1)

  private fun initAyahActionPanel() {
    slidingPanel = findViewById(R.id.sliding_panel)
    val slidingLayout =
      slidingPanel.findViewById<ViewGroup>(R.id.sliding_layout)
    slidingPager = slidingPanel
      .findViewById(R.id.sliding_layout_pager)
    val slidingPageIndicator =
      slidingPanel
        .findViewById<IconPageIndicator>(R.id.sliding_pager_indicator)

    // Find close button and set listener
    val closeButton = slidingPanel
      .findViewById<View>(R.id.sliding_menu_close)
    closeButton.setOnClickListener { v: View? -> endAyahMode() }

    // Create and set fragment pager adapter
    slidingPagerAdapter = SlidingPagerAdapter(
      supportFragmentManager,
      quranSettings.isArabicNames || QuranUtils.isRtl(),
      additionalAyahPanels
    )
    slidingPager.setAdapter(slidingPagerAdapter)

    // Attach the view pager to the action bar
    slidingPageIndicator.setViewPager(slidingPager)

    // Set sliding layout parameters
    val displayHeight = resources.displayMetrics.heightPixels
    slidingLayout.layoutParams.height = (displayHeight * PANEL_MAX_HEIGHT).toInt()
    slidingPanel.setEnableDragViewTouchEvents(true)
    slidingPanel.setPanelSlideListener(SlidingPanelListener())
    slidingLayout.visibility = View.GONE

    // When clicking any menu items, expand the panel
    slidingPageIndicator.setOnClickListener { v: View? ->
      if (!slidingPanel.isExpanded) {
        slidingPanel.expandPane()
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      handler.sendEmptyMessageDelayed(MSG_HIDE_ACTIONBAR, DEFAULT_HIDE_AFTER_TIME)
    } else {
      handler.removeMessages(MSG_HIDE_ACTIONBAR)
    }
  }

  private fun onPageClicked() {
    toggleActionBar()
  }

  private fun onAudioPlaybackAyahChanged(suraAyah: SuraAyah?) {
    if (suraAyah != null) {
      // continue to snap back to the page when the playback ayah changes
      ensurePage(suraAyah.sura, suraAyah.ayah)
    }
  }

  private fun onAyahSelectionChanged(ayahSelection: AyahSelection) {
    val haveSelection = ayahSelection !== AyahSelection.None
    val currentSelection = ayahSelection.selectionIndicator()
    if (currentSelection is SelectionIndicator.None && haveSelection) {
      viewPager.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    if (haveSelection) {
      val startPosition = startPosition(ayahSelection)
      updateLocalTranslations(startPosition)
      onClearAyahModeBackCallback.isEnabled = selectionStart != null
      val endPosition = ayahSelection.endSuraAyah()
      val canShrink = startPosition != null && endPosition != null && endPosition > startPosition
      val canExpand = canExpandAyahSelection(ayahSelection)
      ayahToolBar.setSelectionRangeState(canShrink, canExpand)
      toggleActionBarVisibility(false)
    } else {
      ayahToolBar.setSelectionRangeState(canShrink = false, canExpand = false)
      endAyahMode()
      onClearAyahModeBackCallback.isEnabled = false
    }
  }

  private var selectionToast: Toast? = null

  private fun canExpandAyahSelection(selection: AyahSelection): Boolean {
    val end = selection.endSuraAyah() ?: return false
    return end.next(quranInfo) != null
  }

  private fun showCrossPageSelectionToast(start: SuraAyah, end: SuraAyah) {
    val endPage = quranInfo.getPageFromSuraAyah(end.sura, end.ayah)
    if (quranInfo.getPositionFromPage(endPage, isDualPageVisible) == viewPager.currentItem) {
      return
    }
    val startSuraName = quranDisplayData.getSuraName(this, start.sura, false, false)
    val refText = if (start.sura == end.sura) {
      "$startSuraName: ${start.ayah}–${end.ayah}"
    } else {
      val endSuraName = quranDisplayData.getSuraName(this, end.sura, false, false)
      "$startSuraName ${start.ayah} – $endSuraName ${end.ayah}"
    }
    val pageLabel = quranDisplayData.getPageSubtitle(this, endPage)
    selectionToast?.cancel()
    selectionToast = Toast.makeText(this, "$refText ($pageLabel)", Toast.LENGTH_SHORT).also { it.show() }
  }

  private fun expandAyahSelection() {
    val currentSelection = readingEventPresenter.currentAyahSelection()
    val start = currentSelection.startSuraAyah() ?: return
    val end = currentSelection.endSuraAyah() ?: return
    val next = end.next(quranInfo) ?: return
    viewPager.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    val indicator = currentSelection.selectionIndicator()
    readingEventPresenter.onAyahSelection(AyahRange(start, next, indicator))
    showCrossPageSelectionToast(start, next)
  }

  private fun shrinkAyahSelection() {
    val currentSelection = readingEventPresenter.currentAyahSelection()
    val start = currentSelection.startSuraAyah() ?: return
    val end = currentSelection.endSuraAyah() ?: return
    if (end <= start) return
    val prev = end.prev(quranInfo) ?: return
    viewPager.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    val indicator = currentSelection.selectionIndicator()
    val updated = if (prev <= start) {
      AyahSelection.Ayah(start, indicator)
    } else {
      AyahRange(start, prev, indicator)
    }
    readingEventPresenter.onAyahSelection(updated)
    if (prev > start) {
      showCrossPageSelectionToast(start, prev)
    }
  }

  private fun startPosition(ayahSelection: AyahSelection): SuraAyah? {
    return when (ayahSelection) {
      is AyahSelection.Ayah -> {
        ayahSelection.suraAyah
      }

      is AyahRange -> {
        ayahSelection.startSuraAyah
      }

      else -> {
        null
      }
    }
  }

  private fun setUiVisibility(isVisible: Boolean) {
    if (isVisible) {
      windowInsetsController.show(
        WindowInsetsCompat.Type.statusBars() or
            WindowInsetsCompat.Type.navigationBars()
      )
    } else {
      windowInsetsController.hide(
        WindowInsetsCompat.Type.statusBars() or
            WindowInsetsCompat.Type.navigationBars()
      )
      windowInsetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || isInMultiWindowMode) {
      animateToolBar(isVisible)
    }
  }

  private fun setUiVisibilityListener() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
        val isStatusBarVisible = insets.isVisible(WindowInsetsCompat.Type.statusBars())
        // on devices with "hide full screen indicator" or "hide the bottom bar,"
        // this always returns false, which causes the touches to not work.
        val isNavigationBarVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars())

        // as a fix for the aforementioned point, make either one's visibility suggest
        // visibility (instead of requiring both to agree).
        val isVisible = isStatusBarVisible || isNavigationBarVisible

        animateToolBar(isVisible)
        insets
      }
    }
  }

  private fun clearUiVisibilityListener() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      ViewCompat.setOnApplyWindowInsetsListener(window.decorView, null)
    }
  }

  private fun animateToolBar(visible: Boolean) {
    isActionBarHidden = !visible

    // animate toolbar
    toolBarArea.animate()
      .translationY((if (visible) 0 else -toolBarArea.height).toFloat())
      .setDuration(250)
      .start()

    // and audio bar
    audioStatusBar.animate()
      .translationY((if (visible) 0 else audioStatusBar.height).toFloat())
      .setDuration(250)
      .start()
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val navigate = (audioStatusRepositoryBridge.audioRequest() == null
        && quranSettings.navigateWithVolumeKeys())
    if (navigate && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
      viewPager.currentItem -= 1
      return true
    } else if (navigate && keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      viewPager.currentItem += 1
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    return (((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
        keyCode == KeyEvent.KEYCODE_VOLUME_UP) && (
        audioStatusRepositoryBridge.audioRequest() == null) &&
        PreferenceManager.getDefaultSharedPreferences(this)
          .getBoolean(Constants.PREF_USE_VOLUME_KEY_NAV, false))
        || super.onKeyUp(keyCode, event))
  }

  public override fun onResume() {
    super.onResume()

    audioPresenter.bind(this)
    recentPagePresenter.bind(currentPageFlow)

    if (shouldReconnect) {
      foregroundDisposable.add(
        Completable.timer(500, TimeUnit.MILLISECONDS)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe {
            try {
              startService(
                audioUtils.getAudioIntent(
                  this@PagerActivity,
                  AudioService.ACTION_CONNECT
                )
              )
            } catch (ise: IllegalStateException) {
              // we're likely in the background, so ignore.
            }
            shouldReconnect = false
          })
    }
  }

  override fun inject(audioBarWrapper: AudioBarWrapper) {
    pagerActivityComponent.inject(audioBarWrapper)
  }

  override fun injectQariListWrapper(qariListWrapper: QariListWrapper) {
    pagerActivityComponent.inject(qariListWrapper)
  }

  override fun provideQuranReadingActivityComponent(): QuranReadingActivityComponent {
    return pagerActivityComponent
  }

  override fun provideQuranReadingPageComponent(vararg pages: Int): QuranReadingPageComponent {
    return pagerActivityComponent
      .quranPageComponentFactory()
      .generate(pages)
  }

  override fun injectToolBar(ayahToolBar: AyahToolBar) {
    pagerActivityComponent
      .inject(ayahToolBar)
  }

  fun showGetRequiredFilesDialog() {
    if (promptDialog != null) {
      return
    }
    val builder = AlertDialog.Builder(this)
    builder.setMessage(R.string.download_extra_data)
      .setPositiveButton(
        R.string.downloadPrompt_ok
      ) { dialog: DialogInterface, option: Int ->
        downloadRequiredFiles()
        dialog.dismiss()
        promptDialog = null
      }
      .setNegativeButton(
        R.string.downloadPrompt_no
      ) { dialog: DialogInterface, option: Int ->
        dialog.dismiss()
        promptDialog = null
      }
    promptDialog = builder.create()
    promptDialog!!.show()
  }

  private fun downloadRequiredFiles() {
    var downloadType = QuranDownloadService.DOWNLOAD_TYPE_AUDIO
    if (audioStatusRepositoryBridge.audioRequest() == null) {
      // if we're not playing any audio, use audio download bar as our progress bar
      if (isActionBarHidden) {
        toggleActionBar()
      }
    } else {
      // if audio is playing, let's not disrupt it - do this using a
      // different type so the broadcast receiver ignores it.
      downloadType = QuranDownloadService.DOWNLOAD_TYPE_ARABIC_SEARCH_DB
    }

    var haveDownload = false
    if (!quranFileUtils.haveAyaPositionFile()) {
      var url = quranFileUtils.ayaPositionFileUrl
      if (isDualPages) {
        url = quranFileUtils.getAyaPositionFileUrl(
          quranScreenInfo.tabletWidthParam
        )
      }
      val destination = quranFileUtils.quranAyahDatabaseDirectory
      // start the download
      val notificationTitle = getString(R.string.highlighting_database)
      val intent = getDownloadIntent(
        this, url,
        destination.absolutePath, notificationTitle, AUDIO_DOWNLOAD_KEY,
        downloadType
      )
      Timber.d("starting service to download ayah position file")
      startService(intent)

      haveDownload = true
    }

    if (!quranFileUtils.hasArabicSearchDatabase()) {
      val url = quranFileUtils.arabicSearchDatabaseUrl

      // show "downloading required files" unless we already showed that for
      // highlighting database, in which case show "downloading search data"
      var notificationTitle = getString(R.string.highlighting_database)
      if (haveDownload) {
        notificationTitle = getString(R.string.search_data)
      }

      val extension = if (url.endsWith(".zip")) ".zip" else ""
      val intent = getDownloadIntent(
        this, url,
        quranFileUtils.getQuranDatabaseDirectory().absolutePath, notificationTitle,
        AUDIO_DOWNLOAD_KEY, downloadType
      )
      intent.putExtra(
        QuranDownloadService.EXTRA_OUTPUT_FILE_NAME,
        QuranDataProvider.QURAN_ARABIC_DATABASE + extension
      )
      Timber.d("starting service to download arabic database")
      startService(intent)
    }

    if (downloadType != QuranDownloadService.DOWNLOAD_TYPE_AUDIO) {
      // if audio is playing, just show a status notification
      makeText(
        this, com.quran.mobile.common.download.R.string.downloading,
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  public override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)

    recentPagePresenter.onJump()
    val extras = intent.extras
    if (extras != null) {
      val page = extras.getInt("page", Constants.PAGES_FIRST)

      val currentValue = showingTranslation
      showingTranslation = extras.getBoolean(EXTRA_JUMP_TO_TRANSLATION, showingTranslation)
      val highlightedSura = extras.getInt(EXTRA_HIGHLIGHT_SURA, -1)
      val highlightedAyah = extras.getInt(EXTRA_HIGHLIGHT_AYAH, -1)
      if (highlightedSura > 0 && highlightedAyah > 0) {
        readingEventPresenterBridge.setSelection(highlightedSura, highlightedAyah, true)
      }

      if (showingTranslation != currentValue) {
        if (showingTranslation) {
          pagerAdapter.setTranslationMode()
          updateActionBarSpinner()
          onShowingTranslationBackCallback.isEnabled = true
        } else {
          pagerAdapter.setQuranMode()
          updateActionBarTitle(page)
          onShowingTranslationBackCallback.isEnabled = false
        }

        supportInvalidateOptionsMenu()
      }

      if (highlightedAyah > 0 && highlightedSura > 0) {
        // this will jump to the right page automagically
        ensurePage(highlightedSura, highlightedAyah)
      } else {
        val pagePosition = quranInfo.getPositionFromPage(page, isDualPageVisible)
        viewPager.currentItem = pagePosition
      }

      setIntent(intent)
    }
  }

  override fun jumpTo(page: Int) {
    val i = Intent(this, PagerActivity::class.java)
    i.putExtra("page", page)
    onNewIntent(i)
  }

  override fun jumpToAndHighlight(page: Int, sura: Int, ayah: Int) {
    val i = Intent(this, PagerActivity::class.java)
    i.putExtra("page", page)
    i.putExtra(EXTRA_HIGHLIGHT_SURA, sura)
    i.putExtra(EXTRA_HIGHLIGHT_AYAH, ayah)
    onNewIntent(i)
  }

  public override fun onPause() {
    foregroundDisposable.clear()
    promptDialog?.dismiss()
    promptDialog = null
    recentPagePresenter.unbind()
    quranSettings.wasShowingTranslation = pagerAdapter.isShowingTranslation

    super.onPause()
  }

  override fun onStop() {
    // the activity will be paused when requesting notification
    // permissions, which will otherwise break audio presenter.
    audioPresenter.unbind(this)
    super.onStop()
  }

  override fun onDestroy() {
    Timber.d("onDestroy()")
    clearUiVisibilityListener()

    translationJob?.cancel(CancellationException())
    compositeDisposable.dispose()
    audioStatusRepositoryBridge.dispose()
    readingEventPresenterBridge.dispose()
    downloadBridge.unsubscribe()
    handler.removeCallbacksAndMessages(null)
    scope.cancel()
    dismissProgressDialog()
    super.onDestroy()
  }

  private fun onSessionEnd() {
    onEndSessionBackCallback.isEnabled = false
    pagerActivityRecitationPresenter.onSessionEnd()
  }

  public override fun onSaveInstanceState(state: Bundle) {
    val lastPage = quranInfo.getPageFromPosition(viewPager.currentItem, isDualPageVisible)
    state.putInt(LAST_READ_PAGE, lastPage)
    state.putBoolean(LAST_READING_MODE_IS_TRANSLATION, showingTranslation)
    state.putBoolean(LAST_ACTIONBAR_STATE, isActionBarHidden)
    state.putBoolean(LAST_WAS_DUAL_PAGES, isDualPages)
    state.putBoolean(LAST_FOLDING_STATE, isFoldableDeviceOpenAndVertical)
    super.onSaveInstanceState(state)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    super.onCreateOptionsMenu(menu)
    val inflater = menuInflater
    inflater.inflate(R.menu.quran_menu, menu)
    val item = menu.findItem(R.id.search)
    searchMenuItem = item
    com.quran.labs.androidquran.util.SearchViewStyler.configure(
      activity = this,
      menu = menu,
      searchItem = item,
      hasAlwaysDarkToolbarSurface = true,
      expandToFullToolbarWidth = true,
      onExpandChanged = { expanded -> onCollapseSearchBackCallback.isEnabled = expanded }
    )

    // cache because invalidateOptionsMenu in a toolbar world always calls both
    // onCreateOptionsMenu and onPrepareOptionsMenu, which can be expensive both
    // due to inflation plus due to the search view specific setup work. we can
    // directly modify the bookmark item using a reference to this instead.
    bookmarksMenuItem = menu.findItem(R.id.favorite_item)
    return true
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    super.onPrepareOptionsMenu(menu)

    if (bookmarksMenuItem != null) {
      refreshBookmarksMenu()
    }

    val quran = menu.findItem(R.id.goto_quran)
    val translation = menu.findItem(R.id.goto_translation)
    val fontSizeSelection = menu.findItem(R.id.font_size_selection)
    if (quran != null && translation != null) {
      if (!showingTranslation) {
        quran.isVisible = false
        translation.isVisible = true
      } else {
        quran.isVisible = true
        translation.isVisible = false
      }
    }
    if (fontSizeSelection != null) {
      fontSizeSelection.isVisible = showingTranslation
    }

    val nightMode = menu.findItem(R.id.night_mode)
    if (nightMode != null) {
      val isDark = quranSettings.isAppInDarkMode
      nightMode.isChecked = isDark
      nightMode.setIcon(if (isDark) R.drawable.ic_night_mode else R.drawable.ic_day_mode)
    }
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val itemId = item.itemId
    if (itemId == R.id.favorite_item) {
      togglePageBookmark(currentPage)
      return true
    } else if (itemId == R.id.goto_quran) {
      switchToQuran()
      return true
    } else if (itemId == R.id.font_size_selection) {
      showFontSizeDialog()
      return true
    } else if (itemId == R.id.goto_translation) {
      if (translations != null) {
        quranEventLogger.switchToTranslationMode(translations!!.size)
        switchToTranslation()
      }
      return true
    } else if (itemId == R.id.night_mode) {
      val prefs = PreferenceManager.getDefaultSharedPreferences(this)
      val targetDark = !quranSettings.isAppInDarkMode
      val nextAppTheme = if (targetDark) "dark" else "light"
      prefs.edit().putString(Constants.PREF_APP_THEME, nextAppTheme).apply()
      QuranApplication.applyAppTheme(nextAppTheme)
      item.setIcon(if (targetDark) R.drawable.ic_night_mode else R.drawable.ic_day_mode)
      item.isChecked = targetDark
      refreshQuranPages()
      return true
    } else if (itemId == R.id.settings) {
      val i = Intent(this, QuranPreferenceActivity::class.java)
      startActivity(i)
      return true
    }/* else if (itemId == R.id.tajweed) {
      val i = Intent(this, TajweedActivity::class.java)
      startActivity(i)
      return true
    }*/ else if (itemId == R.id.help) {
      val i = Intent(this, HelpActivity::class.java)
      startActivity(i)
      return true
    } else if (itemId == android.R.id.home) {
      onSessionEnd()
      finish()
      return true
    } else if (itemId == R.id.jump) {
      val fm = supportFragmentManager
      val jumpDialog = JumpFragment()
      jumpDialog.show(fm, JumpFragment.TAG)
      return true
    } else if (itemId == R.id.language) {
      showLanguageDialog()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  fun refreshQuranPages() {
    for (i in 0 until pagerAdapter.count) {
      val f = pagerAdapter.getFragmentIfExists(i)
      if (f is QuranPage) {
        (f as QuranPage).updateView()
      }
    }
  }

  private fun switchToQuran() {
    if (!quranFileUtils.haveAllImages(quranScreenInfo.widthParam, quranInfo.numberOfPages, true) &&
      quranPageProvider.getPageContentType() != com.quran.data.source.PageContentType.Image
    ) {
      AlertDialog.Builder(this, R.style.QuranDialog)
        .setTitle(R.string.downloadPrompt_title)
        .setMessage(R.string.downloadPrompt)
        .setPositiveButton(R.string.downloadPrompt_ok) { dialog, _ ->
          dialog.dismiss()
          quranSettings.wasShowingTranslation = false
          quranSettings.setShouldFetchPages(true)
          val downloadIntent = Intent(this, com.quran.labs.androidquran.QuranDataActivity::class.java).apply {
            putExtra(com.quran.labs.androidquran.QuranDataActivity.EXTRA_RETURN_TO_PAGE, currentPage)
          }
          startActivity(downloadIntent)
          finish()
        }
        .setNegativeButton(R.string.downloadPrompt_no) { dialog, _ ->
          dialog.dismiss()
          quranSettings.pageType = quranPageProvider.getFallbackPageType() ?: "tajweed"
          quranSettings.wasShowingTranslation = false
          val restartIntent = Intent(this, PagerActivity::class.java).apply {
            putExtra("page", currentPage)
            putExtra(EXTRA_JUMP_TO_TRANSLATION, false)
          }
          finish()
          startActivity(restartIntent)
        }
        .show()
      return
    }
    if (selectionStart != null) {
      endAyahMode()
    }
    val page = currentPage
    pagerAdapter.setQuranMode()
    showingTranslation = false
    onShowingTranslationBackCallback.isEnabled = false
    if (shouldUpdatePageNumber()) {
      val position = quranInfo.getPositionFromPage(page, true)
      viewPager.currentItem = position
    }

    supportInvalidateOptionsMenu()
    updateActionBarTitle(page)
  }

  private fun switchToTranslation() {
    if (selectionStart != null) {
      endAyahMode()
    }

    if (translations!!.isEmpty()) {
      startTranslationManager()
    } else {
      val page = currentPage
      pagerAdapter.setTranslationMode()
      showingTranslation = true
      onShowingTranslationBackCallback.isEnabled = true
      if (shouldUpdatePageNumber()) {
        val position = quranInfo.getPositionFromPage(page, false)
        viewPager.currentItem = position
      }
      supportInvalidateOptionsMenu()
      updateActionBarSpinner()
    }

    if (!quranFileUtils.hasArabicSearchDatabase() && !promptedForExtraDownload) {
      promptedForExtraDownload = true
      showGetRequiredFilesDialog()
    }
  }

  fun startTranslationManager() {
    startActivity(Intent(this, TranslationManagerActivity::class.java))
  }

  private val translationItemChangedListener =
    TranslationsSpinnerAdapter.OnSelectionChangedListener { selectedItems: Set<String?>? ->
      quranSettings.activeTranslations = selectedItems
      val pos = viewPager.currentItem - 1
      for (count in 0..2) {
        if (pos + count < 0) {
          continue
        }
        val f = pagerAdapter.getFragmentIfExists(pos + count)
        if (f is TranslationFragment) {
          f.refresh()
        } else if (f is TabletFragment) {
          f.refresh()
        }
      }
    }

  override fun onAddTagSelected() {
    val fm = supportFragmentManager
    val dialog = AddTagDialog()
    dialog.show(fm, AddTagDialog.TAG)
  }

  private fun updateActionBarTitle(page: Int) {
    val sura = quranDisplayData.getSuraNameFromPage(this, page, false)
    val actionBar = supportActionBar
    if (actionBar != null) {
      translationsSpinner.visibility = View.GONE
      actionBar.setDisplayShowTitleEnabled(true)
      actionBar.title = sura
      val desc = quranDisplayData.getPageSubtitle(this, page)
      actionBar.subtitle = desc
    }
  }

  private fun refreshActionBarSpinner() {
    if (translationsSpinnerAdapter != null) {
      translationsSpinnerAdapter!!.notifyDataSetChanged()
    } else {
      updateActionBarSpinner()
    }
  }

  private val currentPage: Int
    get() = quranInfo.getPageFromPosition(viewPager.currentItem, isDualPageVisible)

  private fun updateActionBarSpinner() {
    if (translationNames.isEmpty()) {
      val page = currentPage
      updateActionBarTitle(page)
      return
    }

    if (translationsSpinnerAdapter == null) {
      translationsSpinnerAdapter = object : TranslationsSpinnerAdapter(
        this,
        R.layout.translation_ab_spinner_item, translationNames, translations,
        if (activeTranslationsFilesNames == null) quranSettings.activeTranslations else activeTranslationsFilesNames,
        translationItemChangedListener
      ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
          val type = super.getItemViewType(position)
          val view = super.getView(position, convertView, parent)
          if (type == 0) {
            val holder = view.tag as SpinnerHolder
            val page: Int = currentPage

            val sura =
              quranDisplayData.getSuraNameFromPage(this@PagerActivity, page, false)
            holder.title.text = sura
            holder.title.setTextColor(ResourcesCompat.getColor(resources, R.color.toolbar_text, null))
            val desc = quranDisplayData.getPageSubtitle(this@PagerActivity, page)
            holder.subtitle.text = desc
            holder.subtitle.visibility = View.VISIBLE
            holder.subtitle.setTextColor(ResourcesCompat.getColor(resources, R.color.toolbar_secondary_text, null))
          }
          return view
        }
      }
      translationsSpinner.adapter = translationsSpinnerAdapter
    }

    val actionBar = supportActionBar
    if (actionBar != null) {
      actionBar.setDisplayShowTitleEnabled(false)
      translationsSpinner.visibility = View.VISIBLE
    }
  }

  private fun onDownloadSuccess() {
    refreshQuranPages()
    audioPresenter.onDownloadSuccess()
  }

  fun toggleActionBarVisibility(visible: Boolean) {
    if (visible == isActionBarHidden) {
      toggleActionBar()
    }
  }

  fun toggleActionBar() {
    if (isActionBarHidden) {
      setUiVisibility(true)

      isActionBarHidden = false
    } else {
      handler.removeMessages(MSG_HIDE_ACTIONBAR)
      setUiVisibility(false)

      isActionBarHidden = true
    }
  }

  private fun ensurePage(sura: Int, ayah: Int) {
    val page = quranInfo.getPageFromSuraAyah(sura, ayah)
    if (quranInfo.isValidPage(page)) {
      val position = quranInfo.getPositionFromPage(page, isDualPageVisible)
      if (position != viewPager.currentItem) {
        viewPager.currentItem = position
      }
    }
  }

  private fun requestTranslationsList() {
    translationJob =
      translationListPresenter.registerForTranslations { titles: Array<String>, updatedTranslations: List<LocalTranslation> ->
        var currentActiveTranslationsFilesNames = quranSettings.activeTranslations
        if (updatedTranslations.isNotEmpty()) {
          val availableFilenames = updatedTranslations.map { it.filename }.toSet()
          val validActive = currentActiveTranslationsFilesNames.firstOrNull { it in availableFilenames }
          if (validActive == null) {
            val defaultTranslation = updatedTranslations.firstOrNull { it.filename == "quran.kannada.hamzah.db" }
              ?: updatedTranslations.first()
            currentActiveTranslationsFilesNames = mutableSetOf(defaultTranslation.filename)
            quranSettings.activeTranslations = currentActiveTranslationsFilesNames
          } else if (currentActiveTranslationsFilesNames.size != 1) {
            currentActiveTranslationsFilesNames = mutableSetOf(validActive)
            quranSettings.activeTranslations = currentActiveTranslationsFilesNames
          }
        }
        activeTranslationsFilesNames = currentActiveTranslationsFilesNames

        if (translationsSpinnerAdapter != null) {
          translationsSpinnerAdapter!!
            .updateItems(titles, updatedTranslations, activeTranslationsFilesNames)
        }
        translationNames = titles
        translations = updatedTranslations
        if (showingTranslation) {
          // Since translation items have changed, need to
          updateActionBarSpinner()
        }
      }
  }

  private fun togglePageBookmark(page: Int) {
    scope.launch {
      bookmarksDao.togglePageBookmark(page)
    }
  }

  private fun toggleAyahBookmark(suraAyah: SuraAyah, page: Int) {
    scope.launch {
      val isBookmarked = bookmarksDao.toggleAyahBookmark(suraAyah, page)
      updateAyahBookmark(suraAyah, isBookmarked)
    }
  }

  private fun refreshBookmarksMenu() {
    val currentPage = currentPage
    val isBookmarked = if (isDualPages) {
      currentBookmarks.any { it.page == currentPage || it.page == currentPage - 1 }
    } else {
      currentBookmarks.any { it.page == currentPage }
    }

    if (isBookmarked) {
      refreshBookmarksMenu(true)
    }
    // don't refresh if it's not bookmarked since it'll cause a loop (since this method is called
    // from onPrepareOptionsMenu).
  }

  private fun refreshBookmarksMenu(isBookmarked: Boolean) {
    val menuItem = bookmarksMenuItem
    if (menuItem != null) {
      menuItem.setIcon(if (isBookmarked) com.quran.labs.androidquran.common.toolbar.R.drawable.ic_favorite else com.quran.labs.androidquran.common.toolbar.R.drawable.ic_not_favorite)
    } else {
      supportInvalidateOptionsMenu()
    }
  }

  // region Audio playback
  private fun currentQariItem(): QariItem {
    return fromQari(this, qariManager.currentQari())
  }

  override fun onContinuePlaybackPressed() {
    handlePlayback(null)
  }

  override fun onPlayPressed() {
    val position = viewPager.currentItem
    val page = quranInfo.getPageFromPosition(position, isDualPageVisible)

    // log the event
    quranEventLogger.logAudioPlayback(
      QuranEventLogger.AudioPlaybackSource.PAGE,
      currentQariItem(), isDualPages, showingTranslation, isSplitScreen
    )

    val startSura = quranDisplayData.safelyGetSuraOnPage(page)
    val startAyah = quranInfo.getFirstAyahOnPage(page)
    val startingSuraList = quranInfo.getListOfSurahWithStartingOnPage(page)
    if (startingSuraList.isEmpty() ||
      (startingSuraList.size == 1 && startingSuraList[0] == startSura)
    ) {
      playFromAyah(startSura, startAyah)
    } else {
      promptForMultipleChoicePlay(page, startSura, startAyah, startingSuraList)
    }
  }

  private fun playFromAyah(startSura: Int, startAyah: Int) {
    val page = quranInfo.getPageFromSuraAyah(startSura, startAyah)
    val start = SuraAyah(startSura, startAyah)
    val end = selectionEnd
    // handle the case of multiple ayat being selected and play them as a range if so
    val ending = if ((end == null || start == end || start.after(end))) null else end
    playFromAyah(start, ending, page, 0, 0, ending != null, 1.0f)
  }

  fun playFromAyah(
    start: SuraAyah,
    end: SuraAyah?,
    page: Int,
    verseRepeat: Int,
    rangeRepeat: Int,
    enforceRange: Boolean,
    playbackSpeed: Float
  ) {
    val ending = end
      ?: audioUtils.getLastAyahToPlay(
        start, page,
        quranSettings.preferredDownloadAmount, isDualPageVisible
      )

    if (ending != null) {
      Timber.d(
        "playFromAyah - " + start + ", ending: " +
            ending + " - original: " + end + " -- " +
            quranSettings.preferredDownloadAmount
      )
      val item = currentQariItem()
      val shouldStream = quranSettings.shouldStream()
      audioPresenter.play(
        start,
        ending,
        item,
        verseRepeat,
        rangeRepeat,
        enforceRange,
        playbackSpeed,
        shouldStream
      )
    }
  }

  fun handleRequiredDownload(downloadIntent: Intent?) {
    var needsPermission = needsPermissionToDownloadOver3g
    if (needsPermission) {
      if (QuranUtils.isOnWifiNetwork(this)) {
        Timber.d("on wifi, don't need permission for download...")
        needsPermission = false
      }
    }

    if (needsPermission) {
      downloadInfoStreams.requestDownloadNetworkPermission()
    } else if (!havePostNotificationPermission(this)) {
      if (canRequestPostNotificationPermission(this)) {
        promptDialog = buildPostPermissionDialog(
          this,
          {
            promptDialog = null
            requestPermissionLauncher!!.launch(Manifest.permission.POST_NOTIFICATIONS)
          }, {
            proceedWithDownload(downloadIntent)
            promptDialog = null
          })
        promptDialog!!.show()
      } else {
        requestPermissionLauncher!!.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    } else {
      proceedWithDownload(downloadIntent)
    }
  }

  fun proceedWithDownload(downloadIntent: Intent?) {
    if (isActionBarHidden) {
      toggleActionBar()
    }
    downloadInfoStreams.downloadRequested()
    Timber.d("starting service in handleRequiredDownload")
    startService(downloadIntent)
  }

  fun handlePlayback(request: AudioRequest?) {
    needsPermissionToDownloadOver3g = true
    val intent = Intent(this, AudioService::class.java)
    intent.setAction(AudioService.ACTION_PLAYBACK)
    if (request != null) {
      intent.putExtra(AudioService.EXTRA_PLAY_INFO, request)
    }

    Timber.d("starting service for audio playback")
    startService(intent)
  }

  override fun onPausePressed() {
    startService(
      audioUtils.getAudioIntent(
        this, AudioService.ACTION_PAUSE
      )
    )
  }

  override fun setPlaybackSpeed(speed: Float) {
    val lastAudioRequest = audioStatusRepositoryBridge.audioRequest()
    if (lastAudioRequest != null) {
      val updatedAudioRequest = AudioRequest(
        lastAudioRequest.start,
        lastAudioRequest.end,
        lastAudioRequest.qari,
        lastAudioRequest.repeatInfo,
        lastAudioRequest.rangeRepeatInfo,
        lastAudioRequest.enforceBounds,
        speed,
        lastAudioRequest.shouldStream,
        lastAudioRequest.audioPathInfo,
        lastAudioRequest.sleepTimerMinutes
      )

      val i = Intent(this, AudioService::class.java)
      i.setAction(AudioService.ACTION_UPDATE_SETTINGS)
      i.putExtra(AudioService.EXTRA_PLAY_INFO, updatedAudioRequest)
      startService(i)
    }
  }

  override fun onNextPressed() {
    startService(
      audioUtils.getAudioIntent(
        this,
        AudioService.ACTION_SKIP
      )
    )
  }

  override fun onPreviousPressed() {
    startService(
      audioUtils.getAudioIntent(
        this,
        AudioService.ACTION_REWIND
      )
    )
  }

  override fun onAudioSettingsPressed() {
    showSlider(slidingPagerAdapter.getPagePosition(SlidingPagerAdapter.AUDIO_PAGE))
  }

  override fun onShowQariList() {
    val page = currentPage
    val start = selectionStart
    val end = selectionEnd

    val startSura = quranDisplayData.safelyGetSuraOnPage(page)
    val startAyah = quranInfo.getFirstAyahOnPage(page)

    val starting = start ?: SuraAyah(startSura, startAyah)
    val ending = end
      ?: audioUtils.getLastAyahToPlay(
        starting, page,
        quranSettings.preferredDownloadAmount, isDualPageVisible
      )!!
    val qariListWrapper = QariListWrapper(this, starting, ending)
    overlay.removeAllViews()
    overlay.addView(
      qariListWrapper,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlay.visibility = View.VISIBLE
    toggleActionBarVisibility(false)
  }

  fun updatePlayOptions(
    rangeRepeat: Int,
    verseRepeat: Int,
    enforceRange: Boolean,
    playbackSpeed: Float
  ): Boolean {
    val lastAudioRequest = audioStatusRepositoryBridge.audioRequest()
    if (lastAudioRequest != null) {
      val updatedAudioRequest = AudioRequest(
        lastAudioRequest.start,
        lastAudioRequest.end,
        lastAudioRequest.qari,
        verseRepeat,
        rangeRepeat,
        enforceRange,
        playbackSpeed,
        lastAudioRequest.shouldStream,
        lastAudioRequest.audioPathInfo
      )
      val i = Intent(this, AudioService::class.java)
      i.setAction(AudioService.ACTION_UPDATE_SETTINGS)
      i.putExtra(AudioService.EXTRA_PLAY_INFO, updatedAudioRequest)
      startService(i)
      return true
    } else {
      return false
    }
  }

  override fun setRepeatCount(repeatCount: Int) {
    val lastAudioRequest = audioStatusRepositoryBridge.audioRequest()
    if (lastAudioRequest != null) {
      val updatedAudioRequest = AudioRequest(
        lastAudioRequest.start,
        lastAudioRequest.end,
        lastAudioRequest.qari,
        repeatCount,
        lastAudioRequest.rangeRepeatInfo,
        lastAudioRequest.enforceBounds,
        lastAudioRequest.playbackSpeed,
        lastAudioRequest.shouldStream,
        lastAudioRequest.audioPathInfo,
        lastAudioRequest.sleepTimerMinutes
      )

      val i = Intent(this, AudioService::class.java)
      i.setAction(AudioService.ACTION_UPDATE_SETTINGS)
      i.putExtra(AudioService.EXTRA_PLAY_INFO, updatedAudioRequest)
      startService(i)
    }
  }

  override fun setSleepTimer(minutes: Int) {
    val lastAudioRequest = audioStatusRepositoryBridge.audioRequest()
    if (lastAudioRequest != null) {
      val updatedAudioRequest = AudioRequest(
        lastAudioRequest.start,
        lastAudioRequest.end,
        lastAudioRequest.qari,
        lastAudioRequest.repeatInfo,
        lastAudioRequest.rangeRepeatInfo,
        lastAudioRequest.enforceBounds,
        lastAudioRequest.playbackSpeed,
        lastAudioRequest.shouldStream,
        lastAudioRequest.audioPathInfo,
        minutes
      )

      val i = Intent(this, AudioService::class.java)
      i.setAction(AudioService.ACTION_UPDATE_SETTINGS)
      i.putExtra(AudioService.EXTRA_PLAY_INFO, updatedAudioRequest)
      startService(i)
    }
  }

  override fun onStopPressed() {
    startService(audioUtils.getAudioIntent(this, AudioService.ACTION_STOP))
  }

  override fun onCancelPressed(cancelDownload: Boolean) {
    if (cancelDownload) {
      needsPermissionToDownloadOver3g = true

      val i = Intent(this, QuranDownloadService::class.java)
      i.setAction(QuranDownloadService.ACTION_CANCEL_DOWNLOADS)
      startService(i)
    } else {
      startService(audioUtils.getAudioIntent(this, AudioService.ACTION_STOP))
    }
  }

  override fun onAcceptPressed() {
    needsPermissionToDownloadOver3g = false
    audioPresenter.onDownloadPermissionGranted()
  }

  //endregion

  private val selectionStart: SuraAyah?
    // region Ayah selection
    get() {
      val currentSelection = readingEventPresenter.currentAyahSelection()
      return currentSelection.startSuraAyah()
    }

  private val selectionEnd: SuraAyah?
    get() {
      val currentSelection = readingEventPresenter.currentAyahSelection()
      return currentSelection.endSuraAyah()
    }

  val lastAudioRequest: AudioRequest?
    get() = audioStatusRepositoryBridge.audioRequest()

  fun endAyahMode() {
    readingEventPresenterBridge.clearSelectedAyah()
    slidingPanel.collapsePane()
  }

  //endregion
  private fun updateLocalTranslations(start: SuraAyah?) {
    val ayahTracker = resolveCurrentTracker()
    if (ayahTracker != null) {
      lastActivatedLocalTranslations = ayahTracker.getLocalTranslations() ?: emptyArray()
      lastSelectedTranslationAyah = ayahTracker.getQuranAyahInfo(start!!.sura, start.ayah)
    }
  }

  private fun resolveCurrentTracker(): AyahTracker? {
    val position = viewPager.currentItem
    val f = pagerAdapter.getFragmentIfExists(position)
    return if (f is QuranPage && f.isVisible) {
      (f as QuranPage).getAyahTracker()
    } else {
      null
    }
  }

  private inner class AyahMenuItemSelectionHandler : MenuItem.OnMenuItemClickListener {
    override fun onMenuItemClick(item: MenuItem): Boolean {
      var sliderPage = -1
      val currentSelection = readingEventPresenter.currentAyahSelection()
      val startSuraAyah = currentSelection.startSuraAyah()
      val endSuraAyah = currentSelection.endSuraAyah()
      if (startSuraAyah == null || endSuraAyah == null) {
        return false
      }

      val itemId = item.itemId
      if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_bookmark_ayah) {
        val startPage =
          quranInfo.getPageFromSuraAyah(startSuraAyah.sura, startSuraAyah.ayah)
        toggleAyahBookmark(startSuraAyah, startPage)
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_tag_ayah) {
        sliderPage = slidingPagerAdapter.getPagePosition(SlidingPagerAdapter.TAG_PAGE)
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_translate_ayah) {
        sliderPage =
          slidingPagerAdapter.getPagePosition(SlidingPagerAdapter.TRANSLATION_PAGE)
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_play_from_here) {
        quranEventLogger.logAudioPlayback(
          QuranEventLogger.AudioPlaybackSource.AYAH,
          currentQariItem(), isDualPages, showingTranslation, isSplitScreen
        )
        playFromAyah(startSuraAyah.sura, startSuraAyah.ayah)
        toggleActionBarVisibility(true)
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_recite_from_here) {
        onEndSessionBackCallback.isEnabled = true
        pagerActivityRecitationPresenter.onRecitationPressed()
      }/* else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_share_ayah_link) {
        shareAyahLink(startSuraAyah, endSuraAyah)
      } */else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_share_ayah_text) {
        shareAyah(startSuraAyah, endSuraAyah, false)
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_copy_ayah) {
        shareAyah(startSuraAyah, endSuraAyah, true)
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_expand_ayah) {
        expandAyahSelection()
        return true
      } else if (itemId == com.quran.labs.androidquran.common.toolbar.R.id.cab_shrink_ayah) {
        shrinkAyahSelection()
        return true
      } else {
        return false
      }

      if (sliderPage < 0) {
        endAyahMode()
      } else {
        showSlider(sliderPage)
      }
      return true
    }
  }

  private fun showShareChoiceSheet(
    result: com.quran.labs.androidquran.presenter.translation.BaseTranslationPresenter.ResultHolder,
    verseRange: VerseRange,
    activeTranslations: List<String>
  ) {
    val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val themeId = quranSettings.getReadingTheme()
    val isDark = themeId == QuranSettings.THEME_NIGHT || themeId == QuranSettings.THEME_SLATE
    val sheetBgColor = when (themeId) {
      QuranSettings.THEME_SEPIA -> android.graphics.Color.parseColor("#FAF6EC")
      QuranSettings.THEME_SAGE -> android.graphics.Color.parseColor("#EEF5F0")
      QuranSettings.THEME_SLATE -> android.graphics.Color.parseColor("#242933")
      QuranSettings.THEME_NIGHT -> android.graphics.Color.parseColor("#181818")
      else -> android.graphics.Color.WHITE
    }
    val cardBgColor = when (themeId) {
      QuranSettings.THEME_SEPIA -> android.graphics.Color.parseColor("#F1EBD9")
      QuranSettings.THEME_SAGE -> android.graphics.Color.parseColor("#E1ECE4")
      QuranSettings.THEME_SLATE -> android.graphics.Color.parseColor("#2F3643")
      QuranSettings.THEME_NIGHT -> android.graphics.Color.parseColor("#242424")
      else -> android.graphics.Color.parseColor("#F5F6F7")
    }
    val textColor = if (isDark) android.graphics.Color.parseColor("#F2F2F2") else android.graphics.Color.parseColor("#1C1C1E")
    val secondaryColor = if (isDark) android.graphics.Color.parseColor("#A8A8B0") else android.graphics.Color.parseColor("#636366")
    val handleColor = if (isDark) android.graphics.Color.parseColor("#4A4A52") else android.graphics.Color.parseColor("#D1D1D6")
    val accent = androidx.core.content.ContextCompat.getColor(this, R.color.accent_color_dark)
    val badgeBgColor = androidx.core.graphics.ColorUtils.setAlphaComponent(accent, if (isDark) 52 else 28)
    val badgeIconColor = if (isDark) android.graphics.Color.parseColor("#7CC8A9") else accent

    val container = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.VERTICAL
      setPadding(dp(20), dp(10), dp(20), dp(24))
      background = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        setColor(sheetBgColor)
        val r = 20 * density
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
      }
    }

    val dragHandle = android.view.View(this).apply {
      layoutParams = android.widget.LinearLayout.LayoutParams(dp(36), dp(4)).apply {
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        bottomMargin = dp(14)
      }
      background = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        setColor(handleColor)
        cornerRadius = 2 * density
      }
    }
    container.addView(dragHandle)

    var activeRange = verseRange
    var activeResult = result
    var rangeJob: kotlinx.coroutines.Job? = null

    fun formatReference(range: VerseRange): String {
      val suraName = quranDisplayData.getSuraName(this, range.startSura, false, false)
      return if (range.startSura == range.endingSura) {
        if (range.startAyah == range.endingAyah) {
          "$suraName: ${range.startAyah}"
        } else {
          "$suraName: ${range.startAyah}–${range.endingAyah}"
        }
      } else {
        val endSuraName = quranDisplayData.getSuraName(this, range.endingSura, false, false)
        "$suraName ${range.startAyah} – $endSuraName ${range.endingAyah}"
      }
    }

    val title = android.widget.TextView(this).apply {
      text = getString(R.string.share_ayah_sheet_title)
      textSize = 19f
      setTextColor(textColor)
      setTypeface(null, android.graphics.Typeface.BOLD)
    }
    container.addView(title)

    val refRow = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER_VERTICAL
      setPadding(0, dp(4), 0, dp(16))
    }

    val refView = android.widget.TextView(this).apply {
      text = formatReference(activeRange)
      textSize = 13.5f
      setTextColor(secondaryColor)
      layoutParams = android.widget.LinearLayout.LayoutParams(
        0,
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
      )
    }

    fun makeStepperButton(iconRes: Int, descRes: Int): android.widget.ImageButton {
      return android.widget.ImageButton(this).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(dp(34), dp(34))
        setImageResource(iconRes)
        setColorFilter(textColor)
        contentDescription = getString(descRes)
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(7), dp(7), dp(7), dp(7))
        background = android.graphics.drawable.GradientDrawable().apply {
          shape = android.graphics.drawable.GradientDrawable.OVAL
          setColor(cardBgColor)
        }
      }
    }

    val minusBtn = makeStepperButton(
      com.quran.labs.androidquran.common.toolbar.R.drawable.ic_minus,
      com.quran.labs.androidquran.common.toolbar.R.string.shrink_ayah_selection
    ).apply {
      (layoutParams as android.widget.LinearLayout.LayoutParams).marginEnd = dp(8)
    }
    val plusBtn = makeStepperButton(
      com.quran.labs.androidquran.common.toolbar.R.drawable.ic_plus,
      com.quran.labs.androidquran.common.toolbar.R.string.expand_ayah_selection
    )

    fun refreshStepperState() {
      val start = SuraAyah(activeRange.startSura, activeRange.startAyah)
      val end = SuraAyah(activeRange.endingSura, activeRange.endingAyah)
      val canShrink = end > start
      val canExpand = end.next(quranInfo) != null
      minusBtn.isEnabled = canShrink
      minusBtn.alpha = if (canShrink) 1f else 0.35f
      plusBtn.isEnabled = canExpand
      plusBtn.alpha = if (canExpand) 1f else 0.35f
      refView.text = formatReference(activeRange)
    }

    fun updateRange(expand: Boolean) {
      val start = SuraAyah(activeRange.startSura, activeRange.startAyah)
      val end = SuraAyah(activeRange.endingSura, activeRange.endingAyah)
      val newEnd = if (expand) {
        end.next(quranInfo) ?: return
      } else {
        if (end <= start) return
        val prev = end.prev(quranInfo) ?: return
        if (prev < start) start else prev
      }
      container.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
      val count = 1 + abs(quranInfo.getAyahId(start.sura, start.ayah) - quranInfo.getAyahId(newEnd.sura, newEnd.ayah))
      val nextRange = VerseRange(start.sura, start.ayah, newEnd.sura, newEnd.ayah, count)
      activeRange = nextRange
      refreshStepperState()
      rangeJob?.cancel()
      rangeJob = scope.launch {
        activeResult = translationPresenter.getVerses(true, activeTranslations, nextRange)
      }
    }

    minusBtn.setOnClickListener { updateRange(expand = false) }
    plusBtn.setOnClickListener { updateRange(expand = true) }
    refreshStepperState()

    refRow.addView(refView)
    refRow.addView(minusBtn)
    refRow.addView(plusBtn)
    container.addView(refRow)

    fun addOption(iconRes: Int, label: String, subtitle: String, addBottomGap: Boolean, onClick: () -> Unit) {
      val row = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        layoutParams = android.widget.LinearLayout.LayoutParams(
          android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
          android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
          if (addBottomGap) bottomMargin = dp(10)
        }
        background = android.graphics.drawable.GradientDrawable().apply {
          shape = android.graphics.drawable.GradientDrawable.RECTANGLE
          setColor(cardBgColor)
          cornerRadius = 14 * density
        }
        isClickable = true
        isFocusable = true
        setOnClickListener {
          dialog.dismiss()
          onClick()
        }
      }

      val iconBadge = android.widget.FrameLayout(this).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(dp(42), dp(42)).apply {
          marginEnd = dp(14)
        }
        background = android.graphics.drawable.GradientDrawable().apply {
          shape = android.graphics.drawable.GradientDrawable.RECTANGLE
          setColor(badgeBgColor)
          cornerRadius = 12 * density
        }
        val iconView = android.widget.ImageView(this@PagerActivity).apply {
          setImageResource(iconRes)
          setColorFilter(badgeIconColor)
          layoutParams = android.widget.FrameLayout.LayoutParams(dp(22), dp(22), android.view.Gravity.CENTER)
        }
        addView(iconView)
      }

      val textCol = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }
      val primary = android.widget.TextView(this).apply {
        text = label
        textSize = 16f
        setTextColor(textColor)
        setTypeface(null, android.graphics.Typeface.BOLD)
      }
      val secondary = android.widget.TextView(this).apply {
        text = subtitle
        textSize = 13f
        setTextColor(secondaryColor)
        setPadding(0, dp(2), 0, 0)
      }
      textCol.addView(primary)
      textCol.addView(secondary)

      val chevron = android.widget.TextView(this).apply {
        text = "›"
        textSize = 22f
        setTextColor(secondaryColor)
        setPadding(dp(8), 0, dp(2), 0)
      }

      row.addView(iconBadge)
      row.addView(textCol)
      row.addView(chevron)
      container.addView(row)
    }

    fun launchShareCard(isVideoMode: Boolean) {
      scope.launch {
        rangeJob?.join()
        if (isFinishing || isDestroyed) return@launch
        val cardData = buildAyahShareCardData(activeResult.ayahInformation, activeRange)
        if (isFinishing || isDestroyed) return@launch
        startActivity(
          com.quran.labs.androidquran.ui.shareimage.ShareAyahImageActivity.createIntent(
            this@PagerActivity,
            cardData,
            isVideoMode = isVideoMode
          )
        )
      }
    }

    addOption(
      iconRes = R.drawable.ic_share_image,
      label = getString(R.string.share_as_image_title),
      subtitle = getString(R.string.share_as_image_subtitle),
      addBottomGap = true
    ) {
      launchShareCard(isVideoMode = false)
    }

    addOption(
      iconRes = R.drawable.ic_share_image,
      label = getString(R.string.share_as_video_title),
      subtitle = getString(R.string.share_as_video_subtitle),
      addBottomGap = true
    ) {
      launchShareCard(isVideoMode = true)
    }

    addOption(
      iconRes = R.drawable.ic_share_text,
      label = getString(R.string.share_as_text_title),
      subtitle = getString(R.string.share_as_text_subtitle),
      addBottomGap = false
    ) {
      scope.launch {
        rangeJob?.join()
        shareUtil.shareVersesKQA(this@PagerActivity, activeResult, activeRange, activeTranslations)
      }
    }

    dialog.setContentView(container)
    dialog.setOnShowListener {
      dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
        ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }
    dialog.show()
  }

  private fun toArabicIndicDigits(number: Int): String {
    val digits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    return number.toString().map { ch ->
      if (ch in '0'..'9') digits[ch - '0'] else ch
    }.joinToString("")
  }

  private fun buildAyahShareCardData(
    verses: List<QuranAyahInfo>,
    verseRange: VerseRange
  ): com.quran.labs.androidquran.ui.shareimage.AyahShareCardData {
    val footnoteRegex = "\\[\\[(.+?)]]".toRegex(RegexOption.DOT_MATCHES_ALL)
    val footnotes = mutableListOf<String>()

    val arabicBuilder = StringBuilder()
    val translationBuilder = StringBuilder()
    val ayahSlides = mutableListOf<com.quran.labs.androidquran.ui.shareimage.AyahSlideData>()

    verses.forEach { ayahInfo ->
      val slideArabic = ayahInfo.arabicText?.let { rawArabic ->
        val cleaned = com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils.getAyahWithoutBasmallah(
          ayahInfo.sura,
          ayahInfo.ayah,
          rawArabic.trim()
        )
        if (cleaned.isNotBlank()) {
          "$cleaned\u00A0${toArabicIndicDigits(ayahInfo.ayah)}"
        } else {
          ""
        }
      }.orEmpty()

      if (slideArabic.isNotEmpty()) {
        if (arabicBuilder.isNotEmpty()) arabicBuilder.append(" ")
        arabicBuilder.append(slideArabic)
      }

      val slideFootnotes = mutableListOf<String>()
      val slideTranslationBuilder = StringBuilder()
      ayahInfo.texts.forEach { translation ->
        val text = translation.text
        if (text.isNotEmpty()) {
          val processedText = footnoteRegex.replace(text) { match ->
            val note = match.groupValues[1].trim()
            footnotes.add(note)
            "[${footnotes.size}]"
          }
          if (translationBuilder.isNotEmpty()) translationBuilder.append("\n")
          translationBuilder.append(processedText.trim())

          val slideProcessedText = footnoteRegex.replace(text) { match ->
            val note = match.groupValues[1].trim()
            slideFootnotes.add(note)
            "[${slideFootnotes.size}]"
          }
          if (slideTranslationBuilder.isNotEmpty()) slideTranslationBuilder.append("\n")
          slideTranslationBuilder.append(slideProcessedText.trim())
        }
      }

      ayahSlides.add(
        com.quran.labs.androidquran.ui.shareimage.AyahSlideData(
          sura = ayahInfo.sura,
          ayah = ayahInfo.ayah,
          referenceText = "${quranDisplayData.getSuraName(this@PagerActivity, ayahInfo.sura, true)}: ${ayahInfo.ayah}",
          arabicText = slideArabic,
          translationText = slideTranslationBuilder.toString(),
          footnotes = slideFootnotes
        )
      )
    }

    val reference = buildString {
      append(quranDisplayData.getSuraName(this@PagerActivity, verseRange.startSura, true))
      append(": ")
      append(verseRange.startAyah)
      if (verseRange.versesInRange > 1) {
        if (verseRange.startSura != verseRange.endingSura) {
          append(" - ")
          append(quranDisplayData.getSuraName(this@PagerActivity, verseRange.endingSura, true))
          append(": ")
        } else {
          append("-")
        }
        append(verseRange.endingAyah)
      }
    }

    val isKannada = resources.configuration.locales[0]?.language == "kn"
    return com.quran.labs.androidquran.ui.shareimage.AyahShareCardData(
      referenceText = reference,
      arabicText = arabicBuilder.toString(),
      translationText = translationBuilder.toString(),
      footnotes = footnotes,
      isKannada = isKannada,
      startSura = verseRange.startSura,
      startAyah = verseRange.startAyah,
      endSura = verseRange.endingSura,
      endAyah = verseRange.endingAyah,
      ayahSlides = ayahSlides
    )
  }

  private fun shareAyah(start: SuraAyah?, end: SuraAyah?, isCopy: Boolean) {
    if (start == null || end == null) {
      return
    } else if (!quranFileUtils.hasArabicSearchDatabase()) {
      showGetRequiredFilesDialog()
      return
    }

    /*KQACR7 start*/
    val verses = 1 + abs(
      quranInfo.getAyahId(start.sura, start.ayah) - quranInfo.getAyahId(end.sura, end.ayah)
    )
    val verseRange = VerseRange(start.sura, start.ayah, end.sura, end.ayah, verses)

    scope.launch {
      var activeTranslations = translationPresenter.getTranslations(quranSettings)
      val result = translationPresenter.getVerses(true, activeTranslations, verseRange)
      if (isCopy) {
        shareUtil.copyVersesKQA(this@PagerActivity, result,verseRange,activeTranslations)
      } else {
        showShareChoiceSheet(result, verseRange, activeTranslations)
      }
    }


 /*
    compositeDisposable.add(
      arabicDatabaseUtils
        .getVerses(start, end)
        .filter { quranAyahs: List<QuranText?> -> quranAyahs.isNotEmpty() }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { quranAyahs: List<QuranText> ->
          if (isCopy) {
            shareUtil.copyVerses(this@PagerActivity, quranAyahs)
          } else {
            shareUtil.shareVerses(this@PagerActivity, quranAyahs)
          }
        })
    */
    /*KQACR7 end*/
  }

  fun shareAyahLink(start: SuraAyah, end: SuraAyah) {
    showProgressDialog()
    compositeDisposable.add(
      quranAppUtils.getQuranAppUrlObservable(getString(R.string.quranapp_key), start, end)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(object : DisposableSingleObserver<String>() {
          override fun onSuccess(url: String) {
            shareUtil.shareViaIntent(
              this@PagerActivity,
              url,
              com.quran.labs.androidquran.common.toolbar.R.string.share_ayah
            )
            dismissProgressDialog()
          }

          override fun onError(e: Throwable) {
            dismissProgressDialog()
          }
        })
    )
  }

  private fun showProgressDialog() {
    if (progressDialog == null) {
      progressDialog = ProgressDialog(this).apply {
        isIndeterminate = true
        setMessage(getString(com.quran.mobile.common.ui.core.R.string.loading))
        show()
      }
    }
  }

  private fun dismissProgressDialog() {
    if (progressDialog?.isShowing == true) {
      progressDialog?.dismiss()
    }
    progressDialog = null
  }

  private fun showLanguageDialog() {
    LanguageDialogHelper.show(this, quranSettings)
  }

  private fun showSlider(sliderPage: Int) {
    readingEventPresenterBridge.clearMenuForSelection()
    slidingPager.currentItem = sliderPage
    slidingPanel.showPane()
    // TODO there's got to be a better way than this hack
    // The issue is that smoothScrollTo returns if mCanSlide is false
    // and it's false when the panel is GONE and showPane only calls
    // requestLayout, and only in onLayout does mCanSlide become true.
    // So by posting this later it gives time for onLayout to run.
    handler.post { slidingPanel.expandPane() }
  }

  private fun updateAyahBookmark(suraAyah: SuraAyah, bookmarked: Boolean) {
    // Refresh toolbar icon
    val start = selectionStart
    if (start != null && start == suraAyah) {
      ayahToolBar.setBookmarked(bookmarked)
    }
  }

  private fun promptForMultipleChoicePlay(
    page: Int, startSura: Int, startAyah: Int, startingSuraList: List<Int>
  ) {
    val adapter = ArrayAdapter<String>(this, android.R.layout.select_dialog_item)
    startingSuraList
      .map { quranDisplayData.getSuraName(this, it, false) }
      .onEach { adapter.add(it) }

    val suraList = if (startSura != startingSuraList[0]) {
      adapter.insert(getString(R.string.starting_page_label), 0)
      listOf(startSura) + startingSuraList
    } else {
      startingSuraList
    }

    val builder = AlertDialog.Builder(this)
      .setTitle(getString(R.string.playback_prompt_title))
      .setAdapter(adapter) { dialog: DialogInterface, i: Int ->
        if (i == 0) {
          playFromAyah(startSura, startAyah)
        } else {
          playFromAyah(suraList[i], 1)
        }
        dialog.dismiss()
        promptDialog = null
      }
    promptDialog = builder.create()
    promptDialog!!.show()
  }

  private inner class SlidingPanelListener : SlidingUpPanelLayout.PanelSlideListener {
    override fun onPanelSlide(panel: View, slideOffset: Float) {
    }

    override fun onPanelCollapsed(panel: View) {
      if (selectionStart != null) {
        endAyahMode()
      }
      slidingPanel.hidePane()
      readingEventPresenter.onPanelClosed()
    }

    override fun onPanelExpanded(panel: View) {
      readingEventPresenter.onPanelOpened()
    }

    override fun onPanelAnchored(panel: View) {
    }
  }

  private fun showFontSizeDialog() {
    val quranSettings = QuranSettings.getInstance(this)
    val currentTranslationSize = quranSettings.translationTextSize
    val currentArabicSize = quranSettings.ayahTextSize
    val currentSpacing = quranSettings.getTranslationLineSpacing()
    var currentMode = quranSettings.translationViewMode

    val density = resources.displayMetrics.density

    val container = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.VERTICAL
      val padding = (24 * density).toInt()
      setPadding(padding, padding, padding, padding)
    }

    val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)

    val sheetTitleLabel = android.widget.TextView(this).apply {
      text = "Reading Options"
      textSize = 18f
      setTypeface(null, android.graphics.Typeface.BOLD)
      gravity = android.view.Gravity.LEFT
      setPadding((4 * density).toInt(), 0, 0, (12 * density).toInt())
    }

    // Theme Switcher Section
    val themeLabel = android.widget.TextView(this).apply {
      text = getString(R.string.reading_theme)
      textSize = 15f
      gravity = android.view.Gravity.LEFT
      setPadding((4 * density).toInt(), (4 * density).toInt(), 0, (6 * density).toInt())
    }

    val themeLayout = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    fun createCircleDrawable(color: Int): android.graphics.drawable.GradientDrawable {
      return android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(color)
      }
    }

    val themeBtnLp = android.widget.LinearLayout.LayoutParams(
      0,
      android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
      1f
    )
    val themeBtnPadH = (4 * density).toInt()
    val themeBtnPadV = (6 * density).toInt()

    val lightCircle = android.view.View(this).apply {
      layoutParams = android.widget.LinearLayout.LayoutParams((34 * density).toInt(), (34 * density).toInt())
      background = createCircleDrawable(android.graphics.Color.WHITE)
    }
    val lightLabel = android.widget.TextView(this).apply {
      text = getString(R.string.theme_light)
      textSize = 11.5f
      maxLines = 1
      gravity = android.view.Gravity.CENTER
      setPadding(0, (4 * density).toInt(), 0, 0)
    }
    val lightBtn = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(themeBtnLp)
      setPadding(themeBtnPadH, themeBtnPadV, themeBtnPadH, themeBtnPadV)
      addView(lightCircle)
      addView(lightLabel)
    }

    val sepiaCircle = android.view.View(this).apply {
      layoutParams = android.widget.LinearLayout.LayoutParams((34 * density).toInt(), (34 * density).toInt())
      background = createCircleDrawable(android.graphics.Color.parseColor("#FFFDF9"))
    }
    val sepiaLabel = android.widget.TextView(this).apply {
      text = getString(R.string.theme_sepia)
      textSize = 11.5f
      maxLines = 1
      gravity = android.view.Gravity.CENTER
      setPadding(0, (4 * density).toInt(), 0, 0)
    }
    val sepiaBtn = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(themeBtnLp)
      setPadding(themeBtnPadH, themeBtnPadV, themeBtnPadH, themeBtnPadV)
      addView(sepiaCircle)
      addView(sepiaLabel)
    }

    val nightCircle = android.view.View(this).apply {
      layoutParams = android.widget.LinearLayout.LayoutParams((34 * density).toInt(), (34 * density).toInt())
      background = createCircleDrawable(android.graphics.Color.parseColor("#121212"))
    }
    val nightLabel = android.widget.TextView(this).apply {
      text = getString(R.string.theme_night)
      textSize = 11.5f
      maxLines = 1
      gravity = android.view.Gravity.CENTER
      setPadding(0, (4 * density).toInt(), 0, 0)
    }
    val nightBtn = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(themeBtnLp)
      setPadding(themeBtnPadH, themeBtnPadV, themeBtnPadH, themeBtnPadV)
      addView(nightCircle)
      addView(nightLabel)
    }

    val sageCircle = android.view.View(this).apply {
      layoutParams = android.widget.LinearLayout.LayoutParams((34 * density).toInt(), (34 * density).toInt())
      background = createCircleDrawable(android.graphics.Color.parseColor("#EEF5F0"))
    }
    val sageLabel = android.widget.TextView(this).apply {
      text = getString(R.string.theme_sage)
      textSize = 11.5f
      maxLines = 1
      gravity = android.view.Gravity.CENTER
      setPadding(0, (4 * density).toInt(), 0, 0)
    }
    val sageBtn = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(themeBtnLp)
      setPadding(themeBtnPadH, themeBtnPadV, themeBtnPadH, themeBtnPadV)
      addView(sageCircle)
      addView(sageLabel)
    }

    val slateCircle = android.view.View(this).apply {
      layoutParams = android.widget.LinearLayout.LayoutParams((34 * density).toInt(), (34 * density).toInt())
      background = createCircleDrawable(android.graphics.Color.parseColor("#1E242B"))
    }
    val slateLabel = android.widget.TextView(this).apply {
      text = getString(R.string.theme_slate)
      textSize = 11.5f
      maxLines = 1
      gravity = android.view.Gravity.CENTER
      setPadding(0, (4 * density).toInt(), 0, 0)
    }
    val slateBtn = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(themeBtnLp)
      setPadding(themeBtnPadH, themeBtnPadV, themeBtnPadH, themeBtnPadV)
      addView(slateCircle)
      addView(slateLabel)
    }

    fun updateThemeBorders(selectedTheme: Int) {
      val accent = androidx.core.content.ContextCompat.getColor(this@PagerActivity, R.color.accent_color_dark)
      (lightCircle.background as android.graphics.drawable.GradientDrawable).apply {
        if (selectedTheme == QuranSettings.THEME_LIGHT) {
          setStroke((3 * density).toInt(), accent)
        } else {
          setStroke((1 * density).toInt(), android.graphics.Color.parseColor("#CCCCCC"))
        }
      }
      (sepiaCircle.background as android.graphics.drawable.GradientDrawable).apply {
        if (selectedTheme == QuranSettings.THEME_SEPIA) {
          setStroke((3 * density).toInt(), accent)
        } else {
          setStroke((1 * density).toInt(), android.graphics.Color.parseColor("#E0D6C8"))
        }
      }
      (sageCircle.background as android.graphics.drawable.GradientDrawable).apply {
        if (selectedTheme == QuranSettings.THEME_SAGE) {
          setStroke((3 * density).toInt(), accent)
        } else {
          setStroke((1 * density).toInt(), android.graphics.Color.parseColor("#C2D6C7"))
        }
      }
      (slateCircle.background as android.graphics.drawable.GradientDrawable).apply {
        if (selectedTheme == QuranSettings.THEME_SLATE) {
          setStroke((3 * density).toInt(), accent)
        } else {
          setStroke(0, 0)
        }
      }
      (nightCircle.background as android.graphics.drawable.GradientDrawable).apply {
        if (selectedTheme == QuranSettings.THEME_NIGHT) {
          setStroke((3 * density).toInt(), accent)
        } else {
          setStroke(0, 0)
        }
      }
    }

    // Show all 5 reading themes (Light, Sepia, Sage, Slate, Night) regardless of current dark/light mode
    // so users always discover dark themes easily.
    themeLayout.addView(lightBtn)
    themeLayout.addView(sepiaBtn)
    themeLayout.addView(sageBtn)
    themeLayout.addView(slateBtn)
    themeLayout.addView(nightBtn)

    // Appearance selector row (System / Light / Dark)
    val appearancePrefs = PreferenceManager.getDefaultSharedPreferences(this)
    var currentAppTheme = appearancePrefs.getString(Constants.PREF_APP_THEME, "dark") ?: "dark"
    val appearanceLayout = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
        (36 * density).toInt()
      ).apply {
        bottomMargin = (10 * density).toInt()
      }
    }
    val appearanceAutoBtn = android.widget.TextView(this).apply {
      text = getString(R.string.prefs_app_theme_auto)
      textSize = 12.5f
      maxLines = 1
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }
    val appearanceLightBtn = android.widget.TextView(this).apply {
      text = getString(R.string.prefs_app_theme_light)
      textSize = 12.5f
      maxLines = 1
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }
    val appearanceDarkBtn = android.widget.TextView(this).apply {
      text = getString(R.string.prefs_app_theme_dark)
      textSize = 12.5f
      maxLines = 1
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }
    appearanceLayout.addView(appearanceAutoBtn)
    appearanceLayout.addView(appearanceLightBtn)
    appearanceLayout.addView(appearanceDarkBtn)

    // 3-Way Display Mode Selector Section
    val displayModeLabel = android.widget.TextView(this).apply {
      text = getString(R.string.display_mode)
      textSize = 15f
      gravity = android.view.Gravity.LEFT
      setPadding((4 * density).toInt(), (10 * density).toInt(), 0, (6 * density).toInt())
    }

    val modeLayout = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
        (40 * density).toInt()
      )
    }

    fun createPillDrawable(bgColor: Int, cornerRadius: Float): android.graphics.drawable.GradientDrawable {
      return android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        setColor(bgColor)
        this.cornerRadius = cornerRadius
      }
    }

    val modeBothBtn = android.widget.TextView(this).apply {
      text = getString(R.string.mode_both)
      textSize = 13f
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }

    val modeArabicBtn = android.widget.TextView(this).apply {
      text = getString(R.string.mode_arabic)
      textSize = 13f
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }

    val modeTranslationBtn = android.widget.TextView(this).apply {
      text = getString(R.string.mode_translation)
      textSize = 13f
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }

    modeLayout.addView(modeBothBtn)
    modeLayout.addView(modeArabicBtn)
    modeLayout.addView(modeTranslationBtn)

    // Arabic Font Selector Section
    var currentArabicFont = quranSettings.arabicFont
    val fontLabel = android.widget.TextView(this).apply {
      text = getString(R.string.arabic_font)
      textSize = 15f
      gravity = android.view.Gravity.LEFT
      setPadding((4 * density).toInt(), (10 * density).toInt(), 0, (6 * density).toInt())
    }

    val fontLayout = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
        (40 * density).toInt()
      )
    }

    val fontHafsBoldBtn = android.widget.TextView(this).apply {
      text = getString(R.string.font_hafs_bold)
      textSize = 13f
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }

    val fontHafsClassicBtn = android.widget.TextView(this).apply {
      text = getString(R.string.font_hafs_classic)
      textSize = 13f
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }

    val fontKitabBtn = android.widget.TextView(this).apply {
      text = getString(R.string.font_kitab)
      textSize = 11.5f
      maxLines = 1
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }

    val fontNaskhBtn = android.widget.TextView(this).apply {
      text = "Naskh"
      textSize = 11.5f
      maxLines = 1
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }

    val fontSystemBtn = android.widget.TextView(this).apply {
      text = "System"
      textSize = 11.5f
      maxLines = 1
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }

    fontLayout.addView(fontHafsBoldBtn)
    fontLayout.addView(fontHafsClassicBtn)
    fontLayout.addView(fontKitabBtn)
    fontLayout.addView(fontNaskhBtn)
    fontLayout.addView(fontSystemBtn)

    // Arabic Font Size
    val arabicLabel = android.widget.TextView(this).apply {
      text = getString(R.string.arabic_font_size, currentArabicSize)
      textSize = 15f
      gravity = android.view.Gravity.LEFT
      setPadding((4 * density).toInt(), (10 * density).toInt(), 0, (6 * density).toInt())
    }

    val arabicSeekBar = android.widget.SeekBar(this).apply {
      max = 18 // 16 to 34
      progress = (currentArabicSize - 16).coerceIn(0, 18)
      setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
          val newSize = progress + 16
          arabicLabel.text = getString(R.string.arabic_font_size, newSize)
          quranSettings.ayahTextSize = newSize
          if (fromUser) {
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
          }
          refreshQuranPages()
        }
        override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
        override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
      })
    }

    // Translation Font Size
    val translationLabel = android.widget.TextView(this).apply {
      text = getString(R.string.translation_font_size, currentTranslationSize)
      textSize = 15f
      gravity = android.view.Gravity.LEFT
      setPadding((4 * density).toInt(), (10 * density).toInt(), 0, (6 * density).toInt())
    }

    val translationSeekBar = android.widget.SeekBar(this).apply {
      max = 15 // 13 to 28
      progress = (currentTranslationSize - 13).coerceIn(0, 15)
      setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
          val newSize = progress + 13
          translationLabel.text = getString(R.string.translation_font_size, newSize)
          quranSettings.translationTextSize = newSize
          if (fromUser) {
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
          }
          refreshQuranPages()
        }
        override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
        override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
      })
    }

    // Translation Font Selector Section
    var currentTranslationFont = quranSettings.translationFont
    val translationFontLabel = android.widget.TextView(this).apply {
      text = getString(R.string.translation_font)
      textSize = 15f
      gravity = android.view.Gravity.LEFT
      setPadding((4 * density).toInt(), (10 * density).toInt(), 0, (6 * density).toInt())
    }

    val translationFontLayout = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER
      layoutParams = android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
        (40 * density).toInt()
      )
    }

    fun makeTranslationFontButton(labelRes: Int, previewTypeface: android.graphics.Typeface): android.widget.TextView {
      return android.widget.TextView(this).apply {
        text = getString(labelRes)
        textSize = 13f
        typeface = previewTypeface
        maxLines = 1
        gravity = android.view.Gravity.CENTER
        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
      }
    }

    val transFontSystemBtn = makeTranslationFontButton(R.string.translation_font_system, android.graphics.Typeface.DEFAULT)
    val transFontSansBtn = makeTranslationFontButton(R.string.translation_font_sans, android.graphics.Typeface.SANS_SERIF)
    val transFontSerifBtn = makeTranslationFontButton(R.string.translation_font_serif, android.graphics.Typeface.SERIF)
    val transFontBoldBtn = makeTranslationFontButton(
      R.string.translation_font_bold,
      android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    )

    translationFontLayout.addView(transFontSystemBtn)
    translationFontLayout.addView(transFontSansBtn)
    translationFontLayout.addView(transFontSerifBtn)
    translationFontLayout.addView(transFontBoldBtn)

    // Line Spacing
    val spacingLabel = android.widget.TextView(this).apply {
      text = getString(R.string.line_spacing, String.format(java.util.Locale.US, "%.2fx", currentSpacing))
      textSize = 15f
      gravity = android.view.Gravity.LEFT
      setPadding((4 * density).toInt(), (10 * density).toInt(), 0, (6 * density).toInt())
    }

    val spacingSeekBar = android.widget.SeekBar(this).apply {
      max = 2 // 0 = 1.0f, 1 = 1.25f, 2 = 1.5f
      progress = when {
        currentSpacing <= 1.0f -> 0
        currentSpacing <= 1.25f -> 1
        else -> 2
      }
      setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
          val spacing = when (progress) {
            0 -> 1.0f
            1 -> 1.25f
            else -> 1.5f
          }
          spacingLabel.text = getString(R.string.line_spacing, String.format(java.util.Locale.US, "%.2fx", spacing))
          quranSettings.setTranslationLineSpacing(spacing)
          if (fromUser) {
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
          }
          refreshQuranPages()
        }
        override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
        override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
      })
    }

    fun updateModeVisibility(mode: Int) {
      val showArabic = mode == QuranSettings.DISPLAY_MODE_BOTH || mode == QuranSettings.DISPLAY_MODE_ARABIC_ONLY
      val showTranslation = mode == QuranSettings.DISPLAY_MODE_BOTH || mode == QuranSettings.DISPLAY_MODE_TRANSLATION_ONLY

      fontLabel.visibility = if (showArabic) android.view.View.VISIBLE else android.view.View.GONE
      fontLayout.visibility = if (showArabic) android.view.View.VISIBLE else android.view.View.GONE
      arabicLabel.visibility = if (showArabic) android.view.View.VISIBLE else android.view.View.GONE
      arabicSeekBar.visibility = if (showArabic) android.view.View.VISIBLE else android.view.View.GONE

      translationLabel.visibility = if (showTranslation) android.view.View.VISIBLE else android.view.View.GONE
      translationSeekBar.visibility = if (showTranslation) android.view.View.VISIBLE else android.view.View.GONE
      translationFontLabel.visibility = if (showTranslation) android.view.View.VISIBLE else android.view.View.GONE
      translationFontLayout.visibility = if (showTranslation) android.view.View.VISIBLE else android.view.View.GONE

      spacingLabel.visibility = if (showTranslation) android.view.View.VISIBLE else android.view.View.GONE
      spacingSeekBar.visibility = if (showTranslation) android.view.View.VISIBLE else android.view.View.GONE
    }

    fun updatePillSelector(
      pillContainer: android.widget.LinearLayout,
      items: List<Pair<android.widget.TextView, String>>,
      selectedKey: String,
      themeId: Int
    ) {
      val isDark = themeId == QuranSettings.THEME_NIGHT || themeId == QuranSettings.THEME_SLATE
      val accent = androidx.core.content.ContextCompat.getColor(this@PagerActivity, R.color.accent_color_dark)

      val unselectedBg = if (isDark) android.graphics.Color.parseColor("#2A2A2A") else android.graphics.Color.parseColor("#EAEAEA")
      val unselectedText = if (isDark) android.graphics.Color.parseColor("#9E9E9E") else android.graphics.Color.parseColor("#757575")
      val selectedBg = accent
      val selectedText = android.graphics.Color.WHITE

      val r = 20 * density
      pillContainer.background = createPillDrawable(unselectedBg, r)

      items.forEach { (btn, key) ->
        if (selectedKey == key) {
          btn.background = createPillDrawable(selectedBg, r)
          btn.setTextColor(selectedText)
          btn.setTypeface(btn.typeface, android.graphics.Typeface.BOLD)
        } else {
          btn.background = null
          btn.setTextColor(unselectedText)
          btn.setTypeface(btn.typeface, android.graphics.Typeface.NORMAL)
        }
      }
    }

    fun updateFontButtons(selectedFont: String, themeId: Int) {
      updatePillSelector(
        fontLayout,
        listOf(
          fontHafsBoldBtn to QuranSettings.FONT_HAFS_BOLD,
          fontHafsClassicBtn to QuranSettings.FONT_HAFS_CLASSIC,
          fontKitabBtn to QuranSettings.FONT_KITAB,
          fontNaskhBtn to QuranSettings.FONT_NASKH,
          fontSystemBtn to QuranSettings.FONT_SYSTEM
        ),
        selectedFont,
        themeId
      )
    }

    fun updateTranslationFontButtons(selectedFont: String, themeId: Int) {
      updatePillSelector(
        translationFontLayout,
        listOf(
          transFontSystemBtn to QuranSettings.TRANSLATION_FONT_SYSTEM,
          transFontSansBtn to QuranSettings.TRANSLATION_FONT_SANS,
          transFontSerifBtn to QuranSettings.TRANSLATION_FONT_SERIF,
          transFontBoldBtn to QuranSettings.TRANSLATION_FONT_BOLD
        ),
        selectedFont,
        themeId
      )
    }

    fun updateModeButtons(selectedMode: Int, themeId: Int) {
      val isDark = themeId == QuranSettings.THEME_NIGHT || themeId == QuranSettings.THEME_SLATE
      val accent = androidx.core.content.ContextCompat.getColor(this@PagerActivity, R.color.accent_color_dark)

      val unselectedBg = if (isDark) android.graphics.Color.parseColor("#2A2A2A") else android.graphics.Color.parseColor("#EAEAEA")
      val unselectedText = if (isDark) android.graphics.Color.parseColor("#9E9E9E") else android.graphics.Color.parseColor("#757575")
      val selectedBg = accent
      val selectedText = android.graphics.Color.WHITE

      modeLayout.background = createPillDrawable(unselectedBg, 20 * density)

      val r = 20 * density
      modeBothBtn.apply {
        if (selectedMode == QuranSettings.DISPLAY_MODE_BOTH) {
          background = createPillDrawable(selectedBg, r)
          setTextColor(selectedText)
          setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
          background = null
          setTextColor(unselectedText)
          setTypeface(null, android.graphics.Typeface.NORMAL)
        }
      }

      modeArabicBtn.apply {
        if (selectedMode == QuranSettings.DISPLAY_MODE_ARABIC_ONLY) {
          background = createPillDrawable(selectedBg, r)
          setTextColor(selectedText)
          setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
          background = null
          setTextColor(unselectedText)
          setTypeface(null, android.graphics.Typeface.NORMAL)
        }
      }

      modeTranslationBtn.apply {
        if (selectedMode == QuranSettings.DISPLAY_MODE_TRANSLATION_ONLY) {
          background = createPillDrawable(selectedBg, r)
          setTextColor(selectedText)
          setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
          background = null
          setTextColor(unselectedText)
          setTypeface(null, android.graphics.Typeface.NORMAL)
        }
      }
    }

    fun applyThemeToDialogViews(themeId: Int) {
      val bgColor = when (themeId) {
        QuranSettings.THEME_NIGHT -> android.graphics.Color.parseColor("#1E1E1E")
        QuranSettings.THEME_SLATE -> android.graphics.Color.parseColor("#1E242B")
        QuranSettings.THEME_SEPIA -> android.graphics.Color.parseColor("#FFF5E6")
        QuranSettings.THEME_SAGE -> android.graphics.Color.parseColor("#E6EFE9")
        else -> android.graphics.Color.WHITE
      }

      val textColor = when (themeId) {
        QuranSettings.THEME_NIGHT -> android.graphics.Color.parseColor("#E0E0E0")
        QuranSettings.THEME_SLATE -> android.graphics.Color.parseColor("#E2E8F0")
        QuranSettings.THEME_SEPIA -> android.graphics.Color.parseColor("#3E2723")
        QuranSettings.THEME_SAGE -> android.graphics.Color.parseColor("#1B3022")
        else -> android.graphics.Color.parseColor("#212121")
      }

      val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
      bottomSheet?.background = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        setColor(bgColor)
        val r = 16 * density
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
      }

      val textViews = listOf(
        sheetTitleLabel, themeLabel, lightLabel, sepiaLabel, sageLabel, slateLabel, nightLabel,
        displayModeLabel, fontLabel, arabicLabel, translationLabel, translationFontLabel, spacingLabel
      )
      textViews.forEach { it.setTextColor(textColor) }

      updateModeButtons(currentMode, themeId)
      updateFontButtons(currentArabicFont, themeId)
      updateTranslationFontButtons(currentTranslationFont, themeId)
      updatePillSelector(
        appearanceLayout,
        listOf(
          appearanceAutoBtn to "auto",
          appearanceLightBtn to "light",
          appearanceDarkBtn to "dark"
        ),
        currentAppTheme,
        themeId
      )
    }

    listOf(
      transFontSystemBtn to QuranSettings.TRANSLATION_FONT_SYSTEM,
      transFontSansBtn to QuranSettings.TRANSLATION_FONT_SANS,
      transFontSerifBtn to QuranSettings.TRANSLATION_FONT_SERIF,
      transFontBoldBtn to QuranSettings.TRANSLATION_FONT_BOLD
    ).forEach { (btn, fontKey) ->
      btn.setOnClickListener {
        if (currentTranslationFont != fontKey) {
          currentTranslationFont = fontKey
          quranSettings.translationFont = fontKey
          btn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
          updateTranslationFontButtons(currentTranslationFont, quranSettings.getReadingTheme())
          refreshQuranPages()
        }
      }
    }

    listOf(
      fontHafsBoldBtn to QuranSettings.FONT_HAFS_BOLD,
      fontHafsClassicBtn to QuranSettings.FONT_HAFS_CLASSIC,
      fontKitabBtn to QuranSettings.FONT_KITAB,
      fontNaskhBtn to QuranSettings.FONT_NASKH,
      fontSystemBtn to QuranSettings.FONT_SYSTEM
    ).forEach { (btn, fontKey) ->
      btn.setOnClickListener {
        if (currentArabicFont != fontKey) {
          currentArabicFont = fontKey
          quranSettings.arabicFont = fontKey
          com.quran.labs.androidquran.ui.util.TypefaceManager.clearUthmaniTypefaceCache()
          btn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
          updateFontButtons(currentArabicFont, quranSettings.getReadingTheme())
          refreshQuranPages()
        }
      }
    }

    modeBothBtn.setOnClickListener {
      if (currentMode != QuranSettings.DISPLAY_MODE_BOTH) {
        currentMode = QuranSettings.DISPLAY_MODE_BOTH
        quranSettings.translationViewMode = currentMode
        modeBothBtn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        updateModeButtons(currentMode, quranSettings.getReadingTheme())
        updateModeVisibility(currentMode)
        refreshQuranPages()
      }
    }

    modeArabicBtn.setOnClickListener {
      if (currentMode != QuranSettings.DISPLAY_MODE_ARABIC_ONLY) {
        currentMode = QuranSettings.DISPLAY_MODE_ARABIC_ONLY
        quranSettings.translationViewMode = currentMode
        modeArabicBtn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        updateModeButtons(currentMode, quranSettings.getReadingTheme())
        updateModeVisibility(currentMode)
        refreshQuranPages()
      }
    }

    modeTranslationBtn.setOnClickListener {
      if (currentMode != QuranSettings.DISPLAY_MODE_TRANSLATION_ONLY) {
        currentMode = QuranSettings.DISPLAY_MODE_TRANSLATION_ONLY
        quranSettings.translationViewMode = currentMode
        modeTranslationBtn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        updateModeButtons(currentMode, quranSettings.getReadingTheme())
        updateModeVisibility(currentMode)
        refreshQuranPages()
      }
    }

    fun applyAppearanceAndTheme(nextAppTheme: String, explicitReadingTheme: Int?) {
      val wasDark = quranSettings.isAppInDarkMode
      currentAppTheme = nextAppTheme
      appearancePrefs.edit().putString(Constants.PREF_APP_THEME, nextAppTheme).apply()
      explicitReadingTheme?.let { quranSettings.setReadingTheme(it) }

      val sysDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
      val willBeDark = when (nextAppTheme) {
        "dark" -> true
        "light" -> false
        else -> sysDark
      }

      if (wasDark != willBeDark) {
        dialog.dismiss()
        QuranApplication.applyAppTheme(nextAppTheme)
      } else {
        QuranApplication.applyAppTheme(nextAppTheme)
        val activeTheme = quranSettings.getReadingTheme()
        updateThemeBorders(activeTheme)
        applyThemeToDialogViews(activeTheme)
        invalidateOptionsMenu()
        refreshQuranPages()
      }
    }

    appearanceAutoBtn.setOnClickListener {
      it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
      applyAppearanceAndTheme("auto", null)
    }
    appearanceLightBtn.setOnClickListener {
      it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
      applyAppearanceAndTheme("light", null)
    }
    appearanceDarkBtn.setOnClickListener {
      it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
      applyAppearanceAndTheme("dark", null)
    }

    lightBtn.setOnClickListener {
      lightBtn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
      applyAppearanceAndTheme("light", QuranSettings.THEME_LIGHT)
    }
    sepiaBtn.setOnClickListener {
      sepiaBtn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
      applyAppearanceAndTheme("light", QuranSettings.THEME_SEPIA)
    }
    sageBtn.setOnClickListener {
      sageBtn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
      applyAppearanceAndTheme("light", QuranSettings.THEME_SAGE)
    }
    slateBtn.setOnClickListener {
      slateBtn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
      applyAppearanceAndTheme("dark", QuranSettings.THEME_SLATE)
    }
    nightBtn.setOnClickListener {
      nightBtn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
      applyAppearanceAndTheme("dark", QuranSettings.THEME_NIGHT)
    }

    updateThemeBorders(quranSettings.getReadingTheme())
    updateModeVisibility(currentMode)

    // Reset button
    val resetButton = android.widget.Button(this).apply {
      text = getString(R.string.reset_to_default)
      textSize = 14f
      setBackgroundColor(android.graphics.Color.TRANSPARENT)
      setTextColor(androidx.core.content.ContextCompat.getColor(this@PagerActivity, R.color.accent_color))
      val layoutParams = android.widget.LinearLayout.LayoutParams(
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        gravity = android.view.Gravity.CENTER
        topMargin = (16 * density).toInt()
      }
      this.layoutParams = layoutParams

      setOnClickListener {
        performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        quranSettings.ayahTextSize = Constants.DEFAULT_TEXT_SIZE
        quranSettings.translationTextSize = Constants.DEFAULT_TRANSLATION_TEXT_SIZE
        currentMode = QuranSettings.DISPLAY_MODE_BOTH
        quranSettings.translationViewMode = currentMode
        currentArabicFont = QuranSettings.FONT_HAFS_BOLD
        quranSettings.arabicFont = currentArabicFont
        currentTranslationFont = QuranSettings.TRANSLATION_FONT_SYSTEM
        quranSettings.translationFont = currentTranslationFont
        com.quran.labs.androidquran.ui.util.TypefaceManager.clearUthmaniTypefaceCache()
        val resetTheme = if (quranSettings.isAppInDarkMode) QuranSettings.THEME_NIGHT else QuranSettings.THEME_LIGHT
        quranSettings.setReadingTheme(resetTheme)
        quranSettings.setTranslationLineSpacing(1.0f)

        // Reset dialog controls
        arabicSeekBar.progress = (Constants.DEFAULT_TEXT_SIZE - 16).coerceIn(0, 18)
        translationSeekBar.progress = (Constants.DEFAULT_TRANSLATION_TEXT_SIZE - 13).coerceIn(0, 15)
        arabicLabel.text = getString(R.string.arabic_font_size, Constants.DEFAULT_TEXT_SIZE)
        translationLabel.text = getString(R.string.translation_font_size, Constants.DEFAULT_TRANSLATION_TEXT_SIZE)
        spacingSeekBar.progress = 0
        spacingLabel.text = getString(R.string.line_spacing, "1.00x")

        updateModeVisibility(currentMode)
        updateThemeBorders(resetTheme)
        applyThemeToDialogViews(resetTheme)
        refreshQuranPages()
      }
    }

    // Assemble Container Views
    container.addView(sheetTitleLabel)
    container.addView(themeLabel)
    container.addView(appearanceLayout)
    container.addView(themeLayout)

    container.addView(displayModeLabel)
    container.addView(modeLayout)

    container.addView(arabicLabel)
    container.addView(arabicSeekBar)

    container.addView(fontLabel)
    container.addView(fontLayout)

    container.addView(translationLabel)
    container.addView(translationSeekBar)

    container.addView(translationFontLabel)
    container.addView(translationFontLayout)

    container.addView(spacingLabel)
    container.addView(spacingSeekBar)

    container.addView(resetButton)

    val scrollView = android.widget.ScrollView(this).apply {
      isFillViewport = true
      addView(container)
    }
    dialog.setContentView(scrollView)
    dialog.setOnShowListener {
      applyThemeToDialogViews(quranSettings.getReadingTheme())
    }
    dialog.show()
  }

  companion object {
    private const val AUDIO_DOWNLOAD_KEY = "AUDIO_DOWNLOAD_KEY"
    private const val LAST_READ_PAGE = "LAST_READ_PAGE"
    private const val LAST_READING_MODE_IS_TRANSLATION = "LAST_READING_MODE_IS_TRANSLATION"
    private const val LAST_ACTIONBAR_STATE = "LAST_ACTIONBAR_STATE"
    private const val LAST_FOLDING_STATE = "LAST_FOLDING_STATE"

    const val EXTRA_JUMP_TO_TRANSLATION: String = "jumpToTranslation"
    const val EXTRA_HIGHLIGHT_SURA: String = "highlightSura"
    const val EXTRA_HIGHLIGHT_AYAH: String = "highlightAyah"
    const val LAST_WAS_DUAL_PAGES: String = "wasDualPages"

    private const val DEFAULT_HIDE_AFTER_TIME: Long = 2000

    const val MSG_HIDE_ACTIONBAR: Int = 1

    // AYAH ACTION PANEL STUFF
    // Max height of sliding panel (% of screen)
    private const val PANEL_MAX_HEIGHT = 0.6f
  }
}
