package com.example.cos30017developmentproject.ui.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAlertTrackerTest {
	@Test
	fun initialSnapshotSeedsExistingNotificationsWithoutAlerts() {
		val tracker = NotificationAlertTracker()

		assertEquals(emptyList<String>(), tracker.newlyObserved(listOf("old-1", "old-2")))
		assertEquals(emptyList<String>(), tracker.newlyObserved(listOf("old-1", "old-2")))
	}

	@Test
	fun onlyNewNotificationIdsAreReportedOnce() {
		val tracker = NotificationAlertTracker()
		tracker.newlyObserved(listOf("existing"))

		assertEquals(listOf("new"), tracker.newlyObserved(listOf("existing", "new")))
		assertEquals(emptyList<String>(), tracker.newlyObserved(listOf("new", "existing")))
	}
}
