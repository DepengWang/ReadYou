package me.ash.reader.domain.service

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.pulse.PulseThumbnailStore
import me.ash.reader.ui.ext.extractDomain

@HiltWorker
class PulseThumbnailWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val articleDao: ArticleDao,
    private val imageLoader: ImageLoader,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val accountId = inputData.getInt(ACCOUNT_ID_KEY, -1)
        if (accountId == -1) return Result.failure()

        val articles = withContext(ioDispatcher) {
            articleDao.queryArticlesWithImages(accountId)
        }
        val semaphore = Semaphore(2)

        coroutineScope {
            articles.map { article ->
                async(ioDispatcher) {
                    semaphore.withPermit { generateIfMissing(article.img!!, article.link) }
                }
            }.awaitAll()
        }

        // A failed image should not make the whole sync chain fail. The next
        // sync will retry missing files without blocking article reading.
        return Result.success()
    }

    private suspend fun generateIfMissing(imageUrl: String, articleLink: String) {
        val target = PulseThumbnailStore.fileFor(applicationContext, articleLink)
        if (target.isFile && target.length() > 0L) return

        val request = ImageRequest.Builder(applicationContext)
            .data(imageUrl)
            .apply {
                imageUrl.extractDomain()?.let { addHeader("Referer", it) }
            }
            .size(Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE))
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .build()

        val bitmap = imageLoader.execute(request).drawable?.toBitmapOrNull() ?: return
        try {
            writeWebpAtomically(bitmap, target)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun writeWebpAtomically(bitmap: Bitmap, target: File) {
        withContext(ioDispatcher) {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.tmp")
            runCatching {
                temporary.outputStream().use { output ->
                    val format = if (android.os.Build.VERSION.SDK_INT >= 30) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    check(bitmap.compress(format, 82, output))
                }
                if (!temporary.renameTo(target)) {
                    temporary.delete()
                }
            }.onFailure {
                temporary.delete()
            }
        }
    }

    companion object {
        private const val ACCOUNT_ID_KEY = "accountId"
        private const val THUMBNAIL_SIZE = 480
        private const val WORK_NAME_PREFIX = "PULSE_THUMBNAILS_"

        fun enqueue(workManager: WorkManager, accountId: Int) {
            workManager.enqueueUniqueWork(
                WORK_NAME_PREFIX + accountId,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PulseThumbnailWorker>()
                    .setInputData(androidx.work.workDataOf(ACCOUNT_ID_KEY to accountId))
                    .build(),
            )
        }
    }
}
