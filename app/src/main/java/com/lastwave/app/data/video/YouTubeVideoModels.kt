package com.lastwave.app.data.video

import androidx.compose.runtime.Immutable

@Immutable
data class YouTubeVideoResult(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelAvatarUrl: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val viewCount: Long? = null,
    val uploadDateText: String? = null,
) {
    val formattedDuration: String
        get() {
            val sec = durationSeconds ?: return ""
            if (sec <= 0) return ""
            val m = sec / 60
            val s = sec % 60
            val h = m / 60
            return if (h > 0) {
                String.format(java.util.Locale.US, "%d:%02d:%02d", h, m % 60, s)
            } else {
                String.format(java.util.Locale.US, "%d:%02d", m, s)
            }
        }
}

@Immutable
data class VideoQualityOption(
    val qualityLabel: String,
    val videoUrl: String,
    val height: Int,
)

@Immutable
data class YouTubeVideoStreamInfo(
    val videoId: String,
    val videoUrl: String,
    val mimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val qualityLabel: String = "",
    val audioUrl: String? = null,
    val audioMimeType: String? = null,
    val availableQualities: List<VideoQualityOption> = emptyList(),
)
