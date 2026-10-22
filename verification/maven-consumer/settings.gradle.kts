pluginManagement {
    val driverProperties = java.util.Properties().apply {
        file("../../gradle.properties").inputStream().use(::load)
    }
    plugins {
        id("fabric-loom") version driverProperties.getProperty("loom_version")
    }
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

rootProject.name = "driver-maven-consumer"
