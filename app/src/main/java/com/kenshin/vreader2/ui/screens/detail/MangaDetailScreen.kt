package com.kenshin.vreader2.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kenshin.vreader2.data.repository.MangaRepository
import com.kenshin.vreader2.domain.model.Chapter
import com.kenshin.vreader2.domain.model.Manga
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class DetailState(
    val manga: Manga? = null,
    val chapters: List<Chapter> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class MangaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MangaRepository,
) : ViewModel() {

    private val mangaId = checkNotNull(savedStateHandle.get<String>("mangaId"))

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    init {
        loadDetail()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                // Lấy source từ mangaId (format: "sourceId:url")
                val parts    = mangaId.split(":", limit = 2)
                val sourceId = parts[0].toLong()
                val url      = parts[1]

                // Tạo manga tạm để fetch detail
                val tempManga = com.kenshin.vreader2.domain.model.Manga(
                    id       = mangaId,
                    sourceId = sourceId.toString(),
                    url      = url,
                    title    = "",
                    thumbnailUrl = null
                )

                val detailed = repository.getMangaDetails(tempManga)
                val chapters = repository.fetchAndSaveChapters(detailed)

                _state.update {
                    it.copy(
                        manga     = detailed,
                        chapters  = chapters,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun toggleLibrary() {
        val manga = _state.value.manga ?: return
        viewModelScope.launch {
            repository.toggleLibrary(manga.id, !manga.inLibrary)
            _state.update { it.copy(manga = manga.copy(inLibrary = !manga.inLibrary)) }
        }
    }
}

// ── UI ────────────────────────────────────────────────────────────────────────

@Composable
fun MangaDetailScreen(
    mangaId: String,
    onChapterClick: (Chapter) -> Unit,
    onBack: () -> Unit,
    vm: MangaDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    when {
        state.isLoading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        state.error != null -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Lỗi: ${state.error}", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onBack) { Text("Quay lại") }
            }
        }

        else -> {
            val manga = state.manga ?: return
            LazyColumn(Modifier.fillMaxSize()) {

                // Cover + info
                item {
                    Box {
                        AsyncImage(
                            model              = manga.thumbnailUrl,
                            contentDescription = manga.title,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                        )
                        IconButton(
                            onClick  = onBack,
                            modifier = Modifier
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Quay lại",
                                tint = Color.Cyan,
                            )
                        }
                    }
                }

                // Title + author
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text  = manga.title,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (manga.author != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = manga.author,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        // Add to library button
                        Button(
                            onClick  = vm::toggleLibrary,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                if (manga.inLibrary) Icons.Filled.Favorite
                                else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (manga.inLibrary) "Đã thêm vào thư viện"
                                else "Thêm vào thư viện"
                            )
                        }
                    }
                }

                // Description
                if (manga.description != null) {
                    item {
                        Text(
                            text     = manga.description,
                            style    = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Chapters header
                item {
                    Text(
                        text     = "${state.chapters.size} chương",
                        style    = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    HorizontalDivider()
                }

                // Chapter list
                items(state.chapters, key = { it.id }) { chapter ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text  = chapter.name,
                                color = if (chapter.isRead)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        modifier = Modifier.clickable { onChapterClick(chapter) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

