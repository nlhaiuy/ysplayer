package com.example.glassplayer

/**
 * WebVTT 歌词解析器。
 *
 * 支持：
 *  - 带/不带小时的时间戳（00:01:02.500 / 01:02.500），毫秒分隔符 "." 或 ","
 *  - cue 标识行、cue 设置（position:/align: 等）
 *  - NOTE / STYLE / REGION 块会被忽略
 *  - <c>、<v 说话人>、<i>、<00:00:01.000> 等内联标签会被剥离
 *  - 多行 cue（例如原文 + 翻译）
 *  - UTF-8 BOM、CRLF 换行
 */
object VttParser {

    private val timeRegex = Regex("""^(?:(\d{1,3}):)?(\d{1,2}):(\d{2})[.,](\d{1,3})$""")
    private val tagRegex = Regex("<[^>]*>")
    private val blankLines = Regex("\n{2,}")
    private val whitespace = Regex("\\s+")

    fun parse(raw: String): List<LyricLine> {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val result = ArrayList<LyricLine>()

        for (block in text.split(blankLines)) {
            val lines = block.trim('\n').split('\n')
            val arrowIdx = lines.indexOfFirst { it.contains("-->") }
            if (arrowIdx < 0) continue

            val head = lines[0].trimStart()
            if (head.startsWith("NOTE") || head.startsWith("STYLE") || head.startsWith("REGION")) continue

            val parts = lines[arrowIdx].split("-->")
            if (parts.size < 2) continue
            val start = parseTime(parts[0].trim()) ?: continue
            val end = parseTime(parts[1].trim().split(whitespace).first()) ?: continue

            val body = lines.drop(arrowIdx + 1)
                .map { clean(it) }
                .filter { it.isNotEmpty() }
            if (body.isEmpty()) continue

            result += LyricLine(start, maxOf(start, end), body)
        }
        return result.sortedBy { it.startMs }
    }

    /** 二分查找：返回 startMs <= posMs 的最后一行下标，没有则 -1。 */
    fun activeIndex(lines: List<LyricLine>, posMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].startMs <= posMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    private fun parseTime(s: String): Long? {
        val m = timeRegex.matchEntire(s) ?: return null
        val h = m.groupValues[1].ifEmpty { "0" }.toLong()
        val min = m.groupValues[2].toLong()
        val sec = m.groupValues[3].toLong()
        val ms = m.groupValues[4].padEnd(3, '0').toLong()
        return ((h * 60 + min) * 60 + sec) * 1000 + ms
    }

    private fun clean(s: String): String =
        s.replace(tagRegex, "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&lrm;", "")
            .replace("&rlm;", "")
            .replace("&amp;", "&")
            .trim()
}
