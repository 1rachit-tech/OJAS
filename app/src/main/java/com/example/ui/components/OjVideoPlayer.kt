package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage

/**
 * Robust vertical video player for OJ feed.
 * Utilizes native MediaPlayer + TextureView with proper aspect ratio scaling,
 * automatic looping, lifecycle awareness, and memory leak prevention.
 */
@Composable
fun OjVideoPlayer(
    videoUrl: String,
    thumbnailUrl: String?,
    isActive: Boolean,
    isUserPaused: Boolean,
    modifier: Modifier = Modifier,
    onPlaybackProgress: ((currentPositionMs: Long, durationMs: Long, isPlaying: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPrepared by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentIsActive by rememberUpdatedState(isActive)
    val currentIsUserPaused by rememberUpdatedState(isUserPaused)
    val currentOnPlaybackProgress by rememberUpdatedState(onPlaybackProgress)

    // Reference to active MediaPlayer
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    // Active playback progress tracking for real view analytics (only while active, progressing, and not paused/buffering)
    LaunchedEffect(isActive, isUserPaused, isPrepared, isBuffering, hasError) {
        if (isActive && !isUserPaused && isPrepared && !isBuffering && !hasError) {
            while (isActive && !isUserPaused && isPrepared && !isBuffering && !hasError) {
                val player = mediaPlayerRef
                if (player != null) {
                    try {
                        if (player.isPlaying) {
                            val currentPos = player.currentPosition.toLong()
                            val totalDur = player.duration.toLong()
                            currentOnPlaybackProgress?.invoke(currentPos, totalDur, true)
                        }
                    } catch (_: Exception) {
                    }
                }
                kotlinx.coroutines.delay(com.example.data.model.OjWatchAnalyticsConfig.PROGRESS_POLL_INTERVAL_MS)
            }
        }
    }

    // Synchronize play / pause state based on page active status and user pause toggle
    LaunchedEffect(isActive, isUserPaused, isPrepared) {
        val player = mediaPlayerRef ?: return@LaunchedEffect
        if (isPrepared && !hasError) {
            try {
                if (isActive && !isUserPaused) {
                    if (!player.isPlaying) {
                        player.start()
                    }
                } else {
                    if (player.isPlaying) {
                        player.pause()
                    }
                }
            } catch (_: Exception) {
                // Ignore transient state changes
            }
        }
    }

    // Lifecycle observer to pause playback when app leaves foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val player = mediaPlayerRef ?: return@LifecycleEventObserver
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                try {
                    if (player.isPlaying) {
                        player.pause()
                    }
                } catch (_: Exception) {
                }
            } else if (event == Lifecycle.Event.ON_RESUME) {
                try {
                    if (currentIsActive && !currentIsUserPaused && isPrepared && !hasError) {
                        player.start()
                    }
                } catch (_: Exception) {
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // 1. Thumbnail Background / Preview Poster
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. TextureView Video Surface
        if (!hasError && videoUrl.isNotBlank()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                                val surface = Surface(surfaceTexture)
                                try {
                                    val player = MediaPlayer().apply {
                                        setSurface(surface)
                                        isLooping = true

                                        // Set data source safely
                                        val uri = Uri.parse(videoUrl)
                                        if (videoUrl.startsWith("content://") || videoUrl.startsWith("file://") || videoUrl.startsWith("android.resource://")) {
                                            setDataSource(ctx, uri)
                                        } else if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                                            setDataSource(videoUrl)
                                        } else {
                                            // Handle relative or storage URI
                                            try {
                                                setDataSource(ctx, uri)
                                            } catch (_: Exception) {
                                                setDataSource(videoUrl)
                                            }
                                        }

                                        setOnPreparedListener { mp ->
                                            isPrepared = true
                                            isBuffering = false
                                            hasError = false

                                            // Apply 9:16 aspect ratio fit/crop matrix to avoid stretching
                                            val videoWidth = mp.videoWidth.toFloat()
                                            val videoHeight = mp.videoHeight.toFloat()
                                            if (videoWidth > 0 && videoHeight > 0 && width > 0 && height > 0) {
                                                val viewRatio = width.toFloat() / height.toFloat()
                                                val videoRatio = videoWidth / videoHeight
                                                val matrix = Matrix()

                                                val scaleX: Float
                                                val scaleY: Float
                                                if (videoRatio > viewRatio) {
                                                    scaleX = videoRatio / viewRatio
                                                    scaleY = 1.0f
                                                } else {
                                                    scaleX = 1.0f
                                                    scaleY = viewRatio / videoRatio
                                                }

                                                matrix.setScale(scaleX, scaleY, width / 2f, height / 2f)
                                                setTransform(matrix)
                                            }

                                            if (currentIsActive && !currentIsUserPaused) {
                                                mp.start()
                                            }
                                        }

                                        setOnInfoListener { _, what, _ ->
                                            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                                                isBuffering = true
                                            } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                                isBuffering = false
                                            }
                                            false
                                        }

                                        setOnErrorListener { _, what, extra ->
                                            isBuffering = false
                                            hasError = true
                                            errorMessage = "Video playback failed ($what, $extra)"
                                            true
                                        }

                                        prepareAsync()
                                    }
                                    mediaPlayerRef = player
                                } catch (e: Exception) {
                                    hasError = true
                                    isBuffering = false
                                    errorMessage = e.message ?: "Unable to play video"
                                }
                            }

                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                mediaPlayerRef?.let { player ->
                                    try {
                                        player.stop()
                                        player.reset()
                                        player.release()
                                    } catch (_: Exception) {
                                    }
                                }
                                mediaPlayerRef = null
                                surface.release()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }
                    }
                }
            )
        }

        // Cleanup on composable disposal
        DisposableEffect(Unit) {
            onDispose {
                mediaPlayerRef?.let { player ->
                    try {
                        player.stop()
                        player.reset()
                        player.release()
                    } catch (_: Exception) {
                    }
                }
                mediaPlayerRef = null
            }
        }

        // 3. Subtle Buffering Indicator
        if (isBuffering && !hasError && isActive) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 2.5.dp
            )
        }

        // 4. Subtle Play/Pause Overlay Indicator
        AnimatedVisibility(
            visible = isUserPaused && isActive && !hasError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Paused",
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // 5. Clean Error / Unplayable Fallback
        if (hasError && isActive) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideocamOff,
                    contentDescription = "Playback Error",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}
