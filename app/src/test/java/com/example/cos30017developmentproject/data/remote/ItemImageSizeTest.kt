package com.example.cos30017developmentproject.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemImageSizeTest {
	@Test
	fun acceptsImagesAtOrBelowTheFiveMegabyteLimit() {
		assertTrue(ItemImageSize.isWithinLimit(ItemImageSize.MAX_BYTES))
		assertTrue(ItemImageSize.isWithinLimit(ItemImageSize.MAX_BYTES - 1))
	}

	@Test
	fun rejectsImagesLargerThanFiveMegabytesAndEmptyFiles() {
		assertFalse(ItemImageSize.isWithinLimit(ItemImageSize.MAX_BYTES + 1))
		assertFalse(ItemImageSize.isWithinLimit(0))
	}
}
