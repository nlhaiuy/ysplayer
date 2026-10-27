package com.example.glassplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.example.glassplayer.PlayerViewModel
import com.example.glassplayer.Track
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val Accent = Color(0xFFFF6B8A)

// ============================== 曲库 ==============================

@Composable
fun LibraryList(
    tracks: List<Track>,
    currentId: String?,
    scanning: Boolean,
    onPlay: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = top + 16.dp, bottom = bottom + 120.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("音乐", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (scanning) "正在扫描…" else "${tracks.size} 首歌曲",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        items(tracks, key = { it.id }) { track ->
            TrackRow(track, isCurrent = track.id == currentId, onClick = { onPlay(track) })
        }
    }
}

@Composable
private fun TrackRow(track: Track, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isCurrent) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(track, Modifier.size(54.dp), sample = 4, cornerRadius = 12.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = track.title,
                color = if (isCurrent) Accent else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist + if (track.album.isNotEmpty()) " · ${track.album}" else "",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.lyricsUri != null) {
            Text(
                text = "词",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
fun EmptyState(haze: HazeState, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .glass(haze, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("还没有音乐", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "选择一个存放音频的文件夹。与歌曲同名的 .vtt 文件会自动识别为歌词。",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        GlassPill("选择文件夹", haze, onClick = onPick)
    }
}

// ============================== 迷你播放器 ==============================

@Composable
fun MiniPlayer(
    track: Track,
    playing: Boolean,
    haze: HazeState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(68.dp)
            .bounceClick(onClick = onOpen)
            .glass(haze, RoundedCornerShape(34.dp), tint = Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(track, Modifier.size(48.dp), sample = 4, cornerRadius = 24.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                track.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(46.dp).bounceClick(onClick = onToggle), contentAlignment = Alignment.Center) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        Box(Modifier.size(46.dp).bounceClick(onClick = onNext), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.SkipNext, "下一首", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

// ============================== 正在播放 ==============================

@Composable
fun NowPlayingScreen(
    vm: PlayerViewModel,
    state: PlayerViewModel.UiState,
    track: Track?,
    onClose: () -> Unit,
) {
    val haze = remember { HazeState() }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 封面, 1 歌词
    val pickLyrics = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::setLyrics)
    }

    Box(Modifier.fillMaxSize()) {
        GlassBackdrop(track, Modifier.fillMaxSize().hazeSource(haze))

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                GlassIconButton(
                    Icons.Rounded.KeyboardArrowDown, "收起", haze,
                    Modifier.align(Alignment.CenterStart), size = 44.dp,
                    onClick = onClose,
                )
                GlassSegmented(
                    options = listOf("封面", "歌词"),
                    selected = mode,
                    onSelect = { mode = it },
                    haze = haze,
                    modifier = Modifier.align(Alignment.Center),
                )
                GlassIconButton(
                    Icons.Rounded.Subtitles, "导入 VTT 歌词", haze,
                    Modifier.align(Alignment.CenterEnd), size = 44.dp,
                    onClick = { pickLyrics.launch(arrayOf("*/*")) },
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "nowPlayingMode",
                ) { m ->
                    if (m == 0) {
                        CoverPage(track, state.playing)
                    } else {
                        LyricsPage(
                            vm = vm,
                            state = state,
                            track = track,
                            haze = haze,
                            onImport = { pickLyrics.launch(arrayOf("*/*")) },
                        )
                    }
                }
            }

            PlayerControls(vm, state, haze)
        }
    }
}

@Composable
private fun CoverPage(track: Track?, playing: Boolean) {
    // 播放时封面放大，暂停时缩小（Apple Music 的经典动效）
    val scale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.84f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 220f),
        label = "coverScale",
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = minOf(maxWidth, maxHeight - 120.dp).coerceAtLeast(120.dp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(side)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(32.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black, spotColor = Color.Black),
            ) {
                CoverArt(track, Modifier.fillMaxSize(), sample = 1, cornerRadius = 28.dp)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = track?.title.orEmpty(),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track?.artist.orEmpty(),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LyricsPage(
    vm: PlayerViewModel,
    state: PlayerViewModel.UiState,
    track: Track?,
    haze: HazeState,
    onImport: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(track, Modifier.size(56.dp), sample = 4, cornerRadius = 12.dp)
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    track?.title.orEmpty(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track?.artist.orEmpty(), color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (state.lyrics.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "没有可用的歌词",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "把与歌曲同名的 .vtt 放在音频旁边，\n或手动导入一个。",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    GlassPill("导入 VTT 歌词", haze, onClick = onImport)
                }
            }
        } else {
            val active by vm.activeLine.collectAsStateWithLifecycle()
            LyricsView(
                lyrics = state.lyrics,
                activeLine = active,
                onSeek = vm::seekTo,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlayerControls(vm: PlayerViewModel, state: PlayerViewModel.UiState, haze: HazeState) {
    val position by vm.position.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        GlassSeekBar(positionMs = position, durationMs = state.durationMs, onSeek = vm::seekTo)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime(position), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Text(
                "-" + formatTime((state.durationMs - position).coerceAtLeast(0L)),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                Icons.Rounded.Shuffle, "随机播放", haze,
                size = 44.dp, iconSize = 20.dp, highlighted = state.shuffle,
                onClick = vm::toggleShuffle,
            )
            GlassIconButton(
                Icons.Rounded.SkipPrevious, "上一首", haze,
                size = 60.dp, iconSize = 30.dp, onClick = vm::previous,
            )
            GlassIconButton(
                if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.playing) "暂停" else "播放", haze,
                size = 84.dp, iconSize = 42.dp, highlighted = true, onClick = vm::togglePlay,
            )
            GlassIconButton(
                Icons.Rounded.SkipNext, "下一首", haze,
                size = 60.dp, iconSize = 30.dp, onClick = vm::next,
            )
            GlassIconButton(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "循环", haze,
                size = 44.dp, iconSize = 20.dp, highlighted = state.repeatMode != Player.REPEAT_MODE_OFF,
                onClick = vm::cycleRepeat,
            )
        }
    }
}
