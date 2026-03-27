plugins {
    kotlin("jvm") version "2.3.10"
}

group = "com.depanalyzer"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    // CLI
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    // XML parser (pom.xml)
    implementation("org.apache.maven:maven-model:3.9.14")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
