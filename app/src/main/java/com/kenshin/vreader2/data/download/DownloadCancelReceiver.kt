package com.kenshin.vreader2.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mangaId = intent.getStringExtra("manga_id") ?: return
        val workManager = WorkManager.getInstance(context)
        
        // Hủy toàn bộ các worker có tag download_manga_$mangaId
        workManager.cancelAllWorkByTag("download_manga_$mangaId")
        
        // Xóa thông báo (WorkManager sẽ tự xóa foreground notification khi worker bị cancel, 
        // nhưng ta có thể chủ động xóa theo ID nếu cần)
    }
}
