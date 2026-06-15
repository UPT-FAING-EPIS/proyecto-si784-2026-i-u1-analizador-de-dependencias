package com.depanalyzer.update

import com.depanalyzer.parser.Ecosystem
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class UpdateSuggestion(
    val groupId: String,
    val artifactId: String,
    val currentVersion: String,
    val newVersion: String,
    val reason: UpdateReason,
    val targetType: UpdateTargetType = UpdateTargetType.DIRECT,
    val viaDirectCoordinate: String? = null,
    val ecosystem: Ecosystem = Ecosystem.MAVEN
) {
    val coordinate: String
        get() = "$groupId:$artifactId"

    val suggestionId: String
        get() {
            val raw = listOf(
                ecosystem.name,
                groupId,
                artifactId,
                currentVersion,
                newVersion,
                reason.name,
                targetType.name,
                viaDirectCoordinate.orEmpty()
            ).joinToString("|")
            return MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(StandardCharsets.UTF_8))
                .take(8)
                .joinToString("") { "%02x".format(it) }
        }
}

enum class UpdateTargetType {
    DIRECT,
    TRANSITIVE_OVERRIDE;

    fun label(): String = when (this) {
        DIRECT -> "directa"
        TRANSITIVE_OVERRIDE -> "override"
    }
}

enum class UpdateReason {
    OUTDATED,
    CVE;

    fun label(): String {
        return when (this) {
            OUTDATED -> "outdated"
            CVE -> "CVE"
        }
    }
}

data class UpdateResult(
    val suggestion: UpdateSuggestion,
    val applied: Boolean,
    val note: String
)
