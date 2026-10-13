package com.example.y2akio_mobile.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.y2akio_mobile.settings.AppSettings
import java.io.File

/**
 * Moves finished files out of the app's private work folder into shared storage. yt-dlp needs a
 * real filesystem path, so every job works in cache and its results are exported here: either to
 * `Download/Y2Akio` through MediaStore (no permission needed) or into a folder the user picked
 * through the Storage Access Framework.
 */
class OutputStorage(private val context: Context, private val settings: AppSettings) {

    data class Saved(val uri: Uri, val displayPath: String, val mimeType: String)

    /**
     * @param folder SAF tree URI, or "" for Download/Y2Akio.
     * @param subFolder optional subfolder (gallery posts saved as loose files).
     */
    fun save(source: File, folder: String, subFolder: String? = null, fileName: String = source.name): Saved {
        val mime = mimeFor(fileName)
        return if (folder.isBlank()) saveToDownloads(source, subFolder, fileName, mime)
        else saveToTree(source, Uri.parse(folder), subFolder, fileName, mime)
    }

    /** "Download/Y2Akio[/sub]" or "<picked folder>[/sub]". */
    fun folderLabel(folder: String, subFolder: String? = null): String {
        val base = settings.folderLabel(folder)
        return if (subFolder.isNullOrBlank()) base else "$base/$subFolder"
    }

    private fun saveToDownloads(source: File, subFolder: String?, fileName: String, mime: String): Saved {
        val relative = buildString {
            append(Environment.DIRECTORY_DOWNLOADS).append("/Y2Akio")
            if (!subFolder.isNullOrBlank()) append('/').append(subFolder)
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Couldn't create $fileName in $relative")
        try {
            resolver.openOutputStream(uri)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        // MediaStore renames on collision ("name (1).mp4"); report the name it actually used.
        val actualName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: fileName
        return Saved(uri, "$relative/$actualName", mime)
    }

    private fun saveToTree(source: File, tree: Uri, subFolder: String?, fileName: String, mime: String): Saved {
        var dir = DocumentFile.fromTreeUri(context, tree)
            ?: throw IllegalStateException("The save folder is no longer accessible. Choose it again in Settings.")
        if (!dir.canWrite()) throw IllegalStateException("The save folder is no longer writable. Choose it again in Settings.")
        if (!subFolder.isNullOrBlank()) {
            dir = dir.findFile(subFolder)?.takeIf { it.isDirectory } ?: dir.createDirectory(subFolder)
                ?: throw IllegalStateException("Couldn't create folder $subFolder")
        }
        val doc = dir.createFile(mime, fileName) ?: throw IllegalStateException("Couldn't create $fileName")
        try {
            context.contentResolver.openOutputStream(doc.uri)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
        } catch (e: Exception) {
            runCatching { doc.delete() }
            throw e
        }
        return Saved(doc.uri, "${folderLabel(tree.toString(), subFolder)}/${doc.name ?: fileName}", mime)
    }

    companion object {
        private val extraMimes = mapOf(
            "opus" to "audio/ogg",
            "ogg" to "audio/ogg",
            "m4a" to "audio/mp4",
            "alac" to "audio/mp4",
            "flac" to "audio/flac",
            "wav" to "audio/wav",
            "wma" to "audio/x-ms-wma",
            "aac" to "audio/aac",
            "mkv" to "video/x-matroska",
            "webm" to "video/webm",
            "srt" to "application/x-subrip",
            "vtt" to "text/vtt",
            "heic" to "image/heic",
            "webp" to "image/webp",
            "zip" to "application/zip",
        )

        fun mimeFor(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: extraMimes[ext] ?: "application/octet-stream"
        }
    }
}
