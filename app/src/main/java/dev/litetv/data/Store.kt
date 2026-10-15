package dev.litetv.data

import android.content.Context
import android.content.SharedPreferences
import dev.litetv.net.Episode
import dev.litetv.net.Http
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable

/**
 * 订阅源。合集链接不需要单独的解析接口：
 * 链接里带的 bvid 直接喂给 view，响应里的 ugc_season 就是整个合集。
 */
@Serializable
data class Subscription(
    val key: String,
    val title: String,
    val kind: Kind,
    /** VIDEO: bvid 或 aid；FAV_FOLDER: 收藏夹 media_id */
    val target: String,
    val addedAt: Long = 0,
) {
    enum class Kind { VIDEO, FAV_FOLDER }
}

@Serializable
data class Progress(val positionMs: Long = 0, val durationMs: Long = 0, val updatedAt: Long = 0)

/** 从任意 B 站链接里提取可用标识，不做网络请求 */
data class ParsedLink(val bvid: String = "", val aid: Long = 0) {
    val usable: Boolean get() = bvid.isNotBlank() || aid != 0L
}

object Link {
    private val BV = Regex("""BV[0-9A-Za-z]{10}""")
    private val AV = Regex("""av(\d{1,18})""", RegexOption.IGNORE_CASE)
    private val AID = Regex("""[?&/=]aid=(\d{1,18})""")

    fun parse(input: String): ParsedLink {
        val bv = BV.find(input)?.value ?: ""
        if (bv.isNotEmpty()) return ParsedLink(bvid = bv)
        val av = AV.find(input)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (av != null) return ParsedLink(aid = av)
        val aid = AID.find(input)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (aid != null) return ParsedLink(aid = aid)
        val bare = input.trim().toLongOrNull()
        return if (bare != null) ParsedLink(aid = bare) else ParsedLink()
    }
}

/** 一期用 SharedPreferences + JSON 存，够用；等进度/排序需求复杂了再换 Room */
object Store {
    private const val FILE = "litetv"
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        Http.cookie = sp.getString("cookie", "").orEmpty()
    }

    var cookie: String
        get() = sp.getString("cookie", "").orEmpty()
        set(value) {
            sp.edit().putString("cookie", value).apply()
            Http.cookie = value
        }

    var mid: Long
        get() = sp.getLong("mid", 0)
        set(value) = sp.edit().putLong("mid", value).apply()

    val loggedIn: Boolean get() = cookie.contains("SESSDATA")

    fun saveLogin(cookiePairs: List<String>, refreshToken: String) {
        val merged = (existingCookieMap().toList() + cookiePairs.map { pair ->
            pair.substringBefore(';').trim().split("=", limit = 2).let { it[0] to it[1] }
        }).toMap()
        val out = merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
        cookie = out
        sp.edit().putString("refresh_token", refreshToken).apply()
    }

    private fun existingCookieMap(): Map<String, String> =
        cookie.split(';').mapNotNull {
            val kv = it.trim().split("=", limit = 2)
            if (kv.size == 2) kv[0] to kv[1] else null
        }.toMap()

    fun logout() {
        cookie = ""
        mid = 0
        sp.edit().remove("refresh_token").apply()
    }

    private val subsJson get() = sp.getString("subs", "[]").orEmpty()

    fun subs(): List<Subscription> =
        runCatching { Http.json.decodeFromString<List<Subscription>>(subsJson) }.getOrDefault(emptyList())
            .sortedByDescending { it.addedAt }

    fun addSub(s: Subscription) {
        val next = subs().filter { it.key != s.key } + s.copy(addedAt = System.currentTimeMillis())
        sp.edit().putString("subs", Http.json.encodeToString(next)).apply()
    }

    fun removeSub(key: String) {
        val next = subs().filter { it.key != key }
        sp.edit().putString("subs", Http.json.encodeToString(next)).apply()
    }

    fun progressOf(bvid: String): Progress = runCatching {
        Http.json.decodeFromString<Progress>(sp.getString("p_$bvid", "").orEmpty())
    }.getOrNull() ?: Progress()

    fun saveProgress(ep: Episode, positionMs: Long) {
        if (ep.bvid.isEmpty() || positionMs <= 0) return
        val p = Progress(positionMs, ep.durationMs, System.currentTimeMillis())
        sp.edit().putString("p_${ep.progressKey}", Http.json.encodeToString(p)).apply()
    }
}
