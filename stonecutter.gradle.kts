plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.1"

// The root project builds nothing; `check` only exists to run lint.
tasks.register("check")

// Lint runs once on the shared sources, not per Minecraft version.
apply(from = "gradle/lint.gradle.kts")

repositories {
    mavenCentral()
}
