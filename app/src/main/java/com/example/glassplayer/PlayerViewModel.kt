package com.example.glassplayer

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val tracks: List<Track> = emptyList(),
        val scanning: Boolean = false,
        val hasFolder: Boolean = false,
        val currentId: String? = null,
        val playing: Boolean = false,
        val durationMs: Long = 0L,
        val lyrics: List<LyricLine> = emptyList(),
        val shuffle: Boolean = false,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
    )

    private val ctx: Context = app
    private val prefs = app.getSharedPreferences("glass", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState(hasFolder = prefs.getString("tree", null) != null))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 播放进度单独一个流，避免每帧让整个界面重组。 */
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    /** 当前高亮的歌词行下标，-1 表示还没开始。 */
    private val _activeLine = MutableStateFlow(-1)
    val activeLine: StateFlow<Int> = _activeLine.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private var lyricsFor: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }
    }

    init {
        // 先读缓存，界面立刻有内容
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { Library.loadCache(ctx) }
            _state.update { it.copy(tracks = cached) }
            if (_state.value.hasFolder && cached.isEmpty()) rescan()
        }

        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = try {
                future.get()
            } catch (e: Exception) {
                return@addListener
            }
            controller = c
            c.addListener(listener)
            sync(c)
            while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
        }, ContextCompat.getMainExecutor(ctx))

        // 进度 / 歌词同步
        viewModelScope.launch {
            while (true) {
                val c = controller
                if (c != null) {
                    val pos = c.currentPosition
                    _position.value = pos
                    val lines = _state.value.lyrics
                    val idx = if (lines.isEmpty()) -1 else VttParser.activeIndex(lines, pos + LYRIC_LEAD_MS)
                    if (idx != _activeLine.value) _activeLine.value = idx
                }
                delay(if (_state.value.playing) 40L else 250L)
            }
        }
    }

    // ---------- 与播放器同步 ----------

    private fun sync(p: Player) {
        val id = p.currentMediaItem?.mediaId
        val trackDuration = _state.value.tracks.firstOrNull { it.id == id }?.durationMs ?: 0L
        _state.update {
            it.copy(
                currentId = id,
                playing = p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE,
                durationMs = p.duration.takeIf { d -> d != C.TIME_UNSET && d > 0 } ?: trackDuration,
                shuffle = p.shuffleModeEnabled,
                repeatMode = p.repeatMode,
            )
        }
        if (id != lyricsFor) {
            lyricsFor = id
            loadLyrics(id)
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending.addLast(block)
    }

    // ---------- 曲库 ----------

    fun onFolderPicked(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        prefs.edit().putString("tree", uri.toString()).apply()
        _state.update { it.copy(hasFolder = true) }
        rescan()
    }

    fun rescan() {
        val tree = prefs.getString("tree", null)?.let(Uri::parse) ?: return
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    Library.scan(ctx, tree).also { Library.saveCache(ctx, it) }
                }
            }.getOrNull()
            _state.update { it.copy(tracks = result ?: it.tracks, scanning = false) }
            // 重新扫描后，当前曲目可能多了歌词
            if (_state.value.lyrics.isEmpty()) loadLyrics(lyricsFor)
        }
    }

    // ---------- 歌词 ----------

    private fun loadLyrics(id: String?) {
        _state.update { it.copy(lyrics = emptyList()) }
        _activeLine.value = -1
        if (id == null) return
        val uriStr = prefs.getString(LYRICS_KEY + id, null)
            ?: _state.value.tracks.firstOrNull { it.id == id }?.lyricsUri
            ?: return
        viewModelScope.launch {
            val lines = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(Uri.parse(uriStr))?.use {
                        VttParser.parse(it.readBytes().decodeToString())
                    } ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            if (lyricsFor == id) _state.update { it.copy(lyrics = lines) }
        }
    }

    /** 手动为当前歌曲指定一个 .vtt 文件，并记住。 */
    fun setLyrics(uri: Uri) {
        val id = _state.value.currentId ?: return
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        prefs.edit().putString(LYRICS_KEY + id, uri.toString()).apply()
        loadLyrics(id)
    }

    // ---------- 播放控制 ----------

    private fun toItem(t: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(t.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(t.artPath?.let { Uri.fromFile(File(it)) })
                    .build(),
            )
            .build()

    fun playTrack(trackId: String) {
        val tracks = _state.value.tracks
        val index = tracks.indexOfFirst { it.id == trackId }
        if (index < 0) return
        withController { c ->
            val same = c.mediaItemCount == tracks.size &&
                (0 until c.mediaItemCount).all { c.getMediaItemAt(it).mediaId == tracks[it].id }
            if (same) c.seekTo(index, 0L) else c.setMediaItems(tracks.map(::toItem), index, 0L)
            c.prepare()
            c.play()
        }
    }

    fun togglePlay() = withController { c ->
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) c.pause() else c.play()
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(ms: Long) {
        _position.value = ms
        withController { it.seekTo(ms) }
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }

    private companion object {
        /** 歌词提前 150ms 高亮，视觉上更跟手。 */
        const val LYRIC_LEAD_MS = 150L
        const val LYRICS_KEY = "lyr_"
    }
}
