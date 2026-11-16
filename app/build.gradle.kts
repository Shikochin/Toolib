plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.shiko.toolib"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.shiko.toolib"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

val generateLicenseCatalog = tasks.register("generateLicenseCatalog") {
    notCompatibleWithConfigurationCache("Reads resolved implementation dependencies to regenerate the UI catalog")
    val output = layout.projectDirectory.file("src/main/java/dev/shiko/toolib/logic/GeneratedLicenseCatalog.kt")
    outputs.file(output)
    doLast {
        val licenseByGroup = mapOf(
            "io.github.feice11" to "Apache-2.0",
            "dev.rikka.shizuku" to "Apache-2.0",
            "androidx.activity" to "Apache-2.0",
            "androidx.compose" to "Apache-2.0",
            "androidx.core" to "Apache-2.0",
            "io.github.junkfood02.youtubedl-android" to "GPL-3.0",
        )
        val displayName = mapOf(
            "mochi-compose" to "Mochi Compose",
            "mochi-icons" to "Mochi Icons",
            "api" to "Shizuku API",
            "provider" to "Shizuku Provider",
            "activity-compose" to "AndroidX Activity",
            "core-ktx" to "AndroidX Core KTX",
            "compose-bom" to "Jetpack Compose",
            "library" to "yt-dlp Android Runtime",
            "ffmpeg" to "FFmpeg Android Runtime",
        )
        val dependencies = configurations.getByName("implementation").dependencies
            .mapNotNull { dependency ->
                val group = dependency.group ?: return@mapNotNull null
                val name = displayName[dependency.name] ?: "${group}:${dependency.name}"
                name to (licenseByGroup[group] ?: "Apache-2.0")
            }
            .distinctBy { it.first }
            .sortedBy { it.first }
        output.asFile.apply {
            parentFile.mkdirs()
            writeText(buildString {
                appendLine("package dev.shiko.toolib.logic")
                appendLine()
                appendLine("// Generated from the app's implementation dependencies.")
                appendLine("internal val generatedDependencyLicenses = listOf(")
                dependencies.forEachIndexed { index, (name, license) ->
                    append("    \"").append(name).append("\" to \"").append(license).append('"')
                    if (index != dependencies.lastIndex) append(',')
                    appendLine()
                }
                appendLine(")")
            })
        }
    }
}

tasks.named("preBuild").configure { dependsOn(generateLicenseCatalog) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation("io.github.feice11:mochi-compose:0.2.0")
    implementation("io.github.feice11:mochi-icons:0.2.0")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
