package io.corbado.connect.example

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

fun hasTestTagPrefix(prefix: String): SemanticsMatcher {
    return SemanticsMatcher("${SemanticsProperties.TestTag.name} starts with '$prefix'") { node: SemanticsNode ->
        val testTag = node.config.getOrNull(SemanticsProperties.TestTag)
        testTag != null && testTag.startsWith(prefix)
    }
}
