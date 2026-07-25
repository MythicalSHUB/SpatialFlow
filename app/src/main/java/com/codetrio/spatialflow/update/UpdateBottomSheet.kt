@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.codetrio.spatialflow.update

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.StrokeCap

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.core.content.FileProvider
import com.codetrio.spatialflow.ui.theme.SpatialFlowTheme
import com.codetrio.spatialflow.ui.explore.shimmerEffect
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import androidx.core.net.toUri
import kotlin.time.Duration.Companion.milliseconds

class UpdateBottomSheet : BottomSheetDialogFragment() {

    interface OnUpdateClickListener {
        fun onUpdateClick(apkUrl: String): Long
        fun onLaterClick()
    }

    private var listener: OnUpdateClickListener? = null
    private var pendingApkPath: String? = null

    companion object {
        private const val ARG_TAG_NAME = "tag_name"
        private const val ARG_CHANGELOG = "changelog"
        private const val ARG_APK_URL = "apk_url"
        private const val ARG_GIF_URL = "gif_url"

        @JvmStatic
        fun newInstance(
            tagName: String,
            changelog: String,
            apkUrl: String,
            gifUrl: String? = null
        ): UpdateBottomSheet {
            val fragment = UpdateBottomSheet()
            val args = Bundle().apply {
                putString(ARG_TAG_NAME, tagName)
                putString(ARG_CHANGELOG, changelog)
                putString(ARG_APK_URL, apkUrl)
                putString(ARG_GIF_URL, gifUrl)
            }
            fragment.arguments = args
            return fragment
        }
    }

    fun setOnUpdateClickListener(listener: OnUpdateClickListener) {
        this.listener = listener
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val tagName = arguments?.getString(ARG_TAG_NAME) ?: ""
        val changelog = arguments?.getString(ARG_CHANGELOG) ?: ""
        val apkUrl = arguments?.getString(ARG_APK_URL) ?: ""
        val explicitGifUrl = arguments?.getString(ARG_GIF_URL)
        val parsedGifUrl = explicitGifUrl?.takeIf { it.isNotBlank() } ?: parseGifUrl(changelog)

        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setContent {
                SpatialFlowTheme {
                    UpdateBottomSheetContent(
                        tagName = tagName,
                        changelog = changelog,
                        apkUrl = apkUrl,
                        gifUrl = parsedGifUrl ?: DEFAULT_GIF_URL,
                        onUpdateClick = { url ->
                            listener?.onUpdateClick(url) ?: -1L
                        },
                        onLaterClick = {
                            listener?.onLaterClick()
                            dismiss()
                        },
                        onInstallApk = { path ->
                            installApk(requireContext(), path)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val path = pendingApkPath
        if (path != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (requireContext().packageManager.canRequestPackageInstalls()) {
                    pendingApkPath = null
                    installApk(requireContext(), path)
                }
            }
        }
    }

    private fun installApk(context: Context, path: String) {
        try {
            val apkFile = File(path)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    pendingApkPath = path
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    startActivity(intent)
                    return
                }
            }

            val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            dismiss()
        } catch (e: Exception) {
            Log.e("UpdateBottomSheet", "Failed to install APK", e)
        }
    }
}

enum class UpdateState {
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    FAILED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheetContent(
    tagName: String,
    changelog: String,
    apkUrl: String,
    gifUrl: String,
    onUpdateClick: (String) -> Long,
    onLaterClick: () -> Unit,
    onInstallApk: (String) -> Unit
) {
    val context = LocalContext.current
    val currentVersionName = remember(context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    val localImageLoader = remember(context) {
        coil.ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }
    var updateState by remember { mutableStateOf(UpdateState.AVAILABLE) }
    var downloadId by remember { mutableLongStateOf(-1L) }
    var progress by remember { mutableFloatStateOf(0f) }
    var bytesDownloaded by remember { mutableLongStateOf(0L) }
    var bytesTotal by remember { mutableLongStateOf(0L) }
    var apkPath by remember { mutableStateOf<String?>(null) }

    // Infinite Animations
    val infiniteTransition = rememberInfiniteTransition(label = "update_animations")
    
    // Rotating halo

    // Bouncing icon

    // Pulse scale

    // Glow / border opacity pulse

    // Shimmer translate
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val changelogLines = remember(changelog) {
        changelog.split("\n").filter { line ->
            val trimmed = line.trim()
            trimmed.isNotBlank()
                    && !trimmed.startsWith("![")          // Markdown image (GIF banner)
                    && !trimmed.startsWith("<img", ignoreCase = true)  // HTML img tag
                    && !trimmed.startsWith("#")            // Markdown headings
                    && !trimmed.matches(Regex("""^https?://\S+\.(gif|png|jpe?g|webp)(\?.*)?$""", RegexOption.IGNORE_CASE)) // Raw image URLs
        }
    }

    // Download Polling Logic
    LaunchedEffect(updateState, downloadId) {
        if (updateState == UpdateState.DOWNLOADING && downloadId != -1L) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            var polling = true

            while (polling) {
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    bytesDownloaded = downloaded
                    bytesTotal = total
                    progress = if (total > 0) downloaded.toFloat() / total else 0f

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            updateState = UpdateState.READY_TO_INSTALL
                            polling = false
                            val uriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                            if (uriString != null) {
                                val path = uriString.toUri().path
                                if (path != null) {
                                    apkPath = path
                                    onInstallApk(path)
                                }
                            }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            updateState = UpdateState.FAILED
                            polling = false
                        }
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> {
                            // Continue polling
                        }
                    }
                } else {
                    updateState = UpdateState.FAILED
                    polling = false
                }
                cursor?.close()
                if (polling) {
                    kotlinx.coroutines.delay(350.milliseconds)
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(48.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Spacer(modifier = Modifier.height(16.dp))
            var currentGifUrl by remember(gifUrl) { mutableStateOf(gifUrl) }
            var isGifLoading by remember(currentGifUrl) { mutableStateOf(true) }


            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 180.dp)
                    .animateContentSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(currentGifUrl.trim())
                        .crossfade(true)
                        .build(),
                    imageLoader = localImageLoader,
                    contentDescription = "Update Animation",
                    onState = { state ->
                        isGifLoading = state !is coil.compose.AsyncImagePainter.State.Success
                        if (state is coil.compose.AsyncImagePainter.State.Error && currentGifUrl != DEFAULT_GIF_URL) {
                            currentGifUrl = DEFAULT_GIF_URL
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                )

                if (isGifLoading) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .shimmerEffect()
                    )
                }
            }

            // 2. Metadata Row
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SpatialFlow Team",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = tagName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "New Update Available",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Version Check Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = currentVersionName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Update",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = tagName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Dynamic Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                when (updateState) {
                    UpdateState.AVAILABLE -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val density = LocalDensity.current
                            val dividerStroke = remember(density) {
                                androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = with(density) { 3.dp.toPx() },
                                    cap = StrokeCap.Round
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LinearWavyProgressIndicator(
                                    progress = { 1f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    trackColor = Color.Transparent,
                                    stroke = dividerStroke,
                                    trackStroke = dividerStroke,
                                    wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
                                    amplitude = { _ -> with(density) { 3.dp.toPx() } }
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                changelogLines.forEach { line ->
                                    val trimmed = line.trim()
                                    val primaryColor = MaterialTheme.colorScheme.primary
                                    if (trimmed.startsWith("Important:", ignoreCase = true) ||
                                        trimmed.startsWith("Note:", ignoreCase = true) ||
                                        trimmed.startsWith("Warning:", ignoreCase = true)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(primaryColor.copy(alpha = 0.08f))
                                                .drawWithContent {
                                                    drawContent()
                                                    drawRect(
                                                        color = primaryColor,
                                                        topLeft = Offset.Zero,
                                                        size = size.copy(width = 4.dp.toPx())
                                                    )
                                                }
                                                .padding(start = 16.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = trimmed,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 20.sp
                                            )
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = primaryColor,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(
                                                text = if (trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*")) {
                                                    trimmed.substring(1).trim()
                                                } else {
                                                    trimmed
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 20.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = onLaterClick
                                ) {
                                    Text(
                                        text = "Later",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = {
                                        updateState = UpdateState.DOWNLOADING
                                        val id = onUpdateClick(apkUrl)
                                        downloadId = id
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "Update Now",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                    UpdateState.DOWNLOADING -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Downloading update...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            val animatedProgress by animateFloatAsState(
                                targetValue = progress,
                                animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
                                label = "smooth_progress"
                            )

                            val density = LocalDensity.current
                            val thickStroke = remember(density) {
                                androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = with(density) { 6.dp.toPx() },
                                    cap = StrokeCap.Round
                                )
                            }

                            LinearWavyProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                stroke = thickStroke,
                                trackStroke = thickStroke,
                                wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
                                amplitude = { p -> WavyProgressIndicatorDefaults.indicatorAmplitude(p) * 2.5f }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val downloadedMb = bytesDownloaded.toFloat() / (1024 * 1024)
                                val totalMb = bytesTotal.toFloat() / (1024 * 1024)
                                val sizeText = if (bytesTotal > 0) {
                                    String.format("%.1f MB / %.1f MB", downloadedMb, totalMb)
                                } else {
                                    String.format("%.1f MB", downloadedMb)
                                }
                                Text(
                                    text = sizeText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            TextButton(
                                onClick = {
                                    if (downloadId != -1L) {
                                        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                        downloadManager.remove(downloadId)
                                    }
                                    updateState = UpdateState.AVAILABLE
                                    downloadId = -1L
                                    progress = 0f
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    UpdateState.READY_TO_INSTALL -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Download Complete!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { apkPath?.let { onInstallApk(it) } },
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "Install Now",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    UpdateState.FAILED -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Download Failed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { updateState = UpdateState.AVAILABLE },
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "Try Again",
                                        fontWeight = FontWeight.Bold
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


private const val DEFAULT_GIF_URL = "https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExMmdjaDhpeGd4aHhpc280cHFuajBxa3hoenZiOG5sczUybDB2bDh0MCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/TyCVCdmXJHnK3yHjqy/giphy.gif"

private fun parseGifUrl(changelog: String): String? {
    // Look for markdown image syntax: ![alt](url)
    val markdownRegex = """!\[.*?\]\((.*?)\)""".toRegex()
    val match = markdownRegex.find(changelog)
    if (match != null) {
        val url = match.groupValues[1]
        if (url.contains(".gif", ignoreCase = true) || url.contains("giphy.com", ignoreCase = true)) {
            return url
        }
    }

    // Look for html img src syntax: <img src="url" ...>
    val htmlRegex = """<img\s+[^>]*src=["']([^"']+)["']""".toRegex()
    val htmlMatch = htmlRegex.find(changelog)
    if (htmlMatch != null) {
        val url = htmlMatch.groupValues[1]
        if (url.contains(".gif", ignoreCase = true) || url.contains("giphy.com", ignoreCase = true)) {
            return url
        }
    }

    // Look for a raw http/https URL that ends with .gif or matches giphy patterns
    val urlRegex = """https?://\S+""".toRegex()
    val urlMatches = urlRegex.findAll(changelog)
    for (urlMatch in urlMatches) {
        val url = urlMatch.value.trim(')', ']', '>', '"', '\'')
        if (url.contains(".gif", ignoreCase = true) || url.contains("giphy.com", ignoreCase = true)) {
            return url
        }
    }

    return null
}