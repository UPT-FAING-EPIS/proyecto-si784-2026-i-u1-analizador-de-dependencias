import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.10"
    application
    id("org.graalvm.buildtools.native") version "1.0.0"
    id("org.jetbrains.dokka") version "2.2.0"
    id("info.solidsoft.pitest") version "1.19.0"
    id("org.sonarqube") version "7.3.1.8318"
}

application {
    mainClass.set("com.depanalyzer.cli.DepAnalyzerCliKt")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "-Dfile.encoding=UTF-8")
}

group = "com.depanalyzer"
version = "2.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.10")

    // Jackson 3.1.0 BOM
    implementation(platform("tools.jackson:jackson-bom:3.1.0"))

    // CLI
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("com.github.ajalt.mordant:mordant:3.0.2")
    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    // JSON & XML (Jackson 3.x)
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("tools.jackson.dataformat:jackson-dataformat-xml")

    // XML parser (pom.xml)
    implementation("org.apache.maven:maven-model:3.9.14")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

pitest {
    targetClasses.set(listOf("com.depanalyzer.*"))
    targetTests.set(listOf("com.depanalyzer.*"))
    junit5PluginVersion.set("1.2.3")
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    threads.set(2)
}

sonar {
    properties {
        property("sonar.projectKey", "UPT-FAING-EPIS_proyecto-si784-2026-i-u1-analizador-de-dependencias")
        property("sonar.organization", "upt-faing-epis")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sources", "src/main/kotlin")
        property("sonar.tests", "src/test/kotlin")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.junit.reportPaths", "build/test-results/test")
    }
}

val enableNativeImageAgent =
    providers.gradleProperty("enableNativeImageAgent").orNull?.toBoolean() == true

graalvmNative {
    toolchainDetection.set(true)

    metadataRepository {
        enabled.set(false)
    }

    binaries {
        named("main") {
            imageName.set("depanalyzer")
            mainClass.set("com.depanalyzer.cli.DepAnalyzerCliKt")
            fallback.set(false)
            verbose.set(true)
            buildArgs.addAll(
                "--no-fallback",
                "-H:+ReportExceptionStackTraces",
                "--future-defaults=all",
                "-R:MaxHeapSize=512m"
            )
        }
    }

    agent {
        enabled.set(enableNativeImageAgent)
        metadataCopy {
            inputTaskNames.add("test")
            outputDirectories.add("src/main/resources/META-INF/native-image/com.depanalyzer/depanalyzer")
            mergeWithExisting.set(true)
        }
    }
}
