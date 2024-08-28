/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.sui.utils.tests

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.sui.ide.intentions.MvElementBaseIntentionAction
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

abstract class MvIntentionTestCase(private val intentionClass: KClass<out IntentionAction>) :
    MvTestBase() {

    protected val intention: IntentionAction
        get() = findIntention() ?: error("Failed to find `${intentionClass.simpleName}` intention")

    @Suppress("FunctionName")
    fun `test intention has documentation`() {
        if (!intentionClass.isSubclassOf(MvElementBaseIntentionAction::class)) return

        val directory = "intentionDescriptions/${intentionClass.simpleName}"
        val description = checkFileExists(Paths.get(directory, "description.html"))
        checkHtmlStyle(description)
    }

    private fun checkFileExists(path: Path): String = getResourceAsString(path.toString())
        ?: error("No ${path.fileName} found for $intentionClass ($path)")

    protected fun doAvailableTest(
        @Language("Move") before: String,
        @Language("Move") after: String,
        fileName: String = "main.move"
    ) {
        InlineFile(myFixture, before.trimIndent(), fileName).withCaret()
        launchAction()
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

//    @Suppress("unused")
//    protected fun doAvailableTestWithFileTree(
//        @Language("Move") fileStructureBefore: String,
//        @Language("Move") openedFileAfter: String
//    ) {
//        val fileTree = fileTreeFromText(fileStructureBefore)
//        val testProject = fileTree.toTestProject(myFixture.project, myFixture.findFileInTempDir("."))
//        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
//
//        val moveProjects = MoveProjectsSyncTask.loadProjects(myFixture.project)
//        setupProjectRoots(myFixture.project, moveProjects)
//        FileBasedIndex.getInstance().requestRebuild(MvNamedElementIndex.KEY)
//        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
//        launchAction()
//        myFixture.checkResult(replaceCaretMarker(openedFileAfter.trimIndent()))
//    }

    protected fun launchAction() {
        UIUtil.dispatchAllInvocationEvents()
        myFixture.launchAction(intention)
    }

    protected fun doUnavailableTest(@Language("Move") before: String, fileName: String = "main.move") {
        InlineFile(myFixture, before, fileName).withCaret()
        val intention = findIntention()
        check(intention == null) {
            "\"${intentionClass.simpleName}\" should not be available"
        }
    }

    private fun findIntention(): IntentionAction? {
        return myFixture.availableIntentions.firstOrNull {
            val originalIntention = IntentionActionDelegate.unwrap(it)
            intentionClass == originalIntention::class
        }
    }
}
