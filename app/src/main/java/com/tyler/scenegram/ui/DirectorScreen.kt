@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.tyler.scenegram.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyler.scenegram.director.AppUiState
import com.tyler.scenegram.director.ChatSceneStep
import com.tyler.scenegram.director.ChatSide
import com.tyler.scenegram.director.NewChatMessage
import com.tyler.scenegram.director.NewChatRequest
import com.tyler.scenegram.director.NewPostRequest
import com.tyler.scenegram.director.PostMediaType
import com.tyler.scenegram.director.PostPlacement
import com.tyler.scenegram.director.SavedMessageKind
import com.tyler.scenegram.director.momentHandle

@Composable
internal fun DirectorScreen(
    modifier: Modifier = Modifier,
    state: AppUiState,
    notificationsGranted: Boolean,
    requestNotificationPermission: () -> Unit,
    onPrepareChat: () -> Unit,
    onPrepareReels: () -> Unit,
    onPrepareUninstall: () -> Unit,
    onStartDemoChat: (Int, Int) -> Unit,
    onCancel: () -> Unit,
    onTestNotification: () -> Boolean,
    onAddPost: (NewPostRequest) -> Unit,
    onDeletePost: (String) -> Unit,
    onAddChat: (NewChatRequest) -> Unit,
    onDeleteChat: (String) -> Unit,
    onAddScene: (
        String,
        String,
        String?,
        Int,
        SavedMessageKind,
        Int,
        List<ChatSceneStep>,
    ) -> Unit,
    onDeleteScene: (String) -> Unit,
    onStartScene: (String) -> Unit,
) {
    val context = LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }

    var postPlacement by remember { mutableStateOf(PostPlacement.REELS) }
    var postMediaType by remember { mutableStateOf(PostMediaType.IMAGES) }
    var postMediaUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var postProfileName by remember { mutableStateOf("") }
    var postProfileImageUri by remember { mutableStateOf<String?>(null) }
    var postCaption by remember { mutableStateOf("") }
    var postLikes by remember { mutableStateOf("0") }
    var postComments by remember { mutableStateOf("0") }

    val picturePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { persistReadPermission(context, it) }
        if (uris.isNotEmpty()) {
            postMediaType = PostMediaType.IMAGES
            postMediaUris = uris.map(Uri::toString)
        }
    }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            postMediaType = PostMediaType.VIDEO
            postMediaUris = listOf(it.toString())
        }
    }
    val postAvatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            postProfileImageUri = it.toString()
        }
    }

    var chatProfileName by remember { mutableStateOf("") }
    var chatProfileImageUri by remember { mutableStateOf<String?>(null) }
    var chatMessageSide by remember { mutableStateOf(ChatSide.CONTACT) }
    var chatMessageKind by remember { mutableStateOf(SavedMessageKind.TEXT) }
    var chatVoiceDuration by remember { mutableStateOf("7") }
    var chatMessageText by remember { mutableStateOf("") }
    val chatInitialMessages = remember { mutableStateListOf<NewChatMessage>() }
    val chatAvatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            chatProfileImageUri = it.toString()
        }
    }

    var sceneTitle by remember { mutableStateOf("") }
    var sceneChatId by remember { mutableStateOf(state.chats.firstOrNull()?.id.orEmpty()) }
    var includeInitialMessage by remember { mutableStateOf(false) }
    var initialMessage by remember { mutableStateOf("") }
    var initialDelay by remember { mutableStateOf("3") }
    var initialMessageKind by remember { mutableStateOf(SavedMessageKind.TEXT) }
    var initialVoiceDuration by remember { mutableStateOf("7") }
    var replyText by remember { mutableStateOf("") }
    var replyDelay by remember { mutableStateOf("2") }
    var replyKind by remember { mutableStateOf(SavedMessageKind.TEXT) }
    var replyVoiceDuration by remember { mutableStateOf("7") }
    val sceneSteps = remember { mutableStateListOf<ChatSceneStep>() }

    LaunchedEffect(state.contentStatus) {
        when {
            state.contentStatus?.startsWith("Post saved to ") == true -> {
                postMediaUris = emptyList()
                postProfileName = ""
                postProfileImageUri = null
                postCaption = ""
                postLikes = "0"
                postComments = "0"
            }
            state.contentStatus?.startsWith("Chat with ") == true &&
                state.contentStatus.endsWith(" saved") -> {
                chatProfileName = ""
                chatProfileImageUri = null
                chatMessageText = ""
                chatInitialMessages.clear()
            }
        }
    }

    LaunchedEffect(state.chats) {
        if (state.chats.none { it.id == sceneChatId }) {
            sceneChatId = state.chats.firstOrNull()?.id.orEmpty()
        }
    }

    var demoTextDelay by remember { mutableStateOf("3") }
    var demoVoiceDelay by remember { mutableStateOf("5") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("director"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DirectorCard("Shoot status") {
                Text(state.directorStatus, fontWeight = FontWeight.Bold)
                Text(state.nextCueLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val total = state.totalCues.takeIf { it > 0 }?.toString() ?: "—"
                Text("Delivered cues: ${state.deliveredCues}/$total", fontSize = 13.sp)
                if (state.scriptRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.contentStatus?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }

        item {
            DirectorCard("Quick preparation") {
                Button(onClick = onPrepareChat, modifier = Modifier.fillMaxWidth()) {
                    Text("Prepare first chat")
                }
                FilledTonalButton(onClick = onPrepareReels, modifier = Modifier.fillMaxWidth()) {
                    Text("Prepare reels")
                }
                FilledTonalButton(onClick = onPrepareUninstall, modifier = Modifier.fillMaxWidth()) {
                    Text("Prepare fake uninstall")
                }
                HorizontalDivider()
                Text("Text + voice rehearsal", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(
                        value = demoTextDelay,
                        onValueChange = { demoTextDelay = it },
                        label = "Text delay",
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = demoVoiceDelay,
                        onValueChange = { demoVoiceDelay = it },
                        label = "Voice after",
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        onStartDemoChat(
                            demoTextDelay.toIntOrNull() ?: 0,
                            demoVoiceDelay.toIntOrNull() ?: 0,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("start-chat-cue"),
                ) { Text("Start demo cue") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel pending cues")
                }
            }
        }

        item {
            DirectorCard("Add a post") {
                Text("Destination", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PostPlacement.entries.forEach { placement ->
                        FilterChip(
                            selected = postPlacement == placement,
                            onClick = { postPlacement = placement },
                            label = { Text(placement.directorLabel()) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { picturePicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Choose pictures") }
                    OutlinedButton(
                        onClick = { videoPicker.launch(arrayOf("video/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Choose video") }
                }
                Text(
                    when {
                        postMediaUris.isEmpty() -> "No media selected"
                        postMediaType == PostMediaType.VIDEO -> "1 video selected"
                        else -> "${postMediaUris.size} picture(s) selected for one carousel"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = postProfileName,
                    onValueChange = { postProfileName = it },
                    label = { Text("Profile name") },
                    supportingText = {
                        if (postProfileName.isNotBlank()) Text(momentHandle(postProfileName))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = { postAvatarPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (postProfileImageUri == null) "Choose profile picture" else "Profile picture selected")
                }
                OutlinedTextField(
                    value = postCaption,
                    onValueChange = { postCaption = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(postLikes, { postLikes = it }, "Likes", Modifier.weight(1f))
                    NumberField(postComments, { postComments = it }, "Comments", Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        onAddPost(
                            NewPostRequest(
                                placement = postPlacement,
                                mediaType = postMediaType,
                                sourceUris = postMediaUris,
                                profileName = postProfileName,
                                profileImageSourceUri = postProfileImageUri,
                                caption = postCaption,
                                likes = postLikes.toIntOrNull() ?: 0,
                                comments = postComments.toIntOrNull() ?: 0,
                            ),
                        )
                    },
                    enabled = postMediaUris.isNotEmpty() &&
                        postProfileName.isNotBlank() &&
                        state.contentStatus != "Importing post media…",
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import and save post") }
                if (state.customPosts.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Saved posts", fontWeight = FontWeight.SemiBold)
                    state.customPosts.forEach { post ->
                        SavedItemRow(
                            title = "${post.profileName} · ${post.placement.directorLabel()}",
                            detail = "${post.mediaPaths.size} media · ${post.likes} likes · ${post.comments} comments",
                            onDelete = { onDeletePost(post.id) },
                        )
                    }
                }
            }
        }

        item {
            DirectorCard("Add a chat") {
                OutlinedTextField(
                    value = chatProfileName,
                    onValueChange = { chatProfileName = it },
                    label = { Text("Profile name") },
                    supportingText = {
                        if (chatProfileName.isNotBlank()) Text(momentHandle(chatProfileName))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = { chatAvatarPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (chatProfileImageUri == null) "Choose profile picture" else "Profile picture selected")
                }
                Text("Initial chat history", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = chatMessageSide == ChatSide.CONTACT,
                        onClick = { chatMessageSide = ChatSide.CONTACT },
                        label = { Text("Fake person") },
                    )
                    FilterChip(
                        selected = chatMessageSide == ChatSide.ACTOR,
                        onClick = { chatMessageSide = ChatSide.ACTOR },
                        label = { Text("App user") },
                    )
                }
                Text("Message type", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SavedMessageKind.entries.forEach { kind ->
                        FilterChip(
                            selected = chatMessageKind == kind,
                            onClick = { chatMessageKind = kind },
                            label = { Text(kind.directorLabel()) },
                        )
                    }
                }
                OutlinedTextField(
                    value = chatMessageText,
                    onValueChange = { chatMessageText = it },
                    label = {
                        Text(
                            if (chatMessageKind == SavedMessageKind.VOICE) {
                                "Spoken text (device voice)"
                            } else {
                                "Message"
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (chatMessageKind == SavedMessageKind.VOICE) {
                    NumberField(
                        chatVoiceDuration,
                        { chatVoiceDuration = it },
                        "Voice duration (seconds)",
                        Modifier.fillMaxWidth(),
                    )
                }
                OutlinedButton(
                    onClick = {
                        chatInitialMessages += NewChatMessage(
                            side = chatMessageSide,
                            text = chatMessageText.trim(),
                            kind = chatMessageKind,
                            durationSeconds = if (chatMessageKind == SavedMessageKind.VOICE) {
                                (chatVoiceDuration.toIntOrNull() ?: 7).coerceIn(1, 120)
                            } else {
                                0
                            },
                        )
                        chatMessageText = ""
                    },
                    enabled = chatMessageText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add history message") }
                chatInitialMessages.forEachIndexed { index, message ->
                    SavedItemRow(
                        title = buildString {
                            append(if (message.side == ChatSide.ACTOR) "App user" else "Fake person")
                            append(" · ${message.kind.directorLabel()}")
                            if (message.kind == SavedMessageKind.VOICE) {
                                append(" · ${message.durationSeconds}s")
                            }
                        },
                        detail = message.text,
                        onDelete = { chatInitialMessages.removeAt(index) },
                    )
                }
                Button(
                    onClick = {
                        onAddChat(
                            NewChatRequest(
                                profileName = chatProfileName,
                                profileImageSourceUri = chatProfileImageUri,
                                initialMessages = chatInitialMessages.toList(),
                            ),
                        )
                    },
                    enabled = chatProfileName.isNotBlank() && state.contentStatus != "Saving chat…",
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save chat") }
                HorizontalDivider()
                Text("Saved chats", fontWeight = FontWeight.SemiBold)
                state.chats.forEach { chat ->
                    SavedItemRow(
                        title = chat.profileName,
                        detail = "${chat.handle} · ${chat.initialMessages.size} initial message(s)",
                        onDelete = { onDeleteChat(chat.id) },
                    )
                }
            }
        }

        item {
            DirectorCard("Create a chat scene") {
                OutlinedTextField(
                    value = sceneTitle,
                    onValueChange = { sceneTitle = it },
                    label = { Text("Scene name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Chat", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.chats.forEach { chat ->
                        FilterChip(
                            selected = sceneChatId == chat.id,
                            onClick = { sceneChatId = chat.id },
                            label = { Text(chat.profileName) },
                        )
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeInitialMessage,
                        onCheckedChange = { includeInitialMessage = it },
                    )
                    Text("Start with a delayed incoming notification")
                }
                if (includeInitialMessage) {
                    OutlinedTextField(
                        value = initialMessage,
                        onValueChange = { initialMessage = it },
                        label = {
                            Text(
                                if (initialMessageKind == SavedMessageKind.VOICE) {
                                    "Initial spoken text (device voice)"
                                } else {
                                    "Initial incoming message"
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SavedMessageKind.entries.forEach { kind ->
                            FilterChip(
                                selected = initialMessageKind == kind,
                                onClick = { initialMessageKind = kind },
                                label = { Text(kind.directorLabel()) },
                            )
                        }
                    }
                    NumberField(
                        initialDelay,
                        { initialDelay = it },
                        "Initial delay (seconds)",
                        Modifier.fillMaxWidth(),
                    )
                    if (initialMessageKind == SavedMessageKind.VOICE) {
                        NumberField(
                            initialVoiceDuration,
                            { initialVoiceDuration = it },
                            "Voice duration (seconds)",
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                HorizontalDivider()
                Text("Reply flow", fontWeight = FontWeight.SemiBold)
                Text(
                    "Each message the app user sends advances one step. Moment waits for the programmed delay, then delivers the next reply.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = {
                        Text(
                            if (replyKind == SavedMessageKind.VOICE) {
                                "Incoming spoken text (device voice)"
                            } else {
                                "Incoming reply"
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SavedMessageKind.entries.forEach { kind ->
                        FilterChip(
                            selected = replyKind == kind,
                            onClick = { replyKind = kind },
                            label = { Text(kind.directorLabel()) },
                        )
                    }
                }
                NumberField(
                    replyDelay,
                    { replyDelay = it },
                    "Reply delay (seconds)",
                    Modifier.fillMaxWidth(),
                )
                if (replyKind == SavedMessageKind.VOICE) {
                    NumberField(
                        replyVoiceDuration,
                        { replyVoiceDuration = it },
                        "Voice duration (seconds)",
                        Modifier.fillMaxWidth(),
                    )
                }
                OutlinedButton(
                    onClick = {
                        sceneSteps += ChatSceneStep(
                            replyText = replyText.trim(),
                            delaySeconds = (replyDelay.toIntOrNull() ?: 0).coerceIn(0, 120),
                            replyKind = replyKind,
                            replyDurationSeconds = if (replyKind == SavedMessageKind.VOICE) {
                                (replyVoiceDuration.toIntOrNull() ?: 7).coerceIn(1, 120)
                            } else {
                                0
                            },
                        )
                        replyText = ""
                    },
                    enabled = replyText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add reply step") }
                sceneSteps.forEachIndexed { index, step ->
                    SavedItemRow(
                        title = "${index + 1}. ${step.replyKind.directorLabel()} reply",
                        detail = buildString {
                            append("${step.replyKind.directorLabel()} after ${step.delaySeconds}s")
                            if (step.replyKind == SavedMessageKind.VOICE) {
                                append(" · ${step.replyDurationSeconds}s voice")
                            }
                            append(" · ${step.replyText}")
                        },
                        onDelete = { sceneSteps.removeAt(index) },
                    )
                }
                Button(
                    onClick = {
                        onAddScene(
                            sceneTitle,
                            sceneChatId,
                            if (includeInitialMessage) initialMessage else null,
                            initialDelay.toIntOrNull() ?: 0,
                            initialMessageKind,
                            if (initialMessageKind == SavedMessageKind.VOICE) {
                                (initialVoiceDuration.toIntOrNull() ?: 7).coerceIn(1, 120)
                            } else {
                                0
                            },
                            sceneSteps.toList(),
                        )
                        sceneTitle = ""
                        initialMessage = ""
                        includeInitialMessage = false
                        sceneSteps.clear()
                    },
                    enabled = sceneTitle.isNotBlank() &&
                        sceneChatId.isNotBlank() &&
                        sceneSteps.isNotEmpty() &&
                        (!includeInitialMessage || initialMessage.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save chat scene") }
                if (state.chatScenes.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Saved chat scenes", fontWeight = FontWeight.SemiBold)
                    state.chatScenes.forEach { scene ->
                        val chatName = state.chats.firstOrNull { it.id == scene.chatId }?.profileName ?: "Missing chat"
                        Column(Modifier.fillMaxWidth()) {
                            Text(scene.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                "$chatName · ${scene.steps.size} reply step(s)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                            scene.initialMessage?.let { initial ->
                                Text(
                                    buildString {
                                        append("Initial after ${scene.initialDelaySeconds}s · ")
                                        append(scene.initialMessageKind.directorLabel())
                                        if (scene.initialMessageKind == SavedMessageKind.VOICE) {
                                            append(" · ${scene.initialVoiceDurationSeconds}s voice")
                                        }
                                        append(" · $initial")
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                )
                            }
                            scene.steps.forEachIndexed { index, step ->
                                Text(
                                    buildString {
                                        append("${index + 1}. Reply after ${step.delaySeconds}s · ")
                                        append(step.replyKind.directorLabel())
                                        if (step.replyKind == SavedMessageKind.VOICE) {
                                            append(" · ${step.replyDurationSeconds}s voice")
                                        }
                                        append(" · ${step.replyText}")
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onStartScene(scene.id) }, modifier = Modifier.weight(1f)) {
                                    Text("Start")
                                }
                                OutlinedButton(
                                    onClick = { onDeleteScene(scene.id) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }

        item {
            DirectorCard("Android notifications") {
                Text(
                    if (notificationsGranted) "Permission granted" else "Permission needed",
                    color = if (notificationsGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                if (!notificationsGranted) {
                    Button(onClick = requestNotificationPermission) { Text("Allow notifications") }
                }
                OutlinedButton(
                    onClick = {
                        notice = if (onTestNotification()) "Test sent" else "Permission is not granted"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Send test notification") }
                notice?.let {
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun DirectorCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(7)) },
        modifier = modifier,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Composable
private fun SavedItemRow(title: String, detail: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 2,
            )
        }
        TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
    }
}

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun PostPlacement.directorLabel(): String = when (this) {
    PostPlacement.REELS -> "Reels"
    PostPlacement.SEARCH_DEFAULT -> "Search: default"
    PostPlacement.SEARCH_RESULTS -> "Search: after typing"
}

private fun SavedMessageKind.directorLabel(): String = when (this) {
    SavedMessageKind.TEXT -> "Text"
    SavedMessageKind.VOICE -> "Voice message"
}
