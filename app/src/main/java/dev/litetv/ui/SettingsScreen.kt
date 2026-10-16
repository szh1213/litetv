package dev.litetv.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.core.content.FileProvider
import dev.litetv.data.Store
import dev.litetv.net.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(versionName: String, versionCode: Int, onLoggedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var newVer by remember { mutableStateOf<Updater.VersionInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(Store.loggedIn) }
    var confirmingLogout by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var dl by remember { mutableStateOf(0L to 0L) }

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
                                        status = "发现新版本 v${r.info.versionName}，可下载安装"
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                FocusChip(
                    label = when {
                        downloading -> {
                            val (done, total) = dl
                            if (total > 0) "下载中 ${done * 100 / total}%"
                            else "下载中 ${done / 1024 / 1024}MB…"
                        }
                        else -> "下载 v${info.versionName}"
                    },
                    onClick = {
                        if (downloading) return@FocusChip
                        if (info.apkUrl.isBlank()) {
                            status = "更新地址为空"
                            return@FocusChip
                        }
                        if (!canInstallApk(context)) {
                            status = "请先允许本应用安装未知应用，再点击下载"
                            openUnknownSourcesSettings(context)
                            return@FocusChip
                        }
                        status = "正在下载 v${info.versionName}…"
                        downloading = true
                        dl = 0L to 0L
                        scope.launch {
                            runCatching {
                                Updater.download(context, info) { done, total ->
                                    withContext(Dispatchers.Main) { dl = done to total }
                                }
                            }.onSuccess { file ->
                                downloading = false
                                status = "下载完成，请在系统弹窗中确认安装"
                                runCatching { installApk(context, file) }
                                    .onFailure { status = "打开安装器失败：${it.message}，可改用浏览器下载" }
                            }.onFailure {
                                downloading = false
                                status = "下载失败：${it.message}，可改用浏览器下载"
                            }
                        }
                    },
                )
                FocusChip("浏览器下载") {
                    if (info.apkUrl.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (!loggedIn) "账号：未登录"
                else "账号：" + Store.uname.ifBlank { "已登录" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (loggedIn) {
                FocusChip(
                    label = if (confirmingLogout) "再点一次确认退出" else "退出登录",
                    onClick = {
                        if (!confirmingLogout) {
                            confirmingLogout = true
                        } else {
                            Store.logout()
                            loggedIn = false
                            confirmingLogout = false
                            status = "已退出登录"
                            newVer = null
                            onLoggedOut()
                        }
                    },
                )
            }
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

private fun canInstallApk(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

private fun openUnknownSourcesSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                )
            )
        }
    }
}

private fun installApk(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
