package com.depanalyzer.repository

object MavenToCpeMapper {

    private val KNOWN_MAPPINGS = mapOf(
        "junit:junit" to "junit:junit",
        "org.junit:junit" to "junit:junit",
        "org.junit.jupiter:junit-jupiter-api" to "junit:junit",
        "org.junit.jupiter:junit-jupiter-engine" to "junit:junit",
        "org.junit.vintage:junit-vintage-engine" to "junit:junit",

        "org.mockito:mockito-core" to "mockito:mockito",
        "org.mockito:mockito-all" to "mockito:mockito",

        "org.apache.logging.log4j:log4j-core" to "apache:log4j",
        "org.apache.logging.log4j:log4j-api" to "apache:log4j",

        "org.slf4j:slf4j-api" to "slf4j:slf4j",
        "org.slf4j:slf4j-simple" to "slf4j:slf4j",

        "org.springframework:spring-core" to "springframework:spring",
        "org.springframework:spring-beans" to "springframework:spring",
        "org.springframework.boot:spring-boot-starter" to "springframework:spring_boot",

        "com.fasterxml.jackson.core:jackson-databind" to "fasterxml:jackson_databind",
        "com.fasterxml.jackson.core:jackson-core" to "fasterxml:jackson_core",

        "com.google.guava:guava" to "google:guava",

        "com.squareup.okhttp3:okhttp" to "squareup:okhttp",

        "com.google.code.gson:gson" to "google:gson",

        "org.projectlombok:lombok" to "projectlombok:lombok"
    )

    fun mapToCpe(groupId: String, artifactId: String, version: String): String {
        val key = "$groupId:$artifactId"

        val knownMapping = KNOWN_MAPPINGS[key]
        if (knownMapping != null) {
            val (vendor, product) = knownMapping.split(":")
            return buildCpe(vendor, product, version)
        }

        val vendor = extractVendor(groupId)
        val product = extractProduct(artifactId)

        return buildCpe(vendor, product, version)
    }

    private fun extractVendor(groupId: String): String {
        val parts = groupId.split(".")

        return when {
            parts.size == 1 -> parts[0].lowercase()

            "apache" in parts -> "apache"
            "springframework" in parts -> "springframework"
            "junit" in parts -> "junit"
            "google" in parts -> "google"
            "squareup" in parts -> "squareup"
            "fasterxml" in parts -> "fasterxml"
            "slf4j" in parts -> "slf4j"

            parts.size >= 2 -> parts[parts.size - 2].lowercase()

            else -> parts.last().lowercase()
        }
    }

    private fun extractProduct(artifactId: String): String {
        var product = artifactId.lowercase()

        product = product.replace(Regex("-?v?\\d+$"), "")

        product = when {
            product.endsWith("-starter") -> product.dropLast("-starter".length)
            product.endsWith("-client") -> product.dropLast("-client".length)
            product.endsWith("-core") && product != "jackson-core" -> product.dropLast("-core".length)
            else -> product
        }

        product = product.replace("-", "_")

        return product
    }

    private fun buildCpe(vendor: String, product: String, version: String): String {
        return "cpe:2.3:a:$vendor:$product:$version:*:*:*:*:*:*:*"
    }
}
