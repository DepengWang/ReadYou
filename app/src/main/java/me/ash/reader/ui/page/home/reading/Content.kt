package me.ash.reader.ui.page.home.reading

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import java.util.Date
import kotlin.math.roundToInt
import me.ash.reader.infrastructure.preference.LocalReadingRenderer
import me.ash.reader.infrastructure.preference.LocalReadingSubheadUpperCase
import me.ash.reader.infrastructure.preference.LocalReadingTextHorizontalPadding
import me.ash.reader.infrastructure.preference.ReadingRendererPreference
import me.ash.reader.ui.component.reader.Reader
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.component.scrollbar.drawVerticalScrollIndicator
import me.ash.reader.ui.component.webview.RYWebView
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.roundClick

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun Content(
    modifier: Modifier = Modifier,
    content: String,
    imageUrl: String? = null,
    feedName: String,
    title: String,
    author: String? = null,
    link: String? = null,
    publishedDate: Date,
    scrollState: ScrollState,
    listState: LazyListState,
    isLoading: Boolean,
    contentPadding: PaddingValues = PaddingValues(),
    onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    articleId: String? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedElementEnabled: Boolean = false,
) {
    val context = LocalContext.current
    val subheadUpperCase = LocalReadingSubheadUpperCase.current
    val renderer = LocalReadingRenderer.current

    val articlePageBackgroundColor = Color(0xFF484848)
    val contentShape = RoundedCornerShape(10.dp)
    val textHorizontalPadding = 4.dp
    val webViewTextMargin = with(LocalDensity.current) {
        textHorizontalPadding.toPx().roundToInt()
    }
    val uriHandler = LocalUriHandler.current

    val headline =
        @Composable {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                DisableSelection {
                    Metadata(
                        feedName = feedName,
                        title = title,
                        author = author,
                        publishedDate = publishedDate,
                        modifier = Modifier.roundClick { link?.let { uriHandler.openUri(it) } },
                    )
                }
            }
        }

    val articleHero: @Composable () -> Unit = @Composable {
        if (!imageUrl.isNullOrBlank()) {
            val heroModifier = with(sharedTransitionScope) {
                if (sharedElementEnabled && articleId != null) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState(
                            "pulse-article-$articleId",
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                } else {
                    Modifier
                }
            }
            Box(
                modifier = heroModifier.fillMaxWidth().height(180.dp),
            ) {
                RYAsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    data = imageUrl,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    contentDescription = null,
                )
            }
        }
    }

    if (isLoading) {
        Column { LoadingIndicator(modifier = Modifier.size(56.dp)) }
    } else {

        when (renderer) {
            ReadingRendererPreference.WebView -> {
                Column(
                        modifier =
                        modifier
                            .fillMaxSize()
                            .shadow(5.dp, contentShape, clip = false)
                            .background(articlePageBackgroundColor)
                            .clip(contentShape)
                            .padding(top = contentPadding.calculateTopPadding())
                            .drawVerticalScrollIndicator(scrollState)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            articleHero()
                            headline()

                            RYWebView(
                                modifier = Modifier.fillMaxSize(),
                                textMargin = webViewTextMargin,
                                content = content,
                                refererDomain = link.extractDomain(),
                                onImageClick = onImageClick,
                            )
                            Spacer(modifier = Modifier.height(40.dp))
                            Spacer(
                                modifier = Modifier.height(contentPadding.calculateBottomPadding())
                            )
                        }
                    }
                }
            }

            ReadingRendererPreference.NativeComponent -> {
                CompositionLocalProvider(
                    LocalReadingTextHorizontalPadding provides textHorizontalPadding.value.toInt(),
                ) {
                    SelectionContainer {
                        LazyColumn(
                        modifier = modifier
                            .fillMaxSize()
                            .shadow(5.dp, contentShape, clip = false)
                            .background(articlePageBackgroundColor)
                            .clip(contentShape)
                            .drawVerticalScrollIndicator(listState),
                        state = listState,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                        item {
                            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
                            articleHero()
                            headline()
                        }

                        Reader(
                            context = context,
                            subheadUpperCase = subheadUpperCase.value,
                            link = link ?: "",
                            content = content,
                            onImageClick = onImageClick,
                            onLinkClick = { uriHandler.openUri(it) },
                        )

                        item {
                            Spacer(modifier = Modifier.height(40.dp))
                            Spacer(
                                modifier = Modifier.height(contentPadding.calculateBottomPadding())
                            )
                        }
                        }
                    }
                }
            }
        }
    }
}
