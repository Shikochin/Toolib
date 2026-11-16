/** 取当前仓库的 commit 数；拿不到（比如导出成 zip 构建）时退回 1。 */
fun gitCommitCount(projectDir: java.io.File): Int = runCatching {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(projectDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    process.waitFor()
    output.toIntOrNull()?.takeIf { it > 0 } ?: 1
}.getOrDefault(1)

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
        // versionCode 跟着 git commit 数走，每次提交自动 +1（CI 需要 fetch-depth: 0）
        versionCode = gitCommitCount(rootProject.projectDir)
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Python 运行时是原生组件；只保留真机 ABI，APK 体积最小
            abiFilters += "arm64-v8a"
        }
    }

    // 本地 / CI 通过环境变量注入签名信息；没有 keystore 时打不签名的包
    val keystoreFile = providers.environmentVariable("TOOLIB_KEYSTORE_PATH").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { file(it) }
    val hasKeystore = keystoreFile != null && keystoreFile.exists()

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = providers.environmentVariable("TOOLIB_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("TOOLIB_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("TOOLIB_KEY_PASSWORD").orNull
                // minSdk 29：v1 用不上，v2 + v3 都要（v3 支持密钥轮换，Android 9+ 优先用它校验）
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else null
            // R8：压缩 + 混淆 + 资源裁剪
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
        // 不写死路径：本机 / CI 上把 python3.13 放进 PATH 即可，需要时用环境变量覆盖
        providers.environmentVariable("TOOLIB_BUILD_PYTHON").orNull?.let { buildPython(it) }
        pip {
            install("yt-dlp")
        }
    }
}

// AGP 9.5 的 Compose group mapping 任务默认拉 2.2.10，这个版本没有发布到 Maven Central；
// 统一改成和 Kotlin 插件相同的版本。
configurations.matching { it.name == "composeMappingProducerClasspath" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "compose-group-mapping") {
            useVersion(libs.versions.kotlin.get())
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
