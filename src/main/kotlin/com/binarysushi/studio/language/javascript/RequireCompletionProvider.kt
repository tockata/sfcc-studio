package com.binarysushi.studio.language.javascript

import com.binarysushi.studio.*
import com.binarysushi.studio.cartridges.*
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.*
import com.intellij.json.*
import com.intellij.lang.javascript.*
import com.intellij.openapi.project.*
import com.intellij.psi.search.*
import com.intellij.util.*
import org.jetbrains.annotations.*

class RequireCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val caretPositionOffset = parameters.offset - parameters.position.textOffset
        val query = parameters.position.text.substring(1, caretPositionOffset) // Drop the first quote
        val project = parameters.position.project

        when {
            query.startsWith("~") -> handleCompletion(
                result,
                findFilesStudioFiles(project, parameters.position.containingFile.originalFile.virtualFile.path),
                false
            )
            query.startsWith("*") -> handleCompletion(result, findFilesStudioFiles(project), false)
            query.startsWith("dw/") -> handleApiCompletion(result)
            else -> {
                handleCompletion(result, findFilesStudioFiles(project))
            }
        }
    }

    private fun findFilesStudioFiles(project: @NotNull Project, pathScope: String? = null): List<StudioFile> {
        val files = FileTypeIndex.getFiles(
            JavaScriptFileType.INSTANCE,
            GlobalSearchScope.projectScope(project)
        ) + FileTypeIndex.getFiles(JsonFileType.INSTANCE, GlobalSearchScope.projectScope(project))

        val results = files.mapNotNull { StudioFileManager(project).getStudioFile(it) }

        return if (pathScope != null) {
            results.filter {
                pathScope.contains("/${it.getCartridgeName()}")
            }
        } else {
            results
        }
    }

    /**
     * Tilde should only suggest files in the same cartridge path as the original file
     *
     */
    private fun handleCompletion(
        result: CompletionResultSet,
        studioFiles: List<StudioFile>,
        insertCartridgeName: Boolean = true
    ) {
        studioFiles.forEach {
            var lookupElementBuilder = LookupElementBuilder
                .create(it.getModulePath())
                .withPresentableText(it.getRelativeModulePath())
                .withTypeText(it.getCartridgeName(), StudioIcons.STUDIO_ICON, true)
                .withTypeIconRightAligned(true)

            val insertText = if (insertCartridgeName) {
                it.getModulePath(false)
            } else {
                it.getRelativeModulePath(false)
            }

            lookupElementBuilder = lookupElementBuilder.withInsertHandler(
                RequireInsertHandler(insertText)
            )

            result.addElement(lookupElementBuilder)
        }

        if (studioFiles.isNotEmpty()) result.stopHere()
    }

    /**
     * Star should complete files in any cartridge on the active cartridge path
     *
     */

    private fun handleApiCompletion(
        result: CompletionResultSet
    ) {
        result.addElement(LookupElementBuilder.create("dw/api/result"))
    }

}

private class RequireInsertHandler(private val insertText: String) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val editor = context.editor
        val document = context.document
        val caretOffset = editor.caretModel.offset

        document.insertString(caretOffset, insertText)
        editor.caretModel.moveToOffset(
            caretOffset + insertText.length
        )
        document.deleteString(caretOffset - item.lookupString.length, caretOffset)
    }
}