package com.tyler.scenegram.director

import android.app.Application
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tyler.scenegram.notifications.NotificationController
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    REELS,
    SEARCH,
    INBOX,
    CHAT,
    ME,
    DIRECTOR,
    UNINSTALL_CONFIRMATION,
}

enum class MessageKind { TEXT, VOICE }

data class ChatMessageUi(
    val id: Long,
    val fromActor: Boolean,
    val kind: MessageKind,
    val text: String,
    val timeLabel: String,
    val durationSeconds: Int = 0,
)

data class AppUiState(
    val screen: AppScreen = AppScreen.REELS,
    val customPosts: List<CustomPost> = emptyList(),
    val showPlaceholderVideos: Boolean = true,
    val chats: List<SavedChat> = emptyList(),
    val chatScenes: List<SavedChatScene> = emptyList(),
    val selectedChatId: String = MomentDefaults.ALEX_CHAT_ID,
    val messages: List<ChatMessageUi> = emptyList(),
    val searchQuery: String = "",
    val selectedSearchPostId: String? = null,
    val scriptRunning: Boolean = false,
    val activeSceneId: String? = null,
    val activeSceneStepIndex: Int = 0,
    val directorStatus: String = "Ready",
    val nextCueLabel: String = "No cue scheduled",
    val deliveredCues: Int = 0,
    val totalCues: Int = 0,
    val contentStatus: String? = null,
    val playingVoiceId: Long? = null,
    val voiceProgress: Float = 0f,
    val blackout: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES, 0)
    private val contentStore = MomentContentStore(application)
    private var content = contentStore.load().let { loaded ->
        if (loaded.chats.isEmpty()) loaded.copy(chats = MomentDefaults.content.chats) else loaded
    }
    private val ids = AtomicLong(System.currentTimeMillis())
    private val runtimeMessages = mutableMapOf<String, List<ChatMessageUi>>()
    private val initialChat = content.chats.first()
    private val _state: MutableStateFlow<AppUiState>

    init {
        val initialMessages = initialMessagesFor(initialChat)
        runtimeMessages[initialChat.id] = initialMessages
        _state = MutableStateFlow(
            AppUiState(
                customPosts = content.posts,
                showPlaceholderVideos = preferences.getBoolean(KEY_SHOW_PLACEHOLDER_VIDEOS, true),
                chats = content.chats,
                chatScenes = content.chatScenes,
                selectedChatId = initialChat.id,
                messages = initialMessages,
                blackout = preferences.getBoolean(KEY_BLACKOUT, false),
            ),
        )
    }

    val state = _state.asStateFlow()

    private var scriptJob: Job? = null
    private var voiceJob: Job? = null
    private var lastWordmarkTapAt = 0L
    private var wordmarkTapCount = 0
    private var ttsReady = false
    private val textToSpeech = TextToSpeech(application) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) textToSpeechLanguage()
    }

    fun selectMainScreen(screen: AppScreen) {
        require(screen in MAIN_SCREENS)
        _state.update { it.copy(screen = screen) }
    }

    fun updateSearchQuery(query: String) {
        _state.update { current ->
            current.copy(
                searchQuery = query,
                selectedSearchPostId = if (query == current.searchQuery) {
                    current.selectedSearchPostId
                } else {
                    null
                },
            )
        }
    }

    fun setShowPlaceholderVideos(show: Boolean) {
        preferences.edit { putBoolean(KEY_SHOW_PLACEHOLDER_VIDEOS, show) }
        _state.update { it.copy(showPlaceholderVideos = show) }
    }

    fun selectSearchPost(postId: String?) {
        _state.update { it.copy(selectedSearchPostId = postId) }
    }

    fun openChat(chatId: String? = _state.value.selectedChatId) {
        stopVoice()
        val chat = content.chats.firstOrNull { it.id == chatId } ?: content.chats.first()
        val messages = runtimeMessages.getOrPut(chat.id) { initialMessagesFor(chat) }
        _state.update {
            it.copy(
                screen = AppScreen.CHAT,
                selectedChatId = chat.id,
                messages = messages,
            )
        }
    }

    fun openUninstallConfirmation() =
        _state.update { it.copy(screen = AppScreen.UNINSTALL_CONFIRMATION) }

    fun openDirector() = _state.update { it.copy(screen = AppScreen.DIRECTOR) }

    fun onBack() {
        if (_state.value.screen == AppScreen.CHAT) stopVoice()
        _state.update { current ->
            val destination = when (current.screen) {
                AppScreen.CHAT -> AppScreen.INBOX
                AppScreen.DIRECTOR -> AppScreen.REELS
                AppScreen.UNINSTALL_CONFIRMATION -> AppScreen.ME
                else -> current.screen
            }
            current.copy(screen = destination)
        }
    }

    fun registerWordmarkTap() {
        val now = SystemClock.elapsedRealtime()
        wordmarkTapCount = if (now - lastWordmarkTapAt <= WORDMARK_TAP_WINDOW_MS) {
            wordmarkTapCount + 1
        } else {
            1
        }
        lastWordmarkTapAt = now
        if (wordmarkTapCount >= 5) {
            wordmarkTapCount = 0
            openDirector()
        }
    }

    fun sendActorMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val chatId = _state.value.selectedChatId
        appendMessage(
            chatId,
            ChatMessageUi(
                id = ids.incrementAndGet(),
                fromActor = true,
                kind = MessageKind.TEXT,
                text = clean,
                timeLabel = "now",
            ),
        )

        val current = _state.value
        val scene = current.activeSceneId?.let { id -> content.chatScenes.firstOrNull { it.id == id } }
            ?: return
        if (
            scene.chatId != chatId ||
            current.scriptRunning ||
            current.activeSceneStepIndex >= scene.steps.size
        ) return
        scheduleSceneReply(scene, current.activeSceneStepIndex)
    }

    fun prepareChatScene() {
        cancelScript()
        stopVoice()
        NotificationController.cancelAll(getApplication())
        val chat = content.chats.first()
        val messages = initialMessagesFor(chat)
        runtimeMessages[chat.id] = messages
        _state.update {
            it.copy(
                screen = AppScreen.CHAT,
                selectedChatId = chat.id,
                messages = messages,
                directorStatus = "Chat prepared",
                nextCueLabel = "Choose and start a saved scene",
                deliveredCues = 0,
                totalCues = 0,
            )
        }
    }

    fun prepareReelsScene() {
        cancelScript()
        _state.update {
            it.copy(
                screen = AppScreen.REELS,
                directorStatus = "Reels prepared",
                nextCueLabel = "No timed cue needed",
                deliveredCues = 0,
                totalCues = 0,
            )
        }
    }

    fun prepareUninstallScene() {
        cancelScript()
        preferences.edit { putBoolean(KEY_BLACKOUT, false) }
        _state.update {
            it.copy(
                screen = AppScreen.ME,
                blackout = false,
                directorStatus = "Fake uninstall prepared",
                nextCueLabel = "Open Uninstall app",
                deliveredCues = 0,
                totalCues = 0,
            )
        }
    }

    fun startChatScript(textDelaySeconds: Int, voiceDelaySeconds: Int) {
        val firstDelay = textDelaySeconds.coerceIn(0, MAX_DELAY_SECONDS)
        val secondDelay = voiceDelaySeconds.coerceIn(0, MAX_DELAY_SECONDS)
        cancelScript()
        val chat = content.chats.firstOrNull { it.id == MomentDefaults.ALEX_CHAT_ID } ?: content.chats.first()
        val messages = initialMessagesFor(chat)
        runtimeMessages[chat.id] = messages
        _state.update {
            it.copy(
                screen = AppScreen.CHAT,
                selectedChatId = chat.id,
                messages = messages,
                scriptRunning = true,
                activeSceneId = null,
                directorStatus = "Demo cue running",
                nextCueLabel = "Text message in ${firstDelay}s",
                deliveredCues = 0,
                totalCues = 2,
            )
        }
        scriptJob = viewModelScope.launch {
            delay(firstDelay * 1_000L)
            val text = incomingText("Are you still coming tonight?")
            appendMessage(chat.id, text)
            showNotification(chat, text.id, text.text)
            _state.update {
                it.copy(nextCueLabel = "Voice message in ${secondDelay}s", deliveredCues = 1)
            }

            delay(secondDelay * 1_000L)
            val voice = ChatMessageUi(
                id = ids.incrementAndGet(),
                fromActor = false,
                kind = MessageKind.VOICE,
                text = "Hey, this is placeholder audio for the voice message. Call me when you see this.",
                timeLabel = "now",
                durationSeconds = VOICE_DURATION_SECONDS,
            )
            appendMessage(chat.id, voice)
            showNotification(
                chat,
                voice.id,
                "Voice message · 0:${VOICE_DURATION_SECONDS.toString().padStart(2, '0')}",
            )
            _state.update {
                it.copy(
                    scriptRunning = false,
                    directorStatus = "Demo cue complete",
                    nextCueLabel = "All cues delivered",
                    deliveredCues = 2,
                )
            }
        }
    }

    fun startSavedScene(sceneId: String) {
        val scene = content.chatScenes.firstOrNull { it.id == sceneId } ?: return
        val chat = content.chats.firstOrNull { it.id == scene.chatId } ?: return
        cancelScript()
        stopVoice()
        NotificationController.cancelAll(getApplication())
        val messages = initialMessagesFor(chat)
        runtimeMessages[chat.id] = messages
        val hasInitial = !scene.initialMessage.isNullOrBlank()
        val total = scene.steps.size + if (hasInitial) 1 else 0
        _state.update {
            it.copy(
                screen = AppScreen.CHAT,
                selectedChatId = chat.id,
                messages = messages,
                activeSceneId = scene.id,
                activeSceneStepIndex = 0,
                scriptRunning = hasInitial,
                directorStatus = "${scene.title} running",
                nextCueLabel = if (hasInitial) {
                    "Initial message in ${scene.initialDelaySeconds}s"
                } else {
                    "Waiting for app-user message"
                },
                deliveredCues = 0,
                totalCues = total,
            )
        }

        if (hasInitial) {
            scriptJob = viewModelScope.launch {
                delay(scene.initialDelaySeconds * 1_000L)
                val message = incomingMessage(
                    text = requireNotNull(scene.initialMessage),
                    kind = scene.initialMessageKind,
                    durationSeconds = scene.initialVoiceDurationSeconds,
                )
                appendMessage(chat.id, message)
                showNotification(chat, message.id, notificationPreview(message))
                _state.update {
                    it.copy(
                        scriptRunning = false,
                        directorStatus = "${scene.title}: waiting for app-user message",
                        nextCueLabel = "Waiting for app-user message",
                        deliveredCues = 1,
                    )
                }
            }
        }
    }

    private fun scheduleSceneReply(scene: SavedChatScene, stepIndex: Int) {
        val step = scene.steps[stepIndex]
        val chat = content.chats.firstOrNull { it.id == scene.chatId } ?: return
        _state.update {
            it.copy(
                scriptRunning = true,
                directorStatus = "${scene.title}: reply pending",
                nextCueLabel = "Reply in ${step.delaySeconds}s",
            )
        }
        scriptJob = viewModelScope.launch {
            delay(step.delaySeconds * 1_000L)
            val latest = _state.value
            if (latest.activeSceneId != scene.id || latest.activeSceneStepIndex != stepIndex) return@launch
            val message = incomingMessage(
                text = step.replyText,
                kind = step.replyKind,
                durationSeconds = step.replyDurationSeconds,
            )
            appendMessage(chat.id, message)
            showNotification(chat, message.id, notificationPreview(message))
            val nextIndex = stepIndex + 1
            val nextStep = scene.steps.getOrNull(nextIndex)
            _state.update {
                if (nextStep == null) {
                    it.copy(
                        scriptRunning = false,
                        activeSceneId = null,
                        activeSceneStepIndex = nextIndex,
                        directorStatus = "${scene.title} complete",
                        nextCueLabel = "All scripted replies delivered",
                        deliveredCues = it.deliveredCues + 1,
                    )
                } else {
                    it.copy(
                        scriptRunning = false,
                        activeSceneStepIndex = nextIndex,
                        directorStatus = "${scene.title}: waiting for app-user message",
                        nextCueLabel = "Waiting for app-user message",
                        deliveredCues = it.deliveredCues + 1,
                    )
                }
            }
        }
    }

    fun cancelScript() {
        scriptJob?.cancel()
        scriptJob = null
        _state.update {
            val wasActive = it.scriptRunning || it.activeSceneId != null
            it.copy(
                scriptRunning = false,
                activeSceneId = null,
                directorStatus = if (wasActive) "Cue cancelled" else it.directorStatus,
                nextCueLabel = if (wasActive) "No cue scheduled" else it.nextCueLabel,
            )
        }
    }

    fun addPost(request: NewPostRequest) {
        if (
            request.profileName.isBlank() || request.sourceUris.isEmpty() ||
            (request.mediaType == PostMediaType.VIDEO && request.sourceUris.size != 1)
        ) {
            _state.update { it.copy(contentStatus = "Choose media and enter a profile name") }
            return
        }
        _state.update { it.copy(contentStatus = "Importing post media…") }
        viewModelScope.launch {
            val importedPaths = mutableListOf<String>()
            try {
                val mediaPaths = request.sourceUris.map { sourceUri ->
                    contentStore.importMedia(sourceUri).also(importedPaths::add)
                }
                val avatarPath = request.profileImageSourceUri?.let { sourceUri ->
                    contentStore.importMedia(sourceUri).also(importedPaths::add)
                }
                val post = CustomPost(
                    id = UUID.randomUUID().toString(),
                    placement = request.placement,
                    mediaType = request.mediaType,
                    mediaPaths = mediaPaths,
                    profileName = request.profileName.trim(),
                    profileImagePath = avatarPath,
                    caption = request.caption.trim(),
                    likes = request.likes.coerceAtLeast(0),
                    comments = request.comments.coerceAtLeast(0),
                )
                saveContent(
                    updated = content.copy(posts = content.posts + post),
                    status = "Post saved to ${post.placement.displayLabel()}",
                )
            } catch (error: CancellationException) {
                cleanupFailedImports(importedPaths)
                throw error
            } catch (error: Exception) {
                cleanupFailedImports(importedPaths)
                _state.update { it.copy(contentStatus = error.message ?: "The media import failed") }
            }
        }
    }

    fun deletePost(postId: String) {
        val deleted = content.posts.firstOrNull { it.id == postId }
        if (deleted == null) {
            _state.update { it.copy(contentStatus = "Post was already deleted") }
            return
        }
        saveContent(
            updated = content.copy(posts = content.posts.filterNot { it.id == postId }),
            status = "Post deleted",
        )
        viewModelScope.launch {
            contentStore.deleteMedia(deleted.mediaPaths + listOfNotNull(deleted.profileImagePath))
        }
    }

    fun addChat(request: NewChatRequest) {
        if (request.profileName.isBlank()) {
            _state.update { it.copy(contentStatus = "Enter a profile name") }
            return
        }
        _state.update { it.copy(contentStatus = "Saving chat…") }
        viewModelScope.launch {
            val importedPaths = mutableListOf<String>()
            try {
                val avatarPath = request.profileImageSourceUri?.let { sourceUri ->
                    contentStore.importMedia(sourceUri).also(importedPaths::add)
                }
                val chat = SavedChat(
                    id = UUID.randomUUID().toString(),
                    profileName = request.profileName.trim(),
                    profileImagePath = avatarPath,
                    showStoryRing = request.showStoryRing,
                    initialMessages = request.initialMessages.map { message ->
                        SavedChatMessage(
                            id = UUID.randomUUID().toString(),
                            side = message.side,
                            text = message.text.trim(),
                            kind = message.kind,
                            durationSeconds = message.durationSeconds,
                        )
                    },
                )
                saveContent(
                    updated = content.copy(chats = content.chats + chat),
                    status = "Person ${chat.profileName} saved",
                )
                runtimeMessages[chat.id] = initialMessagesFor(chat)
            } catch (error: CancellationException) {
                cleanupFailedImports(importedPaths)
                throw error
            } catch (error: Exception) {
                cleanupFailedImports(importedPaths)
                _state.update { it.copy(contentStatus = error.message ?: "The chat could not be saved") }
            }
        }
    }

    fun deleteChat(chatId: String) {
        if (content.chats.size <= 1) {
            _state.update { it.copy(contentStatus = "Moment needs at least one chat") }
            return
        }
        if (content.chatScenes.any { it.chatId == chatId && it.id == _state.value.activeSceneId }) {
            cancelScript()
        }
        val deleted = content.chats.firstOrNull { it.id == chatId }
        val chats = content.chats.filterNot { it.id == chatId }
        val scenes = content.chatScenes.filterNot { it.chatId == chatId }
        content = content.copy(chats = chats, chatScenes = scenes)
        runtimeMessages.remove(chatId)
        val selected = if (_state.value.selectedChatId == chatId) chats.first() else null
        contentStore.save(content)
        _state.update { current ->
            current.copy(
                chats = chats,
                chatScenes = scenes,
                selectedChatId = selected?.id ?: current.selectedChatId,
                messages = selected?.let { runtimeMessages.getOrPut(it.id) { initialMessagesFor(it) } }
                    ?: current.messages,
                contentStatus = "Chat and its scenes deleted",
            )
        }
        deleted?.profileImagePath?.let { path ->
            viewModelScope.launch { contentStore.deleteMedia(listOf(path)) }
        }
    }

    fun addChatScene(
        title: String,
        chatId: String,
        initialMessage: String?,
        initialDelaySeconds: Int,
        initialMessageKind: SavedMessageKind,
        initialVoiceDurationSeconds: Int,
        steps: List<ChatSceneStep>,
    ) {
        if (
            title.isBlank() ||
            content.chats.none { it.id == chatId } ||
            steps.isEmpty() ||
            (initialMessage != null && initialMessage.isBlank())
        ) {
            _state.update { it.copy(contentStatus = "Choose a chat, title the scene, and add a reply step") }
            return
        }
        val scene = runCatching {
            SavedChatScene(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                chatId = chatId,
                initialMessage = initialMessage?.trim()?.takeIf(String::isNotBlank),
                initialDelaySeconds = initialDelaySeconds.coerceIn(0, MAX_DELAY_SECONDS),
                steps = steps,
                initialMessageKind = initialMessageKind,
                initialVoiceDurationSeconds = if (initialMessageKind == SavedMessageKind.VOICE) {
                    initialVoiceDurationSeconds.coerceIn(1, MAX_DELAY_SECONDS)
                } else {
                    0
                },
            )
        }.getOrElse { error ->
            _state.update { it.copy(contentStatus = error.message ?: "The scene could not be saved") }
            return
        }
        content = content.copy(chatScenes = content.chatScenes + scene)
        persistContent("Scene ${scene.title} saved")
    }

    fun deleteChatScene(sceneId: String) {
        if (_state.value.activeSceneId == sceneId) cancelScript()
        content = content.copy(chatScenes = content.chatScenes.filterNot { it.id == sceneId })
        persistContent("Scene deleted")
    }

    private fun persistContent(status: String) {
        saveContent(content, status)
    }

    private fun saveContent(updated: MomentContent, status: String) {
        contentStore.save(updated)
        content = updated
        _state.update {
            it.copy(
                customPosts = content.posts,
                chats = content.chats,
                chatScenes = content.chatScenes,
                contentStatus = status,
            )
        }
    }

    fun sendTestNotification(): Boolean {
        val chat = content.chats.firstOrNull { it.id == _state.value.selectedChatId } ?: content.chats.first()
        return NotificationController.showMessage(
            getApplication(),
            TEST_NOTIFICATION_ID,
            chat.profileName,
            "Notification test — tap to open this conversation",
            chat.id,
        )
    }

    fun toggleVoice(messageId: Long) {
        if (_state.value.playingVoiceId == messageId) {
            stopVoice()
            return
        }
        stopVoice()
        val message = runtimeMessages.values
            .asSequence()
            .flatten()
            .firstOrNull { it.id == messageId && it.kind == MessageKind.VOICE }
            ?: return
        if (!ttsReady) return
        val speakResult = textToSpeech.speak(
            message.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "moment-voice-$messageId",
        )
        if (speakResult == TextToSpeech.ERROR) return
        _state.update { it.copy(playingVoiceId = messageId, voiceProgress = 0f) }
        voiceJob = viewModelScope.launch {
            val steps = message.durationSeconds.coerceAtLeast(1) * 10
            repeat(steps) { step ->
                delay(100)
                _state.update { current ->
                    if (current.playingVoiceId != messageId) current
                    else current.copy(voiceProgress = (step + 1).toFloat() / steps)
                }
            }
            _state.update { it.copy(playingVoiceId = null, voiceProgress = 0f) }
        }
    }

    fun confirmFakeUninstall() {
        cancelScript()
        stopVoice()
        NotificationController.cancelAll(getApplication())
        preferences.edit { putBoolean(KEY_BLACKOUT, true) }
        _state.update { it.copy(blackout = true) }
    }

    fun recoverFromBlackout() {
        preferences.edit { putBoolean(KEY_BLACKOUT, false) }
        _state.update { it.copy(blackout = false, screen = AppScreen.REELS) }
    }

    private fun appendMessage(chatId: String, message: ChatMessageUi) {
        val messages = runtimeMessages.getOrDefault(chatId, emptyList()) + message
        runtimeMessages[chatId] = messages
        _state.update { current ->
            if (current.selectedChatId == chatId) current.copy(messages = messages) else current
        }
    }

    private fun initialMessagesFor(chat: SavedChat): List<ChatMessageUi> =
        chat.initialMessages.map { message ->
            ChatMessageUi(
                id = ids.incrementAndGet(),
                fromActor = message.side == ChatSide.ACTOR,
                kind = when (message.kind) {
                    SavedMessageKind.TEXT -> MessageKind.TEXT
                    SavedMessageKind.VOICE -> MessageKind.VOICE
                },
                text = message.text,
                timeLabel = "earlier",
                durationSeconds = message.durationSeconds,
            )
        }

    private fun incomingText(text: String) = ChatMessageUi(
        id = ids.incrementAndGet(),
        fromActor = false,
        kind = MessageKind.TEXT,
        text = text,
        timeLabel = "now",
    )

    private fun incomingMessage(
        text: String,
        kind: SavedMessageKind,
        durationSeconds: Int,
    ) = ChatMessageUi(
        id = ids.incrementAndGet(),
        fromActor = false,
        kind = when (kind) {
            SavedMessageKind.TEXT -> MessageKind.TEXT
            SavedMessageKind.VOICE -> MessageKind.VOICE
        },
        text = text,
        timeLabel = "now",
        durationSeconds = durationSeconds,
    )

    private fun notificationPreview(message: ChatMessageUi): String =
        if (message.kind == MessageKind.VOICE) {
            "Voice message · 0:${message.durationSeconds.toString().padStart(2, '0')}"
        } else {
            message.text
        }

    private fun showNotification(chat: SavedChat, messageId: Long, preview: String) {
        val current = _state.value
        if (current.screen == AppScreen.CHAT && current.selectedChatId == chat.id) return

        NotificationController.showMessage(
            getApplication(),
            messageId.toInt(),
            chat.profileName,
            preview,
            chat.id,
        )
    }

    private fun stopVoice() {
        voiceJob?.cancel()
        voiceJob = null
        textToSpeech.stop()
        _state.update { it.copy(playingVoiceId = null, voiceProgress = 0f) }
    }

    private suspend fun cleanupFailedImports(paths: List<String>) {
        if (paths.isEmpty()) return
        withContext(NonCancellable) { contentStore.deleteMedia(paths) }
    }

    private fun textToSpeechLanguage() {
        textToSpeech.language = Locale.US
        textToSpeech.setSpeechRate(0.94f)
    }

    override fun onCleared() {
        scriptJob?.cancel()
        voiceJob?.cancel()
        textToSpeech.shutdown()
    }

    private fun PostPlacement.displayLabel(): String = when (this) {
        PostPlacement.REELS -> "Reels"
        PostPlacement.SEARCH_DEFAULT -> "default Search"
        PostPlacement.SEARCH_RESULTS -> "searched Search"
    }

    private companion object {
        val MAIN_SCREENS = setOf(AppScreen.REELS, AppScreen.SEARCH, AppScreen.INBOX, AppScreen.ME)
        const val PREFERENCES = "scenegram_director"
        const val KEY_BLACKOUT = "blackout"
        const val KEY_SHOW_PLACEHOLDER_VIDEOS = "show_placeholder_videos"
        const val WORDMARK_TAP_WINDOW_MS = 1_500L
        const val VOICE_DURATION_SECONDS = 7
        const val MAX_DELAY_SECONDS = 120
        const val TEST_NOTIFICATION_ID = 9_001
    }
}
