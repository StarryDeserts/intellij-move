package org.sui.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValuesManager.getProjectPsiDependentCache
import com.intellij.psi.util.PsiTreeUtil
import org.sui.cli.Consts
import org.sui.cli.MoveProject
import org.sui.cli.moveProjectsService
import org.sui.lang.core.psi.*
import org.sui.lang.core.psi.ext.ancestorOrSelf
import org.sui.lang.core.psi.ext.childrenOfType
import org.sui.lang.core.psi.ext.modules
import org.sui.openapiext.resolveAbsPath
import org.sui.openapiext.toPsiFile
import org.sui.stdext.chain
import org.toml.lang.psi.TomlFile
import java.nio.file.Path

fun findMoveTomlPath(currentFilePath: Path): Path? {
    var dir = currentFilePath.parent
    while (dir != null) {
        val moveTomlPath = dir.resolveAbsPath(Consts.MANIFEST_FILE)
        if (moveTomlPath != null) {
            return moveTomlPath
        }
        dir = dir.parent
    }
    return null
}

// requires ReadAccess
val PsiElement.moveProject: MoveProject? get() {
    return project.moveProjectsService.findMoveProjectForPsiElement(this)
}

fun VirtualFile.hasChild(name: String) = this.findChild(name) != null

fun VirtualFile.toNioPathOrNull(): Path? {
    try {
        return this.toNioPath()
    } catch (e: UnsupportedOperationException) {
        return null
    }
}

fun PsiFile.toNioPathOrNull(): Path? {
    return this.originalFile.virtualFile?.toNioPathOrNull()
}

abstract class MoveFileBase(fileViewProvider: FileViewProvider) : PsiFileBase(fileViewProvider, MoveLanguage) {
    override fun getFileType(): FileType = MoveFileType
}

class MoveFile(fileViewProvider: FileViewProvider) : MoveFileBase(fileViewProvider) {

    fun addressBlocks(): List<MvAddressBlock> {
        val defs = PsiTreeUtil.getChildrenOfTypeAsList(this, MvAddressDef::class.java)
        return defs.mapNotNull { it.addressBlock }.toList()
    }

    fun scriptBlocks(): List<MvScriptBlock> {
        val defs = PsiTreeUtil.getChildrenOfTypeAsList(this, MvScript::class.java)
        return defs.mapNotNull { it.scriptBlock }.toList()
    }

    fun modules(): Sequence<MvModule> {
        return getProjectPsiDependentCache(this) {
            it.childrenOfType<MvModule>()
                .chain(it.childrenOfType<MvAddressDef>().flatMap { a -> a.modules() })
        }
    }

    fun moduleSpecs(): List<MvModuleSpec> = this.childrenOfType()

    fun preModules(): Sequence<MvModule> {
        return getProjectPsiDependentCache(this) { it ->
            it.childrenOfType<MvModule>()
                .chain(it.childrenOfType<MvAddressDef>().flatMap { a -> a.modules() })
                .filter {
                    it.addressRef?.namedAddress?.text == "sui" && setOf(
                        "transfer",
                        "object",
                        "tx_context"
                    ).contains(it.name)
                            || it.addressRef?.namedAddress?.text == "std" && setOf("vector", "option").contains(it.name)
                }
        }
    }
}

val VirtualFile.isMoveFile: Boolean get() = fileType == MoveFileType

val VirtualFile.isMoveTomlManifestFile: Boolean get() = name == "Move.toml"

fun VirtualFile.toMoveFile(project: Project): MoveFile? = this.toPsiFile(project) as? MoveFile

fun VirtualFile.toTomlFile(project: Project): TomlFile? = this.toPsiFile(project) as? TomlFile

fun MoveFile.isTempFile(): Boolean =
    this.virtualFile == null
            || this.virtualFile.fileSystem is TempFileSystem

inline fun <reified T : PsiElement> PsiFile.elementAtOffset(offset: Int): T? =
    this.findElementAt(offset)?.ancestorOrSelf<T>()

fun MoveFile.preLoadTypes(): List<MvStruct> {
    val projectPsiDependentCache = getProjectPsiDependentCache(this) { it ->
        it.childrenOfType<MvModule>()
            .chain(it.childrenOfType<MvAddressDef>().flatMap { a -> a.modules() })
            .filter {
                it.addressRef?.namedAddress?.text == "sui" && setOf("object", "tx_context").contains(it.name)
                        || it.addressRef?.namedAddress?.text == "std" && setOf("option").contains(it.name)
            }
    }

    var result = mutableListOf<MvStruct>()
    for (module in projectPsiDependentCache) {
        module.moduleBlock?.structList?.filter { listOf("ID", "UID", "TxContext").contains(it.name) }
            ?.let { result.addAll(it) }
    }
    return result.toList()
}