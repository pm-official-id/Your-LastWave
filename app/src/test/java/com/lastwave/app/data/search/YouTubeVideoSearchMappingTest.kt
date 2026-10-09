package com.lastwave.app.data.search

import com.google.common.truth.Truth.assertThat
import com.lastwave.app.data.video.YouTubeVideoResult
import org.junit.Test

class YouTubeVideoSearchMappingTest {

    @Test
    fun testYouTubeVideoResult_formattedDuration() {
        val shortVideo = YouTubeVideoResult(
            videoId = "vid1",
            title = "Short Video",
            channelName = "Channel",
            durationSeconds = 45L,
        )
        assertThat(shortVideo.formattedDuration).isEqualTo("0:45")

        val standardVideo = YouTubeVideoResult(
            videoId = "vid2",
            title = "Standard Video",
            channelName = "Channel",
            durationSeconds = 504L, // 8 min 24 sec
        )
        assertThat(standardVideo.formattedDuration).isEqualTo("8:24")

        val longVideo = YouTubeVideoResult(
            videoId = "vid3",
            title = "Long Video",
            channelName = "Channel",
            durationSeconds = 3665L, // 1 hr 1 min 5 sec
        )
        assertThat(longVideo.formattedDuration).isEqualTo("1:01:05")
    }

    @Test
    fun testSearchResultItem_fromVideo() {
        val item = SearchResultItem(
            name = "Hans Zimmer - Interstellar Suite",
            artist = "Hans Zimmer",
            artworkUrl = "https://example.com/thumb.jpg",
            subtitle = "Hans Zimmer \u2022 48M views \u2022 6 years ago",
            videoId = "dQw4w9WgXcQ",
            entityId = "dQw4w9WgXcQ",
            durationSeconds = 504L,
            isVideo = true,
        )

        assertThat(item.isVideo).isTrue()
        assertThat(item.videoId).isEqualTo("dQw4w9WgXcQ")
        assertThat(item.name).isEqualTo("Hans Zimmer - Interstellar Suite")
        assertThat(item.durationSeconds).isEqualTo(504L)
    }

    @Test
    fun testSearchTab_containsVideos() {
        val tabs = SearchTab.entries
        assertThat(tabs).contains(SearchTab.VIDEOS)
        assertThat(tabs).contains(SearchTab.TRACKS)
    }
}
