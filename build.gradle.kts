import java.security.MessageDigest

plugins {
    id("fabric-loom") version "1.15.5"
    id("maven-publish")
}

version = property("mod_version") as String
group = property("maven_group") as String
base { archivesName.set(property("archives_base_name") as String) }

repositories {
    mavenCentral()
    maven("https://api.modrinth.com/maven")
}

val launcherHome = providers.environmentVariable("TROPIMON_HOME").orNull?.let(::file)
    ?: providers.environmentVariable("APPDATA").orNull?.let { file(it).resolve(".tropimon") }
    ?: file(System.getProperty("user.home")).resolve(".tropimon")
val localMods = launcherHome.resolve("mods")
val officialDependenciesOnly = providers.gradleProperty("officialDependenciesOnly").isPresent
val cobblemonJar = if (officialDependenciesOnly) null else providers.gradleProperty("cobblemonJar").orNull?.let(::file) ?: run {
    val installed = localMods.listFiles()
        ?.filter { it.isFile && it.name.matches(Regex("Cobblemon-fabric-.+\\.jar", RegexOption.IGNORE_CASE)) }
        .orEmpty()
    if (installed.size > 1) {
        throw GradleException("Plusieurs JAR Cobblemon détectés ; définir -PcobblemonJar=<jar>.")
    }
    installed.singleOrNull()
}
if (cobblemonJar != null && !cobblemonJar.isFile) {
    throw GradleException("JAR Cobblemon introuvable : définir un -PcobblemonJar valide.")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    if (cobblemonJar != null) {
        modImplementation(files(cobblemonJar))
    } else {
        modImplementation("maven.modrinth:MdwFAVRL:${property("cobblemon_modrinth_version")}")
    }
    modRuntimeOnly("net.fabricmc:fabric-language-kotlin:1.13.7+kotlin.2.2.21")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { withSourcesJar() }
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
tasks.test { useJUnitPlatform() }
val cobblemonMinimumVersion = property("cobblemon_min_version") as String
val verifyCobblemonCompatibility = tasks.register("verifyCobblemonCompatibility") {
    group = "verification"
    description = "Refuse les anciennes bornes Cobblemon avant de fabriquer un JAR."
    inputs.property("cobblemonMinimumVersion", cobblemonMinimumVersion)
    inputs.file("src/main/resources/fabric.mod.json")
    doLast {
        val expected = "\"cobblemon\": \">=$cobblemonMinimumVersion\""
        check(file("src/main/resources/fabric.mod.json").readText().contains(expected)) {
            "fabric.mod.json doit déclarer Cobblemon >=$cobblemonMinimumVersion sans borne maximale artificielle."
        }
    }
}

tasks.processResources {
    dependsOn(verifyCobblemonCompatibility)
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}

val privateContent = listOf(
    "**/.git/**", "**/.gradle/**", "**/.env*", "**/*.log", "**/logs/**",
    "**/config/**", "**/backups/**", "**/screenshots/**", "**/captures/**",
    "**/codex-clipboard-*", "**/*.p12", "**/*.pfx", "**/*.keystore"
)
tasks.withType<Jar>().configureEach { exclude(privateContent) }

val privacyCheck by tasks.registering(Exec::class) {
    dependsOn(tasks.remapJar, tasks.remapSourcesJar)
    commandLine(file(System.getProperty("java.home")).resolve("bin/java").absolutePath,
        "tools/PrivacyCheck.java", projectDir.absolutePath,
        tasks.remapJar.get().archiveFile.get().asFile.absolutePath,
        tasks.remapSourcesJar.get().archiveFile.get().asFile.absolutePath)
}
tasks.check { dependsOn(privacyCheck) }

val prepareDelivery by tasks.registering {
    dependsOn(privacyCheck)
    doLast {
        val source = tasks.remapJar.get().archiveFile.get().asFile
        val localDir = layout.buildDirectory.dir("delivery/local").get().asFile.apply { mkdirs() }
        val shareDir = layout.buildDirectory.dir("delivery/shareable").get().asFile.apply { mkdirs() }
        source.copyTo(localDir.resolve("TropimonCasino-${project.version}+1.21.1-LOCAL.jar"), true)
        file("tools/InstallWhenClosed.ps1").copyTo(localDir.resolve("InstallWhenClosed.ps1"), true)
        file("tools/ArmLocalUpdate.ps1").copyTo(localDir.resolve("ArmLocalUpdate.ps1"), true)
        source.copyTo(shareDir.resolve("TropimonCasino-${project.version}+1.21.1.jar"), true)
    }
}

tasks.register<Exec>("armTropimonCasinoLocal") {
    dependsOn(prepareDelivery)
    val localDir = layout.buildDirectory.dir("delivery/local")
    commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
        localDir.map { it.file("ArmLocalUpdate.ps1").asFile.absolutePath }.get(),
        "-Source", localDir.map { it.file("TropimonCasino-${project.version}+1.21.1-LOCAL.jar").asFile.absolutePath }.get())
}

tasks.register<Exec>("installTropimonCasinoLocal") {
    dependsOn(prepareDelivery)
    commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
        file("tools/InstallLocal.ps1").absolutePath,
        "-Source", layout.buildDirectory.file("delivery/local/TropimonCasino-${project.version}+1.21.1-LOCAL.jar").get().asFile.absolutePath,
        "-TargetDirectory", localMods.absolutePath)
}


val prepareReleaseDelivery = tasks.register("prepareReleaseDelivery") {
    group = "distribution"
    description = "Produit les JAR local et partageable vérifiés de la même version."
    dependsOn(tasks.build)
    doLast {
        val source = tasks.remapJar.get().archiveFile.get().asFile
        val deliveryRoot = layout.buildDirectory.dir("release").get().asFile
        val shareDirectory = deliveryRoot.resolve("shareable")
        val localDirectory = deliveryRoot.resolve("local")
        shareDirectory.deleteRecursively()
        localDirectory.deleteRecursively()
        shareDirectory.mkdirs()
        localDirectory.mkdirs()

        fun copyAndHash(target: File) {
            source.copyTo(target, overwrite = true)
            val digest = MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            target.resolveSibling(target.name + ".sha256").writeText(hash + System.lineSeparator())
        }

        copyAndHash(shareDirectory.resolve("TropimonCasino-${project.version}+1.21.1.jar"))
        copyAndHash(localDirectory.resolve("TropimonCasino-${project.version}+1.21.1-LOCAL.jar"))
        file("tools/install-local-deferred.ps1")
            .copyTo(localDirectory.resolve("install-local-deferred.ps1"), overwrite = true)
    }
}

tasks.register("armReleaseLocal") {
    group = "distribution"
    description = "Arme l'installation locale différée sans arrêter Minecraft ni le launcher."
    dependsOn(prepareReleaseDelivery)
    doLast {
        val script = layout.buildDirectory.file("release/local/install-local-deferred.ps1").get().asFile
        ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
            "-ExecutionPolicy", "Bypass", "-File", script.absolutePath)
            .directory(script.parentFile)
            .start()
    }
}


