package me.ash.reader.ui.page.home.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.foundation.lazy.rememberLazyListState
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.component.base.SIZE_1000
import me.ash.reader.ui.ext.collectAsStateValue
import coil.size.Precision
import coil.size.Scale

private val PulseBackgroundColor = Color(0xFF484848)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulsePage(
    navigateToFeeds: () -> Unit,
    navigateToReading: (String, List<String>, Int) -> Unit,
    viewModel: PulseViewModel = hiltViewModel(),
) {
    val groups = viewModel.groups.collectAsStateValue()

    DisposableEffect(viewModel) {
        onDispose { viewModel.commitDiffs() }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
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
                            text = "Pulse",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = viewModel::sync) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onSurface,
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
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                state = listState,
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(groups, key = { it.group.id }) { group ->
                    PulseGroupSection(
                        group = group,
                        onArticleClick = { article ->
                            viewModel.markAsRead(article)
                            val articleIds = group.feeds
                                .firstOrNull { feed ->
                                    feed.articles.any { it.article.id == article.article.id }
                                }
                                ?.articles
                                ?.map { it.article.id }
                                .orEmpty()
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

@Composable
private fun PulseGroupSection(
    group: PulseGroup,
    onArticleClick: (ArticleWithFeed) -> Unit,
    onToggleStarred: (ArticleWithFeed) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (group.feeds.size > 1 && group.group.name != "所有") {
            Text(
                text = group.group.name,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 3.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            group.feeds.forEach { feed ->
                PulseFeedRow(
                    feed = feed,
                    onArticleClick = onArticleClick,
                    onToggleStarred = onToggleStarred,
                )
            }
        }
    }
}

@Composable
private fun PulseFeedRow(
    feed: PulseFeed,
    onArticleClick: (ArticleWithFeed) -> Unit,
    onToggleStarred: (ArticleWithFeed) -> Unit,
) {
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
            )
            {
                Text(
                    text = feed.feed.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cardSize = ((maxWidth - 44.dp) / 3).coerceAtLeast(96.dp)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(feed.articles, key = { it.article.id }) { article ->
                        PulseArticleCard(
                            article = article,
                            size = cardSize,
                            onClick = { onArticleClick(article) },
                            onToggleStarred = { onToggleStarred(article) },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PulseArticleCard(
    article: ArticleWithFeed,
    size: Dp,
    onClick: () -> Unit,
    onToggleStarred: () -> Unit,
) {
    val shape = RoundedCornerShape(0.dp)
    val articleImageAlpha = if (article.article.isUnread) 1f else 0.74f
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
                RYAsyncImage(
                    modifier = Modifier.fillMaxSize().alpha(articleImageAlpha),
                    data = article.article.img,
                    scale = Scale.FILL,
                    precision = Precision.INEXACT,
                    size = SIZE_1000,
                    contentScale = ContentScale.Crop,
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
