const admin = require("firebase-admin");
const { logger } = require("firebase-functions");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");

admin.initializeApp();

exports.deliverUserNotification = onDocumentCreated(
	"notifications/{notificationId}",
	async (event) => {
		const notification = event.data?.data();
		const recipientUid = notification?.recipientUid;
		if (!notification || typeof recipientUid !== "string" || !recipientUid) {
			logger.error("Notification is missing its recipient UID", {
				notificationId: event.params.notificationId,
			});
			return;
		}

		const tokenSnapshot = await admin.firestore()
			.collection("users")
			.doc(recipientUid)
			.collection("fcmTokens")
			.get();
		const tokenDocuments = tokenSnapshot.docs.filter(
			(document) => typeof document.data().token === "string",
		);
		if (tokenDocuments.length === 0) return;

		const data = {
			notificationId: event.params.notificationId,
			type: String(notification.type || ""),
			title: String(notification.title || "Lost and Found"),
			message: String(notification.message || ""),
			itemId: String(notification.itemId || ""),
			itemType: String(notification.itemType || ""),
			relatedRequestId: String(notification.relatedRequestId || ""),
		};
		const invalidTokens = [];
		for (let offset = 0; offset < tokenDocuments.length; offset += 500) {
			const documents = tokenDocuments.slice(offset, offset + 500);
			const response = await admin.messaging().sendEachForMulticast({
				tokens: documents.map((document) => document.data().token),
				data,
				android: { priority: "high" },
			});
			response.responses.forEach((result, index) => {
				if (
					!result.success &&
					(result.error?.code === "messaging/invalid-registration-token" ||
						result.error?.code === "messaging/registration-token-not-registered")
				) {
					invalidTokens.push(documents[index].ref.delete());
				}
			});
		}
		await Promise.all(invalidTokens);
	},
);
