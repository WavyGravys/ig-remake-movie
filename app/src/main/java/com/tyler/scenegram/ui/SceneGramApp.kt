@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tyler.scenegram.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tyler.scenegram.BuildConfig
import com.tyler.scenegram.R
import com.tyler.scenegram.director.AppScreen
import com.tyler.scenegram.director.AppUiState
import com.tyler.scenegram.director.AppViewModel
import com.tyler.scenegram.director.ChatMessageUi
import com.tyler.scenegram.director.MessageKind
import com.tyler.scenegram.director.SavedChat
import kotlinx.coroutines.withTimeoutOrNull

private val MomentColors = darkColorScheme(
    primary = MomentAccent,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF5C2C0F),
    onPrimaryContainer = Color(0xFFFFE0C8),
    secondary = Color(0xFFBDB7B3),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF35312F),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFD9A77F),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF4A3122),
    onTertiaryContainer = Color(0xFFFFDEC3),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF191919),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF333333),
    onSurfaceVariant = Color(0xFFD0CBC8),
    outline = Color(0xFF595552),
    error = Color(0xFFFF6B5F),
)

@Composable
fun SceneGramApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    DisposableEffect(view) {
        val activity = view.context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        window?.statusBarColor = AndroidColor.BLACK
        window?.navigationBarColor = AndroidColor.BLACK
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose { }
    }

    MaterialTheme(colorScheme = MomentColors) {
        if (state.blackout) {
            BlackoutScreen(onRecover = viewModel::recoverFromBlackout)
        } else {
            AppScaffold(
                state = state,
                viewModel = viewModel,
                notificationsGranted = notificationsGranted,
                requestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AppScaffold(
    state: AppUiState,
    viewModel: AppViewModel,
    notificationsGranted: Boolean,
    requestNotificationPermission: () -> Unit,
) {
    val mainScreens = setOf(AppScreen.REELS, AppScreen.SEARCH, AppScreen.INBOX, AppScreen.ME)
    BackHandler(enabled = state.screen !in mainScreens) { viewModel.onBack() }
    val selectedChat = state.chats.firstOrNull { it.id == state.selectedChatId }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MomentTopBar(
                screen = state.screen,
                selectedChatName = selectedChat?.profileName,
                onBack = viewModel::onBack,
                onWordmarkTap = viewModel::registerWordmarkTap,
            )
        },
        bottomBar = {
            if (state.screen in mainScreens) {
                MomentNavigation(selected = state.screen, onSelected = viewModel::selectMainScreen)
            }
        },
    ) { padding ->
        val chatModifier = if (WindowInsets.isImeVisible) {
            Modifier.padding(top = padding.calculateTopPadding())
        } else {
            Modifier.padding(padding)
        }
        when (state.screen) {
            AppScreen.REELS -> ReelsScreen(
                modifier = Modifier.padding(padding),
                customPosts = state.customPosts,
                showPlaceholderVideos = state.showPlaceholderVideos,
            )
            AppScreen.SEARCH -> SearchScreen(
                modifier = Modifier.padding(padding),
                query = state.searchQuery,
                selectedPostId = state.selectedSearchPostId,
                customPosts = state.customPosts,
                showPlaceholderVideos = state.showPlaceholderVideos,
                onQueryChange = viewModel::updateSearchQuery,
                onSelectPost = viewModel::selectSearchPost,
            )
            AppScreen.INBOX -> InboxScreen(
                modifier = Modifier.padding(padding),
                chats = state.chats,
                onOpenChat = viewModel::openChat,
            )
            AppScreen.CHAT -> ChatScreen(
                modifier = chatModifier,
                state = state,
                chat = selectedChat ?: state.chats.first(),
                onSend = viewModel::sendActorMessage,
                onToggleVoice = viewModel::toggleVoice,
            )
            AppScreen.ME -> AccountScreen(
                modifier = Modifier.padding(padding),
                onUninstall = viewModel::openUninstallConfirmation,
            )
            AppScreen.DIRECTOR -> DirectorScreen(
                modifier = Modifier.padding(padding),
                state = state,
                notificationsGranted = notificationsGranted,
                requestNotificationPermission = requestNotificationPermission,
                onTestNotification = viewModel::sendTestNotification,
                onAddPost = viewModel::addPost,
                onDeletePost = viewModel::deletePost,
                onShowPlaceholderVideosChange = viewModel::setShowPlaceholderVideos,
                onAddChat = viewModel::addChat,
                onDeleteChat = viewModel::deleteChat,
                onAddScene = viewModel::addChatScene,
                onDeleteScene = viewModel::deleteChatScene,
                onStartScene = viewModel::startSavedScene,
            )
            AppScreen.UNINSTALL_CONFIRMATION -> UninstallConfirmationScreen(
                modifier = Modifier.padding(padding),
                onCancel = viewModel::onBack,
                onConfirm = viewModel::confirmFakeUninstall,
            )
        }
    }
}

@Composable
private fun MomentTopBar(
    screen: AppScreen,
    selectedChatName: String?,
    onBack: () -> Unit,
    onWordmarkTap: () -> Unit,
) {
    val title = when (screen) {
        AppScreen.REELS -> "Moment"
        AppScreen.SEARCH -> "Search"
        AppScreen.INBOX -> "Messages"
        AppScreen.CHAT -> selectedChatName ?: "Chat"
        AppScreen.ME -> "My Account"
        AppScreen.DIRECTOR -> "Director controls"
        AppScreen.UNINSTALL_CONFIRMATION -> "App info"
    }
    val showBack = screen in setOf(
        AppScreen.CHAT,
        AppScreen.DIRECTOR,
        AppScreen.UNINSTALL_CONFIRMATION,
    )
    val isDirectorTrigger = screen == AppScreen.REELS

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(52.dp)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "‹",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 34.sp,
                        lineHeight = 34.sp,
                    )
                }
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .then(if (isDirectorTrigger) Modifier.clickable(onClick = onWordmarkTap) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (screen == AppScreen.REELS) 25.sp else 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                painter = painterResource(R.drawable.logo_normal_pixel),
                contentDescription = "Moment",
                modifier = Modifier.size(40.dp),
                tint = Color.Unspecified,
            )
        }
    }
}

@Composable
private fun MomentNavigation(selected: AppScreen, onSelected: (AppScreen) -> Unit) {
    val items = listOf(
        Triple(AppScreen.REELS, R.drawable.ic_moment_nav_reels, "reels"),
        Triple(AppScreen.SEARCH, R.drawable.ic_moment_search, "search"),
        Triple(AppScreen.INBOX, R.drawable.ic_moment_nav_inbox, "inbox"),
        Triple(AppScreen.ME, R.drawable.ic_moment_nav_me, "me"),
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        items.forEach { (screen, iconResource, tag) ->
            val interactionSource = remember(screen) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp)
                    .testTag("tab-$tag")
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelected(screen) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconResource),
                    contentDescription = tag,
                    modifier = Modifier.size(28.dp),
                    tint = if (selected == screen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun InboxScreen(
    modifier: Modifier = Modifier,
    chats: List<SavedChat>,
    onOpenChat: (String?) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("inbox"),
    ) {
        item { StoryStrip(chats, onOpenChat) }
        items(chats, key = SavedChat::id) { chat ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { onOpenChat(chat.id) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MomentAvatar(
                        name = chat.profileName,
                        imagePath = chat.profileImagePath,
                        seed = chat.id.hashCode(),
                        size = 58.dp,
                    )
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            chat.profileName,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            chat.initialMessages.lastOrNull()?.text ?: "New conversation",
                            color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                        )
                    }
                    Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onTertiary)
                }
            }
        }
    }
}

@Composable
private fun StoryStrip(chats: List<SavedChat>, onOpenChat: (String?) -> Unit) {
    Column {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(chats, key = { "story-${it.id}" }) { chat ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(76.dp).clickable { onOpenChat(chat.id) },
                ) {
                    val ringModifier = if (chat.showStoryRing) {
                        Modifier
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(Color(0xFF777777), Color.White, MomentAccent),
                                ),
                                CircleShape,
                            )
                            .padding(3.dp)
                            .background(MaterialTheme.colorScheme.background, CircleShape)
                            .padding(3.dp)
                    } else {
                        Modifier.padding(6.dp)
                    }
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .then(ringModifier),
                    ) {
                        MomentAvatar(
                            name = chat.profileName,
                            imagePath = chat.profileImagePath,
                            seed = chat.id.hashCode(),
                            size = 56.dp,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        chat.handle,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    }
}

@Composable
private fun ChatScreen(
    modifier: Modifier = Modifier,
    state: AppUiState,
    chat: SavedChat,
    onSend: (String) -> Unit,
    onToggleVoice: (Long) -> Unit,
) {
    var draft by remember(chat.id) { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex + 1)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .testTag("chat"),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MomentAvatar(chat.profileName, chat.profileImagePath, chat.id.hashCode(), 72.dp)
                    Spacer(Modifier.height(7.dp))
                    Text(chat.profileName, fontWeight = FontWeight.Bold)
                    Text(chat.handle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            items(state.messages, key = ChatMessageUi::id) { message ->
                MessageBubble(
                    message = message,
                    isPlaying = state.playingVoiceId == message.id,
                    progress = if (state.playingVoiceId == message.id) state.voiceProgress else 0f,
                    onToggleVoice = { onToggleVoice(message.id) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).testTag("message-input"),
                placeholder = { Text("Message…") },
                shape = RoundedCornerShape(26.dp),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                modifier = Modifier.size(50.dp).testTag("send-message"),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                enabled = draft.isNotBlank(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_moment_action_share),
                    contentDescription = "Send message",
                    modifier = Modifier.size(23.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageUi,
    isPlaying: Boolean,
    progress: Float,
    onToggleVoice: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromActor) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.78f),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.fromActor) 20.dp else 5.dp,
                bottomEnd = if (message.fromActor) 5.dp else 20.dp,
            ),
            color = if (message.fromActor) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (message.kind == MessageKind.TEXT) {
                Text(
                    message.text,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    color = Color.White,
                    fontSize = 15.sp,
                )
            } else {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onToggleVoice,
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) R.drawable.ic_moment_pause else R.drawable.ic_moment_play,
                            ),
                            contentDescription = if (isPlaying) "Pause voice message" else "Play voice message",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "0:${message.durationSeconds.toString().padStart(2, '0')}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountScreen(modifier: Modifier = Modifier, onUninstall: () -> Unit) {
    LazyColumn(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("account")) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MomentAvatar("Moment", null, 41, 82.dp)
                Spacer(Modifier.height(10.dp))
                Text("Moment", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }
        item { AccountRow("Notifications", "Alerts enabled") }
        item { AccountRow("Permissions", "Notifications and selected media") }
        item { AccountRow("Storage & cache", "Chats and personal data") }
        item {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onUninstall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("fake-uninstall"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB3261E),
                    contentColor = Color.White,
                ),
            ) { Text("Uninstall app") }
        }
    }
}

@Composable
private fun AccountRow(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Text("›", fontSize = 25.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
}

@Composable
private fun UninstallConfirmationScreen(
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxWidth().padding(top = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MomentAvatar("Moment", null, 41, 86.dp)
            Spacer(Modifier.height(12.dp))
            Text("Moment", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Installed", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Uninstall Moment?") },
            text = { Text("Do you want to uninstall this app?") },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-blackout")) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun BlackoutScreen(onRecover: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        window?.statusBarColor = AndroidColor.BLACK
        window?.navigationBarColor = AndroidColor.BLACK
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            window?.statusBarColor = AndroidColor.BLACK
            window?.navigationBarColor = AndroidColor.BLACK
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler(enabled = true) { }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("blackout")) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(180.dp)
                .height(260.dp)
                .pointerInput(onRecover) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var pressed = true
                        val releasedBeforeRecovery = withTimeoutOrNull(3_000L) {
                            while (pressed) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                pressed = event.changes
                                    .firstOrNull { it.id == down.id }
                                    ?.pressed == true
                            }
                            true
                        }
                        if (releasedBeforeRecovery == null && pressed) onRecover()
                    }
                },
        )
    }
}
