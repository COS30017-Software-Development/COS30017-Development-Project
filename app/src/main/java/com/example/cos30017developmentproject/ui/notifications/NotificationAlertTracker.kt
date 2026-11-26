package com.example.cos30017developmentproject.ui.notifications

class NotificationAlertTracker {
	private val knownIds = mutableSetOf<String>()
	private var initialized = false

	fun newlyObserved(ids: Iterable<String>): List<String> {
		if (!initialized) {
			knownIds.addAll(ids)
			initialized = true
			return emptyList()
		}
		return ids.filter { knownIds.add(it) }
	}

	fun markNotified(id: String): Boolean = knownIds.add(id)
}
