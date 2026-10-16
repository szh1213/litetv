package dev.litetv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.litetv.data.Store
import dev.litetv.data.Subscription
import dev.litetv.net.Episode
import dev.litetv.net.FavGroup
import dev.litetv.net.Http
import dev.litetv.ui.CollectionScreen
import dev.litetv.ui.FavEpisodesScreen
import dev.litetv.ui.HomeScreen
import dev.litetv.ui.LiteTvColors
import dev.litetv.ui.LoginScreen
import dev.litetv.ui.PlayerScreen
import dev.litetv.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    /** 播放器在 Activity 层拦截遥控器按键：盒子无焦点时 Compose 的 key 修饰符收不到事件 */
    var playerKeyHandler: ((KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (playerKeyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        Http.cookie = Store.cookie
        val pkg = packageManager.getPackageInfo(packageName, 0)
        val vName = pkg.versionName ?: "0.1.0"
        val vCode = pkg.versionCode.toInt()
        setContent {
            MaterialTheme(colorScheme = LiteTvColors) {
                Surface(Modifier.fillMaxSize(), color = LiteTvColors.background) {
                    App(vName, vCode)
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Login : Screen
    data object Settings : Screen
    data class Collection(val sub: Subscription) : Screen
    data class FavEpisodes(val sub: Subscription, val group: FavGroup) : Screen
    data class Player(val sub: Subscription, val episodes: List<Episode>, val index: Int, val group: FavGroup? = null) : Screen
}

@Composable
private fun App(versionName: String, versionCode: Int) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            onOpen = { sub -> screen = Screen.Collection(sub) },
            onLogin = { screen = Screen.Login },
            onSettings = { screen = Screen.Settings },
        )

        Screen.Settings -> {
            BackHandler { screen = Screen.Home }
            SettingsScreen(versionName, versionCode, onLoggedOut = { screen = Screen.Home })
        }

        Screen.Login -> {
            BackHandler { screen = Screen.Home }
            LoginScreen(onDone = { screen = Screen.Home })
        }

        is Screen.Collection -> {
            BackHandler { screen = Screen.Home }
            CollectionScreen(
                sub = s.sub,
                onBack = { screen = Screen.Home },
                onPlay = { eps, i -> screen = Screen.Player(s.sub, eps, i) },
                onOpenFav = { grp -> screen = Screen.FavEpisodes(s.sub, grp) },
            )
        }

        is Screen.FavEpisodes -> {
            BackHandler { screen = Screen.Collection(s.sub) }
            FavEpisodesScreen(
                group = s.group,
                onBack = { screen = Screen.Collection(s.sub) },
                onPlay = { eps, i -> screen = Screen.Player(s.sub, eps, i, s.group) },
            )
        }

        is Screen.Player -> {
            BackHandler {
                screen = if (s.group != null) Screen.FavEpisodes(s.sub, s.group)
                else Screen.Collection(s.sub)
            }
            PlayerScreen(
                episodes = s.episodes,
                startIndex = s.index,
                onBack = {
                    screen = if (s.group != null) Screen.FavEpisodes(s.sub, s.group)
                    else Screen.Collection(s.sub)
                },
            )
        }
    }
}
