package com.depanalyzer.security

import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

object InputSafety {
    const val CREDENTIAL_HOST_ALLOWLIST_ENV = "DEPANALYZER_TRUSTED_CREDENTIAL_HOSTS"

    private const val MAX_VERSION_LENGTH = 128
    private val SAFE_VERSION_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}$")
    private val IPV4_REGEX = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")
    private const val LOOPBACK_V6 = "::1"

    fun isSafeVersion(version: String): Boolean {
        if (version.isBlank() || version.length > MAX_VERSION_LENGTH) return false
        if (version != version.trim()) return false
        return SAFE_VERSION_REGEX.matches(version)
    }

    fun isAllowedRepositoryUrl(url: String): Boolean {
        val uri = parseUri(url) ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && scheme != "http") return false

        val host = normalizedHost(uri) ?: return false
        if (host.isBlank() || isLocalHost(host)) return false

        val explicitPort = uri.port
        if (explicitPort != -1 && explicitPort !in setOf(80, 443)) return false

        val literal = parseIpLiteral(host)
        return !(literal != null && isPrivateOrReserved(literal))
    }

    fun parseTrustedCredentialHosts(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()

        return raw.split(',')
            .map { normalizeTrustedHostEntry(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun isTrustedCredentialDestination(url: String, trustedHosts: Set<String>): Boolean {
        if (trustedHosts.isEmpty()) return false
        val uri = parseUri(url) ?: return false

        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https") return false

        val host = normalizedHost(uri) ?: return false
        return hostMatchesTrustedList(host, trustedHosts)
    }

    fun isWithinParentBoundary(rootPom: File, candidateParentPom: File): Boolean {
        if (candidateParentPom.name != "pom.xml") return false

        val projectDir = rootPom.parentFile?.canonicalFile ?: return false
        val boundaryRoot = projectDir.parentFile?.canonicalFile ?: projectDir

        return candidateParentPom.canonicalFile.toPath().startsWith(boundaryRoot.toPath())
    }

    private fun isLocalHost(host: String): Boolean {
        return host == "localhost" || host.endsWith(".localhost") || host == LOOPBACK_V6
    }

    private fun parseUri(url: String): URI? {
        return runCatching { URI(url.trim()) }.getOrNull()
    }

    private fun normalizedHost(uri: URI): String? {
        return uri.host?.trim()?.lowercase()
    }

    private fun normalizeTrustedHostEntry(entry: String): String {
        val trimmed = entry.trim().lowercase()
        if (trimmed.isBlank()) return ""

        if ("://" in trimmed) {
            val host = runCatching { URI(trimmed).host }.getOrNull()?.trim()?.lowercase().orEmpty()
            return host
        }

        return trimmed
    }

    private fun hostMatchesTrustedList(host: String, trustedHosts: Set<String>): Boolean {
        return trustedHosts.any { rule ->
            when {
                rule.startsWith('.') -> {
                    val suffix = rule.removePrefix(".")
                    suffix.isNotBlank() && (host == suffix || host.endsWith(".$suffix"))
                }

                else -> host == rule
            }
        }
    }

    private fun parseIpLiteral(host: String): InetAddress? {
        if (IPV4_REGEX.matches(host)) {
            val bytes = host.split('.').mapNotNull { part -> part.toIntOrNull() }
            if (bytes.size != 4 || bytes.any { it !in 0..255 }) return null
            return InetAddress.getByAddress(
                byteArrayOf(
                    bytes[0].toByte(),
                    bytes[1].toByte(),
                    bytes[2].toByte(),
                    bytes[3].toByte()
                )
            )
        }

        if (':' in host) {
            val normalized = host.removePrefix("[").removeSuffix("]")
            val parsed = runCatching { InetAddress.getByName(normalized) }.getOrNull() ?: return null
            if (parsed is Inet6Address) return parsed
        }

        return null
    }

    private fun isPrivateOrReserved(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isMulticastAddress) {
            return true
        }

        return when (address) {
            is Inet4Address -> isPrivateOrReservedIpv4(address)
            is Inet6Address -> isPrivateOrReservedIpv6(address)
            else -> true
        }
    }

    private fun isPrivateOrReservedIpv4(address: Inet4Address): Boolean {
        val b = address.address.map { it.toInt() and 0xFF }

        val first = b[0]
        val second = b[1]

        return first == 10 ||
                first == 127 ||
                (first == 169 && second == 254) ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                (first == 100 && second in 64..127) ||
                (first == 198 && second in 18..19) ||
                first == 0 ||
                first >= 224
    }

    private fun isPrivateOrReservedIpv6(address: Inet6Address): Boolean {
        if (address.isSiteLocalAddress) return true

        val firstByte = address.address[0].toInt() and 0xFF
        val isUniqueLocal = (firstByte and 0xFE) == 0xFC

        return isUniqueLocal
    }
}
