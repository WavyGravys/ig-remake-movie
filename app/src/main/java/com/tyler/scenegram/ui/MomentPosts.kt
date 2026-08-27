@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tyler.scenegram.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tyler.scenegram.director.CustomPost
import com.tyler.scenegram.director.PostMediaType
import com.tyler.scenegram.director.PostPlacement
import com.tyler.scenegram.director.momentHandle
import com.tyler.scenegram.model.FeedPost
import com.tyler.scenegram.model.PlaceholderContent
import com.tyler.scenegram.model.Profile
import com.tyler.scenegram.model.Reel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class DisplayMediaType {
    PLACEHOLDER_VIDEO,
    PLACEHOLDER_IMAGES,
    IMPORTED_VIDEO,
    IMPORTED_IMAGES,
}

private data class DisplayPost(
    val id: String,
    val profileName: String,
    val profileImagePath: String?,
    val caption: String,
    val likes: Int,
    val comments: Int,
    val mediaType: DisplayMediaType,
    val mediaKeys: List<String> = emptyList(),
    val mediaPaths: List<String> = emptyList(),
)

@Composable
internal fun ReelsScreen(modifier: Modifier = Modifier, customPosts: List<CustomPost>) {
    val posts = remember(customPosts) {
        builtInMixedPosts() + customPosts
            .filter { it.placement == PostPlacement.REELS }
            .map(CustomPost::toDisplayPost)
    }
    val pagerState = rememberPagerState(pageCount = { posts.size })
    VerticalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize().testTag("reels"),
    ) { page ->
        PostPage(
            post = posts[page],
            active = pagerState.currentPage == page,
            index = page,
        )
    }
}

@Composable
internal fun SearchScreen(
    modifier: Modifier = Modifier,
    query: String,
    selectedPostId: String?,
    customPosts: List<CustomPost>,
    onQueryChange: (String) -> Unit,
    onSelectPost: (String?) -> Unit,
) {
    val posts = remember(query, customPosts) { searchPosts(query, customPosts) }
    val selectedIndex = posts.indexOfFirst { it.id == selectedPostId }
    BackHandler(enabled = selectedIndex >= 0) { onSelectPost(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("search"),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search") },
            leadingIcon = { Text("⌕", fontSize = 23.sp) },
            trailingIcon = if (selectedIndex >= 0) {
                { TextButton(onClick = { onSelectPost(null) }) { Text("×", fontSize = 22.sp) } }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
        )
        if (selectedIndex >= 0) {
            val pagerState = rememberPagerState(
                initialPage = selectedIndex,
                pageCount = { posts.size },
            )
            LaunchedEffect(selectedPostId, posts.size) {
                val page = posts.indexOfFirst { it.id == selectedPostId }
                if (page >= 0 && pagerState.currentPage != page) pagerState.scrollToPage(page)
            }
            VerticalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                PostPage(posts[page], pagerState.currentPage == page, page)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(posts, key = DisplayPost::id) { post ->
                    PostThumbnail(
                        post = post,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.78f)
                            .clickable { onSelectPost(post.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PostPage(post: DisplayPost, active: Boolean, index: Int) {
    val loop = rememberInfiniteTransition(label = "moment-post-$index")
    val motion by loop.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(2_800), RepeatMode.Reverse),
        label = "placeholder-motion",
    )
    val progress by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8_000), RepeatMode.Restart),
        label = "placeholder-progress",
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (post.mediaType) {
            DisplayMediaType.PLACEHOLDER_VIDEO -> PlaceholderVideoMedia(
                key = post.mediaKeys.first(),
                active = active,
                motion = if (active) motion else 0f,
                index = index,
            )
            DisplayMediaType.PLACEHOLDER_IMAGES -> CarouselMedia(
                count = post.mediaKeys.size,
                render = { page ->
                    PlaceholderImageMedia(post.mediaKeys[page], page + post.id.hashCode())
                },
            )
            DisplayMediaType.IMPORTED_VIDEO -> ImportedVideoMedia(
                path = post.mediaPaths.first(),
                active = active,
            )
            DisplayMediaType.IMPORTED_IMAGES -> CarouselMedia(
                count = post.mediaPaths.size,
                render = { page -> ImportedImage(post.mediaPaths[page], Modifier.fillMaxSize()) },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))),
                )
                .padding(start = 16.dp, end = 82.dp, bottom = 20.dp, top = 110.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MomentAvatar(
                    name = post.profileName,
                    imagePath = post.profileImagePath,
                    seed = post.profileName.hashCode(),
                    size = 36.dp,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    momentHandle(post.profileName),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = {},
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) { Text("Follow", color = Color.White, fontSize = 12.sp) }
            }
            if (post.caption.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(post.caption, color = Color.White, fontSize = 14.sp, maxLines = 3)
            }
            if (post.mediaType == DisplayMediaType.PLACEHOLDER_VIDEO) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.22f),
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            PostAction("♡", compactCount(post.likes))
            PostAction("○", compactCount(post.comments))
            PostAction("⌁", "")
            PostAction("•••", "")
        }
    }
}

@Composable
private fun CarouselMedia(count: Int, render: @Composable (Int) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { count })
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page -> render(page) }
        if (count > 1) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                shape = RoundedCornerShape(15.dp),
                color = Color.Black.copy(alpha = 0.66f),
            ) {
                Text(
                    "${pagerState.currentPage + 1}/$count",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                )
            }
        }
        if (count > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 116.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(count) { index ->
                    Box(
                        Modifier
                            .size(if (pagerState.currentPage == index) 7.dp else 5.dp)
                            .background(
                                if (pagerState.currentPage == index) MomentAccent else Color.White.copy(alpha = 0.55f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderVideoMedia(key: String, active: Boolean, motion: Float, index: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(placeholderGradient(index + 20))),
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer { translationX = motion }
                .size(210.dp)
                .background(Color.White.copy(alpha = 0.09f), CircleShape),
        )
        Column(
            modifier = Modifier.align(Alignment.Center).padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (active) "▶" else "Ⅱ", color = Color.White, fontSize = 54.sp)
            Text("VIDEO PLACEHOLDER", color = Color.White, fontWeight = FontWeight.Bold)
            Text(key, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlaceholderImageMedia(key: String, index: Int) {
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(placeholderGradient(index))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).background(Color.White.copy(alpha = 0.14f), CircleShape))
            Spacer(Modifier.height(18.dp))
            Text(key.replace('_', ' ').uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            Text("PICTURE PLACEHOLDER", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ImportedVideoMedia(path: String, active: Boolean) {
    val context = LocalContext.current
    val latestActive = rememberUpdatedState(active)
    var videoView by remember(path) { mutableStateOf<VideoView?>(null) }
    AndroidView(
        factory = {
            VideoView(context).apply {
                setVideoPath(path)
                setOnPreparedListener { player ->
                    player.isLooping = true
                    if (latestActive.value) start()
                }
                videoView = this
            }
        },
        update = { view ->
            if (active) view.start() else view.pause()
        },
        modifier = Modifier.fillMaxSize().background(Color.Black),
    )
    DisposableEffect(path) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }
}

@Composable
private fun ImportedImage(
    path: String,
    modifier: Modifier = Modifier,
    maxWidth: Int = 1440,
    maxHeight: Int = 2560,
) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = path,
        key2 = maxWidth,
        key3 = maxHeight,
    ) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBitmap(path, maxWidth, maxHeight)?.asImageBitmap()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = requireNotNull(bitmap),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier.background(Brush.linearGradient(placeholderGradient(path.hashCode()))),
            contentAlignment = Alignment.Center,
        ) { Text("MEDIA UNAVAILABLE", color = Color.White, fontSize = 12.sp) }
    }
}

@Composable
private fun PostThumbnail(post: DisplayPost, modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFF1D1B22))) {
        when (post.mediaType) {
            DisplayMediaType.IMPORTED_IMAGES -> ImportedImage(
                post.mediaPaths.first(),
                Modifier.fillMaxSize(),
                maxWidth = 480,
                maxHeight = 640,
            )
            DisplayMediaType.PLACEHOLDER_IMAGES -> PlaceholderImageMedia(
                post.mediaKeys.first(),
                post.id.hashCode(),
            )
            DisplayMediaType.IMPORTED_VIDEO,
            DisplayMediaType.PLACEHOLDER_VIDEO,
            -> Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(placeholderGradient(post.id.hashCode()))),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Color.White, fontSize = 30.sp) }
        }
        if (post.mediaType == DisplayMediaType.IMPORTED_IMAGES ||
            post.mediaType == DisplayMediaType.PLACEHOLDER_IMAGES
        ) {
            Text(
                "▱",
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                color = Color.White,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
internal fun MomentAvatar(
    name: String,
    imagePath: String?,
    seed: Int,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(placeholderColor(seed)),
        contentAlignment = Alignment.Center,
    ) {
        if (imagePath != null) {
            ImportedImage(imagePath, Modifier.fillMaxSize(), maxWidth = 256, maxHeight = 256)
        } else {
            Text(
                name.trim().take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.34f).sp,
            )
        }
    }
}

@Composable
private fun PostAction(glyph: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(glyph, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        if (label.isNotEmpty()) Text(label, color = Color.White, fontSize = 11.sp)
    }
}

private fun builtInMixedPosts(): List<DisplayPost> {
    val profiles = PlaceholderContent.profiles.associateBy(Profile::id)
    val videos = PlaceholderContent.reels.mapIndexed { index, reel ->
        reel.toDisplayPost(profiles.getValue(reel.authorId), index)
    }
    val pictures = PlaceholderContent.feedPosts.map { post ->
        post.toDisplayPost(profiles.getValue(post.authorId))
    }
    return buildList {
        val count = maxOf(videos.size, pictures.size)
        repeat(count) { index ->
            videos.getOrNull(index)?.let(::add)
            pictures.getOrNull(index)?.let(::add)
        }
    }
}

private fun searchPosts(query: String, customPosts: List<CustomPost>): List<DisplayPost> {
    val builtIn = builtInMixedPosts()
    val static = if (query.isBlank()) {
        listOfNotNull(builtIn.getOrNull(0), builtIn.getOrNull(3), builtIn.getOrNull(4))
    } else {
        listOfNotNull(builtIn.getOrNull(1), builtIn.getOrNull(2), builtIn.getOrNull(5))
    }
    val placement = if (query.isBlank()) PostPlacement.SEARCH_DEFAULT else PostPlacement.SEARCH_RESULTS
    return static + customPosts.filter { it.placement == placement }.map(CustomPost::toDisplayPost)
}

private fun Reel.toDisplayPost(profile: Profile, index: Int) = DisplayPost(
    id = id,
    profileName = profile.displayName,
    profileImagePath = null,
    caption = caption,
    likes = listOf(4_800, 9_200, 3_100).getOrElse(index) { 1_000 },
    comments = listOf(126, 88, 47).getOrElse(index) { 20 },
    mediaType = DisplayMediaType.PLACEHOLDER_VIDEO,
    mediaKeys = listOf(video.key),
)

private fun FeedPost.toDisplayPost(profile: Profile) = DisplayPost(
    id = id,
    profileName = profile.displayName,
    profileImagePath = null,
    caption = caption,
    likes = likeCountLabel.filter(Char::isDigit).toIntOrNull() ?: 0,
    comments = commentPreview?.filter(Char::isDigit)?.toIntOrNull() ?: 0,
    mediaType = DisplayMediaType.PLACEHOLDER_IMAGES,
    mediaKeys = media.map { it.key },
)

private fun CustomPost.toDisplayPost() = DisplayPost(
    id = id,
    profileName = profileName,
    profileImagePath = profileImagePath,
    caption = caption,
    likes = likes,
    comments = comments,
    mediaType = if (mediaType == PostMediaType.VIDEO) {
        DisplayMediaType.IMPORTED_VIDEO
    } else {
        DisplayMediaType.IMPORTED_IMAGES
    },
    mediaPaths = mediaPaths,
)

private fun compactCount(value: Int): String = when {
    value >= 1_000_000 -> "${trimDecimal(value / 1_000_000f)}M"
    value >= 1_000 -> "${trimDecimal(value / 1_000f)}K"
    else -> value.toString()
}

private fun trimDecimal(value: Float): String {
    val formatted = "%.1f".format(Locale.US, value)
    return formatted.removeSuffix(".0")
}

private fun decodeSampledBitmap(path: String, maxWidth: Int, maxHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxWidth || bounds.outHeight / sample > maxHeight) sample *= 2
    val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                setRotate(180f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                setRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                setRotate(-90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
    if (matrix.isIdentity) return decoded
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
        if (it !== decoded) decoded.recycle()
    }
}

internal val MomentAccent = Color(0xFFB99CFF)

internal fun placeholderColor(seed: Int): Color {
    val palette = listOf(
        Color(0xFF7657FF),
        Color(0xFFDD3B87),
        Color(0xFF287D88),
        Color(0xFFDA7742),
        Color(0xFF5368A8),
    )
    return palette[(seed and Int.MAX_VALUE) % palette.size]
}

private fun placeholderGradient(seed: Int): List<Color> {
    val first = placeholderColor(seed)
    val second = placeholderColor(seed + 2)
    return listOf(first, second, Color(0xFF0C0B0F))
}
