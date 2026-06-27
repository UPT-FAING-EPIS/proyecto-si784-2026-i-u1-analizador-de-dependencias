import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    kotlin("jvm") version "2.3.10"
    application
    jacoco
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
version = providers.gradleProperty("releaseVersion")
    .orElse(providers.environmentVariable("PROJECT_VERSION"))
    .orElse("2.2.0-SNAPSHOT")
    .map { it.removePrefix("v") }
    .get()

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    testImplementation("io.cucumber:cucumber-java:7.22.2")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.22.2")
    testImplementation("org.junit.platform:junit-platform-suite:1.11.4")
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
    exclude("**/bdd/**")
    finalizedBy(tasks.jacocoTestReport)
}

val unitTest by tasks.registering(Test::class) {
    description = "Runs unit tests independently from integration and interface tests."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    exclude("**/*IntegrationTest*")
    exclude("**/integration/**")
    exclude("**/cli/**")
    exclude("**/tui/**")
    exclude("**/bdd/**")
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/unit"))
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/unit"))
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    include("**/*IntegrationTest*")
    include("**/integration/**")
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/integration"))
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/integration"))
}

val interfaceTest by tasks.registering(Test::class) {
    description = "Runs CLI and TUI interface tests."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    include("**/cli/**")
    include("**/tui/**")
    include("**/report/ConsoleRendererTest*")
    exclude("**/*IntegrationTest*")
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/interface"))
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/interface"))
}

val bddTest by tasks.registering(Test::class) {
    description = "Runs executable Gherkin scenarios."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    include("**/bdd/RunCucumberTest*")
    systemProperty("cucumber.plugin", "pretty,html:build/reports/cucumber/index.html,json:build/reports/cucumber/cucumber.json,junit:build/test-results/bdd/cucumber.xml")
    systemProperty("cucumber.publish.quiet", "true")
}

tasks.register("qualityTest") {
    description = "Runs all independently reported test suites."
    group = "verification"
    dependsOn(unitTest, integrationTest, interfaceTest, bddTest)
}

jacoco {
    toolVersion = "0.8.13"
}

val coverageExclusions = listOf(
    "**/tui/AnalyzeTuiApp*",
    "**/cli/BaseAnalyzeCommand*",
    "**/cli/Update\$Companion*",
    "**/parser/gradle/GradleCommandExecutor*"
)

tasks.withType<JacocoReport>().configureEach {
    classDirectories.setFrom(
        files(classDirectories.files.map { directory ->
            fileTree(directory) {
                exclude(coverageExclusions)
            }
        })
    )
}

tasks.withType<JacocoCoverageVerification>().configureEach {
    classDirectories.setFrom(
        files(classDirectories.files.map { directory ->
            fileTree(directory) {
                exclude(coverageExclusions)
            }
        })
    )
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
    dependsOn(unitTest, integrationTest, interfaceTest, bddTest)
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("projectVersion", project.version.toString())
    filesMatching("version.properties") {
        expand("version" to project.version)
    }
}

pitest {
    targetClasses.set(
        listOf(
            "com.depanalyzer.core.graph.*",
            "com.depanalyzer.report.DependencyTreeBuilder*",
            "com.depanalyzer.report.DependencyTreeNode*",
            "com.depanalyzer.report.Vulnerability*"
        )
    )
    targetTests.set(
        listOf(
            "com.depanalyzer.core.graph.*Test",
            "com.depanalyzer.report.DependencyTreeBuilderTest",
            "com.depanalyzer.report.VulnerabilityTest"
        )
    )
    junit5PluginVersion.set("1.2.3")
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    threads.set(2)
    mutationThreshold.set(45)
    coverageThreshold.set(65)
    failWhenNoMutations.set(false)
    timeoutFactor.set(2.0.toBigDecimal())
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
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
        property(
            "sonar.coverage.exclusions",
            "**/tui/AnalyzeTuiApp.kt,**/cli/DepAnalyzerCli.kt,**/cli/UpdateCommand.kt,**/parser/gradle/GradleCommandExecutor.kt"
        )
        property("sonar.qualitygate.wait", "true")
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
