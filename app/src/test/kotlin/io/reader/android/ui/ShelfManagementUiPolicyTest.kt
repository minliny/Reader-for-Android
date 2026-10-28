package io.reader.android.ui

import io.reader.android.runtime.LibraryBook
import io.reader.android.runtime.ShelfBookKey
import io.reader.android.runtime.ShelfGroup
import org.junit.Assert.*
import org.junit.Test

/** Pure presentation identity only. No simulated groups are fed into the production UI or Core. */
class ShelfManagementUiPolicyTest {
    @Test fun oldOwnerRevisionAndChangedEntityCannotQualifyConfirmation() {
        val group = ShelfGroup(7, "Original", 0, true, true)
        val book = LibraryBook("book-key", "", "", 0, sourceId = "local")
        val read = ShelfManagementRead("read-a", listOf(group), listOf(book))
        val ticket = ShelfManagementTicket("confirm-a", "vm-a", read.revision, ShelfManagementIntent.DeleteGroup(group))
        assertTrue(ShelfManagementUiPolicy.qualifies(ticket, "vm-a", read))
        assertFalse(ShelfManagementUiPolicy.qualifies(ticket, "vm-b", read))
        assertFalse(ShelfManagementUiPolicy.qualifies(ticket, "vm-a", read.copy(revision = "read-b")))
        assertFalse(ShelfManagementUiPolicy.qualifies(ticket, "vm-a", read.copy(groups = listOf(group.copy(groupName = "Changed")))))
        assertFalse(ShelfManagementUiPolicy.qualifies(ticket, "vm-a", read.copy(groups = listOf(group.copy(groupId = 8)))))
        val assign = ticket.copy(intent = ShelfManagementIntent.Assign(ShelfBookKey("other-source", book.id), group))
        assertFalse(ShelfManagementUiPolicy.qualifies(assign, "vm-a", read))
        assertFalse(ShelfManagementUiPolicy.qualifies(ticket, "vm-a", null))
    }

    @Test fun removalCapturePreservesCompositeOrderAndDuplicatesWithoutBorrowingMutableSelection() {
        val a = ShelfBookKey("source-a", "same-id")
        val b = ShelfBookKey("source-b", "same-id")
        val selection = mutableListOf(a, b, a)
        val captured = ShelfManagementUiPolicy.removal(selection)
        selection.clear()
        assertEquals(listOf(a, b, a), captured.targets)
        assertEquals(listOf(a), ShelfManagementUiPolicy.removal(listOf(a)).targets)
        val books = listOf(LibraryBook(a.bookId, "", "", 0, sourceId = a.sourceId), LibraryBook(b.bookId, "", "", 0, sourceId = b.sourceId))
        val read = ShelfManagementRead("read-a", emptyList(), books)
        val ticket = ShelfManagementTicket("confirm-a", "vm-a", read.revision, captured)
        assertTrue(ShelfManagementUiPolicy.qualifies(ticket, "vm-a", read))
        assertFalse(ShelfManagementUiPolicy.qualifies(ticket, "vm-a", read.copy(books = listOf(books.first()))))
        assertThrows(UnsupportedOperationException::class.java) { (captured.targets as MutableList<ShelfBookKey>).clear() }
    }
}
