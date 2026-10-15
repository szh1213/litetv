package dev.litetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.litetv.data.Link
import dev.litetv.data.Store
import dev.litetv.data.Subscription
import dev.litetv.net.BiliApi
import dev.litetv.net.FavFolder
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpen: (Subscription) -> Unit,
    onLogin: () -> Unit,
    onSettings: () -> Unit = {},
) {
    var subs by remember { mutableStateOf(Store.subs()) }
    var showAddLink by remember { mutableStateOf(false) }
    var showFav by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("LiteTV", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(24.dp))
            ActionChip(
                label = if (Store.loggedIn) "已登录" else "未登录 · 扫码登录",
                onClick = { if (!Store.loggedIn) onLogin() },
            )
            ActionChip("从收藏夹导入") { showFav = !showFav }
            ActionChip("添加合集链接") { showAddLink = !showAddLink }
            ActionChip("设置", onClick = onSettings)
        }

        tip?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }

        if (showFav) {
            FavImportPanel(
                onAdded = { f ->
                    Store.addSub(
                        Subscription(
                            key = "fav_${f.id}",
                            title = f.title,
                            kind = Subscription.Kind.FAV_FOLDER,
                            target = f.id.toString(),
                        )
                    )
                    subs = Store.subs()
                    showFav = false
                    tip = "已订阅收藏夹「${f.title}」（${f.count} 条）"
                },
                onNeedLogin = onLogin,
            )
        }

        if (showAddLink) {
            AddLinkPanel(onAdd = { input ->
                val p = Link.parse(input)
                if (!p.usable) {
                    tip = "链接里没找到 BV 号或 aid：$input"
                } else {
                    scope.launch {
                        runCatching {
                            val v = BiliApi.view(p.bvid, p.aid)
                            Store.addSub(
                                Subscription(
                                    key = "video_${if (p.bvid.isNotBlank()) p.bvid else p.aid}",
                                    title = v.title,
                                    kind = Subscription.Kind.VIDEO,
                                    target = if (p.bvid.isNotBlank()) p.bvid else p.aid.toString(),
                                )
                            )
                        }.onSuccess {
                            subs = Store.subs()
                            showAddLink = false
                            tip = null
                        }.onFailure { tip = "拉取失败：${it.message}" }
                    }
                }
            })
        }

        if (subs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有订阅。手机上把想看的合集收藏到一个收藏夹，再回来点「从收藏夹导入」。",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(subs, key = { it.key }) { s ->
                    SubCard(s = s, onClick = { onOpen(s) })
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SubCard(s: Subscription, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(s.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (s.kind == Subscription.Kind.FAV_FOLDER) "收藏夹" else "合集/分P",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun AddLinkPanel(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("粘贴 bilibili.com 链接 / BV号 / av号") },
            modifier = Modifier.weight(1f).height(96.dp),
        )
        TextButton(onClick = { onAdd(text) }, modifier = Modifier.padding(start = 12.dp)) { Text("订阅") }
    }
}

@Composable
private fun FavImportPanel(onAdded: (FavFolder) -> Unit, onNeedLogin: () -> Unit) {
    if (!Store.loggedIn) {
        LaunchedEffect(Unit) { onNeedLogin() }
        return
    }
    var folders by remember { mutableStateOf<List<FavFolder>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { BiliApi.favFolders(Store.mid) }
            .onSuccess { folders = it }
            .onFailure { err = it.message }
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        err?.let { Text("读取收藏夹失败：$it", color = MaterialTheme.colorScheme.primary) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            folders.take(8).forEach { f ->
                ActionChip(label = "${f.title} (${f.count})", onClick = { onAdded(f) })
            }
        }
    }
}
