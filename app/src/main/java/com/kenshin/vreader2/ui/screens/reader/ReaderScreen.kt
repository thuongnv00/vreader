package com.kenshin.vreader2.ui.screens.reader

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kenshin.vreader2.data.repository.MangaRepository
import com.kenshin.vreader2.domain.model.Chapter
import com.kenshin.vreader2.domain.model.Manga
import com.kenshin.vreader2.domain.model.NovelContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── State ─────────────────────────────────────────────────────────────────────

sealed class ReaderState {
    object Loading : ReaderState()
    data class Error(val message: String) : ReaderState()
    data class Ready(
        val manga: Manga,
        val chapter: Chapter,
        val content: NovelContent,
        val chapters: List<Chapter>,
        val showUi: Boolean = true,
        val fontSize: Int = 18,
    ) : ReaderState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MangaRepository,
) : ViewModel() {

    private val chapterId = java.net.URLDecoder.decode(
        checkNotNull(savedStateHandle.get<String>("chapterId")), "UTF-8"
    )

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    init {
        loadChapter(chapterId)
    }

    private fun loadChapter(id: String) {
        viewModelScope.launch {
            _state.value = ReaderState.Loading
            try {
                val chapter  = repository.getChapterById(id)
                    ?: error("Không tìm thấy chương")
                val manga    = repository.getMangaById(chapter.mangaId)
                    ?: error("Không tìm thấy truyện")
                val chapters = repository.getChaptersByManga(chapter.mangaId)
                val content  = repository.getNovelContent(chapter)

                repository.markChapterRead(id, 0)

                _state.value = ReaderState.Ready(
                    manga    = manga,
                    chapter  = chapter,
                    content  = content,
                    chapters = chapters,
                )
            } catch (e: Exception) {
                _state.value = ReaderState.Error(e.message ?: "Lỗi không xác định")
            }
        }
    }

    fun toggleUi() {
        val current = _state.value as? ReaderState.Ready ?: return
        _state.value = current.copy(showUi = !current.showUi)
    }

    fun updateFontSize(size: Int) {
        val current = _state.value as? ReaderState.Ready ?: return
        _state.value = current.copy(fontSize = size)
    }

    fun navigateToChapter(chapter: Chapter) = loadChapter(chapter.id)

    fun getPrevChapter(): Chapter? {
        val s = _state.value as? ReaderState.Ready ?: return null
        val idx = s.chapters.indexOfFirst { it.id == s.chapter.id }
        return if (idx < s.chapters.size - 1) s.chapters[idx + 1] else null
    }

    fun getNextChapter(): Chapter? {
        val s = _state.value as? ReaderState.Ready ?: return null
        val idx = s.chapters.indexOfFirst { it.id == s.chapter.id }
        return if (idx > 0) s.chapters[idx - 1] else null
    }
}

// ── UI ────────────────────────────────────────────────────────────────────────

@Composable
fun ReaderScreen(
    chapterId: String,
    onBack: () -> Unit,
    vm: ReaderViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    when (val s = state) {
        is ReaderState.Loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        is ReaderState.Error -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Quay lại") }
            }
        }

        is ReaderState.Ready -> NovelReader(
            state         = s,
            onToggleUi    = vm::toggleUi,
            onBack        = onBack,
            onPrevChapter = { vm.getPrevChapter()?.let(vm::navigateToChapter) },
            onNextChapter = { vm.getNextChapter()?.let(vm::navigateToChapter) },
            onFontSize    = vm::updateFontSize,
        )
    }
}

@Composable
private fun NovelReader(
    state: ReaderState.Ready,
    onToggleUi: () -> Unit,
    onBack: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onFontSize: (Int) -> Unit,
) {
    var showFontPanel by remember { mutableStateOf(false) }
    val scrollState   = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { onToggleUi() } }
    ) {
        // Nội dung
        LazyColumn(
            state           = scrollState,
            contentPadding  = PaddingValues(
                horizontal = 16.dp,
                vertical   = 72.dp,
            ),
        ) {
            item {
                Text(
                    text     = state.chapter.name,
                    style    = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
            item {
                Text(
                    text  = state.content.textContent,
                    style = TextStyle(
                        fontSize   = state.fontSize.sp,
                        lineHeight = (state.fontSize * 1.7f).sp,
                        color      = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
            item {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    OutlinedButton(onClick = onPrevChapter) {
                        Icon(Icons.Filled.ChevronLeft, null)
                        Text("Chương trước")
                    }
                    Button(onClick = onNextChapter) {
                        Text("Chương sau")
                        Icon(Icons.Filled.ChevronRight, null)
                    }
                }
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = state.showUi,
            enter   = slideInVertically { -it },
            exit    = slideOutVertically { -it },
        ) {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Quay lại")
                    }
                },
                title = {
                    Column {
                        Text(
                            state.manga.title,
                            style    = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text(
                            state.chapter.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showFontPanel = !showFontPanel }) {
                        Icon(Icons.Filled.TextFormat, "Cỡ chữ")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
            )
        }

        // Bottom bar
        AnimatedVisibility(
            visible  = state.showUi,
            enter    = slideInVertically { it },
            exit     = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ) {
                IconButton(onClick = onPrevChapter) {
                    Icon(Icons.Filled.SkipPrevious, "Chương trước")
                }
                Spacer(Modifier.weight(1f))
                Text(state.chapter.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onNextChapter) {
                    Icon(Icons.Filled.SkipNext, "Chương sau")
                }
            }
        }

        // Font size panel
        if (showFontPanel) {
            Card(
                modifier  = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(8.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Cỡ chữ", style = MaterialTheme.typography.titleSmall)
                        Text("${state.fontSize}sp")
                    }
                    Slider(
                        value         = state.fontSize.toFloat(),
                        onValueChange = { onFontSize(it.toInt()) },
                        valueRange    = 12f..28f,
                        steps         = 15,
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier              = Modifier.fillMaxWidth(),
                    ) {
                        listOf(14, 16, 18, 20, 22).forEach { size ->
                            FilterChip(
                                selected = state.fontSize == size,
                                onClick  = { onFontSize(size) },
                                label    = { Text("${size}sp") },
                            )
                        }
                    }
                }
            }
        }
    }
}