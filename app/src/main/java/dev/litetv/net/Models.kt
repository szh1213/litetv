package dev.litetv.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val pay: Boolean = false,
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
