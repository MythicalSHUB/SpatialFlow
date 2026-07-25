package com.codetrio.spatialflow.player.queue

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.innertube.InnerTubeClient
import com.codetrio.spatialflow.data.innertube.InnerTubeParser
import com.codetrio.spatialflow.data.innertube.path
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.player.deriveArtworkSurfaceColor
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

// --- 1. Strategies Section ---

interface Queue {
    suspend fun getInitialStatus(): Status
    fun hasNextPage(): Boolean
    suspend fun nextPage(): List<SongItem>
    
    val currentTitle: String?
    val preloadItem: SongItem?

    fun shouldExpandToFullQueueWhenAutoLoadMoreDisabled(): Boolean = false

    data class Status(
        val title: String?,
        val items: List<SongItem>,
        val mediaItemIndex: Int = 0,
        val position: Long = 0L
    )
}

class StaticQueue(
    override val currentTitle: String? = null,
    private val initialItems: List<SongItem>,
    override val preloadItem: SongItem? = null,
    private val startIndex: Int = 0,
    private val position: Long = 0L
) : Queue {

    override suspend fun getInitialStatus(): Queue.Status {
        return Queue.Status(
            title = currentTitle,
            items = initialItems,
            mediaItemIndex = startIndex,
            position = position
        )
    }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<SongItem> = emptyList()
}

class YouTubeQueue(
    private val videoId: String? = null,
    private val playlistId: String? = null,
    private val initialParams: String? = null,
    private var continuationToken: String? = null,
    override val preloadItem: SongItem? = null,
    private val followAutomixPreview: Boolean = true,
    private val startIndex: Int = 0,
    private val position: Long = 0L
) : Queue {

    override var currentTitle: String? = null
        private set

    companion object {
        fun radio(song: SongItem, params: String? = null) = YouTubeQueue(
            videoId = song.videoId,
            playlistId = song.videoId?.let { "RDAMVM$it" },
            initialParams = params,
            preloadItem = song,
            followAutomixPreview = true
        )
    }

    override suspend fun getInitialStatus(): Queue.Status {
        val status = fetchNext(isInitial = true)
        val matchedIndex = if (preloadItem != null) {
            status.items.indexOfFirst { it.videoId == preloadItem.videoId }
        } else {
            startIndex
        }
        val finalIndex = if (matchedIndex >= 0) matchedIndex else 0
        return Queue.Status(
            title = status.title,
            items = status.items,
            mediaItemIndex = finalIndex,
            position = position
        )
    }

    override fun hasNextPage(): Boolean {
        return continuationToken != null
    }

    override suspend fun nextPage(): List<SongItem> {
        if (continuationToken == null) return emptyList()
        return fetchNext(isInitial = false).items
    }

    private suspend fun fetchNext(isInitial: Boolean): Queue.Status = withContext(Dispatchers.IO) {
        val response = if (isInitial) {
            InnerTubeClient.next(videoId = videoId, playlistId = playlistId, params = initialParams)
        } else {
            InnerTubeClient.next(videoId = null, continuation = continuationToken)
        }

        val songs = mutableListOf<SongItem>()
        
        val playlistPanel = if (isInitial) {
            findJsonObjectRecursively(response, "playlistPanelRenderer")
        } else {
            findJsonObjectRecursively(response, "playlistPanelRenderer") ?: 
            response.path("continuationContents.playlistPanelContinuation")?.asJsonObject
        }

        if (isInitial) {
            currentTitle = playlistPanel?.path("title")?.asString
        }

        val playlistContents = playlistPanel?.path("contents")?.asJsonArray

        playlistContents?.forEach { content ->
            val renderer = content.asJsonObject.path("playlistPanelVideoRenderer")?.asJsonObject ?: return@forEach
            val id = renderer.path("videoId")?.asString ?: return@forEach
            val titleText = renderer.path("title.runs.0.text")?.asString ?: return@forEach
            val shortByline = renderer.path("shortBylineText.runs")?.asJsonArray
                ?.joinToString("") { it.asJsonObject.path("text")?.asString ?: "" } ?: ""
            val lengthText = renderer.path("lengthText.runs.0.text")?.asString
            val thumb = InnerTubeParser.getHighResThumbnailUrl(
                renderer.path("thumbnail.thumbnails")
                    ?.asJsonArray?.lastOrNull()?.asJsonObject?.path("url")?.asString
            )

            songs.add(SongItem.createOnlineSong(
                videoId = id,
                title = titleText,
                artist = shortByline.trim(),
                streamUrl = "",
                durationMs = parseDuration(lengthText),
                thumbnailUrl = thumb
            ))
        }

        continuationToken = playlistPanel?.path("continuations.0.nextRadioContinuationData.continuation")?.asString
            ?: playlistPanel?.path("continuations.0.nextContinuationData.continuation")?.asString

        val automixRenderer = findJsonObjectRecursively(response, "automixPreviewVideoRenderer")
        if (followAutomixPreview && automixRenderer != null) {
            val watchPlaylistEndpoint = findJsonObjectRecursively(automixRenderer, "watchPlaylistEndpoint")
            val autoPlaylistId = watchPlaylistEndpoint?.path("playlistId")?.asString
            val autoParams = watchPlaylistEndpoint?.path("params")?.asString
            if (!autoPlaylistId.isNullOrEmpty()) {
                android.util.Log.d("YouTubeQueue", "Found automixPreviewVideoRenderer! Recursively resolving automix playlistId=$autoPlaylistId, params=$autoParams")
                try {
                    val autoResponse = InnerTubeClient.next(videoId = null, playlistId = autoPlaylistId, params = autoParams)
                    val autoPlaylistPanel = findJsonObjectRecursively(autoResponse, "playlistPanelRenderer")
                    val autoPlaylistContents = autoPlaylistPanel?.path("contents")?.asJsonArray
                    
                    autoPlaylistContents?.forEach { content ->
                        val renderer = content.asJsonObject.path("playlistPanelVideoRenderer")?.asJsonObject ?: return@forEach
                        val id = renderer.path("videoId")?.asString ?: return@forEach
                        val titleText = renderer.path("title.runs.0.text")?.asString ?: return@forEach
                        val shortByline = renderer.path("shortBylineText.runs")?.asJsonArray
                            ?.joinToString("") { it.asJsonObject.path("text")?.asString ?: "" } ?: ""
                        val lengthText = renderer.path("lengthText.runs.0.text")?.asString
                        val thumb = InnerTubeParser.getHighResThumbnailUrl(
                            renderer.path("thumbnail.thumbnails")
                                ?.asJsonArray?.lastOrNull()?.asJsonObject?.path("url")?.asString
                        )

                        if (songs.none { it.videoId == id }) {
                            songs.add(SongItem.createOnlineSong(
                                videoId = id,
                                title = titleText,
                                artist = shortByline.trim(),
                                streamUrl = "",
                                durationMs = parseDuration(lengthText),
                                thumbnailUrl = thumb
                            ))
                        }
                    }
                    
                    val autoContinuation = autoPlaylistPanel?.path("continuations.0.nextRadioContinuationData.continuation")?.asString
                        ?: autoPlaylistPanel?.path("continuations.0.nextContinuationData.continuation")?.asString
                    if (autoContinuation != null) {
                        continuationToken = autoContinuation
                    }
                } catch (e: Exception) {
                    android.util.Log.e("YouTubeQueue", "Failed recursively resolving automix playlist", e)
                }
            }
        }

        Queue.Status(title = currentTitle, items = songs)
    }

    private fun findJsonObjectRecursively(element: com.google.gson.JsonElement?, targetKey: String): com.google.gson.JsonObject? {
        if (element == null || !element.isJsonObject) return null
        val obj = element.asJsonObject
        if (obj.has(targetKey) && obj.get(targetKey).isJsonObject) {
            return obj.getAsJsonObject(targetKey)
        }
        for (entry in obj.entrySet()) {
            val child = entry.value
            if (child.isJsonObject) {
                val found = findJsonObjectRecursively(child, targetKey)
                if (found != null) return found
            } else if (child.isJsonArray) {
                for (item in child.asJsonArray) {
                    val found = findJsonObjectRecursively(item, targetKey)
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun parseDuration(duration: String?): Long {
        if (duration.isNullOrEmpty()) return 0L
        val parts = duration.split(":")
        var millis = 0L
        try {
            when (parts.size) {
                3 -> {
                    millis += parts[0].toLong() * 3600000L
                    millis += parts[1].toLong() * 60000L
                    millis += parts[2].toLong() * 1000L
                }
                2 -> {
                    millis += parts[0].toLong() * 60000L
                    millis += parts[1].toLong() * 1000L
                }
                1 -> {
                    millis += parts[0].toLong() * 1000L
                }
            }
        } catch (e: Exception) {
            // Ignore format errors
        }
        return millis
    }
}

// --- 2. Reordering Core Section ---

// Custom drag-and-drop implementation has been simplified and consolidated.
// We use sh.calvin.reorderable directly inside the UI presentation section below.

// --- 3. UI Presentation Section ---

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun SlidingQueueDrawer(
    isQueueExpanded: Boolean,
    onQueueExpandedChange: (Boolean) -> Unit,
    songList: List<SongItem>,
    currentSongIndex: Int,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    sleepTimerMode: PlayerSharedViewModel.SleepTimerMode,
    onReorderQueue: (Int, Int) -> Unit,
    onPlaySongAtIndex: (Int) -> Unit,
    onRemoveSongAtIndex: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleLoopMode: () -> Unit,
    onShowSleepTimerDialog: () -> Unit,
    playerBackgroundColor: Int,
    dynamicAccentColor: Color,
    isDark: Boolean,
    isAutoplayEnabled: Boolean,
    onAutoplayToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    val slidingOffset by animateDpAsState(
        targetValue = if (isQueueExpanded) 0.dp else screenHeight + 100.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "QueueSlidingOffset"
    )

    val queueCornerRadius by animateDpAsState(
        targetValue = if (isQueueExpanded) 0.dp else 32.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "QueueCornerRadius"
    )
    val safeCornerRadius = queueCornerRadius.coerceAtLeast(0.dp)

    val queueBgColor = deriveArtworkSurfaceColor(
        sourceColor = Color(playerBackgroundColor),
        isDark = isDark,
        darkLightness = 0.12f,
        lightLightness = 0.88f,
        darkSaturationRange = 0.28f..0.48f,
        lightSaturationRange = 0.24f..0.42f
    )

    val queueTrayBackgroundColor = remember(playerBackgroundColor, isDark) {
        deriveArtworkSurfaceColor(
            sourceColor = Color(playerBackgroundColor),
            isDark = isDark,
            darkLightness = 0.18f,
            lightLightness = 0.80f,
            darkSaturationRange = 0.28f..0.52f,
            lightSaturationRange = 0.20f..0.44f
        )
    }

    val queueTrayInactiveButtonColor = remember(queueTrayBackgroundColor, isDark) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(queueTrayBackgroundColor.toArgb(), hsl)
        hsl[1] = if (hsl[1] < 0.08f) 0f else hsl[1].coerceIn(0.20f, 0.40f)
        hsl[2] = if (isDark) 0.28f else 0.72f
        Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
    }

    val queueTrayActiveButtonColor = remember(dynamicAccentColor, isDark) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(dynamicAccentColor.toArgb(), hsl)
        hsl[1] = hsl[1].coerceAtLeast(0.40f)
        hsl[2] = if (isDark) 0.58f else 0.42f
        Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
    }

    val queueTrayInactiveContentColor = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f)
    val queueTrayActiveContentColor = if (isDark) Color.Black else Color.White

    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .offset { androidx.compose.ui.unit.IntOffset(0, slidingOffset.roundToPx()) },
        shape = RoundedCornerShape(topStart = safeCornerRadius, topEnd = safeCornerRadius),
        color = queueBgColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val lazyListState = rememberLazyListState()

            var localSongList by remember(songList) { mutableStateOf(songList) }
            var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
            var lastMovedTo by remember { mutableStateOf<Int?>(null) }
            var searchQuery by remember { mutableStateOf("") }
            var isSearchExpanded by remember { mutableStateOf(false) }

            val filteredSongList = remember(localSongList, searchQuery) {
                if (searchQuery.isEmpty()) {
                    localSongList
                } else {
                    localSongList.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            val reorderableState = rememberReorderableLazyListState(
                lazyListState = lazyListState,
                onMove = { from, to ->
                    val fromIndex = localSongList.indexOfFirst { it.id == from.key }
                    val toIndex = localSongList.indexOfFirst { it.id == to.key }
                    if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                        localSongList = localSongList.toMutableList().apply {
                            add(toIndex, removeAt(fromIndex))
                        }
                        if (lastMovedFrom == null) {
                            lastMovedFrom = fromIndex
                        }
                        lastMovedTo = toIndex
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            )

            LaunchedEffect(songList) {
                if (!reorderableState.isAnyItemDragging) {
                    localSongList = songList
                }
            }

            LaunchedEffect(reorderableState.isAnyItemDragging) {
                if (!reorderableState.isAnyItemDragging) {
                    val fromIdx = lastMovedFrom
                    val toIdx = lastMovedTo
                    lastMovedFrom = null
                    lastMovedTo = null

                    if (fromIdx != null && toIdx != null && fromIdx != toIdx) {
                        onReorderQueue(fromIdx, toIdx)
                    }
                }
            }

            LaunchedEffect(isQueueExpanded, currentSongIndex) {
                if (isQueueExpanded && currentSongIndex in songList.indices) {
                    val currentSongId = songList[currentSongIndex].id
                    val localIndex = localSongList.indexOfFirst { it.id == currentSongId }
                    if (localIndex != -1) {
                        val distance = abs(lazyListState.firstVisibleItemIndex - localIndex)
                        if (distance > 24) {
                            lazyListState.scrollToItem(localIndex)
                        } else {
                            lazyListState.animateScrollToItem(localIndex)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.2f)
                                else Color.Black.copy(alpha = 0.12f)
                            )
                    )
                }

                // Row 1: Title and Collapse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Playback Queue",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = if (isDark) Color.White else Color.Black
                    )

                    IconButton(
                        onClick = { onQueueExpandedChange(false) },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f)
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                            contentDescription = "Collapse Queue",
                            tint = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Row 2: Subtitle and Controls (Autoplay, Search)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${songList.size} tracks • Playing next",
                        style = MaterialTheme.typography.bodyMedium,
                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Autoplay Toggle Switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Autoplay",
                                style = MaterialTheme.typography.labelMedium,
                                color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.6f)
                            )
                            Switch(
                                checked = isAutoplayEnabled,
                                onCheckedChange = onAutoplayToggle,
                                thumbContent = {
                                    if (isAutoplayEnabled) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    } else null
                                },
                                modifier = Modifier.scale(0.75f)
                            )
                        }

                        // Search Toggle Button
                        IconButton(
                            onClick = {
                                isSearchExpanded = !isSearchExpanded
                                if (!isSearchExpanded) {
                                    searchQuery = ""
                                }
                            },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isSearchExpanded) dynamicAccentColor.copy(alpha = 0.15f) else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Toggle Search",
                                tint = if (isSearchExpanded) dynamicAccentColor else (if (isDark) Color.White else Color.Black).copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Beautiful Expandable Search/Filter Input
                AnimatedVisibility(
                    visible = isSearchExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        placeholder = {
                            Text(
                                text = "Search tracks in queue...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.5f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = dynamicAccentColor
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Clear search",
                                        tint = (if (isDark) Color.White else Color.Black).copy(alpha = 0.6f)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f),
                            unfocusedContainerColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.03f),
                            focusedBorderColor = dynamicAccentColor,
                            unfocusedBorderColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.08f),
                            focusedTextColor = if (isDark) Color.White else Color.Black,
                            unfocusedTextColor = if (isDark) Color.White else Color.Black
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f)
                            else Color.Black.copy(alpha = 0.06f)
                        )
                )

                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currentSongId = songList.getOrNull(currentSongIndex)?.id
                    itemsIndexed(
                        items = filteredSongList,
                        key = { _, song -> song.id },
                        contentType = { _, _ -> "queue-song" }
                    ) { index, song ->
                        val isPlaying = (song.id == currentSongId)
                        val originalIndex = songList.indexOfFirst { it.id == song.id }

                        if (searchQuery.isNotEmpty()) {
                            RebuiltQueueListItem(
                                song = song,
                                isPlaying = isPlaying,
                                isDark = isDark,
                                dynamicAccentColor = dynamicAccentColor,
                                onClick = {
                                    if (originalIndex != -1) {
                                        onPlaySongAtIndex(originalIndex)
                                    }
                                },
                                onRemoveClick = {
                                    if (originalIndex != -1) {
                                        onRemoveSongAtIndex(originalIndex)
                                    }
                                },
                                dragHandle = {}
                            )
                        } else {
                            ReorderableItem(
                                state = reorderableState,
                                key = song.id
                            ) { isDragging ->
                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.03f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "scaleAnimation"
                                )

                                RebuiltQueueListItem(
                                    song = song,
                                    isPlaying = isPlaying,
                                    isDark = isDark,
                                    dynamicAccentColor = dynamicAccentColor,
                                    onClick = {
                                        if (originalIndex != -1) {
                                            onPlaySongAtIndex(originalIndex)
                                        }
                                    },
                                    onRemoveClick = {
                                        if (originalIndex != -1) {
                                            onRemoveSongAtIndex(originalIndex)
                                        }
                                    },
                                    dragHandle = {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onDragStopped = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Menu,
                                                contentDescription = "Drag to reorder",
                                                tint = (if (isDark) Color.White else Color.Black).copy(alpha = 0.35f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                        }
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = queueTrayBackgroundColor,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 20.dp, bottom = 24.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.ButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        expandedRatio = 0.3f,
                        overflowIndicator = {}
                    ) {
                        val scope = this

                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 12.dp else 28.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "ShuffleCorner"
                                )
                                androidx.compose.material3.Button(
                                    onClick = {
                                        onToggleShuffle()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    modifier = with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(56.dp)
                                    },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = if (isShuffleEnabled) queueTrayActiveButtonColor else queueTrayInactiveButtonColor,
                                        contentColor = if (isShuffleEnabled) queueTrayActiveContentColor else queueTrayInactiveContentColor
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_shuffle),
                                        contentDescription = "Shuffle",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            menuContent = {}
                        )

                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 12.dp else 28.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "LoopCorner"
                                )
                                val loopIcon = if (repeatMode == PlayerSharedViewModel.REPEAT_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
                                val loopActive = repeatMode != PlayerSharedViewModel.REPEAT_OFF
                                androidx.compose.material3.Button(
                                    onClick = {
                                        onToggleLoopMode()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    modifier = with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(56.dp)
                                    },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = if (loopActive) queueTrayActiveButtonColor else queueTrayInactiveButtonColor,
                                        contentColor = if (loopActive) queueTrayActiveContentColor else queueTrayInactiveContentColor
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = loopIcon),
                                        contentDescription = "Repeat Mode",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            menuContent = {}
                        )

                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 12.dp else 28.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "TimerCorner"
                                )
                                val timerActive = sleepTimerMode != PlayerSharedViewModel.SleepTimerMode.OFF
                                androidx.compose.material3.Button(
                                    onClick = {
                                        onShowSleepTimerDialog()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    modifier = with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(56.dp)
                                    },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = if (timerActive) queueTrayActiveButtonColor else queueTrayInactiveButtonColor,
                                        contentColor = if (timerActive) queueTrayActiveContentColor else queueTrayInactiveContentColor
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_timer),
                                        contentDescription = "Sleep Timer",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            menuContent = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RebuiltQueueListItem(
    song: SongItem,
    isPlaying: Boolean,
    isDark: Boolean,
    dynamicAccentColor: Color,
    onClick: () -> Unit,
    onRemoveClick: (() -> Unit)?,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumArtModel = remember(song.id) { song.getAlbumArtUri() ?: R.drawable.default_album_art }
    val itemBgColor = if (isPlaying) {
        dynamicAccentColor.copy(alpha = 0.12f)
    } else {
        (if (isDark) Color.White else Color.Black).copy(alpha = 0.02f)
    }
    
    val borderStroke = if (isPlaying) {
        BorderStroke(1.dp, dynamicAccentColor.copy(alpha = 0.25f))
    } else {
        BorderStroke(1.dp, (if (isDark) Color.White else Color.Black).copy(alpha = 0.03f))
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = itemBgColor,
        border = borderStroke,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Far Left Glowing Indicator Line for the currently playing item
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) dynamicAccentColor else Color.Transparent)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Thumbnail container
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = albumArtModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val transition = rememberInfiniteTransition(label = "equalizer")
                        val line1Scale by transition.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 0.8f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 480, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "line1"
                        )
                        val line2Scale by transition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 0.95f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 400, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "line2"
                        )
                        val line3Scale by transition.animateFloat(
                            initialValue = 0.1f,
                            targetValue = 0.75f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 550, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "line3"
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier
                                .height(16.dp)
                                .align(Alignment.Center)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .fillMaxHeight(line1Scale)
                                    .background(dynamicAccentColor, RoundedCornerShape(2.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .fillMaxHeight(line2Scale)
                                    .background(dynamicAccentColor, RoundedCornerShape(2.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .fillMaxHeight(line3Scale)
                                    .background(dynamicAccentColor, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = song.title,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (isPlaying) dynamicAccentColor else (if (isDark) Color.White else Color.Black),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    fontSize = 13.sp,
                    color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onRemoveClick != null) {
                    IconButton(
                        onClick = onRemoveClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Remove from Queue",
                            tint = (if (isDark) Color.White else Color.Black).copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                dragHandle()
            }
        }
    }
}
