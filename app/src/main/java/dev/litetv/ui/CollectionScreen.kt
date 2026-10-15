package dev.litetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.litetv.data.Store
import dev.litetv.data.Subscription
import dev.litetv.net.BiliApi
import dev.litetv.net.Episode
import dev.litetv.net.FavGroup
import dev.litetv.net.FavMedia

/**
 * 合集剧集页。VIDEO 订阅直接列出各集点击即播；
 * FAV_FOLDER 订阅列出收藏夹里的视频（合集），多分P 的点进 onOpenFav 二级页再看分P。
 */
@Composable
fun CollectionScreen(
    sub: Subscription,
    onBack: () -> Unit,
    onPlay: (List<Episode>, Int) -> Unit,
    onOpenFav: (FavGroup) -> Unit = {},
) {
    var eps by remember { mutableStateOf<List<Episode>?>(null) }
    var groups by remember { mutableStateOf<List<FavGroup>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sub.key) {
        eps = null
        groups = null
        err = null
        if (sub.kind == Subscription.Kind.VIDEO) {
            runCatching {
                val bvid = if (sub.target.startsWith("BV")) sub.target else ""
                val aid = if (bvid.isEmpty()) sub.target.toLongOrNull() ?: 0 else 0
                BiliApi.episodes(bvid, aid)
            }.onSuccess { eps = it }.onFailure { err = it.message }
        } else {
            runCatching {
                val mediaId = sub.target.toLongOrNull() ?: 0
                // 实测该接口 ps 上限 20，传大值直接 -400，只能翻页
                val all = mutableListOf<FavMedia>()
                var pn = 1
                while (pn <= 25) {
                    val batch = BiliApi.favItems(mediaId, pn = pn, ps = 20)
                    all += batch
                    if (batch.size < 20) break
                    pn++
                }
                all.map { m ->
                    val pages = BiliApi.pages(m.bvid)
                    FavGroup(
                        title = m.title,
                        bvid = m.bvid,
                        episodes = if (pages.size > 1) pages.map { pg ->
                            Episode(
                                bvid = m.bvid,
                                cid = pg.cid,
                                title = "P${pg.page} ${pg.part.ifEmpty { m.title }}",
                                durationMs = pg.duration * 1000L,
                            )
                        }
                        else listOf(
                            Episode(
                                bvid = m.bvid,
                                cid = pages.firstOrNull()?.cid ?: 0,
                                title = m.title,
                                durationMs = m.duration * 1000L,
                            )
                        ),
                    )
                }
            }.onSuccess { groups = it }.onFailure { err = it.message }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                sub.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp),
            )
            SpacerText(
                when {
                    eps != null -> "共 ${eps!!.size} 集"
                    groups != null -> "共 ${groups!!.size} 个视频"
                    else -> ""
                }
            )
        }

        err?.let {
            Text("加载失败：$it（付费内容或未登录会取不到流）", color = MaterialTheme.colorScheme.primary)
        }

        val e = eps
        val g = groups
        when {
            e == null && g == null && err == null -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            e != null -> LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(e) { i, ep -> EpisodeRow(i, ep) { onPlay(e, i) } }
            }

            g != null -> LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(g) { i, grp -> FavGroupRow(i, grp) {
                    if (grp.episodes.size == 1) onPlay(grp.episodes, 0) else onOpenFav(grp)
                } }
            }
        }
    }
}

/** 收藏夹二级页：某个多分P 视频的分P 列表 */
@Composable
fun FavEpisodesScreen(
    group: FavGroup,
    onBack: () -> Unit,
    onPlay: (List<Episode>, Int) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                group.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp),
            )
            SpacerText("共 ${group.episodes.size} 集")
        }

        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(group.episodes) { i, ep -> EpisodeRow(i, ep) { onPlay(group.episodes, i) } }
        }
    }
}

@Composable
private fun SpacerText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 16.dp),
    )
}

@Composable
private fun RowShell(focused: Boolean, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (focused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun FavGroupRow(index: Int, group: FavGroup, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val multi = group.episodes.size > 1
    RowShell(focused) {
        Row(
            Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
        ) {
            Text(
                index.plus(1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(
                group.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            )
            if (multi) {
                Text(
                    "共${group.episodes.size}P",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp),
                )
            } else {
                val saved = Store.progressOf(group.episodes[0].progressKey).positionMs
                Text(
                    text = if (saved > 0) "${fmt(saved)} / ${fmt(group.episodes[0].durationMs)}" else fmt(group.episodes[0].durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(index: Int, ep: Episode, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val saved = Store.progressOf(ep.progressKey).positionMs
    RowShell(focused) {
        Row(
            Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
        ) {
            Text(
                "%02d".format(index + 1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(
                ep.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            )
            if (ep.paid) Text("付费", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
            Text(
                text = if (saved > 0) "${fmt(saved)} / ${fmt(ep.durationMs)}" else fmt(ep.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            )
        }
    }
}
