// Kotlin lint checks. See docs/contributing/code-style.md "Linting" for the rules and how to fix findings.
//
// ktlint and detekt run as their command-line tools in separate classpaths, so
// they do not depend on the Kotlin Gradle plugin version used by the mod.

val ktlintVersion = providers.gradleProperty("ktlint_version").get()
val detektVersion = providers.gradleProperty("detekt_version").get()

val ktlintCli: Configuration = configurations.create("ktlintCli")
val detektCli: Configuration = configurations.create("detektCli")

dependencies {
    ktlintCli("com.pinterest.ktlint:ktlint-cli:$ktlintVersion") {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
    detektCli("io.gitlab.arturbosch.detekt:detekt-cli:$detektVersion")
}

val ktlintSources = listOf("src/**/*.kt", "*.kts", "gradle/**/*.kts")
val ktlintBaseline = "config/ktlint/baseline.xml"
val detektInput = "src/main/kotlin,src/test/kotlin"
val detektConfig = "config/detekt/detekt.yml"
val detektBaseline = "config/detekt/baseline.xml"

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Checks Kotlin formatting with ktlint."
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = projectDir
    args("--relative", "--baseline=$ktlintBaseline")
    args(ktlintSources)
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Fixes Kotlin formatting with ktlint."
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = projectDir
    args("--relative", "--format", "--baseline=$ktlintBaseline")
    args(ktlintSources)
}

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "Checks Kotlin code smells and cognitive complexity with detekt."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    workingDir = projectDir
    args("--input", detektInput)
    args("--config", detektConfig, "--build-upon-default-config")
    args("--baseline", detektBaseline)
    args("--report", "html:build/reports/detekt/detekt.html")
}

tasks.register<JavaExec>("detektBaseline") {
    group = "verification"
    description = "Rewrites the detekt baseline. Use only when intentionally accepting existing findings."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    workingDir = projectDir
    args("--input", detektInput)
    args("--config", detektConfig, "--build-upon-default-config")
    args("--baseline", detektBaseline, "--create-baseline")
}

tasks.register("lint") {
    group = "verification"
    description = "Runs every Kotlin lint check."
    dependsOn("ktlintCheck", "detekt")
}

tasks.named("check") {
    dependsOn("lint")
}
