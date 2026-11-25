package com.example.cos30017developmentproject

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

internal object FirebaseAuthEmulatorConfig {
	fun requireConfigured() {
		val host = InstrumentationRegistry.getArguments()
			.getString(FirebaseAuthEmulatorRunner.ARG_AUTH_EMULATOR_HOST)
		assumeTrue(
			"Set firebaseAuthEmulatorHost to run Firebase Auth emulator tests; tests never use production Auth.",
			!host.isNullOrBlank()
		)
	}
}
