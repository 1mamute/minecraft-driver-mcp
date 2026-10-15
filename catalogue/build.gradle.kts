plugins {
    `java-library`
    kotlin("jvm")
}

group = property("maven_group") as String
version = property("mod_version") as String

base {
    archivesName.set("minecraft-driver-mcp-catalogue")
}

repositories {
    mavenCentral()
}

dependencies {
    api("io.modelcontextprotocol:kotlin-sdk-core:${property("mcp_sdk_version")}")

    testImplementation(platform("org.junit:junit-bom:${property("junit_version")}"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}
