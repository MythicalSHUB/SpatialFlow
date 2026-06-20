package com.codetrio.spatialflow.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.theme.SpatialFlowTheme
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class QueueBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: PlayerSharedViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[PlayerSharedViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SpatialFlowTheme(darkTheme = isSystemInDarkTheme()) {
                    QueueBottomSheetContent(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun QueueBottomSheetContent(
    viewModel: PlayerSharedViewModel
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val songList by viewModel.songListFlow.collectAsStateWithLifecycle()
    val currentSongIndex by viewModel.currentSongIndexFlow.collectAsStateWithLifecycle()

    val displayedSongs = songList

    val lazyListState = rememberLazyListState()
    val dragDropState = rememberDragDropState(lazyListState = lazyListState) { from, to ->
        viewModel.reorderQueue(from, to)
    }

    // Auto-scroll to currently playing track on initial load
    LaunchedEffect(currentSongIndex) {
        if (dragDropState.currentIndexOfDraggedItem != null) return@LaunchedEffect
        if (currentSongIndex in displayedSongs.indices) {
            val distance = abs(lazyListState.firstVisibleItemIndex - currentSongIndex)
            if (distance > 24) {
                lazyListState.scrollToItem(currentSongIndex)
            } else {
                lazyListState.animateScrollToItem(currentSongIndex)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isLandscape) Modifier.fillMaxHeight(0.9f) else Modifier.wrapContentHeight()),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp)
                .then(if (isLandscape) Modifier.padding(start = 88.dp, end = 24.dp) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            if (!isLandscape) {
                // PORTRAIT: Standard Flow Layout
                Spacer(modifier = Modifier.height(20.dp))

                // Title & Counter Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Up Next",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "${displayedSongs.size} tracks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                ) {
                    if (displayedSongs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 64.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_queue_music),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Your queue is empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                            itemsIndexed(
                                items = displayedSongs,
                                key = { _, song -> song.id },
                                contentType = { _, _ -> "queue-song" }
                            ) { index, song ->
                                val isPlaying = (index == currentSongIndex)
                                val shapes = ListItemDefaults.segmentedShapes(index = index, count = displayedSongs.size)
                                
                                val isDragging = index == dragDropState.currentIndexOfDraggedItem
                                val displacement = if (isDragging) dragDropState.elementDisplacement ?: 0f else 0f

                                Box(
                                    modifier = Modifier
                                        .animateItem()
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = displacement
                                            if (isDragging) {
                                                scaleX = 1.03f
                                                scaleY = 1.03f
                                            }
                                        }
                                ) {
                                    QueueListItem(
                                        song = song,
                                        isPlaying = isPlaying,
                                        shapes = shapes,
                                        showReorderControls = true,
                                        dragDropState = dragDropState,
                                        index = index,
                                        onMoveUp = {
                                            if (index > 0) {
                                                viewModel.reorderQueue(index, index - 1)
                                            }
                                        },
                                        onMoveDown = {
                                            if (index < displayedSongs.size - 1) {
                                                viewModel.reorderQueue(index, index + 1)
                                            }
                                        },
                                        onClick = {
                                            viewModel.playSongAtIndex(index)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // LANDSCAPE: Split-Screen Dashboard
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // LEFT PANE: Sidebar Selector (40%)
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "Audio Queue",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${displayedSongs.size} tracks loaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Text(
                            text = "VIEW SOURCE",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        QueueSidebarItem(
                            label = "Up Next",
                            isSelected = true,
                            icon = { 
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow, 
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                ) 
                            },
                            onClick = {}
                        )
                    }

                    // RIGHT PANE: Native Segmented LazyColumn (60%)
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                    ) {
                        if (displayedSongs.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_queue_music),
                                    contentDescription = null,
                                    modifier = Modifier.size(60.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Queue is empty",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .dragContainer(dragDropState, enabled = true),
                                contentPadding = PaddingValues(end = 20.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                            ) {
                                itemsIndexed(
                                    items = displayedSongs,
                                    key = { _, song -> song.id },
                                    contentType = { _, _ -> "queue-song" }
                                ) { index, song ->
                                    val isPlaying = (index == currentSongIndex)
                                    val shapes = ListItemDefaults.segmentedShapes(index = index, count = displayedSongs.size)
                                    
                                    val isDragging = index == dragDropState.currentIndexOfDraggedItem
                                    val displacement = if (isDragging) dragDropState.elementDisplacement ?: 0f else 0f

                                    Box(
                                        modifier = Modifier
                                            .animateItem()
                                            .zIndex(if (isDragging) 1f else 0f)
                                            .graphicsLayer {
                                                translationY = displacement
                                                if (isDragging) {
                                                    scaleX = 1.03f
                                                    scaleY = 1.03f
                                                }
                                            }
                                    ) {
                                        QueueListItem(
                                            song = song,
                                            isPlaying = isPlaying,
                                            shapes = shapes,
                                            showReorderControls = true,
                                            onMoveUp = {
                                                if (index > 0) {
                                                    viewModel.reorderQueue(index, index - 1)
                                                }
                                            },
                                            onMoveDown = {
                                                if (index < displayedSongs.size - 1) {
                                                    viewModel.reorderQueue(index, index + 1)
                                                }
                                            },
                                            onClick = {
                                                viewModel.playSongAtIndex(index)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QueueListItem(
    song: SongItem,
    isPlaying: Boolean,
    shapes: ListItemShapes,
    showReorderControls: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    dragDropState: DragDropState? = null,
    index: Int = -1,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            val albumArtModel = remember(song.id) { song.getAlbumArtUri() ?: R.drawable.default_album_art }
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = albumArtModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Playing",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        content = {
            Text(
                text = song.title,
                fontWeight = if (isPlaying) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize = 16.sp,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = song.artist,
                fontSize = 13.sp,
                color = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            if (showReorderControls) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Move Up") },
                            onClick = {
                                showMenu = false
                                onMoveUp()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move Down") },
                            onClick = {
                                showMenu = false
                                onMoveDown()
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(32.dp)
                            .padding(4.dp)
                            .pointerInput(dragDropState) {
                                if (dragDropState == null || index < 0) return@pointerInput
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val item = dragDropState.lazyListState.layoutInfo.visibleItemsInfo.find { it.index == index }
                                        if (item != null) {
                                            dragDropState.onDragStart(Offset(offset.x, offset.y + item.offset))
                                        }
                                    },
                                    onDragEnd = { dragDropState.onDragInterrupted() },
                                    onDragCancel = { dragDropState.onDragInterrupted() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragDropState.onDrag(dragAmount)
                                    }
                                )
                            }
                    )
                }
            }
        },
        shapes = shapes,
        colors = ListItemDefaults.colors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                Color.Transparent
            }
        )
    )
}

@Composable
fun QueueSidebarItem(
    label: String,
    isSelected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f) else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

class DragDropState(
    val lazyListState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit
) {
    var draggedDistance by mutableFloatStateOf(0f)
    var initiallyDraggedElement by mutableStateOf<LazyListItemInfo?>(null)
    var currentIndexOfDraggedItem by mutableStateOf<Int?>(null)
    private var autoScrollJob: Job? = null
    private var autoScrollDeltaPx: Float = 0f
    private var lastTargetIndex: Int? = null

    val elementDisplacement: Float?
        get() = currentIndexOfDraggedItem?.let { currentIndex ->
            lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == currentIndex }
                ?.let { item ->
                    (initiallyDraggedElement?.offset ?: 0).toFloat() + draggedDistance - item.offset
                }
        }

    fun onDragStart(offset: Offset) {
        stopAutoScroll()
        lastTargetIndex = null
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?.also {
                initiallyDraggedElement = it
                currentIndexOfDraggedItem = it.index
            }
    }

    fun onDragInterrupted() {
        initiallyDraggedElement = null
        currentIndexOfDraggedItem = null
        draggedDistance = 0f
        lastTargetIndex = null
        stopAutoScroll()
    }

    fun onDrag(dragAmount: Offset) {
        draggedDistance += dragAmount.y

        val initialOffset = initiallyDraggedElement?.offset ?: return
        val currentOffset = initialOffset + draggedDistance
        val size = initiallyDraggedElement?.size ?: return
        
        val currentCenter = currentOffset + size / 2f

        val currentIndex = currentIndexOfDraggedItem ?: return
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        
        // Nearest item detection
        val targetItem = visibleItems.minByOrNull { item ->
            kotlin.math.abs((item.offset + item.size / 2f) - currentCenter)
        }

        if (
            targetItem != null && 
            targetItem.index != currentIndex && 
            targetItem.index != lastTargetIndex
        ) {
            lastTargetIndex = targetItem.index

            onMove(currentIndex, targetItem.index)
            currentIndexOfDraggedItem = targetItem.index

            // Re-anchor Dragged Item
            initiallyDraggedElement = lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == targetItem.index }
            draggedDistance = 0f
        }
        
        // Use exact visible window limits to prevent jarring scrolling
        val viewportStart = lazyListState.layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = lazyListState.layoutInfo.viewportEndOffset.toFloat()
        val overscrollThreshold = 80f

        // Smoother delta values
        val scrollDelta = when {
            currentOffset < viewportStart + overscrollThreshold -> -20f
            currentOffset + size > viewportEnd - overscrollThreshold -> 20f
            else -> 0f
        }
        updateAutoScroll(scrollDelta)
    }

    private fun updateAutoScroll(scrollDeltaPx: Float) {
        if (scrollDeltaPx == 0f) {
            stopAutoScroll()
            return
        }
        autoScrollDeltaPx = scrollDeltaPx
        if (autoScrollJob?.isActive == true) return

        autoScrollJob = scope.launch {
            while (currentIndexOfDraggedItem != null) {
                val delta = autoScrollDeltaPx
                if (delta == 0f) break
                lazyListState.scrollBy(delta)
                delay(16L.milliseconds)
            }
        }
    }

    private fun stopAutoScroll() {
        autoScrollDeltaPx = 0f
        autoScrollJob?.cancel()
        autoScrollJob = null
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    val state = remember(lazyListState) {
        DragDropState(lazyListState, scope, onMove)
    }
    return state
}

fun Modifier.dragContainer(dragDropState: DragDropState, enabled: Boolean): Modifier {
    if (!enabled) return this
    return this.then(
        Modifier.pointerInput(dragDropState) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset -> dragDropState.onDragStart(offset) },
                onDragEnd = { dragDropState.onDragInterrupted() },
                onDragCancel = { dragDropState.onDragInterrupted() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragDropState.onDrag(dragAmount)
                }
            )
        }
    )
}
