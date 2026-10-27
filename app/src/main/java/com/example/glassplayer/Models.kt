package com.example.glassplayer

import android.net.Uri

/** 一首歌。id 就是音频文件的 content:// Uri 字符串。 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artPath: String?,
    val lyricsUri: String?,
) {
    val uri: Uri get() = Uri.parse(id)
}

/** 一行歌词（VTT 的一个 cue）。多行文本（如中英双语）保存在 lines 中。 */
data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val lines: List<String>,
)
