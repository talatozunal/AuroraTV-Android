package com.aurora.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aurora.iptv.AppContainer
import com.aurora.iptv.data.db.StreamEntity
import com.aurora.iptv.data.db.StreamKind
import com.aurora.iptv.playback.PlaybackConnection
import com.aurora.iptv.playback.PlaybackRequest
import com.aurora.iptv.ui.components.TvCard
import com.aurora.iptv.ui.screens.GuideScreen
import com.aurora.iptv.ui.screens.LiveScreen
import com.aurora.iptv.ui.screens.MultiScreen
import com.aurora.iptv.ui.screens.PlayerScreen
import com.aurora.iptv.ui.screens.PlaylistsScreen
import com.aurora.iptv.ui.screens.SearchScreen
import com.aurora.iptv.ui.screens.SettingsScreen
import com.aurora.iptv.ui.screens.VodScreen
import com.aurora.iptv.ui.theme.AuroraColors

object Routes {
    const val SETUP = "setup"
    const val LIVE = "live"
    const val GUIDE = "guide"
    const val VOD = "vod"
    const val MULTI = "multi"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val PLAYLISTS = "playlists"
    const val PLAYER = "player"
}

private data class RailEntry(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val railEntries = listOf(
    RailEntry(Routes.LIVE, "nav_live", Icons.Filled.LiveTv),
    RailEntry(Routes.GUIDE, "nav_guide", Icons.Filled.ViewWeek),
    RailEntry(Routes.VOD, "nav_vod", Icons.Filled.Movie),
    RailEntry(Routes.MULTI, "nav_multi", Icons.Filled.ViewQuilt),
    RailEntry(Routes.SEARCH, "nav_search", Icons.Filled.Search),
    RailEntry(Routes.SETTINGS, "nav_settings", Icons.Filled.Settings)
)

@Composable
fun AuroraRoot(
    container: AppContainer,
    appViewModel: AppViewModel,
    connection: PlaybackConnection,
    pendingRoute: String?,
    pendingSearch: String?,
    inPip: Boolean,
    onIntentConsumed: () -> Unit,
    onEnterPip: () -> Unit
) {
    val navController = rememberNavController()
    val ready by appViewModel.ready.collectAsStateWithLifecycle()
    val playlists by appViewModel.playlists.collectAsStateWithLifecycle()
    val controller by connection.controller.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    LaunchedEffect(ready, playlists.size, route) {
        if (!ready) return@LaunchedEffect
        if (playlists.isEmpty() && route != Routes.SETUP) {
            navController.navigate(Routes.SETUP) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        } else if (playlists.isNotEmpty() && route == Routes.SETUP) {
            navController.navigate(Routes.LIVE) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(pendingRoute, pendingSearch, ready, playlists.size) {
        if (!ready) return@LaunchedEffect
        val destination = pendingRoute ?: pendingSearch?.let { Routes.SEARCH } ?: return@LaunchedEffect
        if (playlists.isEmpty()) {
            navController.navigate(Routes.SETUP) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            onIntentConsumed()
            return@LaunchedEffect
        }
        if (!pendingSearch.isNullOrBlank()) {
            appViewModel.setSearchQueryImmediate(pendingSearch)
        }
        val target = when (destination) {
            "home", "live", Routes.LIVE -> Routes.LIVE
            "guide", Routes.GUIDE -> Routes.GUIDE
            "vod", Routes.VOD -> Routes.VOD
            "multi", Routes.MULTI -> Routes.MULTI
            "search", Routes.SEARCH -> Routes.SEARCH
            "settings", Routes.SETTINGS -> Routes.SETTINGS
            Routes.PLAYLISTS -> Routes.PLAYLISTS
            else -> Routes.LIVE
        }
        navController.navigate(target) { launchSingleTop = true }
        onIntentConsumed()
    }

    if (!ready) {
        SplashScreen()
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        AuroraColors.Primary.copy(alpha = 0.07f),
                        AuroraColors.Background,
                        Color(0xFF03060B)
                    ),
                    radius = 1600f
                )
            )
    ) {
        Row(Modifier.fillMaxSize()) {
            if (!inPip && route != Routes.PLAYER && route != Routes.SETUP) {
                NavRail(
                    currentRoute = route,
                    onNavigate = { target ->
                        navController.navigate(target) {
                            popUpTo(Routes.LIVE) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            NavHost(
                navController = navController,
                startDestination = Routes.LIVE,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                composable(Routes.SETUP) {
                    PlaylistsScreen(
                        appViewModel = appViewModel,
                        setup = true,
                        onOpenPlayer = { },
                        onManageFinished = {}
                    )
                }
                composable(Routes.LIVE) {
                    LiveScreen(
                        appViewModel = appViewModel,
                        onPlayStream = { openLive(navController, it) }
                    )
                }
                composable(Routes.GUIDE) {
                    GuideScreen(
                        appViewModel = appViewModel,
                        onPlayLive = { openLive(navController, it) },
                        onPlayCatchup = { stream, start, end, title ->
                            openCatchup(navController, stream, start, end, title)
                        }
                    )
                }
                composable(Routes.VOD) {
                    VodScreen(
                        appViewModel = appViewModel,
                        onPlay = { stream, position -> openVod(navController, stream, position) }
                    )
                }
                composable(Routes.MULTI) {
                    MultiScreen(
                        appViewModel = appViewModel,
                        container = container
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        appViewModel = appViewModel,
                        onPlayLive = { openLive(navController, it) },
                        onPlayVod = { stream, position -> openVod(navController, stream, position) }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        appViewModel = appViewModel,
                        onManagePlaylists = {
                            navController.navigate(Routes.PLAYLISTS) { launchSingleTop = true }
                        }
                    )
                }
                composable(Routes.PLAYLISTS) {
                    PlaylistsScreen(
                        appViewModel = appViewModel,
                        setup = false,
                        onOpenPlayer = { },
                        onManageFinished = { navController.popBackStack() }
                    )
                }
                composable(Routes.PLAYER) {
                    PlayerScreen(
                        container = container,
                        controller = controller,
                        inPip = inPip,
                        onBack = { navController.popBackStack() },
                        onEnterPip = onEnterPip
                    )
                }
            }
        }
    }
}

private fun openLive(navController: NavHostController, stream: StreamEntity) {
    PlaybackRequest.live(stream.uid)
    navController.navigate(Routes.PLAYER) { launchSingleTop = true }
}

private fun openVod(navController: NavHostController, stream: StreamEntity, position: Long) {
    PlaybackRequest.vod(stream.uid, position)
    navController.navigate(Routes.PLAYER) { launchSingleTop = true }
}

private fun openCatchup(
    navController: NavHostController,
    stream: StreamEntity,
    start: Long,
    end: Long,
    title: String
) {
    PlaybackRequest.catchup(stream.uid, start, end, title)
    navController.navigate(Routes.PLAYER) { launchSingleTop = true }
}

@Composable
private fun NavRail(currentRoute: String?, onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .fillMaxHeight()
            .background(Color(0xFF05080F).copy(alpha = 0.92f))
            .padding(horizontal = 8.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "A",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        railEntries.forEach { entry ->
            val selected = currentRoute == entry.route
            val label = androidx.compose.ui.res.stringResource(
                when (entry.route) {
                    Routes.LIVE -> com.aurora.iptv.R.string.nav_live
                    Routes.GUIDE -> com.aurora.iptv.R.string.nav_guide
                    Routes.VOD -> com.aurora.iptv.R.string.nav_vod
                    Routes.MULTI -> com.aurora.iptv.R.string.nav_multi
                    Routes.SEARCH -> com.aurora.iptv.R.string.nav_search
                    else -> com.aurora.iptv.R.string.nav_settings
                }
            )
            TvCard(
                onClick = { onNavigate(entry.route) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = label,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(AuroraColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "AURORA",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "TV",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
