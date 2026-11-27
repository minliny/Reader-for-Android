package io.reader.rebuild.features.search

import com.reader.core.protocol.M2BookSearchData
import com.reader.core.protocol.M2BookSearchParams
import com.reader.core.protocol.M2SearchHistoryAddParams
import com.reader.core.protocol.M2SearchHistoryListParams
import com.reader.core.protocol.M2SourceListParams
import com.reader.core.protocol.ReaderM2
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.core.RequestIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class SourceSearchResult(val sourceId: String, val identity: RequestIdentity, val result: Result<M2BookSearchData>)

internal class SearchCoordinator(private val core: CoreRuntimeActor) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val monitor = Any()
    private var active: Owner? = null
    private var previous: Pair<String, Long?>? = null
    private var closed = false
    private val view = MutableStateFlow<List<SourceSearchResult>>(emptyList())
    val results: StateFlow<List<SourceSearchResult>> = view.asStateFlow()
    val inFlight: Boolean get() = synchronized(monitor) { active != null }

    fun start(keyword: String, page: Long? = null): SearchOperation = synchronized(monitor) {
        val query = keyword.trim().also { require(it.isNotEmpty()) }
        check(!closed && active == null) { "The previous search still owns its requests" }
        val owner = Owner()
        active = owner
        previous = query to page
        view.value = emptyList()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val final = runCatching { run(owner, query, page) }
            synchronized(monitor) {
                if (active === owner) active = null
                final.fold(owner.terminal::complete, owner.terminal::completeExceptionally)
            }
        }
        SearchOperation(owner.terminal)
    }

    fun retry(): SearchOperation = synchronized(monitor) {
        val (keyword, page) = checkNotNull(previous) { "No previous search" }
        start(keyword, page)
    }

    fun stopDisplaying() = synchronized(monitor) { active?.visible = false }

    fun cancel() {
        val requests = synchronized(monitor) {
            val owner = active ?: return
            owner.visible = false
            owner.cancelled = true
            owner.ids.toList()
        }
        for (id in requests) core.cancelRead(id)
    }

    suspend fun history(limit: Long? = null) = core.call(ReaderM2.searchHistoryList, M2SearchHistoryListParams(limit))

    fun close() {
        synchronized(monitor) { closed = true }
        cancel()
        scope.cancel()
    }

    private suspend fun run(owner: Owner, keyword: String, page: Long?): List<SourceSearchResult> {
        core.call(ReaderM2.searchHistoryAdd, M2SearchHistoryAddParams(keyword))
        val sources = core.call(ReaderM2.sourceList, M2SourceListParams(enabledOnly = true)).sources
        val completed = mutableListOf<SourceSearchResult>()
        for (batch in sources.chunked(4)) {
            if (synchronized(monitor) { owner.cancelled }) break
            val children = coroutineScope {
                batch.map { source -> async { fetch(owner, source.sourceId, keyword, page) } }.awaitAll()
            }
            completed += children.filterNotNull()
        }
        return completed
    }

    private suspend fun fetch(owner: Owner, sourceId: String, keyword: String, page: Long?): SourceSearchResult? {
        val operation = synchronized(monitor) {
            if (owner.cancelled) return null
            core.submit(ReaderM2.bookSearch, M2BookSearchParams(sourceId, keyword = keyword, page = page))
                .also { owner.ids += it.identity }
        }
        val result = SourceSearchResult(sourceId, operation.identity, runCatching {
            operation.await().also { check(it.sourceId == sourceId) }
        })
        synchronized(monitor) {
            owner.ids.remove(operation.identity)
            if (active === owner && owner.visible) view.value = view.value + result
        }
        return result
    }

    private class Owner {
        val terminal = CompletableDeferred<List<SourceSearchResult>>()
        val ids = mutableSetOf<RequestIdentity>()
        var visible = true
        var cancelled = false
    }
}

internal class SearchOperation(private val terminal: CompletableDeferred<List<SourceSearchResult>>) {
    suspend fun await(): List<SourceSearchResult> = terminal.await()
}
