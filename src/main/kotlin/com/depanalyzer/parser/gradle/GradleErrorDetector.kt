package com.depanalyzer.parser.gradle

object GradleErrorDetector {

    fun detectError(output: String): GradleErrorInfo? {
        return when {
            isPluginIncompatible(output) -> {
                val pluginName = extractPluginName(output) ?: "unknown plugin"
                GradleErrorInfo(
                    type = GradleErrorType.PLUGIN_INCOMPATIBLE,
                    message = "Plugin $pluginName incompatible with current Gradle version",
                    suggestedFlags = listOf("--no-daemon", "--build-cache=off")
                )
            }

            isJvmIncompatible(output) -> {
                GradleErrorInfo(
                    type = GradleErrorType.JVM_INCOMPATIBLE,
                    message = "Gradle version or JVM incompatibility detected",
                    suggestedFlags = listOf("--no-daemon")
                )
            }

            isClasspathError(output) -> {
                val className = extractClasspathErrorClass(output) ?: "classpath resource"
                GradleErrorInfo(
                    type = GradleErrorType.CLASSPATH_ERROR,
                    message = "Classpath error: $className not found",
                    suggestedFlags = listOf("--build-cache=off")
                )
            }

            isNestedBuildMismatch(output) -> {
                GradleErrorInfo(
                    type = GradleErrorType.NESTED_BUILD_MISMATCH,
                    message = "Target project is not part of parent Gradle settings build",
                    suggestedFlags = emptyList()
                )
            }

            else -> null
        }
    }

    private fun isPluginIncompatible(output: String): Boolean {
        return output.contains("Could not create plugin of type", ignoreCase = true) ||
                output.contains("SourceDirectorySetFactory", ignoreCase = true) ||
                output.contains("Could not generate a decorated class", ignoreCase = true) ||
                (output.contains("An exception occurred applying plugin request", ignoreCase = true) &&
                        output.contains("Failed to apply plugin", ignoreCase = true))
    }

    private fun isJvmIncompatible(output: String): Boolean {
        return (output.contains("NoClassDefFoundError", ignoreCase = true) &&
                output.contains("Groovy", ignoreCase = true)) ||
                output.contains("Could not initialize class org.codehaus.groovy", ignoreCase = true) ||
                output.contains("InvokerHelper", ignoreCase = true) ||
                (output.contains("NoClassDefFoundError", ignoreCase = true) &&
                        output.contains("reflection", ignoreCase = true))
    }

    private fun isClasspathError(output: String): Boolean {
        return output.contains("ClassNotFoundException", ignoreCase = true) ||
                output.contains("Could not find class", ignoreCase = true) ||
                output.contains("ClassDefNotFound", ignoreCase = true)
    }

    private fun isNestedBuildMismatch(output: String): Boolean {
        return output.contains("is not part of the build defined by settings file", ignoreCase = true)
    }

    private fun extractPluginName(output: String): String? {
        val regex = Regex("""(?:plugin of type|plugin request)\s+['\[]([^'"\]]+)['\]]""", RegexOption.IGNORE_CASE)
        return regex.find(output)?.groupValues?.get(1)?.substringAfterLast('.')
    }

    private fun extractClasspathErrorClass(output: String): String? {
        val regex = Regex("""(?:ClassNotFoundException|ClassDefNotFound|Could not find class)[\s:]*([A-Za-z0-9._$]+)""")
        return regex.find(output)?.groupValues?.get(1)
    }
}

data class GradleErrorInfo(
    val type: GradleErrorType,
    val message: String,
    val suggestedFlags: List<String> = emptyList()
)

enum class GradleErrorType {
    PLUGIN_INCOMPATIBLE,

    JVM_INCOMPATIBLE,

    CLASSPATH_ERROR,

    NESTED_BUILD_MISMATCH,

    UNKNOWN
}
