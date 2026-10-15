package dev.litetv.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.litetv.net.Updater
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(versionName: String, versionCode: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var newVer by remember { mutableStateOf<Updater.VersionInfo?>(null) }
    var checking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "当前版本 $versionName",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            FocusChip(
                label = if (checking) "检查中…" else "检查更新",
                onClick = {
                    if (checking) return@FocusChip
                    checking = true
                    status = null
                    newVer = null
                    scope.launch {
                        Updater.check(versionCode)
                            .onSuccess { r ->
                                when (r) {
                                    is Updater.CheckResult.UpToDate ->
                                        status = "已是最新版本"
                                    is Updater.CheckResult.NewVersion -> {
                                        newVer = r.info
                                        status = "发现新版本 v${r.info.versionName}，点击右侧下载"
                                    }
                                }
                            }
                            .onFailure { status = "检查失败：${it.message}" }
                        checking = false
                    }
                },
            )
        }
        status?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        newVer?.let { info ->
            FocusChip(
                label = "下载 v${info.versionName}",
                onClick = {
                    if (info.apkUrl.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
                    }
                },
            )
        }
    }
}

@Composable
private fun FocusChip(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .padding(start = 16.dp)
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
