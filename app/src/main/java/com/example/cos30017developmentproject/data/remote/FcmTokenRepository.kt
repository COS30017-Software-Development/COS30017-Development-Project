package com.example.cos30017developmentproject.data.remote

import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object FcmTokenRepository {
	fun register(uid: String, token: String) {
		tokenReference(uid, token).set(
			mapOf("token" to token, "updatedAt" to Timestamp.now()),
			SetOptions.merge()
		).addOnFailureListener { error ->
			Log.e(TAG, "Unable to register FCM token", error)
		}
	}

	fun unregister(uid: String, token: String) {
		tokenReference(uid, token).delete().addOnFailureListener { error ->
			Log.e(TAG, "Unable to unregister FCM token", error)
		}
	}

	private fun tokenReference(uid: String, token: String) =
		FirebaseFirestore.getInstance()
			.collection(USERS_COLLECTION)
			.document(uid)
			.collection(TOKENS_COLLECTION)
			.document(Uri.encode(token))

	private const val USERS_COLLECTION = "users"
	private const val TOKENS_COLLECTION = "fcmTokens"
	private const val TAG = "FcmTokenRepository"
}
