package com.example.cos30017developmentproject.ui.items

import org.junit.Assert.*
import org.junit.Test

class ItemPaginationTest {
    private fun pager(count: Int = 12) = ItemPagination((1..count).map { "Item $it" }) { it }

    @Test fun pagesContainEightItemsThenTheRemainder() {
        val pager = pager()
        assertEquals(2, pager.pageCount)
        assertEquals(8, pager.visibleItems.size)
        pager.goToPage(1)
        assertEquals(listOf("Item 9", "Item 10", "Item 11", "Item 12"), pager.visibleItems)
    }

    @Test fun pageNavigationStaysWithinBounds() {
        val pager = pager()
        pager.goToPage(100)
        assertEquals(1, pager.pageIndex)
        pager.goToPage(-1)
        assertEquals(0, pager.pageIndex)
    }

    @Test fun searchIgnoresCaseAndSurroundingWhitespaceAndResetsPage() {
        val pager = pager()
        pager.goToPage(1)
        pager.search("  ITEM 12  ")
        assertEquals(listOf("Item 12"), pager.visibleItems)
        assertEquals(0, pager.pageIndex)
        pager.search("")
        assertEquals(12, pager.resultCount)
    }

    @Test fun searchMatchesTitleAndDescription() {
        val pager = ItemPagination(listOf("Backpack" to "Found in the library", "Bottle" to "Lost in class")) {
            "${it.first} ${it.second}"
        }
        pager.search("library")
        assertEquals("Backpack", pager.visibleItems.single().first)
        pager.search("bottle")
        assertEquals("Bottle", pager.visibleItems.single().first)
    }

    @Test fun noMatchesCannotNavigateToAnInvalidPage() {
        val pager = pager()
        pager.search("missing")
        pager.goToPage(1)
        assertEquals(0, pager.resultCount)
        assertEquals(1, pager.pageCount)
        assertEquals(0, pager.pageIndex)
        assertTrue(pager.visibleItems.isEmpty())
    }

    @Test fun exactMultipleDoesNotCreateAnExtraPage() {
        assertEquals(2, pager(16).pageCount)
        assertTrue(pager(0).visibleItems.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun pageSizeMustBePositive() {
        ItemPagination(listOf("Item"), 0) { it }
    }
}
