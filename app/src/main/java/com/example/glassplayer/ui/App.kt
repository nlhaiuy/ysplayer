package com.example.glassplayer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.glassplayer.PlayerViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun GlassPlayerApp(vm: PlayerViewModel) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val state by vm.state.collectAsStateWithLifecycle()
        val haze = remember { HazeState() }
        var showNowPlaying by rememberSaveable { mutableStateOf(false) }
        val current = state.tracks.firstOrNull { it.id == state.currentId }

        val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(vm::onFolderPicked)
        }

        BackHandler(enabled = showNowPlaying) { showNowPlaying = false }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // 被玻璃模糊的内容层：背景 + 列表。玻璃控件本身不能放进这一层。
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                GlassBackdrop(current, Modifier.fillMaxSize())
                LibraryList(
                    tracks = state.tracks,
                    currentId = state.currentId,
                    scanning = state.scanning,
                    onPlay = { track ->
                        if (track.id == state.currentId) {
                            showNowPlaying = true
                            if (!state.playing) vm.togglePlay()
                        } else {
                            vm.playTrack(track.id)
                        }
                    },
                )
            }

            // 右上角悬浮玻璃按钮
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.hasFolder) {
                    GlassIconButton(Icons.Rounded.Refresh, "重新扫描", haze, size = 44.dp) { vm.rescan() }
                }
                GlassIconButton(Icons.Rounded.FolderOpen, "选择文件夹", haze, size = 44.dp) {
                    pickFolder.launch(null)
                }
            }

            if (state.tracks.isEmpty() && !state.scanning) {
                EmptyState(haze, onPick = { pickFolder.launch(null) })
            }
            if (state.tracks.isEmpty() && state.scanning) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }

            // 悬浮迷你播放器
            if (current != null) {
                AnimatedVisibility(
                    visible = !showNowPlaying,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(spring(0.8f, 380f)) { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    MiniPlayer(
                        track = current,
                        playing = state.playing,
                        haze = haze,
                        onOpen = { showNowPlaying = true },
                        onToggle = vm::togglePlay,
                        onNext = vm::next,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            // 正在播放（全屏）
            AnimatedVisibility(
                visible = showNowPlaying && current != null,
                enter = slideInVertically(spring(0.85f, 320f)) { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NowPlayingScreen(
                    vm = vm,
                    state = state,
                    track = current,
                    onClose = { showNowPlaying = false },
                )
            }
        }
    }
}
