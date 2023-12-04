package org.sui.cli.toolwindow

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectViewTestUtil
import org.sui.cli.moveProjects
import org.sui.utils.tests.MvProjectTestBase
import org.sui.utils.tests.TreeBuilder

class MoveProjectsStructureTest : MvProjectTestBase() {
    fun `test move projects`() = doTest("""
Root
 Project(DepPackage)
  Dependencies
  Entrypoints
   Entrypoint(0x1::DepModule::my_init)
  Modules
   Module(0x1::DepModule)
 Project(MyPackage)
  Dependencies
   Package(DepPackage)
    Entrypoints
     Entrypoint(0x1::DepModule::my_init)
    Modules
     Module(0x1::DepModule)
  Entrypoints
   Entrypoint(0x1::M::init)
  Modules
   Module(0x1::M)
  Views
   View(0x1::M::get_coin_value)
    """) {
        moveToml("""
        [package]
        name = "MyPackage"
        
        [dependencies]
        DepPackage = { local = "./dependency" }
        """)
        sources { main("""
            module 0x1::M {
                #[view]
                public fun get_coin_value(): u64 {}
                entry fun init() { /*caret*/ }
            }    
        """) }
        dir("dependency") {
            namedMoveToml("DepPackage")
            sources {
                move("DepModule.move", """
                module 0x1::DepModule {
                    entry fun my_init() {}
                }    
                """)
            }
        }
    }

    private fun doTest(expectedTreeStructure: String, builder: TreeBuilder) {
        testProject(builder)
        val structure = MoveProjectsTreeStructure(
            MoveProjectsTree(),
            testRootDisposable,
            project.moveProjects.allProjects.toList()
        )
        assertStructureEqual(structure, expectedTreeStructure.trimIndent() + "\n")
    }

    /**
     * Same as [ProjectViewTestUtil.assertStructureEqual], but uses [CargoSimpleNode.toTestString] instead of [CargoSimpleNode.toString].
     */
    private fun assertStructureEqual(structure: AbstractTreeStructure, expected: String) {
        ProjectViewTestUtil.checkGetParentConsistency(structure, structure.rootElement)
        val actual = PlatformTestUtil.print(structure, structure.rootElement) {
            if (it is MoveProjectsTreeStructure.MoveSimpleNode) it.toTestString() else it.toString()
        }
        assertEquals(expected, actual)
    }
}
