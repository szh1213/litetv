package dev.litetv.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class BiliError(val code: Int, override val message: String) : Exception("bili code=$code message=$message")

/**
 * B 站 Web 端公开接口。全部 GET、不带任何签名（不碰 wbi / X-Bogus）。
 */
object Http {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    /** 登录后由 Store 注入；为空时接口仍可用，只是画质上限 720P */
    var cookie: String = ""

    fun headers(): Map<String, String> = buildMap {
        put("User-Agent", UA)
        put("Referer", "https://www.bilibili.com/")
        put("Origin", "https://www.bilibili.com")
        if (cookie.isNotBlank()) put("Cookie", cookie)
    }

    class Response(val text: String, val setCookies: List<String>)

    suspend fun raw(url: String): Response = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        headers().forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            Response(resp.body?.string() ?: "", resp.headers.values("Set-Cookie"))
        }
    }

    suspend fun <T> get(url: String, serializer: KSerializer<T>): T {
        val resp = raw(url)
        return unwrap(resp.text, serializer)
    }

    fun <T> unwrap(text: String, serializer: KSerializer<T>): T {
        val wrapper = json.decodeFromString(ApiResp.serializer(serializer), text)
        if (wrapper.code != 0) throw BiliError(wrapper.code, wrapper.message)
        return wrapper.data ?: throw BiliError(-999, "empty data")
    }

    /** 少数接口（pagelist）的 data 直接是数组，没有对象包装 */
    fun <T> unwrapArray(text: String, serializer: KSerializer<List<T>>): List<T> {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        val data = root["data"]?.toString() ?: return emptyList()
        return json.decodeFromString(serializer, data)
    }
}

/** 播放器需要的流地址 + 必须回带的请求头 */
data class Stream(
    val url: String,
    val durationMs: Long,
    val quality: Int,
    val codecId: Int,
    val headers: Map<String, String>,
)

object BiliApi {

    private const val API = "https://api.bilibili.com"
    private const val PASSPORT = "https://passport.bilibili.com"

    /** 播放地址必须带同一套 Cookie/Referer，否则 403 */
    private fun mediaHeaders() = mapOf(
        "User-Agent" to Http.UA,
        "Referer" to "https://www.bilibili.com/",
        "Cookie" to Http.cookie,
    )

    suspend fun nav(): NavData =
        Http.get("$API/x/web-interface/nav", serializer<NavData>())

    /** 一次请求同时拿到视频信息与整个合集列表（实测 /x/v2/section 已废弃） */
    suspend fun view(bvid: String, aid: Long = 0): ViewResult = Http.get(
        if (bvid.isNotBlank()) "$API/x/web-interface/view?bvid=$bvid"
        else "$API/x/web-interface/view?aid=$aid",
        serializer<ViewResult>(),
    )

    suspend fun episodes(bvid: String, aid: Long = 0): List<Episode> {
        val v = view(bvid, aid)
        val season = v.season
        val paid = v.rights?.pay == true
        return when {
            season != null && season.sections.isNotEmpty() ->
                season.sections.flatMap { it.episodes }.map {
                    Episode(
                        bvid = it.bvid,
                        cid = it.cid,
                        title = it.title,
                        durationMs = (it.arc?.duration.takeIf { d -> d != 0 } ?: v.duration) * 1000L,
                        paid = paid || it.attribute != 0,
                    )
                }

            v.pages.size > 1 ->
                v.pages.map {
                    Episode(
                        bvid = v.bvid,
                        cid = it.cid,
                        title = it.part,
                        durationMs = it.duration * 1000L,
                        paid = paid,
                    )
                }

            else -> listOf(
                Episode(
                    bvid = v.bvid,
                    cid = v.pages.firstOrNull()?.cid ?: 0,
                    title = v.title,
                    durationMs = v.duration * 1000L,
                    paid = paid,
                )
            )
        }
    }

    suspend fun pages(bvid: String, aid: Long = 0): List<PageList> {
        val text = Http.raw(
            if (bvid.isNotBlank()) "$API/x/player/pagelist?bvid=$bvid"
            else "$API/x/player/pagelist?aid=$aid"
        ).text
        return Http.unwrapArray(text, serializer<List<PageList>>())
    }

    suspend fun firstCid(bvid: String): Long = pages(bvid).firstOrNull()?.cid ?: 0

    /**
     * 实测：platform=html5 返回单个 MP4、video_codecid=7(H.264)，避免 DASH 音视频分离；
     * qn=64 请求 720P（无 Cookie 时服务端自动降回 360P）；high_quality=1 只给 360P，勿用。
     */
    suspend fun stream(bvid: String, cidIn: Long): Stream {
        val cid = if (cidIn > 0) cidIn else firstCid(bvid)
        val p = Http.get(
            "$API/x/player/playurl?bvid=$bvid&cid=$cid&platform=html5&qn=64&fnver=0&fourk=0",
            serializer<PlayUrl>(),
        )
        val d = p.durl.firstOrNull() ?: throw BiliError(-1, "没有 durl：付费内容或未登录")
        return Stream(
            url = d.url,
            durationMs = d.length,
            quality = p.quality,
            codecId = p.videoCodecId,
            headers = mediaHeaders(),
        )
    }

    suspend fun favFolders(mid: Long): List<FavFolder> =
        Http.get("$API/x/v3/fav/folder/created/list-all?up_mid=$mid", serializer<FavFolderList>()).list

    suspend fun favItems(mediaId: Long, pn: Int = 1, ps: Int = 20): List<FavMedia> =
        Http.get(
            "$API/x/v3/fav/resource/list?media_id=$mediaId&pn=$pn&ps=$ps&platform=web",
            serializer<FavResourceList>(),
        ).medias

    /** 实测：必须 GET，且域名只能是 passport.bilibili.com */
    suspend fun qrGenerate(): QrGenerate =
        Http.get("$PASSPORT/x/passport-login/web/qrcode/generate", serializer<QrGenerate>())

    suspend fun qrPoll(key: String): Pair<QrPoll, List<String>> {
        val resp = Http.raw("$PASSPORT/x/passport-login/web/qrcode/poll?qrcode_key=$key")
        val poll = Http.json.decodeFromString(ApiResp.serializer(serializer<QrPoll>()), resp.text).data
        return (poll ?: QrPoll()) to resp.setCookies
    }
}
