package dev.litetv.net

import android.content.Context
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
     * 下载更新包到应用私有缓存目录（无需存储权限），边下边回调进度。
     * 完成后校验字节数与 ZIP 魔数，防止把错误页/半包拉给安装器。
     */
    suspend fun download(
        context: Context,
        info: VersionInfo,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (info.apkUrl.isBlank()) throw IllegalStateException("更新地址为空")
        val dir = File(context.cacheDir, "updates")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "litetv-${info.versionName.ifBlank { "update" }}.apk")
        client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("响应为空")
            val total = body.contentLength()
            var done = 0L
            var lastTick = 0L
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastTick > 120) {
                            lastTick = now
                            onProgress(done, total)
                        }
                    }
                }
            }
            if (total > 0 && done < total) throw IllegalStateException("下载不完整：$done/$total")
            val isZip = file.inputStream().use { it.read() == 0x50 && it.read() == 0x4B }
            if (!isZip) throw IllegalStateException("下载内容不是 APK")
            onProgress(done, total)
        }
        file
    }
}
