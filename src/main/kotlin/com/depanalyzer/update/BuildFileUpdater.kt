package com.depanalyzer.update

import java.io.File

interface BuildFileUpdater {
    fun applyUpdate(buildFile: File, suggestion: UpdateSuggestion): Boolean
}
