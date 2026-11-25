plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.ksp)

	// Add the Google services Gradle plugin
	id("com.google.gms.google-services")
}

android {
	namespace = "com.example.cos30017developmentproject"
	compileSdk {
		version = release(37)
	}

	defaultConfig {
		applicationId = "com.example.cos30017developmentproject"
		minSdk = 24
		targetSdk = 37
		versionCode = 1
		versionName = "1.0"

		testInstrumentationRunner = "com.example.cos30017developmentproject.FirebaseAuthEmulatorRunner"
	}

	buildTypes {
		release {
			optimization {
				enable = true
				packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
			}
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
}

dependencies {
	implementation(libs.androidx.drawerlayout)
	implementation(libs.androidx.recyclerview)
	implementation(libs.androidx.activity.ktx)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.androidx.room.runtime)
	implementation(libs.androidx.room.ktx)
	ksp(libs.androidx.room.compiler)
	implementation(libs.androidx.core.ktx)
	implementation(libs.material)
	implementation(libs.ucrop)
	implementation(libs.androidx.exifinterface)
	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.junit)

	// Import the Firebase BoM
	implementation(platform(libs.firebase.bom))

	// TODO: Add the dependencies for Firebase products you want to use
	// When using the BoM, don't specify versions in Firebase dependencies
	implementation(libs.firebase.analytics)
	implementation(libs.firebase.auth)
	implementation(libs.kotlinx.coroutines.play.services)

	// Add the dependencies for any other desired Firebase products
	// https://firebase.google.com/docs/android/setup#available-libraries
}
