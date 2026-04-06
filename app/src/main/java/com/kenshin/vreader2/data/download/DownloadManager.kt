package com.kenshin.vreader2.data.download

import android.content.Context
import androidx.work.*
import com.kenshin.vreader2.domain.model.Chapter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueueDownload(mangaTitle: String, chapters: List<Chapter>) {
        chapters.forEach { chapter ->
            val data = Data.Builder()
                .putString(DownloadWorker.KEY_CHAPTER_ID, chapter.id)
                .putString(DownloadWorker.KEY_MANGA_TITLE, mangaTitle)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("download_manga_${chapter.mangaId}")
                .build()

            // Sử dụng KEEP để nếu đang tải thì không tải lại trùng lặp
            workManager.enqueueUniqueWork(
                "download_chapter_${chapter.id}",
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }
    }

    fun getWorkInfosFlow(mangaId: String) =
        workManager.getWorkInfosByTagFlow("download_manga_$mangaId")

    fun cancelDownloads(mangaId: String) {
        workManager.cancelAllWorkByTag("download_manga_$mangaId")
    }
}
