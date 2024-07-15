import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.RunPluginVerifierTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.util.*

val publishingToken = System.getenv("JB_PUB_TOKEN") ?: null
val publishingChannel = System.getenv("JB_PUB_CHANNEL") ?: "default"
// set by default in Github Actions
val isCI = System.getenv("CI") != null

fun prop(name: String): String =
    extra.properties[name] as? String
        ?: error("Property `$name` is not defined in gradle.properties for environment `$shortPlatformVersion`")

fun gitCommitHash(): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        commandLine = "git rev-parse --short HEAD".split(" ")
//            commandLine = "git rev-parse --abbrev-ref HEAD".split(" ")
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim().also {
        if (it == "HEAD")
            logger.warn("Unable to determine current branch: Project is checked out with detached head!")
    }
}

fun gitTimestamp(): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        commandLine = "git show --no-patch --format=%at HEAD".split(" ")
//            commandLine = "git rev-parse --abbrev-ref HEAD".split(" ")
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim().also {
        if (it == "HEAD")
            logger.warn("Unable to determine current branch: Project is checked out with detached head!")
    }
}

val shortPlatformVersion = prop("shortPlatformVersion")
val codeVersion = "1.3.4"
val pluginVersion = "$codeVersion.$shortPlatformVersion"
if (publishingChannel != "default") {
    // timestamp of the commit with this eaps addition
//    val start = 1714498465
//    val commitTimestamp = gitTimestamp().toInt() - start
//    -$publishingChannel.$commitTimestamp"
//    pluginVersion = "$pluginVersion
}

val pluginGroup = "org.sui"
val javaVersion = JavaVersion.VERSION_17
val pluginJarName = "intellij-sui-move-$pluginVersion"

val kotlinReflectVersion = "1.9.10"
// not use
val network = "testnet"
val suiVersion = "v2.1.0"

group = pluginGroup
version = pluginVersion

plugins {
    id("java")
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.3"
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

        downloadSources.set(!isCI)
        instrumentCode.set(false)
        ideaDependencyCachePath.set(dependencyCachePath)

//PluginDependencies.Uses`platformPlugins`propertyfromthegradle.propertiesfile.
        plugins.set(prop("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))

        version.set(prop("platformVersion"))
//localPath.set("/home/mkurnikov/pontem-ide/pontem-ide-2023.2/")
//localSourcesPath.set("/home/mkurnikov/pontem-ide/pontem-232.SNAPSHOT-source")
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
        if (file("src/$shortPlatformVersion/main/kotlin").exists()) {
            sourceSets {
                main {
                    kotlin.srcDirs("src/$shortPlatformVersion/main/kotlin")
                }
            }
        }
    }

    tasks {
        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "17"
                languageVersion = "1.9"
                apiVersion = "1.9"
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
//                    val zipFileUrl = "$baseUrl/$zipFileName"
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

    idea {
        pathVariables(mapOf("USER_HOME" to file("/home/mkurnikov")))
        module {
            name = "intellij-sui-move.main"
        }
    }
}

project(":plugin") {
    dependencies {
        implementation(project(":"))
    }

    tasks {
        patchPluginXml {
            version.set(pluginVersion)
            val codeVersionForUrl = codeVersion.replace('.', '-')
            changeNotes.set(
                """
    <body>
        <p><a href="https://intellij-move.github.io/$codeVersionForUrl.html">
            Changelog for the Intellij-Move $codeVersion
            </a></p>
    </body>
            """
            )
            sinceBuild.set(prop("pluginSinceBuild"))
            untilBuild.set(prop("pluginUntilBuild"))
        }

        ideaModule {
            enabled = false
        }
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
                        RunPluginVerifierTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
                    )
                )
            )
        }

        publishPlugin {
            token.set(publishingToken)
            channels.set(listOf(publishingChannel))
        }

        runIde {
            enabled = true
            systemProperty("org.move.debug.enabled", true)
//            systemProperty("org.move.external.linter.max.duration", 30)  // 30 ms
//            systemProperty("org.move.aptos.bundled.force.unsupported", true)
//            systemProperty("idea.log.debug.categories", "org.move.cli")
        }
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
//
//		downloadRobotServerPlugin{
//			version.set(remoteRobotVersion)
//		}
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
