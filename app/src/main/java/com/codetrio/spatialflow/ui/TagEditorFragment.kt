@file:Suppress("DEPRECATION")

package com.codetrio.spatialflow.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.Glide
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@SuppressLint("ObsoleteSdkInt")
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun TagEditorScreenEntryPoint(
    song: SongItem,
    onNavigateUp: () -> Unit,
    viewModel: PlayerSharedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(LocalContext.current.findActivity() as androidx.activity.ComponentActivity)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSaveData by remember { mutableStateOf<Pair<String, String>?>(null) }

    val pickImageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Image selected")
        }
    }

    val intentSenderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingSaveData?.let { (title, artist) ->
                saveTags(
                    context = context,
                    scope = scope,
                    song = song,
                    title = title,
                    artist = artist,
                    selectedImageUri = selectedImageUri,
                    viewModel = viewModel,
                    onNavigateUp = onNavigateUp,
                    onSecurityException = { _ ->
                        pendingSaveData = Pair(title, artist)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Please try saving again to grant permission")
                        }
                    }
                )
            }
        }
    }

    TagEditorScreen(
        song = song,
        selectedImageUri = selectedImageUri,
        onNavigateUp = onNavigateUp,
        onSave = { title, artist ->
            saveTags(
                context = context,
                scope = scope,
                song = song,
                title = title,
                artist = artist,
                selectedImageUri = selectedImageUri,
                viewModel = viewModel,
                onNavigateUp = onNavigateUp,
                onSecurityException = { e ->
                    pendingSaveData = Pair(title, artist)
                    val songUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id.toString())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(songUri))
                        intentSenderLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val rse = e as? RecoverableSecurityException
                        if (rse != null) {
                            intentSenderLauncher.launch(IntentSenderRequest.Builder(rse.userAction.actionIntent.intentSender).build())
                        }
                    } else {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Storage permission required to edit metadata")
                    }
                }
            )
        },
        onChangeCoverArt = { pickImageLauncher.launch("image/*") },
        onDownloadCoverArt = { _ -> downloadCoverArt(context, scope, selectedImageUri ?: song.getAlbumArtUri()) }
    )
}

private fun saveTags(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    song: SongItem,
    title: String,
    artist: String,
    selectedImageUri: Uri?,
    viewModel: PlayerSharedViewModel,
    onNavigateUp: () -> Unit,
    onSecurityException: (SecurityException) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            updateMediaStore(context, song, title, artist, selectedImageUri)
            withContext(Dispatchers.Main) {
                val currentSong = viewModel.currentSong.value
                if (currentSong != null && currentSong.id == song.id) {
                    val updatedSong = SongItem(
                        currentSong.id,
                        title,
                        artist,
                        currentSong.albumId,
                        currentSong.path,
                        currentSong.duration,
                        currentSong.dateAdded
                    )
                    viewModel.setCurrentSong(updatedSong)
                }

                val songList = viewModel.songList.value
                val updatedList = songList.map {
                    if (it.id == song.id) {
                        SongItem(it.id, title, artist, it.albumId, it.path, it.duration, it.dateAdded)
                    } else it
                }
                viewModel.setSongList(updatedList)

                // Notify system
                val intent = Intent("com.codetrio.spatialflow.TAGS_UPDATED")
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(intent)

                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Tags updated successfully")
                onNavigateUp()
            }
        } catch (e: SecurityException) {
            withContext(Dispatchers.Main) {
                onSecurityException(e)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to update: ${e.message}")
            }
        }
    }
}

private fun updateMediaStore(context: Context, song: SongItem, title: String, artist: String, selectedImageUri: Uri?) {
    val resolver: ContentResolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Audio.Media.TITLE, title)
        put(MediaStore.Audio.Media.ARTIST, artist)
    }

    val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id.toString())
    resolver.update(uri, values, null, null)

    try {
        val file = File(song.path ?: "")
        if (file.exists()) {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
            tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)

            // Embed selected cover art at 100% full original quality
            if (selectedImageUri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(selectedImageUri)
                    val bytes = inputStream?.readBytes()
                    if (bytes != null) {
                        val artwork = org.jaudiotagger.tag.images.StandardArtwork()
                        artwork.binaryData = bytes
                        tag.deleteArtworkField()
                        tag.setField(artwork)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            audioFile.commit()

            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null
            ) { _, _ -> }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}

private fun downloadCoverArt(context: Context, scope: kotlinx.coroutines.CoroutineScope, imageUri: Uri?) {
    if (imageUri == null) return
    scope.launch(Dispatchers.IO) {
        try {
            val filename = "AlbumArt_${System.currentTimeMillis()}.png"
            val resolver = context.contentResolver
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
                }
            }
            
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openInputStream(imageUri).use { input ->
                    resolver.openOutputStream(uri).use { output ->
                        if (input != null && output != null) {
                            input.copyTo(output)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Saved art to Pictures folder!")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to save: ${e.message}")
            }
        }
    }
}

@Composable
private fun ExpressiveButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = Color(0xFF292C34),
    contentColor: Color = Color.White,
    disabledContainerColor: Color = Color(0x33FFFFFF),
    height: Dp = 56.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Medium
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "button_scale"
    )

    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = androidx.compose.material3.ButtonDefaults.shape,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = fontWeight,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CoverArtCard(
    bitmap: android.graphics.Bitmap?,
    isLandscape: Boolean,
    onChange: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F22)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isLandscape) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Cover Art",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (isLandscape) 120.dp else 160.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.default_album_art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (isLandscape) 120.dp else 160.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select a square image and fine-tune it so your cover art looks great across the app.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isLandscape) 0.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveButton(
                    onClick = onChange,
                    text = "Change",
                    icon = Icons.Filled.Add,
                    modifier = Modifier.weight(1f)
                )
                ExpressiveButton(
                    onClick = onDownload,
                    text = "Save to Phone",
                    icon = Icons.Filled.Download,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ActionButtonsRow(
    isSaving: Boolean,
    title: String,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExpressiveButton(
            onClick = onCancel,
            text = "Cancel",
            modifier = Modifier.weight(1f),
            containerColor = Color(0xFF2E313A),
            height = 64.dp,
            fontSize = 16.sp
        )
        ExpressiveButton(
            onClick = onSave,
            enabled = !isSaving && title.isNotEmpty(),
            text = if (isSaving) "Saving..." else "Save",
            modifier = Modifier.weight(1f),
            containerColor = Color(0xFFC2D2FF),
            contentColor = Color(0xFF131D33),
            disabledContainerColor = Color(0x33FFFFFF),
            height = 64.dp,
            fontSize = 16.sp
        )
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
private fun TagEditorScreen(
    song: SongItem,
    selectedImageUri: Uri?,
    onNavigateUp: () -> Unit,
    onSave: (String, String) -> Unit,
    onChangeCoverArt: () -> Unit,
    onDownloadCoverArt: (android.graphics.Bitmap?) -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.title) }
    var isSaving by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var albumArtBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(song, selectedImageUri) {
        if (selectedImageUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val future = Glide.with(context)
                        .asBitmap()
                        .load(selectedImageUri)
                        .submit()
                    albumArtBitmap = future.get()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                val embedded = song.getEmbeddedPicture(context)
                val model = embedded ?: song.getAlbumArtUri()
                if (model != null) {
                    try {
                        val future = Glide.with(context)
                            .asBitmap()
                            .load(model)
                            .submit()
                        val bitmap = future.get()
                        albumArtBitmap = bitmap
                    } catch (_: Exception) {
                        // fallback
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 88.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier.weight(0.45f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CoverArtCard(
                        bitmap = albumArtBitmap,
                        isLandscape = true,
                        onChange = onChangeCoverArt,
                        onDownload = { onDownloadCoverArt(albumArtBitmap) }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Edit Details",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    CustomInputField(label = "Title", value = title, onValueChange = { title = it }, icon = Icons.Filled.PlayArrow)
                    CustomInputField(label = "Artist", value = artist, onValueChange = { artist = it }, icon = Icons.Filled.Person)
                    CustomInputField(label = "Album", value = album, onValueChange = { album = it }, icon = Icons.Filled.Face)

                    Spacer(modifier = Modifier.height(16.dp))

                    ActionButtonsRow(
                        isSaving = isSaving,
                        title = title,
                        onCancel = onNavigateUp,
                        onSave = {
                            isSaving = true
                            onSave(title, artist)
                        }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Song",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                CoverArtCard(
                    bitmap = albumArtBitmap,
                    isLandscape = false,
                    onChange = onChangeCoverArt,
                    onDownload = { onDownloadCoverArt(albumArtBitmap) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                CustomInputField(label = "Title", value = title, onValueChange = { title = it }, icon = Icons.Filled.PlayArrow)
                CustomInputField(label = "Artist", value = artist, onValueChange = { artist = it }, icon = Icons.Filled.Person)
                CustomInputField(label = "Album", value = album, onValueChange = { album = it }, icon = Icons.Filled.Face)

                Spacer(modifier = Modifier.height(32.dp))

                ActionButtonsRow(
                    isSaving = isSaving,
                    title = title,
                    onCancel = onNavigateUp,
                    onSave = {
                        isSaving = true
                        onSave(title, artist)
                    }
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E1F22),
                unfocusedContainerColor = Color(0xFF1E1F22),
                disabledContainerColor = Color(0xFF1E1F22),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = Color.White
            ),
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        )
    }
}
