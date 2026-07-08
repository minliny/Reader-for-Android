package com.reader.android.data.repository

import com.reader.android.data.storage.BookGroupAssignment
import com.reader.android.data.storage.BookGroupDao
import com.reader.android.data.storage.BookGroupEntity

/**
 * P0-3: Book group / category persistence. Groups are local-only definitions; the books
 * themselves remain owned by the Core bookshelf (keyed by `bookUrl`). This repository
 * records group definitions and the `bookUrl → groupId` membership mapping so the UI can
 * render "正在读" / "已读完" / user-defined shelves without round-tripping to Core for
 * group metadata.
 */
class BookGroupRepository(private val dao: BookGroupDao) {

    suspend fun getAllGroups(): List<BookGroupEntity> = dao.getAll()

    suspend fun getGroup(id: Long): BookGroupEntity? = dao.getById(id)

    suspend fun getGroupByName(name: String): BookGroupEntity? = dao.getByName(name)

    suspend fun createGroup(name: String, sortOrder: Int = 0): Long {
        val existing = dao.getByName(name)
        if (existing != null) return existing.id
        return dao.upsert(BookGroupEntity(name = name, sortOrder = sortOrder))
    }

    suspend fun renameGroup(id: Long, newName: String) {
        val existing = dao.getById(id) ?: return
        dao.upsert(existing.copy(name = newName))
    }

    suspend fun deleteGroup(id: Long) {
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
}
