package com.example.y2akio_mobile

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.y2akio_mobile.ui.GlassTab
import com.example.y2akio_mobile.ui.GlassTabBar
import com.example.y2akio_mobile.ui.GlassTabBarBottomGap
import com.example.y2akio_mobile.ui.GlassTabBarHeight
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.y2akio_mobile.download.Notifications
import com.example.y2akio_mobile.ui.HomeScreen
import com.example.y2akio_mobile.ui.HomeViewModel
import com.example.y2akio_mobile.ui.SettingsScreen
import com.example.y2akio_mobile.ui.theme.LocalLanguage
import com.example.y2akio_mobile.ui.theme.Y2AkioTheme
import com.example.y2akio_mobile.ui.theme.tr

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private var selectTab: ((Int) -> Unit)? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as Y2AkioApp

        // Background progress lives in notifications — ask once on first launch.
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (!Notifications.canPost(this) && !prefs.getBoolean("askedNotifications", false)) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (savedInstanceState == null) handleShareIntent(intent)

        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            val jobs by app.downloads.jobs.collectAsStateWithLifecycle()
            val banner by app.downloads.banner.collectAsStateWithLifecycle()
            val log by app.downloads.log.collectAsStateWithLifecycle()
            val history by app.history.items.collectAsStateWithLifecycle()
            val runtimeState by app.runtime.state.collectAsStateWithLifecycle()
            var tab by rememberSaveable { mutableIntStateOf(0) }
            selectTab = { tab = it }

            CompositionLocalProvider(LocalLanguage provides settings.language) {
                Y2AkioTheme(settings.theme) {
                    // Screens are recorded into [backdrop]; the floating glass tab bar refracts them.
                    val backdrop = rememberLayerBackdrop()
                    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val bottom = navInset + GlassTabBarHeight + GlassTabBarBottomGap
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                      Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                        if (tab == 0) {
                            HomeScreen(
                                vm = homeViewModel,
                                jobs = jobs,
                                banner = banner,
                                log = log,
                                history = history,
                                runtimeState = runtimeState,
                                onDismissBanner = { app.downloads.showBanner(null) },
                                onCancelJob = app.downloads::cancel,
                                onDismissJob = app.downloads::dismiss,
                                onClearFinishedJobs = app.downloads::clearFinished,
                                onClearLog = app.downloads::clearLog,
                                onDeleteHistory = app.history::remove,
                                onClearHistory = app.history::clear,
                                bottomPadding = bottom,
                            )
                        } else {
                            SettingsScreen(app, bottom)
                        }
                      }
                      GlassTabBar(
                          backdrop = backdrop,
                          tabs = listOf(
                              GlassTab(tr("Nav.Home"), Icons.Outlined.Home, Icons.Filled.Home),
                              GlassTab(tr("Nav.Settings"), Icons.Outlined.Settings, Icons.Filled.Settings),
                          ),
                          selectedIndex = tab,
                          onSelect = { tab = it },
                          modifier = Modifier
                              .align(Alignment.BottomCenter)
                              .navigationBarsPadding()
                              .padding(bottom = GlassTabBarBottomGap),
                      )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** "Y2Akio · Mở app" share target: prefill the link and analyze it right away. */
    private fun handleShareIntent(intent: Intent?) {
        val url = ShareReceiverActivity.extractUrl(intent) ?: return
        selectTab?.invoke(0)
        homeViewModel.setUrl(url)
        homeViewModel.analyze(url)
    }
}
