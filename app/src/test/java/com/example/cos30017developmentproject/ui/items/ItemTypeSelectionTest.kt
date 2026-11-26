package com.example.cos30017developmentproject.ui.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemTypeSelectionTest {
	@Test
	fun noCheckedButtonRequiresExplicitItemType() {
		assertNull(ItemTypeSelection.fromCheckedButton(-1, 1, 2))
	}

	@Test
	fun checkedButtonsResolveToTheirItemTypes() {
		assertEquals(ItemTypeOption.LOST, ItemTypeSelection.fromCheckedButton(1, 1, 2))
		assertEquals(ItemTypeOption.FOUND, ItemTypeSelection.fromCheckedButton(2, 1, 2))
	}
}
