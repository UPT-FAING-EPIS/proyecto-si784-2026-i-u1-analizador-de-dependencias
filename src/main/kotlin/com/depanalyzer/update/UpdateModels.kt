package com.depanalyzer.update

data class UpdateSuggestion(
    val groupId: String,
    val artifactId: String,
    val currentVersion: String,
    val newVersion: String,
    val reason: UpdateReason,
    val targetType: UpdateTargetType = UpdateTargetType.DIRECT,
    val viaDirectCoordinate: String? = null
) {
    val coordinate: String
        get() = "$groupId:$artifactId"
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
