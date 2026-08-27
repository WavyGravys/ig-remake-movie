package com.tyler.scenegram.director

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MomentContentTest {
    @Test
    fun `handles are derived from display names`() {
        assertEquals("@alex.morgan", momentHandle("Alex Morgan"))
        assertEquals("@nina.park", momentHandle("  Nina---Park  "))
        assertEquals("@user", momentHandle("   "))
    }

    @Test
    fun `image posts may contain a carousel`() {
        val post = CustomPost(
            id = "post",
            placement = PostPlacement.REELS,
            mediaType = PostMediaType.IMAGES,
            mediaPaths = listOf("one.jpg", "two.jpg"),
            profileName = "Alex Morgan",
            profileImagePath = null,
            caption = "Weekend",
            likes = 12,
            comments = 3,
        )

        assertEquals("@alex.morgan", post.handle)
        assertEquals(2, post.mediaPaths.size)
    }

    @Test
    fun `video posts reject multiple files`() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomPost(
                id = "post",
                placement = PostPlacement.SEARCH_RESULTS,
                mediaType = PostMediaType.VIDEO,
                mediaPaths = listOf("one.mp4", "two.mp4"),
                profileName = "Alex Morgan",
                profileImagePath = null,
                caption = "Video",
                likes = 0,
                comments = 0,
            )
        }
    }

    @Test
    fun `chat scenes retain ordered reply steps`() {
        val scene = SavedChatScene(
            id = "scene",
            title = "Arrival",
            chatId = MomentDefaults.ALEX_CHAT_ID,
            initialMessage = "Are you close?",
            initialDelaySeconds = 2,
            steps = listOf(
                ChatSceneStep("Call when you arrive", 3),
                ChatSceneStep(
                    replyText = "Coming down",
                    delaySeconds = 1,
                    replyKind = SavedMessageKind.VOICE,
                    replyDurationSeconds = 6,
                ),
            ),
        )

        assertEquals(listOf("Call when you arrive", "Coming down"), scene.steps.map { it.replyText })
        assertEquals(listOf(3, 1), scene.steps.map { it.delaySeconds })
        assertEquals(SavedMessageKind.VOICE, scene.steps.last().replyKind)
        assertEquals(6, scene.steps.last().replyDurationSeconds)
    }

    @Test
    fun `voice messages require and retain a playback duration`() {
        val message = SavedChatMessage(
            id = "voice",
            side = ChatSide.CONTACT,
            text = "Meet me outside",
            kind = SavedMessageKind.VOICE,
            durationSeconds = 7,
        )

        assertEquals(7, message.durationSeconds)
        assertThrows(IllegalArgumentException::class.java) {
            message.copy(durationSeconds = 0)
        }
    }
}
