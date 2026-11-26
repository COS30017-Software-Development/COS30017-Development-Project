package com.example.cos30017developmentproject.ui.items

import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemFilterTest {
	@Test
	fun allIncludesBothItemTypes() {
		assertTrue(ItemFilter.ALL.includes(FirestoreItemRepository.TYPE_LOST))
		assertTrue(ItemFilter.ALL.includes(FirestoreItemRepository.TYPE_FOUND))
	}

	@Test
	fun lostAndFoundFiltersExcludeOppositeType() {
		assertTrue(ItemFilter.LOST.includes(FirestoreItemRepository.TYPE_LOST))
		assertFalse(ItemFilter.LOST.includes(FirestoreItemRepository.TYPE_FOUND))
		assertTrue(ItemFilter.FOUND.includes(FirestoreItemRepository.TYPE_FOUND))
		assertFalse(ItemFilter.FOUND.includes(FirestoreItemRepository.TYPE_LOST))
	}
}
