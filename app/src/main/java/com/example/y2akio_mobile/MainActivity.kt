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
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            // One UI bottom tabs: flat bar on the canvas, soft pill behind the active tab.
                            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                                val colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                NavigationBarItem(
                                    selected = tab == 0, onClick = { tab = 0 }, colors = colors,
                                    icon = { Icon(if (tab == 0) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = null) },
                                    label = { Text(tr("Nav.Home"), fontWeight = if (tab == 0) FontWeight.SemiBold else FontWeight.Normal) },
                                )
                                NavigationBarItem(
                                    selected = tab == 1, onClick = { tab = 1 }, colors = colors,
                                    icon = { Icon(if (tab == 1) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = null) },
                                    label = { Text(tr("Nav.Settings"), fontWeight = if (tab == 1) FontWeight.SemiBold else FontWeight.Normal) },
                                )
                            }
                        },
                    ) { padding ->
                        val bottom = padding.calculateBottomPadding()
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
