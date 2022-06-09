package org.move.cli

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.addIfNotNull
import org.move.cli.project.BuildInfoYaml
import org.move.cli.project.MovePackage
import org.move.cli.project.MoveToml
import org.move.lang.MoveFile
import org.move.lang.core.types.Address
import org.move.lang.toNioPathOrNull
import org.move.openapiext.contentRoots
import org.move.openapiext.stringValue
import org.move.utils.deepWalkMoveFiles
import java.nio.file.Path

enum class DevMode {
    MAIN, DEV;
}

data class MvModuleFile(
    val file: MoveFile,
    val addressSubst: Map<String, String>,
)

data class DeclaredAddresses(
    val values: AddressMap,
    val placeholders: PlaceholderMap,
) {
    fun placeholdersAsValues(): AddressMap {
        val values = mutableAddressMap()
        for ((name, pVal) in placeholders.entries) {
            val value = pVal.keyValue.value?.stringValue() ?: continue
            values[name] = AddressVal(value, pVal.keyValue, pVal.keyValue, pVal.packageName)
        }
        return values
    }

    fun get(name: String): AddressVal? {
        if (name in this.values) return this.values[name]
        return this.placeholders[name]
            ?.let {
                AddressVal(Consts.ADDR_PLACEHOLDER, null, it.keyValue, it.packageName)
            }
    }
}

fun testEmptyMoveProject(project: Project): MoveProject {
    val moveToml = MoveToml(project)
    val contentRoot = project.contentRoots.first()
    val addresses = DeclaredAddresses(mutableAddressMap(), placeholderMap())
    val movePackage = MovePackage(project, contentRoot, moveToml)
    return MoveProject(
        project,
        addresses,
        addresses.copy(),
        movePackage
    )
}

fun testEmptyMovePackage(project: Project): MovePackage {
    val moveToml = MoveToml(project)
    val contentRoot = project.contentRoots.first()
    return MovePackage(project, contentRoot, moveToml)
}

fun applyAddressSubstitutions(
    addresses: DeclaredAddresses,
    subst: RawAddressMap,
    packageName: String
): Pair<AddressMap, PlaceholderMap> {
    val newDepAddresses = addresses.values.copyMap()
    val newDepPlaceholders = placeholderMap()

    for ((pName, pVal) in addresses.placeholders.entries) {
        val placeholderSubst = subst[pName]
        if (placeholderSubst == null) {
            newDepPlaceholders[pName] = pVal
            continue
        }
        val (value, keyValue) = placeholderSubst
        newDepAddresses[pName] = AddressVal(value, keyValue, pVal.keyValue, packageName)
    }
    return Pair(newDepAddresses, newDepPlaceholders)
}

data class MoveProject(
    private val project: Project,
    private val declaredAddrs: DeclaredAddresses,
    private val declaredDevAddresses: DeclaredAddresses,
    val currentPackage: MovePackage,
) : UserDataHolderBase() {

    val contentRoot get() = this.currentPackage.contentRoot
    val contentRootPath: Path? get() = this.currentPackage.contentRoot.toNioPathOrNull()

    fun moduleFolders(devMode: DevMode): List<VirtualFile> {
        val folders = mutableListOf<VirtualFile>()
        val projectInfo = this.projectInfo ?: return emptyList()
        folders.addIfNotNull(projectInfo.sourcesFolder)

        val q = ArrayDeque<ProjectInfo>()
        val visited = mutableSetOf<String>()
        q.add(projectInfo)

        while (q.isNotEmpty()) {
            val info = q.removeFirst()
            val deps = info.deps(devMode)
            val depInfos = deps.values.mapNotNull { it.projectInfo(project) }
            q.addAll(depInfos)

            val localFolders = deps
                .filter { it.value is Dependency.Local }
                .mapNotNull {
                    visited.add(it.key)
                    it.value.projectInfo(project)?.sourcesFolder
                }
            folders.addAll(localFolders)
        }

        val gitFolders = this.buildRoot()
            ?.findChild("sources")
            ?.findChild("dependencies")
            ?.children
            .orEmpty()
            .filter { it.nameWithoutExtension !in visited }
        folders.addAll(gitFolders)
        return folders
    }

    private fun buildRoot(): VirtualFile? {
        val packageName = this.currentPackage.packageName ?: return null
        return this.currentPackage.contentRoot
            .findChild("build")
            ?.findChild(packageName)
    }

    fun declaredAddresses(): DeclaredAddresses {
        return CachedValuesManager.getManager(this.project).getCachedValue(this) {
            val addresses = mutableAddressMap()
            val placeholders = placeholderMap()

            for ((dep, subst) in this.currentPackage.moveToml.dependencies.values) {
                val depDeclaredAddrs = dep.declaredAddresses(project) ?: continue

                val (newDepAddresses, newDepPlaceholders) = applyAddressSubstitutions(
                    depDeclaredAddrs,
                    subst,
                    currentPackage.packageName ?: ""
                )

                // renames
                for ((renamedName, originalVal) in subst.entries) {
                    val (originalName, keyVal) = originalVal
                    val origVal = depDeclaredAddrs.get(originalName)
                    if (origVal != null) {
                        newDepAddresses[renamedName] =
                            AddressVal(
                                origVal.value,
                                keyVal,
                                null,
                                currentPackage.packageName ?: ""
                            )
                    }
                }
                addresses.putAll(newDepAddresses)
                placeholders.putAll(newDepPlaceholders)
            }
            addresses.putAll(packageAddresses())
            // add dev-addresses
            addresses.putAll(declaredDevAddresses.values)
            // add placeholders that weren't filled
            placeholders.putAll(declaredAddrs.placeholders)

            // add addresses from git dependencies
            buildRoot()
                ?.findChild("BuildInfo.yaml")
                ?.let { BuildInfoYaml.fromPath(it.toNioPath()) }
                ?.addresses()
                .orEmpty()
                .forEach {
                    addresses.putIfAbsent(it.key, it.value)
                }

            val res = DeclaredAddresses(addresses, placeholders)

            CachedValueProvider.Result.create(res, PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    fun addresses(): AddressMap {
        return declaredAddresses().values
    }

    fun packageAddresses(): AddressMap {
        // add addresses defined in this package
        val map = mutableAddressMap()
        map.putAll(this.declaredAddrs.values)
        // add placeholders defined in this package as address values
        map.putAll(this.declaredAddrs.placeholdersAsValues())
        return map
    }

    fun getNamedAddress(name: String): Address.Named? {
        val addressVal = addresses()[name] ?: return null
        return Address.Named(name, addressVal.value)
    }

    fun searchScope(devMode: DevMode = DevMode.MAIN): GlobalSearchScope {
        var searchScope = GlobalSearchScope.EMPTY_SCOPE
        for (folder in moduleFolders(devMode)) {
            val dirScope = GlobalSearchScopes.directoryScope(project, folder, true)
            searchScope = searchScope.uniteWith(dirScope)
        }
        return searchScope
    }

    fun processModuleFiles(devMode: DevMode, processFile: (MvModuleFile) -> Boolean) {
        val folders = moduleFolders(devMode)
        var stopped = false
        for (folder in folders) {
            if (stopped) break
            deepWalkMoveFiles(project, folder) {
                val moduleFile = MvModuleFile(it, emptyMap())
                val continueForward = processFile(moduleFile)
                stopped = !continueForward
                continueForward
            }
        }
    }

    fun walkMoveFiles(process: (MoveFile) -> Boolean) = deepWalkMoveFiles(
        project,
        currentPackage.contentRoot,
        process
    )
}
