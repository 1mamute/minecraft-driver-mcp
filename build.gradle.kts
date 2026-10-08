import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("fabric-loom")
    java
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val minecraftVersion = property("minecraft_version") as String
val loaderVersion = property("loader_version") as String
val fabricVersion = property("fabric_version") as String
val parchmentVersion = property("parchment_version") as String
val mcpSdkVersion = property("mcp_sdk_version") as String
val ktorVersion = property("ktor_version") as String

version = "${property("mod_version")}+$minecraftVersion"
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.parchmentmc.org")
}

// Libraries that ship inside the mod jar (jar-in-jar), so the mod is one file with no
// dependency on Fabric Language Kotlin. Minecraft already provides slf4j and the annotations (providedByMinecraft).
val bundled = configurations.create("bundled")
val providedByMinecraft = setOf("org.slf4j", "org.jetbrains:annotations")

configurations.named("implementation") { extendsFrom(bundled) }

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(
        loom.layered {
            officialMojangMappings()
            parchment("org.parchmentmc.data:parchment-$parchmentVersion@zip")
        }
    )

    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    bundled("io.modelcontextprotocol:kotlin-sdk-server:$mcpSdkVersion")
    bundled("io.ktor:ktor-server-cio:$ktorVersion")
    bundled("org.jetbrains.kotlin:kotlin-stdlib:${property("kotlin_version")}")

    testImplementation(platform("org.junit:junit-bom:${property("junit_version")}"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// `include` packs one module at a time, so every resolved library of `bundled` is listed.
afterEvaluate {
    bundled.resolvedConfiguration.resolvedArtifacts
        .map { it.moduleVersion.id }
        .distinct()
        .filterNot { it.group in providedByMinecraft || "${it.group}:${it.name}" in providedByMinecraft }
        .forEach { dependencies.add("include", "${it.group}:${it.name}:${it.version}") }
}

loom {
    runs {
        named("client") {
            // Created unfocused so the developer keeps working in other windows (see WindowMixin).
            property("driver.unfocused", "true")
        }
    }
}

// A fresh run directory opens the accessibility onboarding screen, which blocks an agent. Seed options once.
val prepareClientRun = tasks.register("prepareClientRun") {
    val options = layout.projectDirectory.file("run/options.txt").asFile
    doLast {
        if (options.exists()) return@doLast
        options.parentFile.mkdirs()
        val lines = listOf("onboardAccessibility:false", "pauseOnLostFocus:false", "soundCategory_master:0.0")
        options.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }
}

tasks.matching { it.name == "runClient" }.configureEach {
    dependsOn(prepareClientRun)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft", minecraftVersion)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "minecraft" to minecraftVersion, "loader" to loaderVersion)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

tasks.test {
    useJUnitPlatform()
}
