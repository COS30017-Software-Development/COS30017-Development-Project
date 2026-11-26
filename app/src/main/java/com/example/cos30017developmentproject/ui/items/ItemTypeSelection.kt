package com.example.cos30017developmentproject.ui.items

enum class ItemTypeOption {
	LOST,
	FOUND
}

object ItemTypeSelection {
	fun fromCheckedButton(checkedButtonId: Int, lostButtonId: Int, foundButtonId: Int): ItemTypeOption? =
		when (checkedButtonId) {
			lostButtonId -> ItemTypeOption.LOST
			foundButtonId -> ItemTypeOption.FOUND
			else -> null
		}
}
