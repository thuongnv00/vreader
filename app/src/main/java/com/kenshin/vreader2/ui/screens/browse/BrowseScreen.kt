package com.kenshin.vreader2.ui.screens.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import androidx.paging.compose.collectAsLazyPagingItems
import com.kenshin.vreader2.data.repository.MangaRepository
import com.kenshin.vreader2.data.source.SourceManager
import com.kenshin.vreader2.domain.model.Manga
import com.kenshin.vreader2.ui.screens.library.MangaCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Paging Source ─────────────────────────────────────────────────────────────

enum class BrowseMode { POPULAR, LATEST, SEARCH }

class MangaPagingSource(
    private val repository: MangaRepository,
    private val sourceId: Long,
    private val mode: BrowseMode,
    private val query: String,
) : PagingSource<Int, Manga>() {

    override fun getRefreshKey(state: PagingState<Int, Manga>): Int? =
        state.anchorPosition?.let {
            state.closestPageToPosition(it)?.prevKey?.plus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Manga> {
        val page = params.key ?: 1
        return try {
            val result = when (mode) {
                BrowseMode.POPULAR -> repository.getPopular(sourceId, page)
                BrowseMode.LATEST  -> repository.getLatest(sourceId, page)
                BrowseMode.SEARCH  -> repository.search(sourceId, query, page)
            }
            LoadResult.Page(
                data    = result.mangas,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (result.hasNextPage) page + 1 else null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class BrowseState(
    val selectedSourceId: Long = 1L,
    val mode: BrowseMode = BrowseMode.POPULAR,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: MangaRepository,
    private val sourceManager: SourceManager,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    val sources get() = sourceManager.getAll()

    val pagingData: Flow<PagingData<Manga>> = _state
        .debounce(300)
        .flatMapLatest { s ->
            Pager(
                config = PagingConfig(pageSize = 20, prefetchDistance = 5),
                pagingSourceFactory = {
                    MangaPagingSource(
                        repository = repository,
                        sourceId   = s.selectedSourceId,
                        mode       = s.mode,
                        query      = s.searchQuery,
                    )
                }
            ).flow
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            val allSources = sourceManager.getAll()
            if (allSources.isNotEmpty()) {
                _state.update { it.copy(selectedSourceId = allSources.first().id) }
            }
        }
    }

    fun selectSource(sourceId: Long) = _state.update { it.copy(selectedSourceId = sourceId) }
    fun selectMode(mode: BrowseMode)  = _state.update { it.copy(mode = mode) }
    fun toggleSearch(on: Boolean)     = _state.update { it.copy(isSearching = on) }
    fun onSearchQuery(q: String)      = _state.update {
        it.copy(
            searchQuery = q,
            mode = if (q.isBlank()) BrowseMode.POPULAR else BrowseMode.SEARCH,
        )
    }
}

// ── UI ────────────────────────────────────────────────────────────────────────

@Composable
fun BrowseScreen(
    onMangaClick: (Manga) -> Unit,
    vm: BrowseViewModel = hiltViewModel(),
) {
    val state  by vm.state.collectAsState()
    val mangas = vm.pagingData.collectAsLazyPagingItems()

    Column(Modifier.fillMaxSize()) {

        // Top bar
        if (state.isSearching) {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        vm.toggleSearch(false)
                        vm.onSearchQuery("")
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng")
                    }
                },
                title = {
                    OutlinedTextField(
                        value         = state.searchQuery,
                        onValueChange = vm::onSearchQuery,
                        placeholder   = { Text("Tìm truyện...") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }
            )
        } else {
            var showDropdown by remember { mutableStateOf(false) }
            val selectedSource = vm.sources.find { it.id == state.selectedSourceId }
            TopAppBar(
                title = {
                    Box {
                        TextButton(onClick = { showDropdown = true }) {
                            Text(selectedSource?.name ?: "Chọn nguồn")
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded        = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            vm.sources.forEach { source ->
                                DropdownMenuItem(
                                    text    = { Text("${source.name} (${source.lang})") },
                                    onClick = {
                                        vm.selectSource(source.id)
                                        showDropdown = false
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.toggleSearch(true) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Tìm kiếm")
                    }
                }
            )
        }

        // Mode tabs
        if (!state.isSearching) {
            TabRow(selectedTabIndex = if (state.mode == BrowseMode.POPULAR) 0 else 1) {
                Tab(
                    selected = state.mode == BrowseMode.POPULAR,
                    onClick  = { vm.selectMode(BrowseMode.POPULAR) },
                    text     = { Text("Phổ biến") }
                )
                Tab(
                    selected = state.mode == BrowseMode.LATEST,
                    onClick  = { vm.selectMode(BrowseMode.LATEST) },
                    text     = { Text("Mới cập nhật") }
                )
            }
        }

        // Grid
        when (mangas.loadState.refresh) {
            is LoadState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            is LoadState.Error -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Lỗi tải dữ liệu",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { mangas.retry() }) {
                        Text("Thử lại")
                    }
                }
            }
            else -> LazyVerticalGrid(
                columns               = GridCells.Adaptive(110.dp),
                contentPadding        = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
            ) {
                items(mangas.itemCount) { index ->
                    mangas[index]?.let { manga ->
                        MangaCard(manga = manga, onClick = { onMangaClick(manga) })
                    }
                }
                if (mangas.loadState.append is LoadState.Loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

