package studio.rocknite.blog

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import studio.rocknite.blog.ui.analytics.AnalyticsScreen
import studio.rocknite.blog.ui.post.PostScreen
import studio.rocknite.blog.ui.settings.SettingsScreen
import studio.rocknite.blog.ui.theme.RockniteBlogTheme
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RockniteBlogTheme {
                RootScaffold()
            }
        }
    }
}

private sealed class Dest(val route: String, val label: String) {
    data object Post : Dest("post", "Poster")
    data object Analytics : Dest("analytics", "Analytique")
    data object Settings : Dest("settings", "Config")
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RootScaffold(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val items = listOf(Dest.Post, Dest.Analytics, Dest.Settings)
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                items.forEach { dest ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val icon = when (dest) {
                                Dest.Post -> Icons.Filled.Edit
                                Dest.Analytics -> Icons.Filled.QueryStats
                                Dest.Settings -> Icons.Filled.Settings
                            }
                            Icon(icon, contentDescription = dest.label)
                        },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Post.route,
            modifier = androidx.compose.ui.Modifier.padding(padding),
        ) {
            composable(Dest.Post.route) {
                PostScreen(
                    isPublishing = uiState.isPublishing,
                    lastError = uiState.lastError,
                    quickStatusLabels = uiState.quickStatusLabels,
                    onPublish = { content: String, images: List<Uri>, publishedAt: Date ->
                        viewModel.publishPost(content, images, publishedAt)
                    },
                    onPickStatus = { label -> viewModel.pickQuickStatus(label) },
                    onAddStatus = { label -> viewModel.addQuickStatus(label) },
                    onRemoveStatus = { label -> viewModel.removeQuickStatus(label) },
                )
            }
            composable(Dest.Analytics.route) {
                LaunchedEffect(Unit) { viewModel.loadAnalytics() }
                AnalyticsScreen(summary = uiState.analytics)
            }
            composable(Dest.Settings.route) {
                SettingsScreen(
                    currentServerUrl = uiState.serverUrl,
                    currentToken = uiState.token,
                    onSave = { url, token -> viewModel.saveSettings(url, token) },
                )
            }
        }
    }
}
