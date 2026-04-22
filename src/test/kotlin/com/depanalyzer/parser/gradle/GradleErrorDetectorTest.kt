package com.depanalyzer.parser.gradle

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GradleErrorDetectorTest {

    @Test
    fun `should detect plugin incompatible error - SourceDirectorySetFactory`() {
        val output = """
            FAILURE: Build failed with an exception.
            * Where:
            Build file 'build.gradle' line: 3
            * What went wrong:
            An exception occurred applying plugin request [id: 'org.web3j', version: '4.5.0']
            > Failed to apply plugin class 'org.web3j.solidity.gradle.plugin.SolidityPlugin'.
               > Could not create plugin of type 'SolidityPlugin'.
                  > Could not generate a decorated class for type SolidityPlugin.
                     > org/gradle/api/internal/file/SourceDirectorySetFactory
        """.trimIndent()

        val error = GradleErrorDetector.detectError(output)
        assertNotNull(error)
        assertEquals(GradleErrorType.PLUGIN_INCOMPATIBLE, error.type)
        assert(error.message.contains("incompatible", ignoreCase = true))
        assert(error.suggestedFlags.contains("--no-daemon"))
        assert(error.suggestedFlags.contains("--build-cache=off"))
    }

    @Test
    fun `should detect plugin incompatible error - Could not create plugin`() {
        val output = """
            FAILURE: Build failed with an exception.
            * What went wrong:
            An exception occurred applying plugin request [id: 'some.plugin']
            > Failed to apply plugin class 'com.example.SomePlugin'.
            > Could not create plugin of type 'SomePlugin'.
        """.trimIndent()

        val error = GradleErrorDetector.detectError(output)
        assertNotNull(error)
        assertEquals(GradleErrorType.PLUGIN_INCOMPATIBLE, error.type)
    }

    @Test
    fun `should detect JVM incompatible error - NoClassDefFoundError in Groovy`() {
        val output = """
            FAILURE: Build failed with an exception.
            * What went wrong:
            Could not initialize class org.codehaus.groovy.runtime.InvokerHelper
            > Exception java.lang.NoClassDefFoundError: Could not initialize class org.codehaus.groovy.reflection.ReflectionCache
        """.trimIndent()

        val error = GradleErrorDetector.detectError(output)
        assertNotNull(error)
        assertEquals(GradleErrorType.JVM_INCOMPATIBLE, error.type)
        assert(
            error.message.contains("Gradle version", ignoreCase = true) || error.message.contains(
                "JVM",
                ignoreCase = true
            )
        )
    }

    @Test
    fun `should detect JVM incompatible error - InvokerHelper`() {
        val output = """
            WARNING: A restricted method in java.lang.System has been called
            Starting Build
            FAILURE: Build failed with an exception.
            * What went wrong:
            Could not initialize class org.codehaus.groovy.runtime.InvokerHelper
        """.trimIndent()

        val error = GradleErrorDetector.detectError(output)
        assertNotNull(error)
        assertEquals(GradleErrorType.JVM_INCOMPATIBLE, error.type)
    }

    @Test
    fun `should detect classpath error`() {
        val output = """
            FAILURE: Build failed with an exception.
            * What went wrong:
            java.lang.ClassNotFoundException: com.example.SomeClass
        """.trimIndent()

        val error = GradleErrorDetector.detectError(output)
        assertNotNull(error)
        assertEquals(GradleErrorType.CLASSPATH_ERROR, error.type)
    }

    @Test
    fun `should return null for successful build`() {
        val output = """
            > Configure project :
            
            Project ':' with tasks: [dependencies]
            
            root project 'sample'
            
            compileClasspath - Compile classpath for source set 'main'.
            \--- org.example:lib:1.0.0
            
            runtimeClasspath - Runtime classpath of source set 'main'.
            \--- org.example:lib:1.0.0
        """.trimIndent()

        val error = GradleErrorDetector.detectError(output)
        assertNull(error)
    }

    @Test
    fun `should return null for timeout or unknown error`() {
        val output = "Some unknown error message"

        val error = GradleErrorDetector.detectError(output)
        assertNull(error)
    }

    @Test
    fun `should extract plugin name from error message`() {
        val output = """
            An exception occurred applying plugin request [id: 'org.web3j', version: '4.5.0']
            > Failed to apply plugin class 'org.web3j.solidity.gradle.plugin.SolidityPlugin'.
        """.trimIndent()

        val error = GradleErrorDetector.detectError(output)
        assertNotNull(error)
        assert(error.message.isNotEmpty())
    }

    @Test
    fun `should suggest correct flags for plugin incompatible error`() {
        val output = """
            FAILURE: Build failed with an exception.
            * What went wrong:
            Could not generate a decorated class for type SolidityPlugin.
               > org/gradle/api/internal/file/SourceDirectorySetFactory
        """.trimIndent()

        val error = GradleErrorDetector.detectError(output)
        assertNotNull(error)
        assertEquals(
            listOf("--no-daemon", "--build-cache=off"),
            error.suggestedFlags
        )
    }

    @Test
    fun `should suggest correct flags for JVM incompatible error`() {
        val output = """
            FAILURE: Build failed with an exception.
            * What went wrong:
            Could not initialize class org.codehaus.groovy.runtime.InvokerHelper
        """.trimIndent()

        val error = GradleErrorDetector.detectError(output)
        assertNotNull(error)
        assertEquals(listOf("--no-daemon"), error.suggestedFlags)
    }

    @Test
    fun `should detect error with BUILD FAILED keyword`() {
        val output = """
            BUILD FAILED in 2s
            
            * What went wrong:
            Plugin incompatibility detected
        """.trimIndent()

        val pluginError = """
            BUILD FAILED
            Could not create plugin of type 'SomePlugin'
        """.trimIndent()

        val error = GradleErrorDetector.detectError(pluginError)
        assertNotNull(error)
        assertEquals(GradleErrorType.PLUGIN_INCOMPATIBLE, error.type)
    }

    @Test
    fun `should detect nested build mismatch error`() {
        val output = """
            FAILURE: Build failed with an exception.
            * What went wrong:
            Project directory 'D:\\repo\\repos\\java-binance-api' is not part of the build defined by settings file 'D:\\repo\\settings.gradle.kts'.
        """.trimIndent()

        val error = GradleErrorDetector.detectError(output)
        assertNotNull(error)
        assertEquals(GradleErrorType.NESTED_BUILD_MISMATCH, error.type)
        assertEquals(emptyList(), error.suggestedFlags)
    }
}
