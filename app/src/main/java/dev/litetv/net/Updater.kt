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
    /** 依次尝试：jsDelivr CDN（国内可直连；实测带 @branch 会 503，默认分支写法才 200）→ GitHub raw（部分网络被 DNS 污染） */
    const val JSDELIVR_URL =
        "https://cdn.jsdelivr.net/gh/szh1213/litetv/version.json"
    const val RAW_URL =
        "https://raw.githubusercontent.com/szh1213/litetv/main/version.json"
    val CHECK_URLS = listOf(JSDELIVR_URL, RAW_URL)

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
            for (url in CHECK_URLS) {
                try {
                    val text = client.newCall(Request.Builder().url(url).build())
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
