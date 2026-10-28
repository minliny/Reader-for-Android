package io.reader.android

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reader.android.runtime.ShelfBookKey
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ShelfManagementIntent
import io.reader.android.ui.ShelfManagementRead
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Separate unapplied Native journey draft. Requires two already imported, independently frozen real owned books. */
@RunWith(AndroidJUnit4::class)
class UiShelfManagementTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = compose.activity.readerViewModel
    @get:Rule val rules: TestRule = RuleChain.outerRule(Timeout.builder().withTimeout(120, TimeUnit.SECONDS).build()).around(compose)

    @Test fun ownedGroupsAssignmentAndTwoRealBooksMoveOutOfShelf() {
        marker("method-enter")
        var stage = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            fun bookArg(name: String): String {
                val sha = args.getString(name) ?: throw AssertionError("$name required from actual stopped proof")
                assertTrue(sha.matches(Regex("[a-f0-9]{64}")))
                return "local:$sha"
            }
            val protected = bookArg("protectedBookSHA")
            val first = bookArg("firstOwnedBookSHA")
            val second = bookArg("secondOwnedBookSHA")
            assertEquals(3, setOf(protected, first, second).size)
            val name = args.getString("ownedGroupName") ?: throw AssertionError("ownedGroupName required")
            assertTrue(name.isNotBlank())
            val renamed = "$name · 已改名"
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || model.state.value.error != null || model.state.value.protocolRecoveryReference != null) }
            assertNull(model.state.value.error); assertNull(model.state.value.protocolRecoveryReference)
            assertTrue(model.state.value.pendingReady && model.state.value.pendingImports.isEmpty() &&
                model.state.value.pendingPositions.isEmpty() && model.state.value.pendingBookmarks.isEmpty())
            assertNull(model.state.value.pendingSearchClose)
            assertEquals(0L, model.state.value.importRevision)
            assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
            compose.onNodeWithTag("reader_shelf_management").performClick()
            val initial = ready { true }
            assertTrue(initial.books.any { it.sourceId == "local" && it.id == first } && initial.books.any { it.sourceId == "local" && it.id == second })
            val protectedBefore = initial.books.single { it.sourceId == "local" && it.id == protected }
            assertTrue(initial.groups.none { it.groupName == name || it.groupName == renamed })
            assertTrue(initial.books.single { it.id == first }.group == null && initial.books.single { it.id == second }.group == null)
            val originalGroups = initial.groups

            stage = "create-group"
            node("reader_group_name").performTextReplacement(name)
            node("reader_group_save").performClick()
            val created = ready { it.groups.any { group -> group.groupName == name } }.groups.single { it.groupName == name }
            assertTrue(originalGroups.none { it.groupId == created.groupId })
            marker("owned-group-create-visible", Bundle().apply { putLong("groupId", created.groupId) })

            stage = "rename-group"
            node("reader_group_edit_${created.groupId}").performClick()
            node("reader_group_name").performTextReplacement(renamed)
            node("reader_group_save").performClick()
            val updated = ready { it.groups.any { group -> group.groupId == created.groupId && group.groupName == renamed } }
                .groups.single { it.groupId == created.groupId }
            assertEquals(renamed, updated.groupName)
            marker("owned-group-rename-visible", Bundle().apply { putLong("groupId", updated.groupId) })

            stage = "assign-book"
            node("reader_management_book_local_$first").performClick()
            assertEquals(listOf(ShelfBookKey("local", first)), model.shelfManagement.state.value.selected)
            node("reader_group_assign_${created.groupId}").performClick()
            ready { it.books.single { book -> book.id == first }.group == renamed }
            marker("owned-book-assignment-visible")

            stage = "delete-group"
            node("reader_group_delete_${created.groupId}").performClick()
            val delete = model.shelfManagement.state.value.confirmation?.intent as? ShelfManagementIntent.DeleteGroup
                ?: throw AssertionError("Exact group confirmation absent")
            assertEquals(updated, delete.group)
            compose.onNodeWithTag("reader_confirmation_confirm").performClick()
            val afterDelete = ready { it.groups.none { group -> group.groupId == created.groupId } }
            assertEquals(originalGroups, afterDelete.groups)
            assertEquals(renamed, afterDelete.books.single { it.id == first }.group)
            marker("owned-group-delete-assignment-not-cascaded")

            stage = "batch-remove-owned-books"
            node("reader_management_book_local_$first").performClick()
            node("reader_management_book_local_$second").performClick()
            val targets = listOf(ShelfBookKey("local", first), ShelfBookKey("local", second))
            assertEquals(targets, model.shelfManagement.state.value.selected)
            node("reader_remove_shelf_selection").performClick()
            val remove = model.shelfManagement.state.value.confirmation?.intent as? ShelfManagementIntent.Remove
                ?: throw AssertionError("Exact removal confirmation absent")
            assertEquals(targets, remove.targets)
            assertTrue(remove.targets.none { it.bookId == protected })
            compose.onNodeWithText("移出后会清除所选书籍的阅读位置、正文修改和阅读缓存；原文件会保留。").assertExists()
            compose.onNodeWithTag("reader_confirmation_confirm").performClick()
            val after = ready { it.books.none { book -> book.id == first || book.id == second } }
            assertEquals(protectedBefore, after.books.single { it.sourceId == "local" && it.id == protected })
            assertEquals(originalGroups, after.groups)
            marker("owned-batch2-move-out-visible") // Authoritative original receipts still require the controller's stopped postproof.

            compose.onNodeWithTag("reader_back").performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.route == ReaderRoute.Bookshelf || model.state.value.error != null || model.state.value.protocolRecoveryReference != null) }
            assertNull(model.state.value.error); assertNull(model.state.value.protocolRecoveryReference)
            assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
            assertEquals(protectedBefore, model.state.value.books.single { it.sourceId == "local" && it.id == protected })
            assertTrue(model.state.value.books.none { it.id == first || it.id == second })
            assertEquals(0L, model.state.value.importRevision)
            marker("method-exit")
        } catch (failure: Throwable) {
            try { marker("method-failure", Bundle().apply { putString("phase", stage); putString("message", failure.message?.take(256)) }) }
            catch (markerFailure: Throwable) { if (failure !== markerFailure) failure.addSuppressed(markerFailure) }
            throw failure
        }
    }
    private fun ready(predicate: (ShelfManagementRead) -> Boolean): ShelfManagementRead {
        compose.waitUntil(20_000) {
            val state = model.shelfManagement.state.value
            !state.busy && ((state.read?.let(predicate) == true) || state.error != null || state.hasPending || model.state.value.protocolRecoveryReference != null)
        }
        val state = model.shelfManagement.state.value
        assertNull(state.error); assertFalse(state.hasPending); assertNull(model.state.value.protocolRecoveryReference)
        val read = state.read ?: throw AssertionError("Actual Core management read absent")
        assertTrue(predicate(read))
        return read
    }
    private fun node(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("reader_shelf_management_screen").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun marker(step: String, extras: Bundle = Bundle()) {
        extras.putString("readerStep", step); extras.putString("readerMethod", "ownedGroupsAssignmentAndTwoRealBooksMoveOutOfShelf")
        extras.putLong("readerElapsedMs", SystemClock.elapsedRealtime())
        InstrumentationRegistry.getInstrumentation().sendStatus(2, extras)
    }
}
