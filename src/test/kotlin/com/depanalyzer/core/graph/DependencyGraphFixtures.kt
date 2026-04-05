package com.depanalyzer.core.graph

import com.depanalyzer.report.AffectedDependency
import com.depanalyzer.report.Vulnerability
import com.depanalyzer.report.VulnerabilitySeverity
import com.depanalyzer.report.VulnerabilitySource
import java.time.Instant

object DependencyGraphFixtures {

    fun build2LevelChain(): Pair<List<DependencyNode>, Map<String, List<Vulnerability>>> {
        val vuln = createVulnerability(
            "CVE-2024-0001", 
            VulnerabilitySeverity.HIGH,
            "com.vulnerable",
            "lib",
            "1.0"
        )

        val direct = DependencyNode(
            id = "com.myapp:app:1.0",
            groupId = "com.myapp",
            artifactId = "app",
            version = "1.0"
        )

        val transitive = DependencyNode(
            id = "com.vulnerable:lib:1.0",
            groupId = "com.vulnerable",
            artifactId = "lib",
            version = "1.0",
            parent = direct,
            vulnerabilities = listOf(vuln)
        )

        direct.addChild(transitive)

        val vulnMap = mapOf(
            "com.vulnerable:lib:1.0" to listOf(vuln)
        )

        return Pair(listOf(direct, transitive), vulnMap)
    }

    fun build3LevelChain(): Pair<List<DependencyNode>, Map<String, List<Vulnerability>>> {
        val vuln = createVulnerability(
            "CVE-2024-0002", 
            VulnerabilitySeverity.CRITICAL,
            "com.lib",
            "vulnerable",
            "2.0"
        )

        val direct = DependencyNode(
            id = "com.myapp:service:1.0",
            groupId = "com.myapp",
            artifactId = "service",
            version = "1.0"
        )

        val intermediate = DependencyNode(
            id = "com.lib:connector:1.5",
            groupId = "com.lib",
            artifactId = "connector",
            version = "1.5",
            parent = direct
        )

        val vulnerable = DependencyNode(
            id = "com.lib:vulnerable:2.0",
            groupId = "com.lib",
            artifactId = "vulnerable",
            version = "2.0",
            parent = intermediate,
            vulnerabilities = listOf(vuln)
        )

        direct.addChild(intermediate)
        intermediate.addChild(vulnerable)

        val vulnMap = mapOf(
            "com.lib:vulnerable:2.0" to listOf(vuln)
        )

        return Pair(listOf(direct, intermediate, vulnerable), vulnMap)
    }

    fun build4LevelChain(): Pair<List<DependencyNode>, Map<String, List<Vulnerability>>> {
        val vuln = createVulnerability(
            "CVE-2024-0003", 
            VulnerabilitySeverity.MEDIUM,
            "com.deps",
            "deep",
            "1.2"
        )

        val direct = DependencyNode(
            id = "com.myapp:main:1.0",
            groupId = "com.myapp",
            artifactId = "main",
            version = "1.0"
        )

        val level2 = DependencyNode(
            id = "com.deps:upper:3.0",
            groupId = "com.deps",
            artifactId = "upper",
            version = "3.0",
            parent = direct
        )

        val level3 = DependencyNode(
            id = "com.deps:mid:2.0",
            groupId = "com.deps",
            artifactId = "mid",
            version = "2.0",
            parent = level2
        )

        val level4 = DependencyNode(
            id = "com.deps:deep:1.2",
            groupId = "com.deps",
            artifactId = "deep",
            version = "1.2",
            parent = level3,
            vulnerabilities = listOf(vuln)
        )

        direct.addChild(level2)
        level2.addChild(level3)
        level3.addChild(level4)

        val vulnMap = mapOf(
            "com.deps:deep:1.2" to listOf(vuln)
        )

        return Pair(listOf(direct, level2, level3, level4), vulnMap)
    }

    fun buildDiamondDependency(): Pair<List<DependencyNode>, Map<String, List<Vulnerability>>> {
        val vuln = createVulnerability(
            "CVE-2024-0004", 
            VulnerabilitySeverity.HIGH,
            "com.lib",
            "base",
            "1.0"
        )

        val direct = DependencyNode(
            id = "com.myapp:app:1.0",
            groupId = "com.myapp",
            artifactId = "app",
            version = "1.0"
        )

        val mid1 = DependencyNode(
            id = "com.lib:mid1:1.0",
            groupId = "com.lib",
            artifactId = "mid1",
            version = "1.0",
            parent = direct
        )

        val mid2 = DependencyNode(
            id = "com.lib:mid2:1.0",
            groupId = "com.lib",
            artifactId = "mid2",
            version = "1.0",
            parent = direct
        )

        val vulnerable = DependencyNode(
            id = "com.lib:base:1.0",
            groupId = "com.lib",
            artifactId = "base",
            version = "1.0",
            parent = mid1,
            vulnerabilities = listOf(vuln)
        )

        direct.addChild(mid1)
        direct.addChild(mid2)
        mid1.addChild(vulnerable)

        val vulnMap = mapOf(
            "com.lib:base:1.0" to listOf(vuln)
        )

        return Pair(listOf(direct, mid1, mid2, vulnerable), vulnMap)
    }

    fun buildCircularReference(): Pair<List<DependencyNode>, Map<String, List<Vulnerability>>> {
        val vuln = createVulnerability(
            "CVE-2024-0005", 
            VulnerabilitySeverity.MEDIUM,
            "com.circular",
            "a",
            "1.0"
        )

        val nodeA = DependencyNode(
            id = "com.circular:a:1.0",
            groupId = "com.circular",
            artifactId = "a",
            version = "1.0",
            vulnerabilities = listOf(vuln)
        )

        val nodeB = DependencyNode(
            id = "com.circular:b:1.0",
            groupId = "com.circular",
            artifactId = "b",
            version = "1.0",
            parent = nodeA
        )

        val nodeC = DependencyNode(
            id = "com.circular:c:1.0",
            groupId = "com.circular",
            artifactId = "c",
            version = "1.0",
            parent = nodeB
        )

        nodeA.addChild(nodeB)
        nodeB.addChild(nodeC)

        val vulnMap = mapOf(
            "com.circular:a:1.0" to listOf(vuln)
        )

        return Pair(listOf(nodeA, nodeB, nodeC), vulnMap)
    }

    fun buildMultipleCVEs(): Pair<List<DependencyNode>, Map<String, List<Vulnerability>>> {
        val vuln1 = createVulnerability(
            "CVE-2024-1001", 
            VulnerabilitySeverity.CRITICAL,
            "org.apache",
            "commons",
            "3.0"
        )
        val vuln2 = createVulnerability(
            "CVE-2024-1002", 
            VulnerabilitySeverity.HIGH,
            "org.apache",
            "commons",
            "3.0"
        )
        val vuln3 = createVulnerability(
            "CVE-2024-1003", 
            VulnerabilitySeverity.MEDIUM,
            "org.apache",
            "commons",
            "3.0"
        )

        val direct = DependencyNode(
            id = "com.myapp:app:1.0",
            groupId = "com.myapp",
            artifactId = "app",
            version = "1.0"
        )

        val vulnerable = DependencyNode(
            id = "org.apache:commons:3.0",
            groupId = "org.apache",
            artifactId = "commons",
            version = "3.0",
            parent = direct,
            vulnerabilities = listOf(vuln1, vuln2, vuln3)
        )

        direct.addChild(vulnerable)

        val vulnMap = mapOf(
            "org.apache:commons:3.0" to listOf(vuln1, vuln2, vuln3)
        )

        return Pair(listOf(direct, vulnerable), vulnMap)
    }

    fun buildMultiplePathsToVulnerable(): Pair<List<DependencyNode>, Map<String, List<Vulnerability>>> {
        val vuln = createVulnerability(
            "CVE-2024-0006", 
            VulnerabilitySeverity.HIGH,
            "com.lib",
            "unsafe",
            "1.0"
        )

        val direct = DependencyNode(
            id = "com.myapp:app:1.0",
            groupId = "com.myapp",
            artifactId = "app",
            version = "1.0"
        )

        val pathA = DependencyNode(
            id = "com.dep:pathA:1.0",
            groupId = "com.dep",
            artifactId = "pathA",
            version = "1.0",
            parent = direct
        )

        val pathB = DependencyNode(
            id = "com.dep:pathB:1.0",
            groupId = "com.dep",
            artifactId = "pathB",
            version = "1.0",
            parent = direct
        )

        val vulnerable = DependencyNode(
            id = "com.lib:unsafe:1.0",
            groupId = "com.lib",
            artifactId = "unsafe",
            version = "1.0",
            parent = pathA,
            vulnerabilities = listOf(vuln)
        )

        direct.addChild(pathA)
        direct.addChild(pathB)
        pathA.addChild(vulnerable)
        pathB.addChild(vulnerable)

        val vulnMap = mapOf(
            "com.lib:unsafe:1.0" to listOf(vuln)
        )

        return Pair(listOf(direct, pathA, pathB, vulnerable), vulnMap)
    }

    private fun createVulnerability(
        cveId: String,
        severity: VulnerabilitySeverity,
        groupId: String,
        artifactId: String,
        version: String
    ): Vulnerability {
        return Vulnerability(
            cveId = cveId,
            description = "Test vulnerability: $cveId",
            severity = severity,
            cvssScore = 7.5,
            affectedDependency = AffectedDependency(groupId, artifactId, version),
            source = VulnerabilitySource.OSS_INDEX,
            retrievedAt = Instant.now(),
            referenceUrl = "https://example.com/$cveId"
        )
    }
}
