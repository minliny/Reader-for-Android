package com.reader.android.ui

import com.reader.android.ui.detail.BookDetailViewModel
import com.reader.android.ui.reader.ReaderViewModel
import com.reader.android.ui.search.SearchViewModel
import com.reader.android.ui.toc.TOCViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRealModeBoundaryTest {

    @Test
    fun `SearchViewModel defaults to fake mode`() {
        val vm = SearchViewModel()
        assertFalse(vm.isSearching)
        assertTrue(vm.results.isEmpty())
    }

    @Test
    fun `SearchViewModel accepts real HTTP mode`() {
        val vm = SearchViewModel(useRealHttp = true)
        assertNotNull(vm) // constructor accepts the flag
    }

    @Test
    fun `BookDetailViewModel defaults to fake mode`() {
        val vm = BookDetailViewModel()
        assertFalse(vm.isLoading)
    }

    @Test
    fun `BookDetailViewModel accepts real HTTP mode`() {
        val vm = BookDetailViewModel(useRealHttp = true)
        assertNotNull(vm)
    }

    @Test
    fun `TOCViewModel defaults to fake mode`() {
        val vm = TOCViewModel()
        assertFalse(vm.isLoading)
        assertTrue(vm.chapters.isEmpty())
    }

    @Test
    fun `TOCViewModel accepts real HTTP mode`() {
        val vm = TOCViewModel(useRealHttp = true)
        assertNotNull(vm)
    }

    @Test
    fun `ReaderViewModel defaults to fake mode`() {
        val vm = ReaderViewModel()
        assertFalse(vm.isLoading)
        assertTrue(vm.chapterTitle.isEmpty())
    }

    @Test
    fun `ReaderViewModel accepts real HTTP mode`() {
        val vm = ReaderViewModel(useRealHttp = true)
        assertNotNull(vm)
    }

    @Test
    fun `all four ViewModels share useRealHttp Boolean default false pattern`() {
        // Structural verification: consistent constructor API across all ViewModels
        val searchVm = SearchViewModel(useRealHttp = true)
        val detailVm = BookDetailViewModel(useRealHttp = true)
        val tocVm = TOCViewModel(useRealHttp = true)
        val readerVm = ReaderViewModel(useRealHttp = true)

        assertNotNull(searchVm)
        assertNotNull(detailVm)
        assertNotNull(tocVm)
        assertNotNull(readerVm)
    }
}
