package com.kenshin.vreader2.data.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.kenshin.vreader2.MainActivity
import com.kenshin.vreader2.VReaderApp
import com.kenshin.vreader2.data.repository.MangaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MangaRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val chapterId = inputData.getString(KEY_CHAPTER_ID) ?: return Result.failure()
        val mangaTitle = inputData.getString(KEY_MANGA_TITLE) ?: "Đang tải truyện"
        
        // Hiển thị thông báo tiền cảnh
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            // Có thể bị lỗi nếu OS đang quá tải hoặc cấm Foreground Service
        }

        return try {
            val chapter = repository.getChapterById(chapterId) ?: return Result.failure()
            val content = repository.getNovelContent(chapter) 
            repository.saveChapterContent(content)
            
            // Sau khi tải xong 1 chương, cập nhật lại thông báo để tăng thanh Progress
            updateNotification(chapter.mangaId, mangaTitle)
            
            android.util.Log.d("DownloadWorker", "✅ Tải thành công chương: ${chapter.name}")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DownloadWorker", "Error downloading chapter $chapterId", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val chapterId = inputData.getString(KEY_CHAPTER_ID) ?: ""
        val mangaTitle = inputData.getString(KEY_MANGA_TITLE) ?: "Đang tải truyện"
        
        // Lấy thông tin chương để tìm mangaId
        val chapter = repository.getChapterById(chapterId)
        val mangaId = chapter?.mangaId ?: ""
        
        return createForegroundInfo(mangaId, mangaTitle, 0, 100)
    }

    private suspend fun updateNotification(mangaId: String, mangaTitle: String) {
        val chapters = repository.getChaptersByManga(mangaId)
        val total = chapters.size
        val downloaded = chapters.count { it.isDownloaded }
        
        setForeground(createForegroundInfo(mangaId, mangaTitle, downloaded, total))
    }

    private fun createForegroundInfo(
        mangaId: String, 
        mangaTitle: String, 
        progress: Int, 
        max: Int
    ): ForegroundInfo {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(applicationContext, DownloadCancelReceiver::class.java).apply {
            putExtra("manga_id", mangaId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext, mangaId.hashCode(), cancelIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, VReaderApp.CHANNEL_ID)
            .setContentTitle(mangaTitle)
            .setContentText("Đang tải: $progress / $max chương")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(max, progress, false)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "HỦY", cancelPendingIntent)
            .build()

        // Sử dụng hashCode của mangaId để các chương cùng 1 truyện dùng chung 1 NotificationID
        val notificationId = mangaId.hashCode()
        return ForegroundInfo(notificationId, notification)
    }

    companion object {
        const val KEY_CHAPTER_ID = "chapter_id"
        const val KEY_MANGA_TITLE = "manga_title"
    }
}
