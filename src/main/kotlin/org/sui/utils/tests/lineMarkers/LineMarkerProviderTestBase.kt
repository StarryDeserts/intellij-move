package org.sui.utils.tests.lineMarkers

import org.intellij.lang.annotations.Language
import org.sui.utils.tests.MvTestBase

abstract class LineMarkerProviderTestBase : MvTestBase() {

    protected lateinit var lineMarkerTestHelper: LineMarkerTestHelper

    override fun setUp() {
        super.setUp()
        lineMarkerTestHelper = LineMarkerTestHelper(myFixture)
    }

    protected fun doTestByText(@Language("Sui Move") source: String) {
        lineMarkerTestHelper.doTestByText("main.move", source)
    }

//    protected fun doTestByFileTree(filePath: String, builder: FileTreeBuilder.() -> Unit) {
//        val fileTree = fileTree(builder)
//        val testProject = fileTree(builder).toTestProject(project, )
//        lineMarkerTestHelper.doTestFromFile(testProject.file(filePath))
//    }
}
