package me.ash.reader.ui.page.home.pulse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.foundation.lazy.rememberLazyListState
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.ui.ext.collectAsStateValue
import coil.imageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import kotlin.math.exp
import kotlinx.coroutines.launch

private val PulseBackgroundColor = Color(0xFF484848)
private val PulseToolbarColor = PulseBackgroundColor
private val PulseFeedRowHeight = 147.dp
private val PulseArticleCardSize = 112.dp
private val PulseRefreshDragThreshold = PulseArticleCardSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulsePage(
    navigateToFeeds: () -> Unit,
    navigateToReading: (String, List<String>, Int) -> Unit,
    viewModel: PulseViewModel = hiltViewModel(),
) {
    val groups = viewModel.groups.collectAsStateValue()
    val lastGroupId = viewModel.lastGroupId.collectAsStateValue()
    val refreshingFeedIds = viewModel.refreshingFeedIds.collectAsStateValue()
    val syncAllInProgress = viewModel.syncAllInProgress.collectAsStateValue()
    val syncCompletionVersion = viewModel.syncCompletionVersion.collectAsStateValue()
    var selectedGroupId by rememberSaveable { mutableStateOf("") }
    var showSyncCompletionFeedback by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val refreshTransition = rememberInfiniteTransition(label = "pulse-refresh")
    val refreshRotation by refreshTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-refresh-rotation",
    )
    val completionPulseAlpha by refreshTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(240),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-refresh-completion-pulse",
    )

    LaunchedEffect(syncCompletionVersion) {
        if (syncCompletionVersion == 0) return@LaunchedEffect
        showSyncCompletionFeedback = true
        kotlinx.coroutines.delay(900)
        showSyncCompletionFeedback = false
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.commitDiffs() }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .background(PulseToolbarColor),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = navigateToFeeds) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ViewList,
                            contentDescription = "原首页",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Read You",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.syncAllFeeds(
                                preferredGroupId = selectedGroupId
                                    .takeUnless {
                                        it.isBlank() || it == "__pulse_last_group_unset__"
                                    }
                                    ?: lastGroupId.takeUnless {
                                    it.isBlank() || it == "__pulse_last_group_unset__"
                                    },
                            )
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.rotate(
                                if (!syncAllInProgress && refreshingFeedIds.isEmpty() && !showSyncCompletionFeedback) {
                                    0f
                                } else {
                                    refreshRotation
                                },
                            ).alpha(
                                if (showSyncCompletionFeedback) {
                                    completionPulseAlpha
                                } else {
                                    1f
                                },
                            ),
                        )
                    }
                }
            }
        },
        // Original Pulse uses a neutral charcoal content canvas rather than
        // the darker blue-gray surface used by the surrounding Read You pages.
        containerColor = PulseBackgroundColor,
    ) { contentPadding ->
        if (groups.isEmpty() || groups.all { it.feeds.isEmpty() }) {
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无订阅源",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LaunchedEffect(groups, lastGroupId) {
                if (lastGroupId == "__pulse_last_group_unset__") return@LaunchedEffect
                val restoredGroupId = lastGroupId.takeIf { id ->
                    groups.any { it.group.id == id }
                }
                val targetGroupId = restoredGroupId ?: selectedGroupId.takeIf { id ->
                    groups.any { it.group.id == id }
                } ?: groups.firstOrNull()?.group?.id
                targetGroupId?.let { groupId ->
                    selectedGroupId = groupId
                    viewModel.activateGroup(groupId)
                }
            }

            val selectedGroupIndex = groups.indexOfFirst { it.group.id == selectedGroupId }
                .takeIf { it >= 0 } ?: 0
            val selectedGroup = groups[selectedGroupIndex]

            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                ScrollableTabRow(
                    selectedTabIndex = selectedGroupIndex,
                    edgePadding = 12.dp,
                    containerColor = PulseToolbarColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    groups.forEachIndexed { index, group ->
                        Tab(
                            selected = selectedGroupIndex == index,
                            onClick = {
                                selectedGroupId = group.group.id
                                viewModel.activateGroup(group.group.id)
                            },
                            text = {
                                Text(
                                    text = group.group.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }

                AnimatedContent(
                    targetState = selectedGroupIndex,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val direction = if (targetState >= initialState) 1 else -1
                        (slideInHorizontally(
                            animationSpec = tween(220),
                            initialOffsetX = { width -> width * direction },
                        ) + fadeIn(animationSpec = tween(220))).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(180),
                                targetOffsetX = { width -> -width * direction },
                            ) + fadeOut(animationSpec = tween(180)),
                        )
                    },
                    label = "pulse-group-content",
                ) { groupIndex ->
                    val group = groups[groupIndex]
                    val listState = rememberLazyListState()
                    val pulseItems = remember(group) {
                        group.feeds.map { feed ->
                            PulseListItem.FeedRow(
                                key = "feed:${feed.feed.id}",
                                feed = feed,
                            )
                        }
                    }

                    LaunchedEffect(pulseItems, listState.firstVisibleItemIndex) {
                        val start = (listState.firstVisibleItemIndex + 1)
                            .coerceAtMost(pulseItems.size)
                        pulseItems
                            .subList(start, pulseItems.size)
                            .take(2)
                            .flatMap { item ->
                                item.feed.articles.take(3).map { article ->
                                    item.feed.thumbnailPaths[article.article.id]
                                        ?: article.article.img
                                }
                            }
                            .filterNotNull()
                            .forEach { data ->
                                context.imageLoader.enqueue(
                                    ImageRequest.Builder(context)
                                        .data(data)
                                        .size(Size(480, 480))
                                        .scale(Scale.FILL)
                                        .precision(Precision.INEXACT)
                                        .allowHardware(false)
                                        .build(),
                                )
                            }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(pulseItems, key = { it.key }) { item ->
                            PulseFeedRow(
                                feed = item.feed,
                                isRefreshing = refreshingFeedIds.contains(item.feed.feed.id),
                                onRefresh = { viewModel.refreshFeed(item.feed.feed) },
                                onArticleClick = { article ->
                                    viewModel.markAsRead(article)
                                    val articleIds = item.feed.articles.map { it.article.id }
                                    val articleIndex = articleIds.indexOf(article.article.id)
                                    navigateToReading(article.article.id, articleIds, articleIndex)
                                },
                                onToggleStarred = viewModel::toggleStarred,
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface PulseListItem {
    val key: String

    data class GroupHeader(
        override val key: String,
        val title: String,
    ) : PulseListItem

    data class FeedRow(
        override val key: String,
        val feed: PulseFeed,
    ) : PulseListItem
}

@Composable
private fun PulseFeedRow(
    feed: PulseFeed,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onArticleClick: (ArticleWithFeed) -> Unit,
    onToggleStarred: (ArticleWithFeed) -> Unit,
) {
    val listState = rememberLazyListState()
    val dragThreshold = with(LocalDensity.current) { PulseRefreshDragThreshold.toPx() }
    var pullOffset by remember { mutableFloatStateOf(0f) }
    val pullBackOffset = remember { Animatable(0f) }
    val pullBackScope = rememberCoroutineScope()

    LaunchedEffect(listState, feed.feed.id) {
        var wasScrollableToEnd = false
        snapshotFlow { listState.canScrollBackward }.collect { canScrollBackward ->
            if (canScrollBackward) {
                wasScrollableToEnd = true
            } else if (wasScrollableToEnd) {
                wasScrollableToEnd = false
                onRefresh()
            }
        }
    }

    val unreadCount = feed.articles.count { it.article.isUnread }
    val hasUnread = unreadCount > 0
    val rowBackground = Color(0xFF2B2B2B)
    val headerBackground = PulseBackgroundColor
    val flagColor = Color(0xFF333333)
    val foldColor = Color(0xFF4A4A4A)
    val foldDetailColor = Color(0xFF666666)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(PulseFeedRowHeight)
            .background(rowBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(27.dp)
                .background(headerBackground),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(27.dp)
                    .background(flagColor)
                    .drawBehind {
                        val foldSize = 13.dp.toPx()
                        // The original Pulse tag has a flat bottom edge and a small
                        // folded page corner at the upper-right, not a cut-out arrow.
                        val foldShadow = Path().apply {
                            moveTo(size.width - foldSize, 0f)
                            lineTo(size.width, foldSize)
                            lineTo(size.width - foldSize, foldSize)
                            close()
                        }
                        val fold = Path().apply {
                            moveTo(size.width - foldSize, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, foldSize)
                            close()
                        }
                        drawPath(foldShadow, foldDetailColor)
                        drawPath(fold, foldColor)
                        drawLine(
                            color = foldDetailColor,
                            start = Offset(size.width - foldSize, 0f),
                            end = Offset(size.width, foldSize),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .padding(start = 10.dp, end = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = feed.feed.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isRefreshing) {
                        val transition = rememberInfiniteTransition(label = "feed-refresh")
                        val rotation by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(850),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "feed-refresh-rotation",
                        )
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "正在更新",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp).size(14.dp).rotate(rotation),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (hasUnread) {
                Text(
                    text = "$unreadCount 未读",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                )
            } else {
                Text(
                    text = "已读",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))

        if (feed.articles.isEmpty()) {
            Text(
                text = "暂无文章",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .background(rowBackground)
                    .pointerInput(feed.feed.id, dragThreshold) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            var reachedLeft = !listState.canScrollBackward
                            var dragDistance = 0f
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!reachedLeft && !listState.canScrollBackward) {
                                        reachedLeft = true
                                        dragDistance = 0f
                                    }
                                    if (reachedLeft) {
                                        dragDistance += change.position.x - change.previousPosition.x
                                        val rightPull = dragDistance.coerceAtLeast(0f)
                                        // Resistance: the farther the user pulls, the less the
                                        // image follows, creating a soft "cannot pull further"
                                        // feeling instead of a linear translation.
                                        val resistedOffset = dragThreshold * (
                                            1f - exp(-rightPull / dragThreshold)
                                        )
                                        pullOffset = resistedOffset.coerceAtMost(dragThreshold)
                                    }
                                    if (change.changedToUp()) {
                                        if (reachedLeft && dragDistance >= dragThreshold) {
                                            onRefresh()
                                        }
                                        break
                                    }
                                }
                            } finally {
                                val releasedOffset = pullOffset
                                pullOffset = 0f
                                if (releasedOffset > 0f) {
                                    pullBackScope.launch {
                                        pullBackOffset.snapTo(releasedOffset)
                                        pullBackOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = 0.72f,
                                                stiffness = 520f,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp)
                        .background(rowBackground)
                        .graphicsLayer {
                            translationX = pullOffset + pullBackOffset.value
                        },
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        state = listState,
                    ) {
                        items(feed.articles, key = { it.article.id }) { article ->
                            PulseArticleCard(
                                article = article,
                                size = PulseArticleCardSize,
                                thumbnailPath = feed.thumbnailPaths[article.article.id],
                                onClick = { onArticleClick(article) },
                                onToggleStarred = { onToggleStarred(article) },
                            )
                        }
                    }
                }
                val visiblePullOffset = maxOf(pullOffset, pullBackOffset.value)
                Text(
                    text = "刷新",
                    color = Color(0xFFBDBDBD),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                        .zIndex(1f)
                        .alpha(
                            (visiblePullOffset / (dragThreshold * 0.62f))
                                .coerceIn(0f, 1f),
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PulseArticleCard(
    article: ArticleWithFeed,
    size: Dp,
    thumbnailPath: String?,
    onClick: () -> Unit,
    onToggleStarred: () -> Unit,
) {
    val shape = RoundedCornerShape(0.dp)
    val articleImageAlpha = if (article.article.isUnread) 1f else 0.74f
    val requestedImageSize = with(LocalDensity.current) { size.toPx().toInt().coerceAtLeast(1) }
    Card(
        modifier = Modifier
            .width(size)
            .height(size)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            if (article.article.img.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(articleImageAlpha)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
            } else {
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailPath ?: article.article.img)
                        .size(requestedImageSize, requestedImageSize)
                        .scale(Scale.FILL)
                        .precision(Precision.INEXACT)
                        .crossfade(220)
                        .allowHardware(false)
                        .build(),
                )
                if (painter.state !is AsyncImagePainter.State.Success) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(articleImageAlpha),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = if (article.article.isUnread) 0.08f else 0.22f),
                            ),
                        ),
                    ),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(44.dp)
                    .clickable(onClick = onToggleStarred),
                contentAlignment = Alignment.TopEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (article.article.isStarred) {
                            Icons.Rounded.Star
                        } else {
                            Icons.Rounded.StarOutline
                        },
                        contentDescription = if (article.article.isStarred) "取消收藏" else "收藏",
                        tint = if (article.article.isStarred) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White.copy(alpha = 0.9f)
                        },
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.76f)),
                        ),
                    )
                    .padding(start = 6.dp, top = 24.dp, end = 6.dp, bottom = 5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (article.article.isUnread) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = article.article.title.ifBlank { "无标题文章" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (article.article.isUnread) FontWeight.Medium else FontWeight.Normal,
                        color = Color.White.copy(alpha = if (article.article.isUnread) 0.96f else 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
