import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Assinatura do release e OPCIONAL, e de proposito.
//
// O APK e publicado como asset de release num repositorio PUBLICO, entao nao ha
// keystore versionada aqui. Se as variaveis abaixo estiverem presentes (secrets
// do CI, ou um keystore.properties local nao versionado), o release sai assinado;
// se nao estiverem, `assembleRelease` continua funcionando e produz um APK
// *unsigned* (que precisa ser assinado depois pra ser instalavel).
//
// Ordem de precedencia: keystore.properties  >  variaveis de ambiente.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

val releaseStoreFile = signingValue("storeFile", "DIALOGLITE_KEYSTORE")
val releaseStorePassword = signingValue("storePassword", "DIALOGLITE_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "DIALOGLITE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "DIALOGLITE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() } && file(releaseStoreFile!!).exists()

android {
    namespace = "me.zippert.dialoglite"
    compileSdk = 36
    // Pinado: sem isto o AGP pede a sua versao minima (34.0.0) e tenta
    // instalar dentro do container, onde o SDK e read-only de proposito.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "me.zippert.dialoglite"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Reprodutibilidade: o esquema do Room versionado permite diff de migracao.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.generateKotlin", "true")
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.serialization.json)
}
