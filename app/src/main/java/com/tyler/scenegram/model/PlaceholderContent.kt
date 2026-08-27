package com.tyler.scenegram.model

/**
 * One deliberately small, editable content pack for the placeholder build.
 * Replace asset keys and copy the corresponding files into Android resources when shoot media arrives.
 */
object PlaceholderContent {
    const val ACTOR_PROFILE_ID = "profile_actor"
    const val CONTACT_PROFILE_ID = "profile_contact"
    const val CHAT_THREAD_ID = "thread_contact"
    const val ACTOR_REPLY_SIGNAL = "chat.actor_reply_sent"

    private val actorAvatar = image("avatar_actor")
    private val contactAvatar = image("avatar_contact")
    private val travelAvatar = image("avatar_travel")

    val profiles = listOf(
        Profile(
            id = ACTOR_PROFILE_ID,
            handle = "mara.lines",
            displayName = "Mara",
            avatar = actorAvatar,
        ),
        Profile(
            id = CONTACT_PROFILE_ID,
            handle = "nina.park",
            displayName = "Nina Park",
            avatar = contactAvatar,
        ),
        Profile(
            id = "profile_travel",
            handle = "northbound",
            displayName = "Northbound",
            avatar = travelAvatar,
        ),
    )

    val feedPosts = listOf(
        FeedPost(
            id = "post_city",
            authorId = "profile_travel",
            media = listOf(image("feed_city_evening")),
            caption = "Last light in the city.",
            likeCountLabel = "1,248 likes",
            commentPreview = "View all 23 comments",
        ),
        FeedPost(
            id = "post_weekend_carousel",
            authorId = CONTACT_PROFILE_ID,
            media = listOf(
                image("feed_weekend_01"),
                image("feed_weekend_02"),
                image("feed_weekend_03"),
            ),
            caption = "A few frames from the weekend.",
            likeCountLabel = "387 likes",
            commentPreview = "View all 8 comments",
        ),
        FeedPost(
            id = "post_coast_carousel",
            authorId = "profile_travel",
            media = listOf(
                image("feed_coast_01"),
                image("feed_coast_02"),
            ),
            caption = "Keep swiping →",
            likeCountLabel = "2,031 likes",
        ),
    )

    val reels = listOf(
        Reel(
            id = "reel_train",
            authorId = "profile_travel",
            video = video("reel_train_window"),
            poster = image("reel_train_window_poster"),
            caption = "Somewhere between here and there.",
            audioLabel = "Original audio",
        ),
        Reel(
            id = "reel_kitchen",
            authorId = CONTACT_PROFILE_ID,
            video = video("reel_kitchen"),
            poster = image("reel_kitchen_poster"),
            caption = "Five minutes, one pan.",
            audioLabel = "Nina Park · Original audio",
        ),
        Reel(
            id = "reel_night_drive",
            authorId = "profile_travel",
            video = video("reel_night_drive"),
            poster = image("reel_night_drive_poster"),
            caption = "Night drive.",
            audioLabel = "Midnight Loop",
        ),
    )

    private val chatHistory = listOf(
        ChatMessage(
            id = "message_history_01",
            senderProfileId = CONTACT_PROFILE_ID,
            direction = MessageDirection.INCOMING,
            body = MessageBody.Text("Are you still coming by later?"),
            displayTime = "18:41",
        ),
        ChatMessage(
            id = "message_history_02",
            senderProfileId = ACTOR_PROFILE_ID,
            direction = MessageDirection.OUTGOING,
            body = MessageBody.Text("Yes, I should be there after eight."),
            displayTime = "18:42",
        ),
    )

    val threads = listOf(
        ConversationThread(
            id = CHAT_THREAD_ID,
            participantProfileId = CONTACT_PROFILE_ID,
            initialMessages = chatHistory,
        ),
    )

    private val scriptedText = ChatMessage(
        id = "message_scripted_text",
        senderProfileId = CONTACT_PROFILE_ID,
        direction = MessageDirection.INCOMING,
        body = MessageBody.Text("Okay. Listen to this before you leave."),
        displayTime = "now",
    )

    private val scriptedVoice = ChatMessage(
        id = "message_scripted_voice",
        senderProfileId = CONTACT_PROFILE_ID,
        direction = MessageDirection.INCOMING,
        body = MessageBody.Voice(
            audio = audio("voice_message_nina_01"),
            durationMs = 8_400L,
        ),
        displayTime = "now",
    )

    val scenePresets = listOf(
        ScenePreset(
            id = "scene_chat",
            title = "Chat and voice note",
            startDestination = AppDestination.CHAT,
            startThreadId = CHAT_THREAD_ID,
            cues = listOf(
                SceneCue(
                    id = "cue_incoming_text",
                    anchor = CueAnchor.Signal(ACTOR_REPLY_SIGNAL),
                    delayMs = 1_200L,
                    action = CueAction.DeliverMessage(CHAT_THREAD_ID, scriptedText),
                    notification = LocalNotificationSpec(
                        title = "Nina Park",
                        body = "Okay. Listen to this before you leave.",
                    ),
                ),
                SceneCue(
                    id = "cue_incoming_voice",
                    anchor = CueAnchor.Signal(ACTOR_REPLY_SIGNAL),
                    delayMs = 4_200L,
                    action = CueAction.DeliverMessage(CHAT_THREAD_ID, scriptedVoice),
                    notification = LocalNotificationSpec(
                        title = "Nina Park",
                        body = "Voice message",
                    ),
                ),
            ),
        ),
        ScenePreset(
            id = "scene_feed_reels",
            title = "Feed, carousel and reels",
            startDestination = AppDestination.FEED,
        ),
        ScenePreset(
            id = "scene_fake_uninstall",
            title = "Fake uninstall and blackout",
            startDestination = AppDestination.SETTINGS,
        ),
    )

    val shootContent = ShootContent(
        profiles = profiles,
        feedPosts = feedPosts,
        reels = reels,
        threads = threads,
        scenePresets = scenePresets,
    )

    private fun image(key: String) = MediaAsset(key, MediaKind.IMAGE)

    private fun video(key: String) = MediaAsset(key, MediaKind.VIDEO)

    private fun audio(key: String) = MediaAsset(key, MediaKind.AUDIO)
}
