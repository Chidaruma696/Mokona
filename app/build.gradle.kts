import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.compose.compiler)
}

// Release signing comes from local.properties (never committed):
//   keystore.file=keystore.jks  keystore.password=...  keystore.alias=...  keystore.keyPassword=...
val localProperties = Properties().apply {
	rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val hasReleaseKey = localProperties.getProperty("keystore.file")?.let { rootProject.file(it).exists() } == true

android {
	namespace = "com.mokona.app"
	compileSdk = 36

	defaultConfig {
		applicationId = "com.mokona.app"
		minSdk = 26
		targetSdk = 36
		versionCode = 3
		versionName = "0.2.1"
	}

	signingConfigs {
		if (hasReleaseKey) {
			create("release") {
				storeFile = rootProject.file(localProperties.getProperty("keystore.file"))
				storePassword = localProperties.getProperty("keystore.password")
				keyAlias = localProperties.getProperty("keystore.alias")
				keyPassword = localProperties.getProperty("keystore.keyPassword")
			}
		}
	}

	// The OCR models for Japanese, Chinese and Korean are native libraries, ~25 MB per ABI: one APK
	// per architecture keeps the download at a third of a universal one. Phones are arm; x86 is emulators.
	splits {
		abi {
			isEnable = true
			reset()
			include("arm64-v8a", "armeabi-v7a")
			isUniversalApk = false
		}
	}

	buildTypes {
		debug {
			applicationIdSuffix = ".debug"
		}
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = "17"
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}

	sourceSets {
		getByName("main") {
			java.srcDir("src/main/kotlin")
		}
	}

	packaging {
		resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/DEPENDENCIES")
	}
}

dependencies {
	implementation(libs.androidx.core)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.browser)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.coroutines.android)
	implementation(libs.serialization.json)
	implementation(libs.okhttp)

	val composeBom = platform(libs.compose.bom)
	implementation(composeBom)
	implementation(libs.compose.ui)
	implementation(libs.compose.foundation)
	implementation(libs.compose.material3)
	implementation(libs.compose.material.icons)
	implementation(libs.compose.ui.tooling.preview)
	debugImplementation(libs.compose.ui.tooling)

	implementation(libs.coil.compose)
	implementation(libs.coil.network)

	// Manga in your language: on-device OCR for Japanese, Chinese and Korean, and offline translation models.
	implementation(libs.mlkit.text.latin)
	implementation(libs.mlkit.text.japanese)
	implementation(libs.mlkit.text.chinese)
	implementation(libs.mlkit.text.korean)
	implementation(libs.mlkit.translate)
	implementation(libs.coroutines.play.services)
	// Wallpaper rotation in the background.
	implementation(libs.androidx.work)
}
