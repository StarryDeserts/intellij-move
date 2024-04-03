package org.sui.cli.settings

import com.intellij.configurationStore.serializeObjectInto
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.sui.cli.settings.aptos.AptosExec
import org.sui.openapiext.debugInProduction
import org.sui.stdext.exists
import org.sui.stdext.isExecutableFile
import org.sui.stdext.toPathOrNull
import java.nio.file.Path
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

data class MoveSettingsChangedEvent(
    val oldState: MoveProjectSettingsService.State,
    val newState: MoveProjectSettingsService.State,
) {
    /** Use it like `event.isChanged(State::foo)` to check whether `foo` property is changed or not */
    fun isChanged(prop: KProperty1<MoveProjectSettingsService.State, *>): Boolean =
        prop.get(oldState) != prop.get(newState)
}

interface MoveSettingsListener {
    fun moveSettingsChanged(e: MoveSettingsChangedEvent)
}

enum class Blockchain {
    APTOS, SUI;

    override fun toString(): String = if (this == APTOS) "Aptos" else "Sui"
}

private const val settingsServiceName: String = "MoveProjectSettingsService_Sui"

@Service(Service.Level.PROJECT)
@State(
    name = settingsServiceName,
    storages = [
        Storage(StoragePathMacros.WORKSPACE_FILE),
        Storage("sui-misc.xml", deprecated = true)
    ]
)
class MoveProjectSettingsService(private val project: Project) : PersistentStateComponent<Element> {

    // default values for settings
    data class State(
        //nullnotMac->Bundled,nullandMac->Local(""),notnull->Local(value)
        var blockchain: Blockchain = Blockchain.SUI,
        var aptosPath: String? = if (AptosExec.isBundledSupportedForThePlatform()) null else "",
        var suiPath: String = "",
        var foldSpecs: Boolean = false,
        var disableTelemetry: Boolean = true,
        var debugMode: Boolean = false,
        var skipFetchLatestGitDeps: Boolean = false,
        var isValidExec: Boolean = false,
        var dumpStateOnTestFailure: Boolean = false,
    ) {
        fun aptosExec(): AptosExec {
            val path = aptosPath
            return when (path) {
                null -> AptosExec.Bundled
                else -> AptosExec.LocalPath(path)
            }
        }
    }

    @Volatile
    private var _state = State()

    var state: State
        get() = _state.copy()
        set(newState) {
            if (_state != newState) {
                val oldState = _state
                _state = newState.copy()
                notifySettingsChanged(oldState, newState)
            }
        }

    private fun notifySettingsChanged(
        oldState: State,
        newState: State,
    ) {
        val event = MoveSettingsChangedEvent(oldState, newState)

        for (prop in State::class.memberProperties) {
            if (event.isChanged(prop)) {
                val oldValue = prop.get(oldState)
                val newValue = prop.get(newState)
                LOG.debugInProduction("SETTINGS updated [${prop.name}: $oldValue -> $newValue]")
            }
        }

        project.messageBus.syncPublisher(MOVE_SETTINGS_TOPIC).moveSettingsChanged(event)

        if (event.isChanged(State::foldSpecs)) {
            PsiManager.getInstance(project).dropPsiCaches()
        }
    }

    override fun getState(): Element {
        val element = Element(settingsServiceName)
        serializeObjectInto(_state, element)
        return element
    }

    override fun loadState(element: Element) {
        val rawState = element.clone()
        XmlSerializer.deserializeInto(_state, rawState)
    }

    /**
     * Allows to modify settings.
     * After setting change,
     */
    fun modify(action: (State) -> Unit) {
        val oldState = state.copy()
        val newState = state.copy().also(action)
        state = newState

        notifySettingsChanged(oldState, newState)
//        val event = MoveSettingsChangedEvent(oldState, newState)
//        project.messageBus.syncPublisher(MOVE_SETTINGS_TOPIC).moveSettingsChanged(event)
    }

    /**
     * Allows to modify settings.
     * After setting change,
     */
    @TestOnly
    fun modifyTemporary(parentDisposable: Disposable, action: (State) -> Unit) {
        val oldState = state
        state = oldState.also(action)
        Disposer.register(parentDisposable) {
            _state = oldState
        }
    }

    /**
     * Returns current state of the service.
     * Note, result is a copy of service state, so you need to set modified state back to apply changes
     */
    companion object {
        val MOVE_SETTINGS_TOPIC = Topic("move settings changes", MoveSettingsListener::class.java)

        private val LOG = logger<MoveProjectSettingsService>()
    }
}

val Project.moveSettings: MoveProjectSettingsService get() = service()

val Project.collapseSpecs: Boolean get() = this.moveSettings.state.foldSpecs

val Project.aptosExec: AptosExec get() = AptosExec.fromSettingsFormat(this.moveSettings.state.aptosPath)
val Project.suiExec: SuiExec get() = SuiExec.fromSettingsFormat(this.moveSettings.state.suiPath)
val Project.blockchain: Blockchain get() = this.moveSettings.state.blockchain

val Project.aptosExec: AptosExec get() = this.moveSettings.state.aptosExec()

val Project.aptosPath: Path? get() = this.aptosExec.toPathOrNull()

val Project.suiPath: Path? get() = this.moveSettings.state.suiPath.toPathOrNull()

fun Path?.isValidExecutable(): Boolean {
    return this != null
            && this.toString().isNotBlank()
            && this.exists()
            && this.isExecutableFile()
}

val Project.isValidSuiExec: Boolean get() = this.moveSettings.state.isValidExec
val Project.isDebugModeEnabled: Boolean get() = this.moveSettings.state.debugMode

fun <T> Project.debugErrorOrFallback(message: String, fallback: T): T {
    if (this.isDebugModeEnabled) {
        error(message)
    }
    return fallback
}

fun <T> Project.debugErrorOrFallback(message: String, cause: Throwable?, fallback: () -> T): T {
    if (this.isDebugModeEnabled) {
        throw IllegalStateException(message, cause)
    }
    return fallback()
}

val Project.skipFetchLatestGitDeps: Boolean
    get() =
        this.moveSettings.state.skipFetchLatestGitDeps

val Project.dumpStateOnTestFailure: Boolean
    get() =
        this.moveSettings.state.dumpStateOnTestFailure
