package me.ash.reader.ui.page.home.pulse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ash.reader.domain.data.GroupWithFeedsListUseCase
import me.ash.reader.domain.data.DiffMapHolder
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.di.IODispatcher

private const val ARTICLES_PER_FEED = 10

@HiltViewModel
class PulseViewModel @Inject constructor(
    groupWithFeedsListUseCase: GroupWithFeedsListUseCase,
    private val articleDao: ArticleDao,
    private val diffMapHolder: DiffMapHolder,
    private val rssService: RssService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val groups: StateFlow<List<PulseGroup>> =
        groupWithFeedsListUseCase.groupWithFeedListFlow
            .flatMapLatest { groupsWithFeeds ->
                val feeds = groupsWithFeeds.flatMap { it.feeds }
                val articleMapFlow = if (feeds.isEmpty()) {
                    flowOf(emptyMap<String, List<ArticleWithFeed>>())
                } else {
                    combine(
                        feeds.map { feed ->
                            articleDao.queryLatestArticlesFromFeed(
                                feedId = feed.id,
                                accountId = feed.accountId,
                                limit = ARTICLES_PER_FEED,
                            )
                        }
                    ) { articleLists ->
                        feeds.mapIndexed { index, feed -> feed.id to articleLists[index] }
                            .toMap()
                    }
                }

                articleMapFlow
                    .combine(diffMapHolder.diffMapSnapshotFlow) { articlesByFeed, _ ->
                        articlesByFeed
                    }
                    .combine(flowOf(groupsWithFeeds)) { articlesByFeed, currentGroups ->
                    currentGroups.map { groupWithFeeds ->
                        PulseGroup(
                            group = groupWithFeeds.group,
                            feeds = groupWithFeeds.feeds.map { feed ->
                                PulseFeed(
                                    feed = feed,
                                    articles = articlesByFeed[feed.id].orEmpty().map { article ->
                                        article.copy(
                                            article = article.article.copy(
                                                isUnread = diffMapHolder.checkIfUnread(article),
                                            )
                                        )
                                    },
                                )
                            },
                        )
                    }
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markAsRead(article: ArticleWithFeed) {
        diffMapHolder.updateDiff(article, isUnread = false)
    }

    fun toggleStarred(article: ArticleWithFeed) {
        viewModelScope.launch(ioDispatcher) {
            rssService.get().markAsStarred(
                articleId = article.article.id,
                isStarred = !article.article.isStarred,
            )
        }
    }

    fun sync() {
        rssService.get().doSyncOneTime()
    }

    fun commitDiffs() {
        diffMapHolder.commitDiffsToDb()
    }
}

data class PulseGroup(
    val group: Group,
    val feeds: List<PulseFeed>,
)

data class PulseFeed(
    val feed: Feed,
    val articles: List<ArticleWithFeed>,
)
