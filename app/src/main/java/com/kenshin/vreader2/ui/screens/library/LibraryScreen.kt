package com.kenshin.vreader2.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kenshin.vreader2.data.repository.MangaRepository
import com.kenshin.vreader2.domain.model.Manga
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class LibraryState(
    val mangas: List<Manga> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MangaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLibrary()
                .collect { mangas ->
                    _state.update { it.copy(mangas = mangas, isLoading = false) }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun filteredMangas(): List<Manga> {
        val s = _state.value
        return if (s.searchQuery.isBlank()) s.mangas
        else s.mangas.filter {
            it.title.contains(s.searchQuery, ignoreCase = true)
        }
    }
}

// ── UI ────────────────────────────────────────────────────────────────────────

@Composable
fun LibraryScreen(
    onMangaClick: (Manga) -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value         = state.searchQuery,
            onValueChange = vm::onSearchQueryChange,
            placeholder   = { Text("Tìm trong thư viện...") },
            leadingIcon   = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine    = true,
            shape         = MaterialTheme.shapes.large,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            vm.filteredMangas().isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Thư viện trống",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Vào Khám phá để thêm truyện",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> LazyVerticalGrid(
                columns             = GridCells.Adaptive(minSize = 110.dp),
                contentPadding      = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.filteredMangas(), key = { it.id }) { manga ->
                    MangaCard(manga = manga, onClick = { onMangaClick(manga) })
                }
            }
        }
    }
}

@Composable
fun MangaCard(manga: Manga, onClick: () -> Unit) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape     = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            AsyncImage(
                model              = manga.thumbnailUrl,
                contentDescription = manga.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(MaterialTheme.shapes.medium),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
            ) {
                Text(
                    text     = manga.title,
                    style    = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (manga.unreadCount > 0) {
                    Badge(modifier = Modifier.align(Alignment.TopEnd)) {
                        Text(manga.unreadCount.toString())
                    }
                }
            }
        }
    }
}

