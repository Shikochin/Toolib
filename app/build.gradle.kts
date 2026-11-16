plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.chaquo.python")
}

android {
    namespace = "dev.shiko.toolib"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.shiko.toolib"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Python 运行时是原生组件；只保留真机 ABI，APK 体积最小
            abiFilters += "arm64-v8a"
        }
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
}

chaquopy {
    defaultConfig {
        version = "3.13"
        buildPython("/opt/homebrew/bin/python3.13")
        pip {
            install("yt-dlp")
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
            "com.chaquo.python" to "MIT",
        )
        val displayName = mapOf(
            "mochi-compose" to "Mochi Compose",
            "mochi-icons" to "Mochi Icons",
            "api" to "Shizuku API",
            "provider" to "Shizuku Provider",
            "activity-compose" to "AndroidX Activity",
            "core-ktx" to "AndroidX Core KTX",
            "compose-bom" to "Jetpack Compose",
            "gradle" to "Chaquopy",
        )
        val dependencies = configurations.getByName("implementation").dependencies
            .mapNotNull { dependency ->
                val group = dependency.group ?: return@mapNotNull null
                val name = displayName[dependency.name] ?: "${group}:${dependency.name}"
                name to (licenseByGroup[group] ?: "Apache-2.0")
            }
            .distinctBy { it.first }
            .sortedBy { it.first } + listOf("yt-dlp" to "Unlicense", "CPython" to "PSF-2.0")
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
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
