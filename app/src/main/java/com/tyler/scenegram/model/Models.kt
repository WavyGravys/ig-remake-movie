package com.tyler.scenegram.model

/**
 * Pure Kotlin content models. Android resource lookup and playback stay in the UI layer;
 * [MediaAsset.key] is resolved there to a bundled drawable/raw resource.
 */
enum class MediaKind {
    IMAGE,
    VIDEO,
    AUDIO,
}

data class MediaAsset(
    val key: String,
    val kind: MediaKind,
) {
    init {
        require(key.isNotBlank()) { "Media asset keys cannot be blank" }
    }
}

data class Profile(
    val id: String,
    val handle: String,
    val displayName: String,
    val avatar: MediaAsset,
) {
    init {
        require(id.isNotBlank()) { "Profile ids cannot be blank" }
        require(handle.isNotBlank()) { "Profile handles cannot be blank" }
        require(avatar.kind == MediaKind.IMAGE) { "Profile avatars must be images" }
    }
}

data class FeedPost(
    val id: String,
    val authorId: String,
    val media: List<MediaAsset>,
    val caption: String,
    val likeCountLabel: String,
    val commentPreview: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Feed post ids cannot be blank" }
        require(authorId.isNotBlank()) { "Feed posts need an author" }
        require(media.isNotEmpty()) { "Feed posts need at least one media item" }
        require(media.all { it.kind == MediaKind.IMAGE }) {
            "The placeholder feed supports image posts and image carousels only"
        }
    }

    val isCarousel: Boolean
        get() = media.size > 1
}

data class Reel(
    val id: String,
    val authorId: String,
    val video: MediaAsset,
    val poster: MediaAsset,
    val caption: String,
    val audioLabel: String,
) {
    init {
        require(id.isNotBlank()) { "Reel ids cannot be blank" }
        require(authorId.isNotBlank()) { "Reels need an author" }
        require(video.kind == MediaKind.VIDEO) { "Reel media must be video" }
        require(poster.kind == MediaKind.IMAGE) { "Reel posters must be images" }
    }
}

enum class MessageDirection {
    INCOMING,
    OUTGOING,
}

sealed class MessageBody {
    data class Text(val value: String) : MessageBody() {
        init {
            require(value.isNotBlank()) { "Text messages cannot be blank" }
        }
    }

    data class Voice(
        val audio: MediaAsset,
        val durationMs: Long,
    ) : MessageBody() {
        init {
            require(audio.kind == MediaKind.AUDIO) { "Voice messages need an audio asset" }
            require(durationMs > 0L) { "Voice messages need a positive duration" }
        }
    }
}

data class ChatMessage(
    val id: String,
    val senderProfileId: String,
    val direction: MessageDirection,
    val body: MessageBody,
    val displayTime: String,
) {
    init {
        require(id.isNotBlank()) { "Message ids cannot be blank" }
        require(senderProfileId.isNotBlank()) { "Messages need a sender" }
    }
}

data class ConversationThread(
    val id: String,
    val participantProfileId: String,
    val initialMessages: List<ChatMessage>,
) {
    init {
        require(id.isNotBlank()) { "Thread ids cannot be blank" }
        require(participantProfileId.isNotBlank()) { "Threads need a participant" }
        require(initialMessages.map { it.id }.distinct().size == initialMessages.size) {
            "Initial message ids must be unique within a thread"
        }
    }
}

enum class AppDestination {
    FEED,
    REELS,
    INBOX,
    CHAT,
    SETTINGS,
}

/** An event can be timed from scene start or from a named UI signal. */
sealed class CueAnchor {
    object SceneStart : CueAnchor()

    data class Signal(val key: String) : CueAnchor() {
        init {
            require(key.isNotBlank()) { "Signal keys cannot be blank" }
        }
    }
}

data class LocalNotificationSpec(
    val title: String,
    val body: String,
    // Kept as data rather than an Android constant; it matches the app's pre-created channel.
    val channelId: String = "scene_messages",
) {
    init {
        require(title.isNotBlank()) { "Notification titles cannot be blank" }
        require(body.isNotBlank()) { "Notification bodies cannot be blank" }
        require(channelId.isNotBlank()) { "Notification channel ids cannot be blank" }
    }
}

sealed class CueAction {
    data class DeliverMessage(
        val threadId: String,
        val message: ChatMessage,
    ) : CueAction() {
        init {
            require(threadId.isNotBlank()) { "Message delivery needs a thread id" }
        }
    }
}

data class SceneCue(
    val id: String,
    val anchor: CueAnchor,
    val delayMs: Long,
    val action: CueAction,
    val notification: LocalNotificationSpec? = null,
) {
    init {
        require(id.isNotBlank()) { "Cue ids cannot be blank" }
        require(delayMs >= 0L) { "Cue delays cannot be negative" }
    }
}

data class ScenePreset(
    val id: String,
    val title: String,
    val startDestination: AppDestination,
    val startThreadId: String? = null,
    val cues: List<SceneCue> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "Scene preset ids cannot be blank" }
        require(title.isNotBlank()) { "Scene preset titles cannot be blank" }
        require(startDestination != AppDestination.CHAT || !startThreadId.isNullOrBlank()) {
            "Chat scene presets need a start thread id"
        }
        require(cues.map { it.id }.distinct().size == cues.size) {
            "Cue ids must be unique within a scene preset"
        }
    }
}

data class ShootContent(
    val profiles: List<Profile>,
    val feedPosts: List<FeedPost>,
    val reels: List<Reel>,
    val threads: List<ConversationThread>,
    val scenePresets: List<ScenePreset>,
) {
    init {
        requireUnique("profile", profiles.map { it.id })
        requireUnique("feed post", feedPosts.map { it.id })
        requireUnique("reel", reels.map { it.id })
        requireUnique("thread", threads.map { it.id })
        requireUnique("scene preset", scenePresets.map { it.id })
    }

    private fun requireUnique(label: String, ids: List<String>) {
        require(ids.distinct().size == ids.size) { "Duplicate $label ids are not allowed" }
    }
}
