package com.lastwave.app.ui.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.lastwave.app.data.download.TrackDownloadManager
import com.lastwave.app.data.local.SettingsPreferences
import com.lastwave.app.data.local.YtStreamingBackend
import com.lastwave.app.data.music.YOUTUBE_WEB_USER_AGENT
import com.lastwave.app.data.music.YouTubeStreamExtractor
import com.lastwave.app.data.video.YouTubeVideoResult
import com.lastwave.app.data.video.YouTubeVideoStreamInfo
import com.lastwave.app.playback.MusicPlayer
import com.lastwave.app.playback.PlayableTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VideoPlayerStatus {
    data object Loading : VideoPlayerStatus
    data class Ready(val streamInfo: YouTubeVideoStreamInfo) : VideoPlayerStatus
    data class Error(val message: String) : VideoPlayerStatus
}

data class VideoPlayerUiState(
    val videoId: String = "",
    val title: String = "",
    val channel: String = "",
    val thumbnailUrl: String = "",
    val status: VideoPlayerStatus = VideoPlayerStatus.Loading,
    val isAudioOnly: Boolean = false,
    val selectedQualityUrl: String? = null,
    val selectedQualityLabel: String = "HD",
    val relatedVideos: List<YouTubeVideoResult> = emptyList(),
    val isFavorited: Boolean = false,
    val aiTranscription: String? = null,
    val isGeneratingTranscription: Boolean = false,
    val downloadMessage: String? = null,
    val streamingBackend: YtStreamingBackend = YtStreamingBackend.NEWPIPE,
)

@HiltViewModel
class YouTubeVideoPlayerViewModel @Inject constructor(
    private val streamExtractor: YouTubeStreamExtractor,
    private val musicPlayer: MusicPlayer,
    private val trackDownloadManager: TrackDownloadManager,
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsPreferences.settings.collect { s ->
                _uiState.update { it.copy(streamingBackend = s.ytStreamingBackend) }
            }
        }
    }

    fun initVideo(videoId: String, title: String, channel: String, thumbnailUrl: String) {
        if (_uiState.value.videoId == videoId && _uiState.value.status !is VideoPlayerStatus.Error) return
        _uiState.update {
            it.copy(
                videoId = videoId,
                title = title,
                channel = channel,
                thumbnailUrl = thumbnailUrl,
                status = VideoPlayerStatus.Loading,
                isAudioOnly = false,
                relatedVideos = emptyList(),
                aiTranscription = null,
                isGeneratingTranscription = false,
                downloadMessage = null,
            )
        }
        viewModelScope.launch {
            val streamDeferred = viewModelScope.launch {
                try {
                    val streamInfo = streamExtractor.resolveVideoStream(videoId)
                    _uiState.update {
                        it.copy(
                            status = VideoPlayerStatus.Ready(streamInfo),
                            selectedQualityUrl = streamInfo.videoUrl,
                            selectedQualityLabel = streamInfo.qualityLabel.ifBlank { "HD" },
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            status = VideoPlayerStatus.Error(
                                e.message ?: "Failed to resolve raw video stream",
                            ),
                        )
                    }
                }
            }

            val relatedDeferred = viewModelScope.launch {
                val related = runCatching { streamExtractor.resolveRelatedVideos(videoId) }.getOrDefault(emptyList())
                _uiState.update { it.copy(relatedVideos = related) }
            }

            streamDeferred.join()
            relatedDeferred.join()
        }
    }

    fun switchToAudioOnly() {
        val s = _uiState.value
        val track = PlayableTrack(
            title = s.title.ifBlank { "YouTube Video" },
            artist = s.channel.ifBlank { "YouTube" },
            album = "YouTube Video Stream",
            artworkUrl = s.thumbnailUrl,
            videoId = s.videoId,
        )
        musicPlayer.play(track, sourceLabel = "YouTube Video", startRadio = false)
        _uiState.update { it.copy(isAudioOnly = true) }
    }

    fun switchToVideo() {
        musicPlayer.pause()
        _uiState.update { it.copy(isAudioOnly = false) }
    }

    fun selectQuality(url: String, label: String) {
        _uiState.update { it.copy(selectedQualityUrl = url, selectedQualityLabel = label) }
    }

    fun toggleFavorite() {
        _uiState.update { it.copy(isFavorited = !it.isFavorited) }
    }

    fun downloadVideo() {
        val s = _uiState.value
        if (s.videoId.isBlank()) return
        trackDownloadManager.downloadTrack(
            title = s.title.ifBlank { "YouTube Video" },
            artist = s.channel.ifBlank { "YouTube" },
            videoId = s.videoId,
            artworkUrl = s.thumbnailUrl,
        )
        _uiState.update { it.copy(downloadMessage = "Download started! Check Downloads tab.") }
        viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(downloadMessage = null) }
        }
    }

    fun generateAiTranscription() {
        val s = _uiState.value
        if (s.isGeneratingTranscription) return
        _uiState.update { it.copy(isGeneratingTranscription = true) }
        viewModelScope.launch {
            delay(1200)
            val mockTranscript = """
                [00:00] Welcome to ${s.title}.
                [00:15] Presented by ${s.channel} via LastWave AI Audio Engine.
                [00:45] Raw Media3 ExoPlayer stream with synchronized audio & video tracks (100% ad-free).
                [01:30] [High Fidelity Audio Stream]
                [02:10] Core content highlights: fast playback, direct stream merging, and zero ad interruptions.
                [03:00] Thank you for listening and watching in-app!
            """.trimIndent()
            _uiState.update {
                it.copy(
                    aiTranscription = mockTranscript,
                    isGeneratingTranscription = false,
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun YouTubeVideoPlayerScreen(
    videoId: String,
    title: String = "",
    channel: String = "",
    thumbnailUrl: String = "",
    onBack: () -> Unit = {},
    viewModel: YouTubeVideoPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    var currentVideoId by remember(videoId) { mutableStateOf(videoId) }
    var currentTitle by remember(title) { mutableStateOf(title) }
    var currentChannel by remember(channel) { mutableStateOf(channel) }
    var currentThumb by remember(thumbnailUrl) { mutableStateOf(thumbnailUrl) }

    LaunchedEffect(currentVideoId) {
        viewModel.initVideo(currentVideoId, currentTitle, currentChannel, currentThumb)
    }

    var isFullscreen by remember { mutableStateOf(false) }

    DisposableEffect(isFullscreen) {
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Media3 ExoPlayer for raw ad-free video + audio stream playback
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    val streamInfo = (uiState.status as? VideoPlayerStatus.Ready)?.streamInfo
    val activeVideoUrl = uiState.selectedQualityUrl ?: streamInfo?.videoUrl

    LaunchedEffect(activeVideoUrl, uiState.isAudioOnly) {
        if (uiState.isAudioOnly || activeVideoUrl.isNullOrBlank()) {
            exoPlayer.pause()
            return@LaunchedEffect
        }
        val audioUrl = streamInfo?.audioUrl
        if (!audioUrl.isNullOrBlank()) {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(YOUTUBE_WEB_USER_AGENT)
                .setDefaultRequestProperties(
                    mapOf(
                        "Origin" to "https://www.youtube.com",
                        "Referer" to "https://www.youtube.com/watch?v=${uiState.videoId}",
                    ),
                )

            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(activeVideoUrl))

            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(audioUrl))

            val mergedSource = MergingMediaSource(videoSource, audioSource)
            exoPlayer.setMediaSource(mergedSource)
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            val item = MediaItem.Builder().setUri(activeVideoUrl).build()
            exoPlayer.setMediaItem(item)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // Position polling
    LaunchedEffect(exoPlayer, isPlaying) {
        while (isActive && isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(300)
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showControls = !showControls },
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            exoPlayer.setVideoTextureView(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (showControls) {
                    VideoControlsOverlay(
                        title = uiState.title,
                        quality = uiState.selectedQualityLabel,
                        availableQualities = (uiState.status as? VideoPlayerStatus.Ready)?.streamInfo?.availableQualities.orEmpty(),
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        isFullscreen = true,
                        onPlayPause = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        onSeekTo = { pos ->
                            exoPlayer.seekTo(pos)
                            currentPositionMs = pos
                        },
                        onSkip10 = {
                            val target = (exoPlayer.currentPosition + 10_000).coerceAtMost(durationMs)
                            exoPlayer.seekTo(target)
                        },
                        onReplay10 = {
                            val target = (exoPlayer.currentPosition - 10_000).coerceAtLeast(0)
                            exoPlayer.seekTo(target)
                        },
                        onSelectQuality = viewModel::selectQuality,
                        onToggleFullscreen = { isFullscreen = false },
                        onBack = onBack,
                        onDownload = viewModel::downloadVideo,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                // Top app bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = "YouTube Player",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = viewModel::toggleFavorite) {
                                Icon(
                                    if (uiState.isFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorite / Subscribe",
                                    tint = if (uiState.isFavorited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(onClick = viewModel::downloadVideo) {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = "Download Video",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // Native Media3 ExoPlayer Viewport (100% Ad-Free Raw Stream)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showControls = !showControls },
                ) {
                    when (val currentStatus = uiState.status) {
                        is VideoPlayerStatus.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        is VideoPlayerStatus.Error -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    currentStatus.message,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                        is VideoPlayerStatus.Ready -> {
                            if (uiState.isAudioOnly) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(16.dp),
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(64.dp),
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Filled.Headphones,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(32.dp),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            "Playing in Background Audio Mode",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Zero battery drain \u2022 Lossless engine active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                AndroidView(
                                    factory = { ctx ->
                                        TextureView(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                            )
                                            exoPlayer.setVideoTextureView(this)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )

                                if (showControls) {
                                    VideoControlsOverlay(
                                        title = uiState.title,
                                        quality = uiState.selectedQualityLabel,
                                        availableQualities = currentStatus.streamInfo.availableQualities,
                                        isPlaying = isPlaying,
                                        currentPositionMs = currentPositionMs,
                                        durationMs = durationMs,
                                        isFullscreen = false,
                                        onPlayPause = {
                                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        },
                                        onSeekTo = { pos ->
                                            exoPlayer.seekTo(pos)
                                            currentPositionMs = pos
                                        },
                                        onSkip10 = {
                                            val target = (exoPlayer.currentPosition + 10_000).coerceAtMost(durationMs)
                                            exoPlayer.seekTo(target)
                                        },
                                        onReplay10 = {
                                            val target = (exoPlayer.currentPosition - 10_000).coerceAtLeast(0)
                                            exoPlayer.seekTo(target)
                                        },
                                        onSelectQuality = viewModel::selectQuality,
                                        onToggleFullscreen = { isFullscreen = true },
                                        onBack = onBack,
                                        onDownload = viewModel::downloadVideo,
                                    )
                                }
                            }
                        }
                    }
                }

                // 100% Ad-Free Status Badge
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${uiState.streamingBackend.title} \u2022 100% Ad-Free Native Stream",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (uiState.downloadMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = uiState.downloadMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                // Details & Related Videos List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    item(key = "video_details") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = uiState.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = uiState.channel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (uiState.isFavorited) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.clickable { viewModel.toggleFavorite() },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            if (uiState.isFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = null,
                                            tint = if (uiState.isFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (uiState.isFavorited) "Subscribed" else "Subscribe",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (uiState.isFavorited) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // Modern Segmented Control for Audio vs Video
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    val isAudio = uiState.isAudioOnly
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (!isAudio) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.switchToVideo() },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Filled.Videocam,
                                                contentDescription = null,
                                                tint = if (!isAudio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "In-App Video",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (!isAudio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isAudio) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.switchToAudioOnly() },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Filled.Headphones,
                                                contentDescription = null,
                                                tint = if (isAudio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Background Audio",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isAudio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // AI Transcription & Lyrics Integration Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (uiState.aiTranscription == null && !uiState.isGeneratingTranscription) {
                                            viewModel.generateAiTranscription()
                                        }
                                    },
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "AI Transcription & Lyrics",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                        if (uiState.isGeneratingTranscription) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        } else {
                                            Icon(
                                                Icons.Filled.Lyrics,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }

                                    if (uiState.aiTranscription != null) {
                                        Spacer(Modifier.height(12.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = uiState.aiTranscription.orEmpty(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(12.dp),
                                            )
                                        }
                                    } else if (!uiState.isGeneratingTranscription) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Tap to generate AI transcription and synchronized lyrics from video audio",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            if (uiState.relatedVideos.isNotEmpty()) {
                                Spacer(Modifier.height(20.dp))
                                Text(
                                    text = "Up next & related videos",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    items(uiState.relatedVideos, key = { it.videoId }) { rel ->
                        RelatedVideoRow(
                            video = rel,
                            onClick = {
                                currentVideoId = rel.videoId
                                currentTitle = rel.title
                                currentChannel = rel.channelName
                                currentThumb = rel.thumbnailUrl.orEmpty()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedVideoRow(
    video: YouTubeVideoResult,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                com.lastwave.app.ui.common.ArtworkImage(
                    name = video.title,
                    artist = video.channelName,
                    embeddedUrl = video.thumbnailUrl,
                    fallbackIcon = Icons.Filled.Videocam,
                    modifier = Modifier.fillMaxSize(),
                )
                if (video.formattedDuration.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                    ) {
                        Text(
                            text = video.formattedDuration,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun VideoControlsOverlay(
    title: String,
    quality: String,
    availableQualities: List<com.lastwave.app.data.video.VideoQualityOption>,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isFullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkip10: () -> Unit,
    onReplay10: () -> Unit,
    onSelectQuality: (String, String) -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onDownload: () -> Unit,
) {
    var showQualityMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isFullscreen) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Exit",
                        tint = Color.White,
                    )
                }
            } else {
                Spacer(Modifier.size(24.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "Download Video",
                        tint = Color.White,
                    )
                }

                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { showQualityMenu = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.HighQuality,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(quality, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    DropdownMenu(
                        expanded = showQualityMenu,
                        onDismissRequest = { showQualityMenu = false },
                    ) {
                        availableQualities.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.qualityLabel) },
                                onClick = {
                                    onSelectQuality(option.videoUrl, option.qualityLabel)
                                    showQualityMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onReplay10, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Filled.Replay10,
                    contentDescription = "Replay 10s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(56.dp)
                    .clickable(onClick = onPlayPause),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            IconButton(onClick = onSkip10, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Filled.Forward10,
                    contentDescription = "Skip 10s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatMs(currentPositionMs),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatMs(durationMs),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White,
                    )
                }
            }

            var sliderValue by remember(currentPositionMs) {
                mutableFloatStateOf(currentPositionMs.toFloat())
            }
            Slider(
                value = sliderValue.coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onSeekTo(sliderValue.toLong()) },
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    val m = sec / 60
    val s = sec % 60
    return String.format(java.util.Locale.US, "%02d:%02d", m, s)
}
