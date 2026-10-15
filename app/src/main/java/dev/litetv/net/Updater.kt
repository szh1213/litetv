package dev.litetv.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
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
}
