package com.example.cos30017developmentproject

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.google.firebase.auth.FirebaseAuth

class FirebaseAuthEmulatorRunner : AndroidJUnitRunner() {
	override fun onCreate(arguments: Bundle) {
		super.onCreate(arguments)
		val host = arguments.getString(ARG_AUTH_EMULATOR_HOST)
		if (!host.isNullOrBlank()) {
			val port = arguments.getString(ARG_AUTH_EMULATOR_PORT)?.toIntOrNull() ?: DEFAULT_AUTH_EMULATOR_PORT
			FirebaseAuth.getInstance().useEmulator(host, port)
		} else {
			FirebaseAuth.getInstance().signOut()
		}
	}

	companion object {
		const val ARG_AUTH_EMULATOR_HOST = "firebaseAuthEmulatorHost"
		const val ARG_AUTH_EMULATOR_PORT = "firebaseAuthEmulatorPort"
		const val DEFAULT_AUTH_EMULATOR_PORT = 9099
	}
}
