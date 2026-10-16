package dev.litetv.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * B 站同一字段在不同接口/不同视频上会给布尔、数字或字符串三种形态
 * （实测 rights.pay 是 0/1，但别处见过 false）。严格 Boolean 会直接抛 JSON 异常。
 */
object LenientBool : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientBool", PrimitiveKind.BOOLEAN)

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)

    override fun deserialize(decoder: Decoder): Boolean {
        val json = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val el = json.decodeJsonElement() as? JsonPrimitive ?: return false
        return when {
            el.isString -> el.content.trim().equals("true", true) || el.content.trim() == "1"
            else -> el.booleanOrNull ?: (el.content.trim() != "0" && el.content.trim().isNotEmpty())
        }
    }
}

@Serializable
data class ApiResp<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
)

@Serializable
data class NavData(
    @SerialName("isLogin") val isLogin: Boolean = false,
    val mid: Long = 0,
    val vipStatus: Int = 0,
)

@Serializable
data class ViewResult(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pages: List<PageItem> = emptyList(),
    @SerialName("ugc_season") val season: Season? = null,
    val rights: Rights? = null,
    /** UP 主充电专属：未充电只能看试看片段（实测佩奇合集 durl 只给 3 分钟） */
    @SerialName("is_upower_exclusive")
    @Serializable(with = LenientBool::class)
    val upowerExclusive: Boolean = false,
    @SerialName("is_upower_preview")
    @Serializable(with = LenientBool::class)
    val upowerPreview: Boolean = false,
)

@Serializable
data class PageItem(
    val cid: Long = 0,
    val page: Int = 0,
    val part: String = "",
    val duration: Int = 0,
)

@Serializable
data class Rights(
    @Serializable(with = LenientBool::class) val pay: Boolean = false,
)

@Serializable
data class Season(
    val title: String = "",
    val sections: List<Section> = emptyList(),
)

@Serializable
data class Section(
    val title: String = "",
    val episodes: List<SeasonEpisode> = emptyList(),
)

@Serializable
data class SeasonEpisode(
    val aid: Long = 0,
    val cid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    /** bit 位标记，付费语义尚未实测，先原样保留 */
    val attribute: Int = 0,
    val arc: ArcLite? = null,
)

@Serializable
data class ArcLite(
    val pic: String = "",
    val duration: Int = 0,
)

@Serializable
data class PlayUrl(
    val quality: Int = 0,
    val format: String = "",
    /** 正片总时长。试看时它仍是完整时长，而 durl[0].length 只到试看终点 */
    val timelength: Long = 0,
    @SerialName("video_codecid") val videoCodecId: Int = 0,
    @SerialName("accept_quality") val acceptQuality: List<Int> = emptyList(),
    val durl: List<Durl> = emptyList(),
)

@Serializable
data class Durl(
    val url: String = "",
    val size: Long = 0,
    /** 毫秒 */
    val length: Long = 0,
)

@Serializable
data class PageList(val cid: Long = 0, val page: Int = 0, val part: String = "", val duration: Int = 0)

@Serializable
data class FavFolder(
    val id: Long = 0,
    val title: String = "",
    @SerialName("media_count") val count: Int = 0,
)

@Serializable
data class FavFolderList(
    val list: List<FavFolder> = emptyList(),
)

@Serializable
data class FavMedia(
    val id: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val duration: Int = 0,
    val cover: String = "",
)

@Serializable
data class FavResourceList(
    val medias: List<FavMedia> = emptyList(),
)

@Serializable
data class QrGenerate(
    val url: String = "",
    @SerialName("qrcode_key") val qrcodeKey: String = "",
)

@Serializable
data class QrPoll(
    val url: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    /** 86101 未扫码 / 86090 已扫码待确认 / 86038 二维码失效 / 0 成功 */
    val code: Int = -1,
    val message: String = "",
)

/** 合集里的一集。cid=0 表示要播放时再补查（收藏夹来源没有 cid） */
data class Episode(
    val bvid: String,
    val cid: Long,
    val title: String,
    val durationMs: Long = 0,
    val paid: Boolean = false,
    /** UP 主充电专属，未充电播放时只会拿到试看片段 */
    val chargingOnly: Boolean = false,
) {
    /** 同一 bvid 的多个分P 各自存进度，必须用 cid 区分 */
    val progressKey: String get() = if (cid > 0) cid.toString() else bvid
}

/** 收藏夹一级条目：单P 视频或多分P 合集（episodes 为其分P 展开） */
data class FavGroup(
    val title: String,
    val bvid: String,
    val episodes: List<Episode>,
)
