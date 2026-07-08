package com.reader.android.data.repository

import com.reader.android.data.storage.BookGroupAssignment
import com.reader.android.data.storage.BookGroupDao
import com.reader.android.data.storage.BookGroupEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-3: JVM proof for [BookGroupRepository] — group CRUD is idempotent by name,
 * assignment is many-to-many, and deleting a group clears its assignments.
 */
class BookGroupRepositoryJvmTest {

    @Test
    fun `createGroup is idempotent by name and returns existing id`() = runBlocking {
        val dao = FakeBookGroupDao()
        val repo = BookGroupRepository(dao)

        val id1 = repo.createGroup("正在读")
        val id2 = repo.createGroup("正在读")

        assertEquals(id1, id2)
        assertEquals(1, dao.allGroups.size)
    }

    @Test
    fun `getAllGroups returns sorted by sortOrder then createdAt`() = runBlocking {
        val dao = FakeBookGroupDao()
        val repo = BookGroupRepository(dao)

        repo.createGroup("已读完", sortOrder = 2)
        repo.createGroup("正在读", sortOrder = 0)
        repo.createGroup("武侠", sortOrder = 1)

        val groups = repo.getAllGroups()
        assertEquals(listOf("正在读", "武侠", "已读完"), groups.map { it.name })
    }

    @Test
    fun `renameGroup updates name keeping id`() = runBlocking {
        val dao = FakeBookGroupDao()
        val repo = BookGroupRepository(dao)
        val id = repo.createGroup("旧名")

        repo.renameGroup(id, "新名")

        val group = repo.getGroup(id)
        assertEquals("新名", group?.name)
        assertEquals(id, group?.id)
    }

    @Test
    fun `deleteGroup removes group and its assignments`() = runBlocking {
        val dao = FakeBookGroupDao()
        val repo = BookGroupRepository(dao)
        val id = repo.createGroup("正在读")
        repo.assignBook(id, "http://book/1")
        repo.assignBook(id, "http://book/2")
        assertEquals(2, repo.bookUrlsInGroup(id).size)

        repo.deleteGroup(id)

        assertNull(repo.getGroup(id))
        assertTrue(repo.bookUrlsInGroup(id).isEmpty())
    }

    @Test
    fun `assignBook is idempotent and supports many-to-many`() = runBlocking {
        val dao = FakeBookGroupDao()
        val repo = BookGroupRepository(dao)
        val g1 = repo.createGroup("正在读")
        val g2 = repo.createGroup("已读完")

        repo.assignBook(g1, "http://book/1")
        repo.assignBook(g1, "http://book/1") // duplicate — ignored
        repo.assignBook(g2, "http://book/1") // same book in a second group
        repo.assignBook(g1, "http://book/2")

        assertEquals(listOf("http://book/1", "http://book/2"), repo.bookUrlsInGroup(g1).sorted())
        assertEquals(listOf("http://book/1"), repo.bookUrlsInGroup(g2))
        assertEquals(2, repo.groupsForBook("http://book/1").size)
    }

    @Test
    fun `unassignBook removes only the targeted mapping`() = runBlocking {
        val dao = FakeBookGroupDao()
        val repo = BookGroupRepository(dao)
        val g = repo.createGroup("正在读")
        repo.assignBook(g, "http://book/1")
        repo.assignBook(g, "http://book/2")

        repo.unassignBook(g, "http://book/1")

        assertEquals(listOf("http://book/2"), repo.bookUrlsInGroup(g))
    }

    @Test
    fun `removeFromAllGroups clears every membership for a book`() = runBlocking {
        val dao = FakeBookGroupDao()
        val repo = BookGroupRepository(dao)
        val g1 = repo.createGroup("正在读")
        val g2 = repo.createGroup("已读完")
        repo.assignBook(g1, "http://book/1")
        repo.assignBook(g2, "http://book/1")

        repo.removeFromAllGroups("http://book/1")

        assertTrue(repo.groupsForBook("http://book/1").isEmpty())
        assertTrue(repo.bookUrlsInGroup(g1).isEmpty())
        assertTrue(repo.bookUrlsInGroup(g2).isEmpty())
    }

    @Test
    fun `getGroupByName returns null for unknown name`() = runBlocking {
        val dao = FakeBookGroupDao()
        val repo = BookGroupRepository(dao)
        assertNull(repo.getGroupByName("不存在"))
    }
}

// ── Test double ──

class FakeBookGroupDao : BookGroupDao {
    private var nextId = 1L
    val allGroups = mutableMapOf<Long, BookGroupEntity>()
    val assignments = mutableListOf<BookGroupAssignment>()

    override suspend fun getAll(): List<BookGroupEntity> =
        allGroups.values.sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))

    override suspend fun getById(id: Long): BookGroupEntity? = allGroups[id]

    override suspend fun getByName(name: String): BookGroupEntity? =
        allGroups.values.firstOrNull { it.name == name }

    override suspend fun upsert(group: BookGroupEntity): Long {
        val id = if (group.id == 0L) nextId++ else group.id
        val stored = group.copy(id = id)
        allGroups[id] = stored
        return id
    }

    override suspend fun delete(id: Long) {
        allGroups.remove(id)
        assignments.removeAll { it.groupId == id }
    }

    override suspend fun assign(assignment: BookGroupAssignment) {
        if (assignments.none { it.groupId == assignment.groupId && it.bookUrl == assignment.bookUrl }) {
            assignments.add(assignment)
        }
    }

    override suspend fun unassign(groupId: Long, bookUrl: String) {
        assignments.removeAll { it.groupId == groupId && it.bookUrl == bookUrl }
    }

    override suspend fun clearGroup(groupId: Long) {
        assignments.removeAll { it.groupId == groupId }
    }

    override suspend fun removeFromAllGroups(bookUrl: String) {
        assignments.removeAll { it.bookUrl == bookUrl }
    }

    override suspend fun bookUrlsInGroup(groupId: Long): List<String> =
        assignments.filter { it.groupId == groupId }.map { it.bookUrl }

    override suspend fun groupsForBook(bookUrl: String): List<BookGroupAssignment> =
        assignments.filter { it.bookUrl == bookUrl }
}
