import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.RunPluginVerifierTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

val JB_PUB_TOKEN = "perm:TW92ZV9GdW5z.OTItOTgzNQ==.1uGwoqvUEqLb2z3rtOSQtxTtn8TnkG\n"
val publishingToken = System.getenv("JB_PUB_TOKEN") ?: null
// set by default in Github Actions
val isCI = System.getenv("CI") != null

fun prop(name: String): String =
    extra.properties[name] as? String
        ?: error("Property `$name` is not defined in gradle.properties for environment `$shortPlatformVersion`")

val shortPlatformVersion = prop("shortPlatformVersion")
val codeVersion = "1.2.0"
val pluginVersion = "$codeVersion.$shortPlatformVersion"
val pluginGroup = "org.sui.move"
val javaVersion = JavaVersion.VERSION_17
val pluginJarName = "intellij-sui-move-$pluginVersion"

val kotlinReflectVersion = "1.8.10"
// not use
val network = "testnet"
val suiVersion = "v2.1.0"

group = pluginGroup
version = pluginVersion

plugins {
    id("java")
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.2"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
    id("net.saliman.properties") version "1.5.2"
    id("org.gradle.idea")
    id("de.undercouch.download") version "5.5.0"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinReflectVersion")

    implementation("io.sentry:sentry:7.2.0") {
        exclude("org.slf4j")
    }
    implementation("com.github.ajalt.clikt:clikt:3.5.2")
}

allprojects {
    apply {
        plugin("kotlin")
        plugin("org.jetbrains.grammarkit")
        plugin("org.jetbrains.intellij")
        plugin("de.undercouch.download")
    }

    repositories {
        mavenCentral()
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/central/")
        maven("https://maven.aliyun.com/repository/public/")
        maven("https://maven.aliyun.com/repository/google/")
        maven("https://maven.aliyun.com/repository/jcenter/")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
    }

    intellij {
        pluginName.set(pluginJarName)
        type.set(prop("platformType"))
        version.set(prop("platformVersion"))

        downloadSources.set(!isCI)
        instrumentCode.set(false)
        ideaDependencyCachePath.set(dependencyCachePath)
        updateSinceUntilBuild.set(false)
        plugins.set(
            prop("platformPlugins")
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
        )

    }

    configure<JavaPluginExtension> {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    sourceSets {
        main {
            java.srcDirs("src/main/gen")
        }
    }

    kotlin {
        sourceSets {
            main {
                kotlin.srcDirs("src/$shortPlatformVersion/main/kotlin")
            }
        }
    }

    tasks {
        patchPluginXml {
            version.set(pluginVersion)
            changeNotes.set(
                """
                <body>
                    <p><a href="https://github.com/pontem-network/intellij-move/blob/master/changelog/$pluginVersion.md">
                        Changelog for Intellij-Move $pluginVersion on Github
                        </a></p>
                </body>
                """
            )
            sinceBuild.set(prop("pluginSinceBuild"))
            untilBuild.set(prop("pluginUntilBuild"))
        }

        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "17"
                languageVersion = "1.9"
                apiVersion = "1.8"
                freeCompilerArgs = listOf("-Xjvm-default=all")
            }
        }

        // All these tasks don't make sense for non-root subprojects
        // Root project (i.e. `:plugin`) enables them itself if needed
        runIde { enabled = false }
        prepareSandbox { enabled = false }
        buildSearchableOptions { enabled = false }

        withType<Jar> {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        task("downloadSuiBinaries") {
            val baseUrl = "https://github.com/MystenLabs/sui/releases/download/$network-$suiVersion"
            doLast {
                for (releasePlatform in listOf("macos-arm64", "macos-x86_64", "ubuntu-x86_64", "windows-x86_64")) {
                    val zipFileName = "sui-$network-$suiVersion-$releasePlatform.tgz"
                    val zipFileUrl = "$baseUrl/$zipFileName"
                    val zipRoot = "${rootProject.buildDir}/zip"
                    val zipFile = file("$zipRoot/$zipFileName")
                    if (zipFile.exists()) {
                        continue
                    }
//                    download.run {
//                        src(zipFileUrl)
//                        dest(zipFile)
//                        overwrite(false)
//                    }

                    val platformName =
                        when (releasePlatform) {
                            "macos-arm64" -> "macos-arm"
                            "macos-x86_64" -> "macos"
                            "ubuntu-x86_64" -> "ubuntu"
                            "windows-x86_64" -> "windows"
                            else -> error("unreachable")
                        }
                    val platformRoot = file("${rootProject.rootDir}/bin/$platformName")
                    copy {
                        from(
                            tarTree(zipFile)
                        )
                        into(platformRoot)
                    }
                }
            }
        }
    }

}

project(":") {
    tasks {
        generateLexer {
            sourceFile.set(file("src/main/grammars/MoveLexer.flex"))
            targetOutputDir.set(file("src/main/gen/org/sui/lang"))
            purgeOldFiles.set(true)
            // targetClass.set("_MoveLexer")
        }
        generateParser {
            sourceFile.set(file("src/main/grammars/MoveParser.bnf"))
            targetRootOutputDir.set(file("src/main/gen"))
            pathToParser.set("/org/sui/lang/MoveParser.java")
            pathToPsiRoot.set("/org/sui/lang/psi")
            purgeOldFiles.set(true)
        }
        withType<KotlinCompile> {
            dependsOn(generateLexer, generateParser)
        }
    }

    task("resolveDependencies") {
        doLast {
            rootProject.allprojects
                .map { it.configurations }
                .flatMap { it.filter { c -> c.isCanBeResolved } }
                .forEach { it.resolve() }
        }
    }
}

project(":plugin") {
    dependencies {
        implementation(project(":"))
    }

    tasks {
        runPluginVerifier {
            if ("SNAPSHOT" !in shortPlatformVersion) {
                ideVersions.set(
                    prop("verifierIdeVersions")
                        .split(',').map(String::trim).filter(String::isNotEmpty)
                )
            }
            failureLevel.set(
                EnumSet.complementOf(
                    EnumSet.of(
                        // these are the only issues we tolerate
                        RunPluginVerifierTask.FailureLevel.DEPRECATED_API_USAGES,
                        RunPluginVerifierTask.FailureLevel.EXPERIMENTAL_API_USAGES,
                        RunPluginVerifierTask.FailureLevel.INTERNAL_API_USAGES,
                        RunPluginVerifierTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
                    )
                )
            )
        }

        publishPlugin {
            token.set(publishingToken)
        }

//        verifyPlugin {
//            pluginDir.set(
//                file("$rootDir/plugin/build/idea-sandbox/plugins/$pluginJarName")
//            )
//        }
        runIde { enabled = true }
        prepareSandbox { enabled = true }
        buildSearchableOptions {
            enabled = true
            jbrVersion.set(prop("jbrVersion"))
        }


        withType<PrepareSandboxTask> {
            // copy bin/ directory inside the plugin zip file
            from("$rootDir/bin") {
                into("${pluginName.get()}/bin")
                include("**")
            }
        }

        withType<org.jetbrains.intellij.tasks.RunIdeTask> {
            jbrVersion.set(prop("jbrVersion"))

            if (environment.getOrDefault("CLION_LOCAL", "false") == "true") {
                val clionDir = File("/snap/clion/current")
                if (clionDir.exists()) {
                    ideDir.set(clionDir)
                }
            }
        }
    }
}

val Project.dependencyCachePath
    get(): String {
        val cachePath = file("${rootProject.projectDir}/deps")
        // If cache path doesn't exist, we need to create it manually
        // because otherwise gradle-intellij-plugin will ignore it
        if (!cachePath.exists()) {
            cachePath.mkdirs()
        }
        return cachePath.absolutePath
    }