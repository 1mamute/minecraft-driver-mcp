import java.net.URLClassLoader
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("fabric-loom")
    java
}

val driverRoot = rootDir.resolve("../..").canonicalFile
val driverProperties = Properties().apply { driverRoot.resolve("gradle.properties").inputStream().use(::load) }
val gameProperties = Properties().apply { driverRoot.resolve("versions/1.21.1/gradle.properties").inputStream().use(::load) }
val driverVersion = "${driverProperties.getProperty("mod_version")}+1.21.1"
val driverCoordinate = "${driverProperties.getProperty("maven_group")}:minecraft-driver-mcp:$driverVersion"

repositories {
    maven {
        url = driverRoot.resolve("build/maven-verification").toURI()
        if (providers.gradleProperty("pomOnly").isPresent) {
            metadataSources {
                mavenPom()
                ignoreGradleMetadataRedirection()
            }
        }
    }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${driverProperties.getProperty("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${gameProperties.getProperty("fabric_version")}")
    modCompileOnly(driverCoordinate)
    modLocalRuntime(driverCoordinate)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

loom {
    runs.named("client") {
        property("driver.unfocused", "true")
    }
}

tasks.register("verifyDriverRuntime") {
    val runtime = configurations.named("runtimeClasspath")
    dependsOn(tasks.jar)
    inputs.files(runtime)
    doLast {
        URLClassLoader(runtime.get().files.map { it.toURI().toURL() }.toTypedArray(), ClassLoader.getPlatformClassLoader()).use { loader ->
            listOf(
                "io.github.ummamute.driver.api.DriverExtension",
                "kotlin.collections.ArrayDeque",
                "io.ktor.server.cio.CIO",
                "io.modelcontextprotocol.kotlin.sdk.server.Server"
            ).forEach { name -> check(Class.forName(name, false, loader).name == name) }
        }
        ZipFile(tasks.jar.get().archiveFile.get().asFile).use { jar ->
            check(jar.entries().asSequence().none { it.name.startsWith("io/github/ummamute/") || it.name.endsWith(".jar") })
        }
        println("Repository dependency supplies driver, Kotlin, Ktor and MCP runtime classes without bundling the driver into the consumer.")
    }
}
