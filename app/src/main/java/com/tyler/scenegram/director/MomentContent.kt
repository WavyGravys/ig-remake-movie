package com.tyler.scenegram.director

import java.util.Locale

enum class PostPlacement {
    REELS,
    SEARCH_DEFAULT,
    SEARCH_RESULTS,
}

enum class PostMediaType {
    VIDEO,
    IMAGES,
}

data class CustomPost(
    val id: String,
    val placement: PostPlacement,
    val mediaType: PostMediaType,
    val mediaPaths: List<String>,
    val profileName: String,
    val profileImagePath: String?,
    val caption: String,
    val likes: Int,
    val comments: Int,
) {
    init {
        require(id.isNotBlank()) { "Post ids cannot be blank" }
        require(mediaPaths.isNotEmpty()) { "A post needs media" }
        require(mediaType != PostMediaType.VIDEO || mediaPaths.size == 1) {
            "A video post can contain only one video"
        }
        require(profileName.isNotBlank()) { "A post needs a profile name" }
        require(likes >= 0) { "Likes cannot be negative" }
        require(comments >= 0) { "Comments cannot be negative" }
    }

    val handle: String
        get() = momentHandle(profileName)
}

enum class ChatSide {
    ACTOR,
    CONTACT,
}

enum class SavedMessageKind {
    TEXT,
    VOICE,
}

data class SavedChatMessage(
    val id: String,
    val side: ChatSide,
    val text: String,
    val kind: SavedMessageKind = SavedMessageKind.TEXT,
    val durationSeconds: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "Message ids cannot be blank" }
        require(text.isNotBlank()) { "Message text cannot be blank" }
        require(kind != SavedMessageKind.VOICE || durationSeconds in 1..120) {
            "Voice-message durations must be between 1 and 120 seconds"
        }
    }
}

data class SavedChat(
    val id: String,
    val profileName: String,
    val profileImagePath: String?,
    val initialMessages: List<SavedChatMessage>,
) {
    init {
        require(id.isNotBlank()) { "Chat ids cannot be blank" }
        require(profileName.isNotBlank()) { "A chat needs a profile name" }
    }

    val handle: String
        get() = momentHandle(profileName)
}

data class ChatSceneStep(
    val replyText: String,
    val delaySeconds: Int,
    val replyKind: SavedMessageKind = SavedMessageKind.TEXT,
    val replyDurationSeconds: Int = 0,
) {
    init {
        require(replyText.isNotBlank()) { "Scene replies cannot be blank" }
        require(delaySeconds in 0..120) { "Scene delays must be between 0 and 120 seconds" }
        require(replyKind != SavedMessageKind.VOICE || replyDurationSeconds in 1..120) {
            "Voice-reply durations must be between 1 and 120 seconds"
        }
    }
}

data class SavedChatScene(
    val id: String,
    val title: String,
    val chatId: String,
    val initialMessage: String?,
    val initialDelaySeconds: Int,
    val steps: List<ChatSceneStep>,
    val initialMessageKind: SavedMessageKind = SavedMessageKind.TEXT,
    val initialVoiceDurationSeconds: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "Scene ids cannot be blank" }
        require(title.isNotBlank()) { "A scene needs a title" }
        require(chatId.isNotBlank()) { "A scene needs a chat" }
        require(initialDelaySeconds in 0..120) { "Initial delay must be between 0 and 120 seconds" }
        require(steps.isNotEmpty()) { "A scene needs at least one reply step" }
        require(
            initialMessage.isNullOrBlank() ||
                initialMessageKind != SavedMessageKind.VOICE ||
                initialVoiceDurationSeconds in 1..120,
        ) { "Initial voice-message durations must be between 1 and 120 seconds" }
    }
}

data class MomentContent(
    val posts: List<CustomPost> = emptyList(),
    val chats: List<SavedChat> = emptyList(),
    val chatScenes: List<SavedChatScene> = emptyList(),
)

data class NewPostRequest(
    val placement: PostPlacement,
    val mediaType: PostMediaType,
    val sourceUris: List<String>,
    val profileName: String,
    val profileImageSourceUri: String?,
    val caption: String,
    val likes: Int,
    val comments: Int,
)

data class NewChatRequest(
    val profileName: String,
    val profileImageSourceUri: String?,
    val initialMessages: List<NewChatMessage>,
)

data class NewChatMessage(
    val side: ChatSide,
    val text: String,
    val kind: SavedMessageKind,
    val durationSeconds: Int,
)

fun momentHandle(displayName: String): String {
    val words = displayName
        .trim()
        .lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter(String::isNotBlank)
    return "@${words.ifEmpty { listOf("user") }.joinToString(".")}"
}

object MomentDefaults {
    const val ALEX_CHAT_ID = "chat_alex_morgan"

    val content = MomentContent(
        chats = listOf(
            SavedChat(
                id = ALEX_CHAT_ID,
                profileName = "Alex Morgan",
                profileImagePath = null,
                initialMessages = listOf(
                    SavedChatMessage(
                        id = "alex_initial_1",
                        side = ChatSide.CONTACT,
                        text = "This is the placeholder conversation.",
                    ),
                    SavedChatMessage(
                        id = "alex_initial_2",
                        side = ChatSide.ACTOR,
                        text = "Perfect — I can replace all of this later.",
                    ),
                ),
            ),
            SavedChat(
                id = "chat_film_unit",
                profileName = "Film Unit",
                profileImagePath = null,
                initialMessages = listOf(
                    SavedChatMessage(
                        id = "film_initial_1",
                        side = ChatSide.CONTACT,
                        text = "Secondary thread placeholder.",
                    ),
                ),
            ),
        ),
    )
}
