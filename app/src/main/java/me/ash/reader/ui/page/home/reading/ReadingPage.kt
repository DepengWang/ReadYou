package me.ash.reader.ui.page.home.reading

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.android.TextToSpeechManager
import me.ash.reader.infrastructure.preference.LocalReadingAutoHideToolbar
import me.ash.reader.infrastructure.preference.LocalReadingBoldCharacters
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.infrastructure.preference.LocalSharedContent
import me.ash.reader.infrastructure.preference.not
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.adaptive.NavigationAction
import me.ash.reader.ui.page.adaptive.ReaderState
import me.ash.reader.ui.page.home.reading.tts.TtsButton

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun ReadingPage(
    //    navController: NavHostController,
    viewModel: ArticleListReaderViewModel,
    navigationAction: NavigationAction,
    onLoadArticle: (String, Int) -> Unit,
    onNavAction: (NavigationAction) -> Unit,
    onNavigateToStylePage: () -> Unit,
) {
    val readerCanvasColor = Color(0xFF202426)
    val readerPageColor = Color(0xFF2C3032)
    val readerPageShape = RoundedCornerShape(6.dp)
    val context = LocalContext.current
    val sharedContent = LocalSharedContent.current
    val hapticFeedback = LocalHapticFeedback.current
    val readingUiState = viewModel.readingUiState.collectAsStateValue()
    val readerState = viewModel.readerStateStateFlow.collectAsStateValue()
    val prefetchedReaderStates = viewModel.prefetchedReaderStates.collectAsStateValue()
    val boldCharacters = LocalReadingBoldCharacters.current
    val coroutineScope = rememberCoroutineScope()

    var isReaderScrollingDown by remember { mutableStateOf(false) }
    var showFullScreenImageViewer by remember { mutableStateOf(false) }

    var currentImageData by remember { mutableStateOf(ImageData()) }

    val isShowToolBar =
        if (LocalReadingAutoHideToolbar.current.value) {
            readerState.articleId != null && !isReaderScrollingDown
        } else {
            true
        }

    //    LaunchedEffect(readerState.listIndex) {
    //        readerState.listIndex?.let {
    //            navController.previousBackStackEntry?.savedStateHandle?.set("articleIndex", it)
    //        }
    //    }

    var bringToTop by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = readerCanvasColor,
        content = { paddings ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (readerState.articleId != null) {
                    val articleSequence = readerState.articleSequence.ifEmpty {
                        listOfNotNull(readerState.articleId)
                    }
                    val currentArticleIndex = articleSequence
                        .indexOf(readerState.articleId)
                        .coerceAtLeast(0)
                    val canSwipePrevious = currentArticleIndex > 0
                    val canSwipeNext = currentArticleIndex < articleSequence.lastIndex
                    val pagerState = rememberPagerState(initialPage = currentArticleIndex) {
                        articleSequence.size
                    }
                    LaunchedEffect(currentArticleIndex) {
                        if (pagerState.currentPage != currentArticleIndex) {
                            pagerState.scrollToPage(currentArticleIndex)
                        }
                    }
                    LaunchedEffect(pagerState, readerState.articleId, articleSequence) {
                        snapshotFlow { pagerState.settledPage }.collect { page ->
                            val targetArticleId = articleSequence.getOrNull(page)
                            if (targetArticleId != null && targetArticleId != readerState.articleId) {
                                onLoadArticle(targetArticleId, page)
                            }
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(readerCanvasColor)
                            .nestedScroll(
                                remember(canSwipePrevious, canSwipeNext) {
                                    object : NestedScrollConnection {
                                        override fun onPreScroll(
                                            available: Offset,
                                            source: NestedScrollSource,
                                        ): Offset {
                                            val isBlocked =
                                                (available.x > 0f && !canSwipePrevious) ||
                                                    (available.x < 0f && !canSwipeNext)
                                            return if (isBlocked) {
                                                Offset(available.x, 0f)
                                            } else {
                                                Offset.Zero
                                            }
                                        }
                                    }
                                }
                            ),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        pageSpacing = 8.dp,
                        pageSize = PageSize.Fill,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            snapPositionalThreshold = 0.25f,
                        ),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = articleSequence.size > 1,
                    ) { page ->
                        val pageArticleId = articleSequence.getOrNull(page)
                        val isCurrentPage = pageArticleId == readerState.articleId
                        val pageState = if (isCurrentPage) {
                            readerState
                        } else {
                            pageArticleId?.let { prefetchedReaderStates[it] }
                                ?: ReaderState(articleId = pageArticleId, content = ReaderState.Loading)
                        }

                        val listState = rememberSaveable(
                            pageState.articleId,
                            saver = LazyListState.Saver,
                        ) { LazyListState() }
                        val scrollState = rememberSaveable(
                            pageState.articleId,
                            saver = androidx.compose.foundation.ScrollState.Saver,
                        ) { androidx.compose.foundation.ScrollState(0) }

                        LaunchedEffect(bringToTop, pageState.articleId) {
                            if (bringToTop && isCurrentPage) {
                                if (scrollState.value != 0) scrollState.animateScrollTo(0)
                                else if (listState.firstVisibleItemIndex != 0) listState.animateScrollToItem(0)
                                bringToTop = false
                            }
                        }

                        CompositionLocalProvider(
                            LocalTextStyle provides LocalTextStyle.current.run {
                                merge(
                                    lineHeight = if (lineHeight.isSpecified) {
                                        (lineHeight.value * LocalReadingTextLineHeight.current).sp
                                    } else TextUnit.Unspecified
                                )
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(readerPageColor, readerPageShape)
                                    .clip(readerPageShape)
                                    .graphicsLayer {
                                        val pageOffset = (
                                            pagerState.currentPage - page +
                                                pagerState.currentPageOffsetFraction
                                        ).coerceIn(-1f, 1f)
                                        val distance = pageOffset.absoluteValue
                                        cameraDistance = 12f * density
                                        rotationY = pageOffset * -5f
                                        scaleX = 1f - distance * 0.035f
                                        scaleY = 1f - distance * 0.02f
                                        shadowElevation = (1f - distance) * 10.dp.toPx()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Content(
                                    modifier = Modifier,
                                    contentPadding = paddings,
                                    content = pageState.content.text ?: "",
                                    feedName = pageState.feedName,
                                    title = pageState.title.orEmpty(),
                                    author = pageState.author,
                                    link = pageState.link,
                                    publishedDate = pageState.publishedDate,
                                    isLoading = pageState.content is ReaderState.Loading,
                                    scrollState = scrollState,
                                    listState = listState,
                                    onImageClick = { imgUrl, altText ->
                                        currentImageData = ImageData(imgUrl, altText)
                                        showFullScreenImageViewer = true
                                    },
                                )
                            }
                        }
                    }
                }
                // Bottom Bar
                if (readerState.articleId != null) {
                    BottomBar(
                        isShow = isShowToolBar,
                        isUnread = readingUiState.isUnread,
                        isStarred = readingUiState.isStarred,
                        isFullContent =
                            readerState.content is ReaderState.FullContent ||
                                readerState.content is ReaderState.Error,
                        isBoldCharacters = boldCharacters.value,
                        onUnread = { viewModel.updateReadStatus(it) },
                        onStarred = { viewModel.updateStarredStatus(it) },
                        onClose = { onNavAction(navigationAction) },
                        onNavigateToStylePage = onNavigateToStylePage,
                        onShare = { sharedContent.share(context, readerState.title, readerState.link) },
                        onFullContent = {
                            if (it) viewModel.renderFullContent()
                            else viewModel.renderDescriptionContent()
                        },
                        onBoldCharacters = { (!boldCharacters).put(context, coroutineScope) },
                        onReadAloud = {
                            viewModel.textToSpeechManager.readHtml(
                                readerState.content.text ?: return@BottomBar
                            )
                        },
                        ttsButton = {
                            TtsButton(
                                onClick = {
                                    when (it) {
                                        TextToSpeechManager.State.Error -> {
                                            context.showToast("TextToSpeech initialization failed")
                                        }

                                        TextToSpeechManager.State.Idle -> {
                                            viewModel.textToSpeechManager.readHtml(
                                                readerState.content.text ?: ""
                                            )
                                        }

                                        is TextToSpeechManager.State.Reading -> {
                                            viewModel.textToSpeechManager.stop()
                                        }

                                        TextToSpeechManager.State.Preparing -> {
                                            /* no-op */
                                        }
                                    }
                                },
                                state =
                                    viewModel.textToSpeechManager.stateFlow.collectAsStateValue(),
                            )
                        },
                    )
                }
            }
        },
    )
    if (showFullScreenImageViewer) {

        ReaderImageViewer(
            imageData = currentImageData,
            onDownloadImage = {
                viewModel.downloadImage(
                    it,
                    onSuccess = { context.showToast(context.getString(R.string.image_saved)) },
                    onFailure = {
                        // FIXME: crash the app for error report
                        th ->
                        throw th
                    },
                )
            },
            onDismissRequest = { showFullScreenImageViewer = false },
        )
    }
}
