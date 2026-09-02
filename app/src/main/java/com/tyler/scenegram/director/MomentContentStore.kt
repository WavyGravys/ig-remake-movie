package com.tyler.scenegram.director

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.core.content.edit
import androidx.core.net.toUri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MomentContentStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): MomentContent {
        val encoded = preferences.getString(KEY_CONTENT, null) ?: return MomentDefaults.content
        return runCatching { decode(JSONObject(encoded)) }.getOrElse { MomentDefaults.content }
    }

    fun save(content: MomentContent) {
        preferences.edit { putString(KEY_CONTENT, encode(content).toString()) }
    }

    suspend fun importMedia(sourceUri: String): String = withContext(Dispatchers.IO) {
        val uri = sourceUri.toUri()
        val mimeType = context.contentResolver.getType(uri)
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf(String::isNotBlank)
            ?: "bin"
        val directory = File(context.filesDir, "moment-media").apply { mkdirs() }
        val temporary = File(directory, "${UUID.randomUUID()}.$extension.part")
        val destination = File(directory, temporary.name.removeSuffix(".part"))
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "The selected file could not be opened" }
                temporary.outputStream().use(input::copyTo)
            }
            check(temporary.renameTo(destination)) { "The selected file could not be saved" }
            destination.absolutePath
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    suspend fun deleteMedia(paths: Iterable<String>) = withContext(Dispatchers.IO) {
        val mediaDirectory = File(context.filesDir, "moment-media").canonicalFile
        paths.forEach { path ->
            runCatching {
                val file = File(path).canonicalFile
                if (file.parentFile == mediaDirectory) file.delete()
            }
        }
    }

    private fun encode(content: MomentContent) = JSONObject().apply {
        put("schemaVersion", 1)
        put("posts", JSONArray().apply { content.posts.forEach { put(encodePost(it)) } })
        put("chats", JSONArray().apply { content.chats.forEach { put(encodeChat(it)) } })
        put("chatScenes", JSONArray().apply { content.chatScenes.forEach { put(encodeScene(it)) } })
    }

    private fun encodePost(post: CustomPost) = JSONObject().apply {
        put("id", post.id)
        put("placement", post.placement.name)
        put("mediaType", post.mediaType.name)
        put("mediaPaths", JSONArray(post.mediaPaths))
        put("profileName", post.profileName)
        put("profileImagePath", post.profileImagePath)
        put("caption", post.caption)
        put("likes", post.likes)
        put("comments", post.comments)
    }

    private fun encodeChat(chat: SavedChat) = JSONObject().apply {
        put("id", chat.id)
        put("profileName", chat.profileName)
        put("profileImagePath", chat.profileImagePath)
        put("showStoryRing", chat.showStoryRing)
        put("initialMessages", JSONArray().apply {
            chat.initialMessages.forEach { message ->
                put(JSONObject().apply {
                    put("id", message.id)
                    put("side", message.side.name)
                    put("text", message.text)
                    put("kind", message.kind.name)
                    put("durationSeconds", message.durationSeconds)
                })
            }
        })
    }

    private fun encodeScene(scene: SavedChatScene) = JSONObject().apply {
        put("id", scene.id)
        put("title", scene.title)
        put("chatId", scene.chatId)
        put("initialMessage", scene.initialMessage)
        put("initialDelaySeconds", scene.initialDelaySeconds)
        put("steps", JSONArray().apply {
            scene.steps.forEach { step ->
                put(JSONObject().apply {
                    put("replyText", step.replyText)
                    put("delaySeconds", step.delaySeconds)
                    put("replyKind", step.replyKind.name)
                    put("replyDurationSeconds", step.replyDurationSeconds)
                })
            }
        })
        put("initialMessageKind", scene.initialMessageKind.name)
        put("initialVoiceDurationSeconds", scene.initialVoiceDurationSeconds)
    }

    private fun decode(root: JSONObject): MomentContent {
        require(root.optInt("schemaVersion", 1) == 1) { "Unsupported content schema" }
        val chats = root.optJSONArray("chats").objects(::decodeChat)
            .ifEmpty { MomentDefaults.content.chats }
        val chatIds = chats.mapTo(mutableSetOf()) { it.id }
        return MomentContent(
            posts = root.optJSONArray("posts").objects(::decodePost),
            chats = chats,
            chatScenes = root.optJSONArray("chatScenes").objects(::decodeScene)
                .filter { it.chatId in chatIds },
        )
    }

    private fun decodePost(value: JSONObject) = CustomPost(
        id = value.getString("id"),
        placement = PostPlacement.valueOf(value.getString("placement")),
        mediaType = PostMediaType.valueOf(value.getString("mediaType")),
        mediaPaths = value.getJSONArray("mediaPaths").strings(),
        profileName = value.getString("profileName"),
        profileImagePath = value.nullableString("profileImagePath"),
        caption = value.optString("caption"),
        likes = value.optInt("likes"),
        comments = value.optInt("comments"),
    )

    private fun decodeChat(value: JSONObject) = SavedChat(
        id = value.getString("id"),
        profileName = value.getString("profileName"),
        profileImagePath = value.nullableString("profileImagePath"),
        showStoryRing = value.optBoolean("showStoryRing", true),
        initialMessages = value.optJSONArray("initialMessages").objects { message ->
            SavedChatMessage(
                id = message.getString("id"),
                side = ChatSide.valueOf(message.getString("side")),
                text = message.getString("text"),
                kind = message.enumOrDefault("kind", SavedMessageKind.TEXT),
                durationSeconds = message.optInt("durationSeconds"),
            )
        },
    )

    private fun decodeScene(value: JSONObject) = SavedChatScene(
        id = value.getString("id"),
        title = value.getString("title"),
        chatId = value.getString("chatId"),
        initialMessage = value.nullableString("initialMessage"),
        initialDelaySeconds = value.optInt("initialDelaySeconds"),
        initialMessageKind = value.enumOrDefault("initialMessageKind", SavedMessageKind.TEXT),
        initialVoiceDurationSeconds = value.optInt("initialVoiceDurationSeconds"),
        steps = value.optJSONArray("steps").objects { step ->
            ChatSceneStep(
                replyText = step.getString("replyText"),
                delaySeconds = step.optInt("delaySeconds"),
                replyKind = step.enumOrDefault("replyKind", SavedMessageKind.TEXT),
                replyDurationSeconds = step.optInt("replyDurationSeconds"),
            )
        },
    )

    private fun <T> JSONArray?.objects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                runCatching { transform(getJSONObject(index)) }.getOrNull()?.let(::add)
            }
        }
    }

    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index))
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private inline fun <reified T : Enum<T>> JSONObject.enumOrDefault(key: String, default: T): T =
        runCatching { enumValueOf<T>(optString(key)) }.getOrDefault(default)

    private companion object {
        const val PREFERENCES = "moment_director_content"
        const val KEY_CONTENT = "content_v1"
    }
}
