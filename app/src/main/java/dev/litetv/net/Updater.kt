package dev.litetv.net

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 检查更新。直接 GET raw JSON，无签名。
 * 注意：绝不能复用 Http.client —— 那会把 B 站 Cookie 发到 GitHub。
 */
object Updater {
    /** 依次尝试：Gitee（国内直连、同步自 GitHub）→ jsDelivr CDN → GitHub raw（部分网络被 DNS 污染） */
    const val GITEE_URL =
        "https://gitee.com/szh1213/litetv/raw/main/version.json"
    const val JSDELIVR_URL =
        "https://cdn.jsdelivr.net/gh/szh1213/litetv/version.json"
    const val RAW_URL =
        "https://raw.githubusercontent.com/szh1213/litetv/main/version.json"
    val CHECK_URLS = listOf(GITEE_URL, JSDELIVR_URL, RAW_URL)

    @Serializable
    data class VersionInfo(
        val versionCode: Int = 0,
        val versionName: String = "",
        val apkUrl: String = "",
    )

    sealed interface CheckResult {
        data object UpToDate : CheckResult
        data class NewVersion(val info: VersionInfo) : CheckResult
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersionCode: Int): Result<CheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            var lastError: Exception? = null
            // jsDelivr 会缓存默认分支引用数小时，加时间戳查询串强制回源
            val cb = "?cb=" + System.currentTimeMillis()
            for (url in CHECK_URLS) {
                try {
                    val text = client.newCall(Request.Builder().url(url + cb).build())
                        .execute().use { it.body?.string().orEmpty() }
                    val info = Http.json.decodeFromString(serializer<VersionInfo>(), text)
                    return@runCatching if (info.versionCode > currentVersionCode) {
                        CheckResult.NewVersion(info)
                    } else {
                        CheckResult.UpToDate
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw lastError ?: IllegalStateException("所有更新源均不可用")
        }
    }

    /**
     * 把更新包交给系统下载器（DownloadManager）下载，返回系统任务 id。
     * 文件落在应用专属外部目录（Android/data/<pkg>/files/Download/），全版本无需任何权限；
     * 安装由系统完成——本 App 只发起下载和显示进度，绝不自行安装。
     */
    fun enqueueDownload(context: Context, info: VersionInfo): Long {
        val name = "litetv-${info.versionName.ifBlank { "update" }}.apk"
        // 同名旧包先删：部分系统的下载器遇到已存在的目标文件会直接报错
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name).delete()
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("LiteTV v${info.versionName}")
            .setDescription("下载完成后点按通知，由系统完成安装")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    data class DownloadProgress(val status: Int, val done: Long, val total: Long) {
        val succeeded get() = status == DownloadManager.STATUS_SUCCESSFUL
        val failed get() = status == DownloadManager.STATUS_FAILED
    }

    suspend fun queryProgress(context: Context, id: Long): DownloadProgress =
        withContext(Dispatchers.IO) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (!c.moveToFirst()) return@withContext DownloadProgress(DownloadManager.STATUS_FAILED, 0, 0)
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                DownloadProgress(status, done, total.coerceAtLeast(0))
            }
        }
}
