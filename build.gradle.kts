import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24"
    id("com.gradleup.shadow") version "9.6.1"
    id("com.diffplug.spotless") version "8.10.3"
}

group = "com.github.jikoo"
version = "3.0.3-SNAPSHOT-FORK"

repositories {
    mavenCentral()
    maven("https://repo.tcoded.com/releases")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://repo.glaremasters.me/repository/bloodshot")
    maven("https://jitpack.io")
    maven("https://raw.githubusercontent.com/FabioZumbi12/RedProtect/mvn-repo/")
    // BentoBox's release POM declares a snapshot version; resolve only its JAR.
    exclusiveContent {
        forRepository {
            maven("https://repo.codemc.org/repository/maven-public/") {
                name = "bentoboxArtifacts"
                metadataSources { artifact() }
            }
        }
        filter { includeModule("world.bentobox", "bentobox") }
    }
}

dependencies {
    paperweight.foliaDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:26.1.0")

    implementation("com.github.jikoo:planarwrappers:4.0.0")

    // Artifact-only dependencies avoid importing the plugins' conflicting server-library
    // constraints. They are supplied by the corresponding plugins at runtime.
    compileOnly("com.sk89q.worldedit:worldedit-core:7.4.2@jar")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.2@jar")
    compileOnly("com.sk89q.worldguard:worldguard-core:7.0.15@jar")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.15@jar")
    compileOnly("br.net.fabiozumbi12.RedProtect:RedProtect-Core:7.7.3") { isTransitive = false }
    compileOnly("br.net.fabiozumbi12.RedProtect:RedProtect-Spigot:7.7.3") { isTransitive = false }
    compileOnly("com.plotsquared:PlotSquared-Core:6.11.1@jar")
    // PlotSquared exposes Guice types in its API.
    compileOnly("com.google.inject:guice:5.1.0")
    // The published 2.7.0 POM incorrectly declares 2.7.0-SNAPSHOT.
    compileOnly("world.bentobox:bentobox:2.7.0@jar")
    compileOnly("com.griefdefender:api:2.1.1-SNAPSHOT") { isTransitive = false }
    compileOnly("com.github.cjburkey01:ClaimChunk:0.0.22") { isTransitive = false }
    compileOnly("com.github.angeschossen:LandsAPI:7.25.4") { isTransitive = false }
    compileOnly("com.github.TechFortress:GriefPrevention:18.0.0") { isTransitive = false }
    compileOnly("com.github.elBukkit:PreciousStones:1.17.2") { isTransitive = false }
    compileOnly("com.github.TownyAdvanced.towny:towny:0.102.0.13") { isTransitive = false }
    compileOnly("com.github.chrisrnj.Terrainer:terrainer-core:9ab2f53d41") { isTransitive = false }
    compileOnly("com.github.Zrips:Residence:6.0.2.3") { isTransitive = false }

    // Linear region libraries are supplied by the server.
    compileOnly("com.github.luben:zstd-jni:1.5.7-7")
    compileOnly("at.yawk.lz4:lz4-java:1.10.2")
    compileOnly("net.openhft:zero-allocation-hashing:2026.0")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.configureEach {
    resolutionStrategy.capabilitiesResolution.withCapability("org.lz4:lz4-java") {
        select("at.yawk.lz4:lz4-java:1.10.2")
        because("Use the maintained LZ4 implementation required by linear regions")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

paperweight {
    reobfArtifactConfiguration.set(ReobfArtifactConfiguration.MOJANG_PRODUCTION)
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
    from("src/main/precompiled_hooks") {
        include("*Hook.class")
        into("com/github/jikoo/regionerator/hooks")
    }
}

tasks.jar {
    archiveClassifier.set("plain")
    manifest.attributes("paperweight-mappings-namespace" to "mojang")
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Preserve the existing relocated names, including utilities moved into PlanarWrappers.
    relocate("com.github.jikoo.planarwrappers.util.Coords", "com.github.jikoo.regionerator.Coords")
    relocate("com.github.jikoo.planarwrappers.scheduler.DistributedTask", "com.github.jikoo.regionerator.util.DistributedTask")
    relocate("com.github.jikoo.planarwrappers.tuple.CachingSupplier", "com.github.jikoo.regionerator.util.SupplierCache")
    relocate("com.github.jikoo.planarwrappers", "com.github.jikoo.regionerator.planarwrappers")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    java {
        palantirJavaFormat()
        trimTrailingWhitespace()
        endWithNewline()
        removeUnusedImports()
        licenseHeaderFile("header.txt")
        importOrder().wildcardsLast(true)
    }
    kotlinGradle {
        target("*.gradle.kts")
        lineEndings = com.diffplug.spotless.LineEnding.UNIX
        ktlint()
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "regionerator"
            artifact(tasks.shadowJar)
        }
    }
}
