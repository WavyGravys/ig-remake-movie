package com.tyler.scenegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScriptTimelineTest {
    @Test
    fun `scene cues are ordered by due time then declaration order`() {
        val preset = preset(
            cue("second-at-same-time", CueAnchor.SceneStart, 2_000L),
            cue("first-due", CueAnchor.SceneStart, 500L),
            cue("third-at-same-time", CueAnchor.SceneStart, 2_000L),
        )

        val scheduled = ScriptTimeline.scheduledCues(
            preset = preset,
            anchors = ScriptAnchors(sceneStartedAtMs = 10_000L),
        )

        assertEquals(
            listOf("first-due", "second-at-same-time", "third-at-same-time"),
            scheduled.map { it.cue.id },
        )
        assertEquals(listOf(10_500L, 12_000L, 12_000L), scheduled.map { it.dueAtMs })
    }

    @Test
    fun `signal cue remains unresolved until its signal is recorded`() {
        val preset = preset(cue("reply", CueAnchor.Signal("actor.sent"), 1_200L))

        assertEquals(emptyList<ScheduledCue>(), ScriptTimeline.scheduledCues(preset, ScriptAnchors()))

        val scheduled = ScriptTimeline.scheduledCues(
            preset,
            ScriptAnchors().withSignal("actor.sent", 25_000L),
        )

        assertEquals(26_200L, scheduled.single().dueAtMs)
    }

    @Test
    fun `recording the same signal twice keeps its first timestamp`() {
        val anchors = ScriptAnchors()
            .withSignal("actor.sent", 1_000L)
            .withSignal("actor.sent", 9_000L)

        assertEquals(1_000L, anchors.signalTimesMs.getValue("actor.sent"))
    }

    @Test
    fun `due cues exclude future and already delivered cues`() {
        val preset = preset(
            cue("already-delivered", CueAnchor.SceneStart, 100L),
            cue("due-now", CueAnchor.SceneStart, 500L),
            cue("future", CueAnchor.SceneStart, 501L),
        )

        val due = ScriptTimeline.dueCues(
            preset = preset,
            anchors = ScriptAnchors(sceneStartedAtMs = 1_000L),
            nowMs = 1_500L,
            deliveredCueIds = setOf("already-delivered"),
        )

        assertEquals(listOf("due-now"), due.map { it.cue.id })
    }

    @Test
    fun `next pending skips delivered cues`() {
        val preset = preset(
            cue("one", CueAnchor.SceneStart, 100L),
            cue("two", CueAnchor.SceneStart, 200L),
        )
        val anchors = ScriptAnchors(sceneStartedAtMs = 5_000L)

        assertEquals(
            "two",
            ScriptTimeline.nextPendingCue(preset, anchors, setOf("one"))?.cue?.id,
        )
        assertNull(ScriptTimeline.nextPendingCue(preset, anchors, setOf("one", "two")))
    }

    @Test
    fun `placeholder content covers every filmed interaction`() {
        val content = PlaceholderContent.shootContent
        val chatScene = content.scenePresets.single { it.id == "scene_chat" }
        val deliveredBodies = chatScene.cues.map { (it.action as CueAction.DeliverMessage).message.body }

        assertEquals(true, content.feedPosts.any { it.isCarousel })
        assertEquals(true, content.reels.isNotEmpty())
        assertEquals(true, deliveredBodies.any { it is MessageBody.Text })
        assertEquals(true, deliveredBodies.any { it is MessageBody.Voice })
        assertEquals(true, chatScene.cues.all { it.notification != null })
        assertEquals(
            AppDestination.SETTINGS,
            content.scenePresets.single { it.id == "scene_fake_uninstall" }.startDestination,
        )
    }

    private fun preset(vararg cues: SceneCue) = ScenePreset(
        id = "test-scene",
        title = "Test scene",
        startDestination = AppDestination.FEED,
        cues = cues.toList(),
    )

    private fun cue(id: String, anchor: CueAnchor, delayMs: Long) = SceneCue(
        id = id,
        anchor = anchor,
        delayMs = delayMs,
        action = CueAction.DeliverMessage(
            threadId = "test-thread",
            message = ChatMessage(
                id = "message-$id",
                senderProfileId = "test-contact",
                direction = MessageDirection.INCOMING,
                body = MessageBody.Text("Message $id"),
                displayTime = "now",
            ),
        ),
    )
}
