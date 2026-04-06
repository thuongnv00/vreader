package com.kenshin.vreader2.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.kenshin.vreader2.data.download.DownloadManager
import com.kenshin.vreader2.data.repository.MangaRepository
import com.kenshin.vreader2.domain.model.Chapter
import com.kenshin.vreader2.domain.model.Manga
import androidx.work.WorkInfo
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
    val downloadProgress: Float? = null,
    val downloadLabel: String? = null,
)

@HiltViewModel
class MangaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MangaRepository,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    private val mangaId = checkNotNull(savedStateHandle.get<String>("mangaId"))

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    init {
        loadDetail()
        observeChapters()
        observeDownloadProgress()
    }

    private fun observeDownloadProgress() {
        downloadManager.getWorkInfosFlow(mangaId).onEach { workInfos ->
            if (workInfos.isEmpty()) {
                _state.update { state -> state.copy(downloadProgress = null, downloadLabel = null) }
                return@onEach
            }

            val total = workInfos.size
            val succeeded = workInfos.count { info -> info.state == WorkInfo.State.SUCCEEDED }
            val failed = workInfos.count { info -> info.state == WorkInfo.State.FAILED }
            val finished = succeeded + failed
            val isActive = workInfos.any { info -> !info.state.isFinished }

            if (isActive) {
                _state.update { state ->
                    state.copy(
                        downloadProgress = finished.toFloat() / total,
                        downloadLabel = "Đang tải: $finished/$total chương"
                    )
                }
            } else {
                // Tải xong hoặc không còn việc nào đang chạy
                _state.update { state -> state.copy(downloadProgress = null, downloadLabel = null) }
            }
        }.launchIn(viewModelScope)
    }

    private fun observeChapters() {
        viewModelScope.launch {
            repository.observeChapters(mangaId).collect { chapters ->
                _state.update { it.copy(chapters = chapters) }
            }
        }
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            // Bước 1: Thử lấy dữ liệu từ nội bộ (Database) trước để hiển thị ngay lập tức
            val localManga = repository.getMangaById(mangaId)
            if (localManga != null) {
                _state.update { it.copy(manga = localManga) }
            }

            try {
                // Lấy source từ mangaId (format: "sourceId:url")
                val parts    = mangaId.split(":", limit = 2)
                val sourceId = parts[0].toLong()
                val url      = parts[1]

                // Tạo manga tạm để fetch detail
                val tempManga = localManga ?: com.kenshin.vreader2.domain.model.Manga(
                    id       = mangaId,
                    sourceId = sourceId.toString(),
                    url      = url,
                    title    = "",
                    thumbnailUrl = null
                )

                val detailed = repository.getMangaDetails(tempManga)
                repository.fetchAndSaveChapters(detailed)

                _state.update {
                    it.copy(
                        manga     = detailed,
                        isLoading = false,
                        error     = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
                // Nếu đã có dữ liệu local thì không hiện lỗi (vẫn cho xem offline)
                if (localManga == null) {
                    _state.update { it.copy(error = e.message) }
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

    fun downloadAll() {
        val manga = _state.value.manga ?: return
        val chapters = _state.value.chapters
        if (chapters.isNotEmpty()) {
            viewModelScope.launch {
                // Tự động thêm vào thư viện nếu chưa có
                if (!manga.inLibrary) {
                    repository.toggleLibrary(manga.id, true)
                    _state.update { it.copy(manga = manga.copy(inLibrary = true)) }
                }
                downloadManager.enqueueDownload(manga.title, chapters)
            }
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
            val manga = state.manga
            if (manga == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Không tìm thấy dữ liệu truyện", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Scaffold(
                    bottomBar = {
                        BottomButtonsSection(
                            onDownloadClick = vm::downloadAll,
                            onChaptersClick = { /* TODO */ },
                            onAddToLibraryClick = vm::toggleLibrary
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
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
                                    Icons.AutoMirrored.Filled.ArrowBack,
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

                            // Nút trạng thái (đã thêm hay chưa)
                            if (manga.inLibrary) {
                                AssistChip(
                                    onClick = { },
                                    label = { Text("Đã thêm vào thư viện") },
                                    leadingIcon = { Icon(Icons.Filled.Favorite, null, tint = Color.Red) }
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
                            trailingContent = {
                                if (chapter.isDownloaded) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Đã tải",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
}

@Composable
fun BottomButtonsSection(
    onDownloadClick: () -> Unit,
    onChaptersClick: () -> Unit,
    onAddToLibraryClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nút Tải xuống
            BottomNavItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.FileDownload,
                label = "Tải xuống",
                onClick = onDownloadClick
            )

            // Nút Mục lục (Nổi bật với màu xanh)
            BottomNavItem(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF4FC3F7)), // Light Blue 300
                icon = Icons.AutoMirrored.Filled.MenuBook,
                label = "Mục lục",
                iconColor = Color.White,
                textColor = Color.White,
                onClick = onChaptersClick
            )

            // Nút Thêm vào kệ
            BottomNavItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LibraryAdd,
                label = "Thêm vào kệ",
                onClick = onAddToLibraryClick
            )
        }
    }
}

@Composable
fun BottomNavItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    iconColor: Color = MaterialTheme.colorScheme.onSurface,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}


