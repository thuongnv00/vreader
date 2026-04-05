package com.kenshin.vreader2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kenshin.vreader2.ui.screens.browse.BrowseScreen
import com.kenshin.vreader2.ui.screens.library.LibraryScreen
import com.kenshin.vreader2.ui.screens.reader.ReaderScreen
import com.kenshin.vreader2.ui.theme.VReader2Theme
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Browse  : Screen("browse")
    object History : Screen("history")
    object Reader  : Screen("reader/{chapterId}") {
        fun createRoute(chapterId: String) = "reader/$chapterId"
    }
    object MangaDetail : Screen("manga/{mangaId}") {
        fun createRoute(mangaId: String) = "manga/$mangaId"
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VReader2Theme {
                VReaderAppContent()
            }
        }
    }
}

@Composable
fun VReaderAppContent() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        Triple(Screen.Library, "Thư viện",  Icons.Filled.CollectionsBookmark),
        Triple(Screen.Browse,  "Khám phá",  Icons.Filled.Explore),
        Triple(Screen.History, "Lịch sử",   Icons.Filled.History),
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val showBottomBar = currentRoute?.startsWith("reader/") != true
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { (screen, label, icon) ->
                        NavigationBarItem(
                            icon     = { Icon(icon, contentDescription = label) },
                            label    = { Text(label) },
                            selected = currentRoute == screen.route,
                            onClick  = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Library.route,
            modifier         = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    onMangaClick = { manga ->
                        navController.navigate(Screen.MangaDetail.createRoute(manga.id))
                    }
                )
            }
            composable(Screen.Browse.route) {
                BrowseScreen(
                    onMangaClick = { manga ->
                        val encodedId = java.net.URLEncoder.encode(manga.id, "UTF-8")
                        navController.navigate(Screen.MangaDetail.createRoute(encodedId))
                    }
                )
            }
            composable(Screen.History.route) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text("Lịch sử — coming soon")
                }
            }
            composable(Screen.MangaDetail.route) { backStackEntry ->
                val mangaId = backStackEntry.arguments?.getString("mangaId") ?: return@composable
                com.kenshin.vreader2.ui.screens.detail.MangaDetailScreen(
                    mangaId      = mangaId,
                    onChapterClick = { chapter ->
                        val encodedId = java.net.URLEncoder.encode(chapter.id, "UTF-8")
                        navController.navigate(Screen.Reader.createRoute(encodedId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Reader.route) { backStackEntry ->
                val chapterId = backStackEntry.arguments?.getString("chapterId") ?: return@composable
                ReaderScreen(
                    chapterId = chapterId,
                    onBack    = { navController.popBackStack() }
                )
            }
        }
    }
}