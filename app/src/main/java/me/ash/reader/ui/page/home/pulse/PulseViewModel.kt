package me.ash.reader.ui.page.home.pulse

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.domain.data.DiffMapHolder
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.RssService
import me.ash.reader.domain.service.SyncWorker
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.pulse.PulseThumbnailStore
import me.ash.reader.ui.ext.dataStore

// Keep the first Pulse render small. More articles can be added later without
// making the initial database combine and image work wait on a larger window.
private const val ARTICLES_PER_FEED = 6
private const val LAST_GROUP_UNSET = "__pulse_last_group_unset__"

@HiltViewModel
class PulseViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val diffMapHolder: DiffMapHolder,
    private val rssService: RssService,
    private val workManager: WorkManager,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val lastGroupKey = stringPreferencesKey("pulseLastGroupId")
    private val loadedGroupIds = MutableStateFlow<Set<String>>(emptySet())
    private var syncAllJob: Job? = null
    private val _syncAllInProgress = MutableStateFlow(false)
    val syncAllInProgress: StateFlow<Boolean> = _syncAllInProgress
    private val _syncCompletionVersion = MutableStateFlow(0)
    val syncCompletionVersion: StateFlow<Int> = _syncCompletionVersion

    val refreshingFeedIds: StateFlow<Set<String>> =
        workManager
            .getWorkInfosByTagFlow(SyncWorker.SYNC_TAG)
            .map { workInfos ->
                workInfos
                    .asSequence()
                    .filter { workInfo ->
                        workInfo.state == WorkInfo.State.ENQUEUED ||
                            workInfo.state == WorkInfo.State.RUNNING ||
                            workInfo.state == WorkInfo.State.BLOCKED
                    }
                    .flatMap { workInfo -> workInfo.tags.asSequence() }
                    .filter { tag -> tag.startsWith(SyncWorker.FEED_TAG_PREFIX) }
                    .map { tag -> tag.removePrefix(SyncWorker.FEED_TAG_PREFIX) }
                    .toSet()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val lastGroupId: StateFlow<String> =
        context.dataStore.data
            .map { preferences -> preferences[lastGroupKey] }
            .map { it ?: "" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LAST_GROUP_UNSET)

    val groups: StateFlow<List<PulseGroup>> =
        rssService.flow()
            .flatMapLatest { service -> service.pullFeeds() }
            .combine(loadedGroupIds) { groupsWithFeeds, loadedIds ->
                groupsWithFeeds to loadedIds
            }
            .flatMapLatest { (groupsWithFeeds, loadedIds) ->
                val feeds = groupsWithFeeds
                    .filter { it.group.id in loadedIds }
                    .flatMap { it.feeds }
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
                    // groupsWithFeeds is already captured by this flatMapLatest
                    // branch. Avoid a second one-shot Flow combine on every
                    // article/status emission.
                    .map { articlesByFeed ->
                    val thumbnailPaths = withContext(ioDispatcher) {
                        articlesByFeed.values
                            .flatten()
                            .associate { article ->
                                article.article.id to PulseThumbnailStore
                                    .existing(context, article.article.link)
                                    ?.absolutePath
                            }
                    }
                    groupsWithFeeds.map { groupWithFeeds ->
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
                                    thumbnailPaths = articlesByFeed[feed.id]
                                        .orEmpty()
                                        .associate { article ->
                                            article.article.id to thumbnailPaths[article.article.id]
                                        },
                                )
                            },
                        )
                    }
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun activateGroup(groupId: String) {
        loadedGroupIds.update { it + groupId }
        if (lastGroupId.value == groupId) return
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[lastGroupKey] = groupId
            }
        }
    }

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
        syncAllFeeds(null)
    }

    fun refreshFeed(feed: Feed) {
        if (refreshingFeedIds.value.contains(feed.id)) return
        rssService.get().doSyncOneTime(
            accountId = feed.accountId,
            feedId = feed.id,
        )
    }

    fun syncAllFeeds(preferredGroupId: String?) {
        if (syncAllJob?.isActive == true) return
        syncAllJob = viewModelScope.launch(ioDispatcher) {
            _syncAllInProgress.value = true
            try {
                val currentGroups = groups.value
                val orderedGroups = buildList {
                    currentGroups.firstOrNull { it.group.id == preferredGroupId }?.let(::add)
                    currentGroups
                        .filterNot { it.group.id == preferredGroupId }
                        .forEach(::add)
                }
                orderedGroups
                    .flatMap { group -> group.feeds.map { it.feed } }
                    .distinctBy { feed -> feed.id }
                    .forEach { feed ->
                        val workId = rssService.get().doSyncOneTime(
                            accountId = feed.accountId,
                            feedId = feed.id,
                        )
                        workManager
                            .getWorkInfoByIdFlow(workId)
                            .filterNotNull()
                            .first { workInfo ->
                                workInfo.state == WorkInfo.State.SUCCEEDED ||
                                    workInfo.state == WorkInfo.State.FAILED ||
                                    workInfo.state == WorkInfo.State.CANCELLED
                            }
                    }
                _syncCompletionVersion.update { it + 1 }
            } finally {
                _syncAllInProgress.value = false
            }
        }
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
    val thumbnailPaths: Map<String, String?> = emptyMap(),
)
