package com.example.glassplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 曲库：递归扫描用户选择的文件夹（SAF），把音频与同名 .vtt 歌词配对，
 * 并读取标签与内嵌封面。结果缓存为 JSON，下次启动秒开。
 */
object Library {

    private val audioExt = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "wma", "ape", "m4b")

    private class Child(val docId: String, val name: String, val mime: String)

    suspend fun scan(ctx: Context, tree: Uri): List<Track> = withContext(Dispatchers.IO) {
        val found = ArrayList<Pair<String, Track>>()
        val artDir = File(ctx.filesDir, "art").apply { mkdirs() }

        fun walk(docId: String, path: String) {
            val kids = children(ctx, tree, docId)

            // 本目录里的 vtt：小写文件名(不含扩展名) -> documentId
            val vtts = HashMap<String, String>()
            for (k in kids) {
                if (k.mime != DocumentsContract.Document.MIME_TYPE_DIR && k.name.endsWith(".vtt", ignoreCase = true)) {
                    vtts[k.name.dropLast(4).lowercase()] = k.docId
                }
            }

            for (k in kids) {
                if (k.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(k.docId, path + k.name + "/")
                    continue
                }
                val ext = k.name.substringAfterLast('.', "").lowercase()
                if (ext !in audioExt && !k.mime.startsWith("audio/")) continue

                val base = k.name.substringBeforeLast('.')
                val key = base.lowercase()
                // 先精确匹配「歌名.vtt」，再匹配「歌名.zh.vtt」这类带语言后缀的
                val vttDoc = vtts[key]
                    ?: vtts.entries.firstOrNull { it.key.startsWith("$key.") }?.value

                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, k.docId)
                val lyricsUri = vttDoc?.let {
                    DocumentsContract.buildDocumentUriUsingTree(tree, it).toString()
                }
                found += (path + base).lowercase() to readTrack(ctx, uri, base, lyricsUri, artDir)
            }
        }

        walk(DocumentsContract.getTreeDocumentId(tree), "")
        found.sortedBy { it.first }.map { it.second }
    }

    private fun children(ctx: Context, tree: Uri, parentDocId: String): List<Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val out = ArrayList<Child>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        try {
            ctx.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    out += Child(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "")
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun readTrack(ctx: Context, uri: Uri, base: String, lyrics: String?, artDir: File): Track {
        var title = base
        var artist = "未知艺人"
        var album = ""
        var duration = 0L
        var artPath: String? = null

        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
                ?.takeIf { it.isNotBlank() }?.let { artist = it }
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            r.embeddedPicture?.let { bytes ->
                val out = File(artDir, Integer.toHexString(uri.toString().hashCode()) + ".jpg")
                artPath = saveArt(bytes, out)
            }
        } catch (_: Exception) {
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
        return Track(uri.toString(), title, artist, album, duration, artPath, lyrics)
    }

    private fun saveArt(bytes: ByteArray, out: File): String? {
        if (out.exists() && out.length() > 0) return out.absolutePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1000) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return out.absolutePath
    }

    // ---- 缓存 ----

    fun saveCache(ctx: Context, tracks: List<Track>) {
        try {
            val arr = JSONArray()
            for (t in tracks) {
                arr.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("title", t.title)
                        .put("artist", t.artist)
                        .put("album", t.album)
                        .put("duration", t.durationMs)
                        .put("art", t.artPath ?: JSONObject.NULL)
                        .put("lyrics", t.lyricsUri ?: JSONObject.NULL),
                )
            }
            File(ctx.filesDir, "library.json").writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun loadCache(ctx: Context): List<Track> {
        val f = File(ctx.filesDir, "library.json")
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Track(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    album = o.getString("album"),
                    durationMs = o.getLong("duration"),
                    artPath = if (o.isNull("art")) null else o.getString("art"),
                    lyricsUri = if (o.isNull("lyrics")) null else o.getString("lyrics"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
