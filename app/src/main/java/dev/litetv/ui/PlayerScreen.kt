package dev.litetv.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import dev.litetv.MainActivity
import dev.litetv.data.Store
import dev.litetv.net.BiliApi
import dev.litetv.net.BiliError
import dev.litetv.net.Episode
import dev.litetv.net.Http

/**
 * 遥控器（Activity 层拦截）：上/下=上一集/下一集，左/右=快退/快进 15s，
 * 确认/回车/空格=播放暂停，返回=退出。按键时点亮 Media3 控制条作为反馈。
 * 只喂 playurl 给的单文件 MP4（H.264），走硬解；不做 DASH、不做多播放器兜底。
 */
@Composable
fun PlayerScreen(
    episodes: List<Episode>,
    startIndex: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, episodes.lastIndex)) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var quality by remember { mutableStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pv by remember { mutableStateOf<PlayerView?>(null) }

    val player = remember { ExoPlayer.Builder(context).build() }

    fun saveProgress() = Store.saveProgress(episodes[index], player.currentPosition)

    val activity = context as? MainActivity
    DisposableEffect(activity) {
        activity?.playerKeyHandler = { ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) {
                false
            } else {
                when (ev.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (index > 0) {
                            saveProgress()
                            index--
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (index < episodes.lastIndex) {
                            saveProgress()
                            index++
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        player.seekTo((player.currentPosition - 15_000).coerceAtLeast(0))
                        pv?.showController()
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        val d = player.duration
                        val target = player.currentPosition + 15_000
                        player.seekTo(if (d > 0) target.coerceAtMost(d) else target)
                        pv?.showController()
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    -> {
                        player.playWhenReady = !player.playWhenReady
                        pv?.showController()
                        true
                    }

                    else -> false
                }
            }
        }
        onDispose { activity?.playerKeyHandler = null }
    }

    LaunchedEffect(index) {
        val ep = episodes[index]
        loading = true
        error = null
        notice = null
        runCatching { BiliApi.stream(ep.bvid, ep.cid) }
            .onSuccess { s ->
                quality = s.quality
                // 服务器给的片段明显短于正片 = 试看：充电专属或未解锁付费内容
                val full = if (s.fullDurationMs > 0) s.fullDurationMs else ep.durationMs
                notice = if (s.isPreview) {
                    val tail = "仅能试看 ${fmt(s.durationMs)}（完整 ${fmt(full)}）"
                    if (ep.chargingOnly) "UP 主充电专属，$tail；给该 UP 主充电后可看完整版"
                    else "该视频未解锁（充电专属或付费内容），$tail"
                } else null
                // 流地址强校验 Referer/Cookie，必须回带给播放器，否则 403
                val dsFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(Http.UA)
                    .setDefaultRequestProperties(
                        mapOf(
                            "Referer" to "https://www.bilibili.com/",
                            "Cookie" to Http.cookie,
                        )
                    )
                val source = ProgressiveMediaSource.Factory(dsFactory)
                    .createMediaSource(MediaItem.fromUri(s.url))
                player.setMediaSource(source)
                val resume = Store.progressOf(ep.progressKey).positionMs
                if (resume > 10_000 && resume < s.durationMs) player.seekTo(resume)
                player.prepare()
                player.playWhenReady = true
                loading = false
            }
            .onFailure { t ->
                loading = false
                error = if (t is BiliError) {
                    if (t.code == 87008) "该视频为充电专属内容，无法播放"
                    else "取流失败 code=${t.code} ${t.message}"
                } else "取流失败：${t.message}"
            }
    }

    DisposableEffect(index) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && index < episodes.lastIndex) index++
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose {
            saveProgress()
            player.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    setControllerShowTimeoutMs(4000)
                    setPlayer(player)
                }.also { pv = it }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        error?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(24.dp))
            }
        }

        val ep = episodes[index]
        Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Text(
                text = buildString {
                    append("${index + 1}/${episodes.size}  ${ep.title}")
                    if (quality > 0) append("   [${quality}P]")
                    if (ep.paid) append("   付费内容")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
            notice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
