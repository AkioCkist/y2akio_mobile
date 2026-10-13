package com.example.y2akio_mobile.storage

import android.content.Context
import com.example.y2akio_mobile.model.DownloadHistoryItem
import com.example.y2akio_mobile.util.objects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File

/** Download history persisted as JSON in the app's private files (newest first). */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<DownloadHistoryItem>> = _items.asStateFlow()

    private fun load(): List<DownloadHistoryItem> = try {
        if (!file.exists()) emptyList()
        else JSONArray(file.readText()).objects().map(DownloadHistoryItem::fromJson).sortedByDescending { it.completedAt }
    } catch (_: Exception) {
        emptyList() // corrupt file → start fresh
    }

    fun add(item: DownloadHistoryItem) = mutate { listOf(item) + it }
    fun remove(item: DownloadHistoryItem) = mutate { list -> list.filterNot { it == item } }
    fun clear() = mutate { emptyList() }

    private fun mutate(block: (List<DownloadHistoryItem>) -> List<DownloadHistoryItem>) {
        _items.update(block)
        val snapshot = _items.value
        scope.launch {
            writeLock.withLock {
                runCatching {
                    val arr = JSONArray()
                    snapshot.forEach { arr.put(it.toJson()) }
                    val tmp = File(file.parentFile, "history.json.tmp")
                    tmp.writeText(arr.toString())
                    tmp.renameTo(file)
                }
            }
        }
    }
}
