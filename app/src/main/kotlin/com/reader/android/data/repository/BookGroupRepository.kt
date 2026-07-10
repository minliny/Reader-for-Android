package com.reader.android.data.repository

import com.reader.android.data.storage.BookGroupAssignment
import com.reader.android.data.storage.BookGroupDao
import com.reader.android.data.storage.BookGroupEntity
import com.reader.api.ReaderCoreClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * P0-3: Book group / category persistence. Groups are local-only definitions; the books
 * themselves remain owned by the Core bookshelf (keyed by `bookUrl`). This repository
 * records group definitions and the `bookUrl → groupId` membership mapping so the UI can
 * render "正在读" / "已读完" / user-defined shelves without round-tripping to Core for
 * group metadata.
 *
 * C2: `createGroup` / `getAllGroups` / `renameGroup` / `deleteGroup` delegate to Core
 * `book-group.create` / `book-group.list` / `book-group.update` / `book-group.delete`
 * first; the Room-backed DAO stays as the fallback path used when the Core bridge is
 * unavailable (e.g. JVM unit tests where [ReaderCoreClient] is not initialized).
 */
class BookGroupRepository(private val dao: BookGroupDao) {

    /**
     * C2: Tries Core `book-group.list` first; falls back to the Room-backed DAO
     * when the Core bridge is unavailable.
     */
    suspend fun getAllGroups(): List<BookGroupEntity> {
        try {
            val result = ReaderCoreClient.get().sendAndAwait(
                CORE_LIST_METHOD, JSONObject(), CORE_TIMEOUT_MILLIS
            )
            return parseGroupList(result)
        } catch (e: Exception) {
            // Core bridge unavailable — fall back to Room below.
        }
        return dao.getAll()
    }

    suspend fun getGroup(id: Long): BookGroupEntity? = dao.getById(id)

    suspend fun getGroupByName(name: String): BookGroupEntity? = dao.getByName(name)

    /**
     * C2: Tries Core `book-group.create` first; falls back to the Room-backed DAO
     * when the Core bridge is unavailable.
     */
    suspend fun createGroup(name: String, sortOrder: Int = 0): Long {
        // Local idempotency check — keeps behavior consistent across both paths.
        val existing = dao.getByName(name)
        if (existing != null) return existing.id
        try {
            val params = JSONObject().apply {
                put("name", name)
                put("sortOrder", sortOrder)
            }
            val result = ReaderCoreClient.get().sendAndAwait(
                CORE_CREATE_METHOD, params, CORE_TIMEOUT_MILLIS
            )
            val coreId = result.optLong("id", result.optLong("groupId", -1L))
            if (coreId >= 0) return coreId
            // Core didn't return an id — fall back to Room below.
        } catch (e: Exception) {
            // Core bridge unavailable — fall back to Room below.
        }
        return dao.upsert(BookGroupEntity(name = name, sortOrder = sortOrder))
    }

    /**
     * C2: Tries Core `book-group.update` first; falls back to the Room-backed DAO
     * when the Core bridge is unavailable.
     */
    suspend fun renameGroup(id: Long, newName: String) {
        val existing = dao.getById(id) ?: return
        try {
            val params = JSONObject().apply {
                put("id", id)
                put("name", newName)
            }
            ReaderCoreClient.get().sendAndAwait(
                CORE_UPDATE_METHOD, params, CORE_TIMEOUT_MILLIS
            )
            return
        } catch (e: Exception) {
            // Core bridge unavailable — fall back to Room below.
        }
        dao.upsert(existing.copy(name = newName))
    }

    /**
     * C2: Tries Core `book-group.delete` first; falls back to the Room-backed DAO
     * when the Core bridge is unavailable.
     */
    suspend fun deleteGroup(id: Long) {
        try {
            val params = JSONObject().apply { put("id", id) }
            ReaderCoreClient.get().sendAndAwait(
                CORE_DELETE_METHOD, params, CORE_TIMEOUT_MILLIS
            )
            return
        } catch (e: Exception) {
            // Core bridge unavailable — fall back to Room below.
        }
        // CASCADE on the foreign key removes assignments automatically, but explicit
        // clear keeps the operation correct even if FK enforcement is off (e.g. tests).
        dao.clearGroup(id)
        dao.delete(id)
    }

    suspend fun assignBook(groupId: Long, bookUrl: String) {
        dao.assign(BookGroupAssignment(groupId = groupId, bookUrl = bookUrl))
    }

    suspend fun unassignBook(groupId: Long, bookUrl: String) = dao.unassign(groupId, bookUrl)

    suspend fun bookUrlsInGroup(groupId: Long): List<String> = dao.bookUrlsInGroup(groupId)

    suspend fun groupsForBook(bookUrl: String): List<BookGroupAssignment> = dao.groupsForBook(bookUrl)

    suspend fun removeFromAllGroups(bookUrl: String) = dao.removeFromAllGroups(bookUrl)

    private fun parseGroupList(result: JSONObject): List<BookGroupEntity> {
        val arr: JSONArray = result.optJSONArray("groups") ?: return emptyList()
        val groups = mutableListOf<BookGroupEntity>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            groups.add(
                BookGroupEntity(
                    id = obj.optLong("id", 0L),
                    name = obj.optString("name", ""),
                    sortOrder = obj.optInt("sortOrder", 0),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return groups
    }

    companion object {
        private const val CORE_CREATE_METHOD = "book-group.create"
        private const val CORE_LIST_METHOD = "book-group.list"
        private const val CORE_UPDATE_METHOD = "book-group.update"
        private const val CORE_DELETE_METHOD = "book-group.delete"
        private const val CORE_TIMEOUT_MILLIS = 10_000L
    }
}
