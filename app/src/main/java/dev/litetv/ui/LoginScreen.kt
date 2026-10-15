package dev.litetv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import dev.litetv.data.Store
import dev.litetv.net.BiliApi
import kotlinx.coroutines.delay

/**
 * B 站官方 Web 扫码登录。实测 GET passport.bilibili.com/x/passport-login/web/qrcode/generate
 * 即可，无需任何签名；成功时响应头下发 SESSDATA 等 Cookie。
 */
@Composable
fun LoginScreen(onDone: () -> Unit) {
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("正在生成二维码…") }
    var failed by remember { mutableStateOf(false) }
    var epoch by remember { mutableStateOf(0) }

    LaunchedEffect(epoch) {
        qrUrl = null
        failed = false
        status = "正在生成二维码…"
        runCatching { BiliApi.qrGenerate() }
            .onFailure {
                failed = true
                status = "生成失败：${it.message}"
            }
            .onSuccess { gen ->
                qrUrl = gen.url
                status = "用哔哩哔哩 App 扫这个码"
                while (true) {
                    delay(2000)
                    val r = runCatching { BiliApi.qrPoll(gen.qrcodeKey) }.getOrNull() ?: continue
                    val (poll, setCookies) = r
                    when (poll.code) {
                        0 -> {
                            Store.saveLogin(setCookies, poll.refreshToken)
                            Store.mid = runCatching { BiliApi.nav().mid }.getOrDefault(0)
                            status = "登录成功"
                            onDone()
                            return@LaunchedEffect
                        }

                        86090 -> status = "已扫码，请在手机上确认"
                        86101 -> status = "用哔哩哔哩 App 扫这个码"
                        86038 -> {
                            failed = true
                            status = "二维码已失效，请刷新"
                            return@LaunchedEffect
                        }

                        else -> status = poll.message.ifBlank { "等待扫码" }
                    }
                }
            }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("扫码登录 B 站", style = MaterialTheme.typography.headlineMedium)
            Text(status, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.onSurface)
            val url = qrUrl
            if (url != null) {
                Image(
                    bitmap = rememberQrBitmap(url),
                    contentDescription = "登录二维码",
                    modifier = Modifier.width(240.dp).height(240.dp),
                )
            } else if (!failed) {
                CircularProgressIndicator(modifier = Modifier.padding(60.dp))
            }
            if (failed) {
                TextButton(onClick = { epoch++ }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("刷新二维码")
                }
            }
        }
    }
}
