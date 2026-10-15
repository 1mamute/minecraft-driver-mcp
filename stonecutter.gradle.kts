plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.1"

// The root project builds nothing; `check` only exists to run lint, and `build` runs it so `./gradlew build` lints too.
val check = tasks.register("check")
tasks.register("build") {
    dependsOn(check)
}

// Lint runs once on the shared sources, not per Minecraft version.
apply(from = "gradle/lint.gradle.kts")

repositories {
    mavenCentral()
}
