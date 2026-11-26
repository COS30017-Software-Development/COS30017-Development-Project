const fs = require("node:fs");
const path = require("node:path");
const { after, before, beforeEach, test } = require("node:test");
const {
	assertFails,
	assertSucceeds,
	initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {
	collection,
	doc,
	getDoc,
	getDocs,
	query,
	runTransaction,
	serverTimestamp,
	setDoc,
	where,
} = require("firebase/firestore");

const PROJECT_ID = "demo-cos30017";
const OWNER_UID = "owner123";
const SENDER_UID = "sender123";
const OTHER_SENDER_UID = "other123";
const ITEM_ID = "item123";

let environment;

before(async () => {
	environment = await initializeTestEnvironment({
		projectId: PROJECT_ID,
		firestore: {
			rules: fs.readFileSync(path.resolve(__dirname, "../../firestore.rules"), "utf8"),
		},
	});
});

beforeEach(async () => {
	await environment.clearFirestore();
	await environment.withSecurityRulesDisabled(async (context) => {
		await setDoc(doc(context.firestore(), `items/${ITEM_ID}`), itemData(ITEM_ID, "FOUND"));
	});
});

after(async () => {
	await environment.cleanup();
});

test("a signed-in user can request a public Found or Lost item", async (t) => {
	const anonymousDatabase = environment.unauthenticatedContext().firestore();
	await assertSucceeds(getDoc(doc(anonymousDatabase, `items/${ITEM_ID}`)));
	for (const type of ["FOUND", "LOST"]) {
		await t.test(type, async () => {
			await environment.withSecurityRulesDisabled(async (context) => {
				await setDoc(doc(context.firestore(), `items/${ITEM_ID}`), itemData(ITEM_ID, type));
			});
			const sender = environment.authenticatedContext(SENDER_UID).firestore();
			await assertSucceeds(createRequest(sender, SENDER_UID, type));
		});
	}
});

test("the owner cannot request their own item", async () => {
	const ownerDatabase = environment.authenticatedContext(OWNER_UID).firestore();
	await assertFails(createRequest(ownerDatabase, OWNER_UID, "FOUND"));
});

test("accepting one request only updates that request and leaves the item pending return", async () => {
	const firstRequestId = `${SENDER_UID}_${ITEM_ID}`;
	const secondRequestId = `${OTHER_SENDER_UID}_${ITEM_ID}`;
	await environment.withSecurityRulesDisabled(async (context) => {
		const database = context.firestore();
		await setDoc(doc(database, `claims/${firstRequestId}`), requestData(firstRequestId, SENDER_UID));
		await setDoc(doc(database, `claims/${secondRequestId}`), requestData(secondRequestId, OTHER_SENDER_UID));
	});

	const ownerDatabase = environment.authenticatedContext(OWNER_UID).firestore();
	await assertSucceeds(runTransaction(ownerDatabase, async (transaction) => {
		const itemReference = doc(ownerDatabase, `items/${ITEM_ID}`);
		const requestReference = doc(ownerDatabase, `claims/${firstRequestId}`);
		transaction.update(requestReference, { status: "Accepted" });
		transaction.update(itemReference, {
			status: "PENDING_RETURN",
			lastAcceptedRequestId: firstRequestId,
		});
		transaction.set(doc(ownerDatabase, "notifications/accepted-note"), notificationData({
			recipientUid: SENDER_UID,
			senderUid: OWNER_UID,
			senderName: "",
			type: "REQUEST_ACCEPTED",
			requestId: firstRequestId,
			title: "Request accepted",
			message: 'Your request for "Backpack" was accepted.',
		}));
	}));

	const accepted = await assertSucceeds(getDoc(doc(ownerDatabase, `claims/${firstRequestId}`)));
	const untouched = await assertSucceeds(getDoc(doc(ownerDatabase, `claims/${secondRequestId}`)));
	const item = await assertSucceeds(getDoc(doc(ownerDatabase, `items/${ITEM_ID}`)));
	if (accepted.data().status !== "Accepted") throw new Error("Selected request was not accepted");
	if (untouched.data().status !== "Pending") throw new Error("Other request status changed");
	if (item.data().status !== "PENDING_RETURN") throw new Error("Item did not remain pending return");
});

test("the owner can read and accept a legacy claim without owner metadata", async () => {
	const legacyRequestId = `${SENDER_UID}_${ITEM_ID}`;
	await environment.withSecurityRulesDisabled(async (context) => {
		const legacyRequest = requestData(legacyRequestId, SENDER_UID);
		delete legacyRequest.ownerUid;
		delete legacyRequest.itemType;
		legacyRequest.status = "PENDING";
		await setDoc(doc(context.firestore(), `claims/${legacyRequestId}`), legacyRequest);
	});

	const ownerDatabase = environment.authenticatedContext(OWNER_UID).firestore();
	const requests = await assertSucceeds(
		getDocs(query(
			collection(ownerDatabase, "claims"),
			where("itemId", "==", ITEM_ID),
		)),
	);
	if (requests.size !== 1) throw new Error("Owner could not read the legacy request");
	await assertSucceeds(runTransaction(ownerDatabase, async (transaction) => {
		transaction.update(doc(ownerDatabase, `claims/${legacyRequestId}`), {
			status: "Accepted",
			ownerUid: OWNER_UID,
			itemType: "FOUND",
		});
		transaction.update(doc(ownerDatabase, `items/${ITEM_ID}`), {
			status: "PENDING_RETURN",
			lastAcceptedRequestId: legacyRequestId,
		});
		transaction.set(doc(ownerDatabase, "notifications/legacy-accepted-note"), notificationData({
			recipientUid: SENDER_UID,
			senderUid: OWNER_UID,
			senderName: "",
			type: "REQUEST_ACCEPTED",
			requestId: legacyRequestId,
			title: "Request accepted",
			message: 'Your request for "Backpack" was accepted.',
		}));
	}));
	const accepted = await assertSucceeds(getDoc(doc(ownerDatabase, `claims/${legacyRequestId}`)));
	if (accepted.data().status !== "Accepted") throw new Error("Legacy request was not accepted");
});

async function createRequest(database, senderUid, itemType) {
	const requestId = `${senderUid}_${ITEM_ID}`;
	const request = requestData(requestId, senderUid, itemType);
	request.notificationId = `note_${senderUid}`;
	const type = itemType === "FOUND" ? "CLAIM_REQUEST" : "FOUND_REQUEST";
	const title = itemType === "FOUND" ? "New claim request" : "New found request";
	const notification = notificationData({
		recipientUid: OWNER_UID,
		senderUid,
		senderName: "Sender",
		type,
		requestId,
		title,
		message: 'Sender sent a request for "Backpack".',
		itemType,
	});
	return runTransaction(database, async (transaction) => {
		transaction.set(doc(database, `claims/${requestId}`), {
			...request,
			submittedAt: serverTimestamp(),
		});
		transaction.set(doc(database, `notifications/${request.notificationId}`), {
			...notification,
			createdAt: serverTimestamp(),
		});
	});
}

function itemData(itemId, type) {
	return {
		itemId,
		itemName: "Backpack",
		description: "Blue campus backpack",
		category: "Bag",
		location: "Library",
		date: new Date(),
		type,
		imageUrl: null,
		imageName: "sample_backpack",
		status: "OPEN",
		pendingClaimCount: 0,
		postedBy: OWNER_UID,
	};
}

function requestData(requestId, senderUid, itemType = "FOUND") {
	return {
		claimId: requestId,
		itemId: ITEM_ID,
		itemType,
		studentId: senderUid,
		ownerUid: OWNER_UID,
		description: "I can identify this item",
		status: "Pending",
		submittedAt: serverTimestamp(),
		itemTitle: "Backpack",
		itemLocation: "Library",
		studentName: "Sender",
		studentEmail: "sender@example.test",
		studentNumber: "123456789",
		notificationId: "notification-id",
	};
}

function notificationData({
	recipientUid,
	senderUid,
	senderName,
	type,
	requestId,
	title,
	message,
	itemType = "FOUND",
}) {
	return {
		recipientUid,
		senderUid,
		senderName,
		type,
		relatedRequestId: requestId,
		itemId: ITEM_ID,
		itemType,
		itemTitle: "Backpack",
		itemLocation: "Library",
		relatedItemId: null,
		relatedItemType: null,
		relatedItemTitle: null,
		relatedItemLocation: null,
		title,
		message,
		createdAt: serverTimestamp(),
		isRead: false,
	};
}
