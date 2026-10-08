pluginManagement {
    plugins {
        id("fabric-loom") version providers.gradleProperty("loom_version").get()
        kotlin("jvm") version providers.gradleProperty("kotlin_version").get()
        kotlin("plugin.serialization") version providers.gradleProperty("kotlin_version").get()
    }
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        // One entry per supported Minecraft version; each has a folder under versions/.
        versions("1.21.1")
        vcsVersion = "1.21.1"
    }
}

rootProject.name = "minecraft-driver-mcp"
