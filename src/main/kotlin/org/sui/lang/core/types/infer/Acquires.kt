package org.sui.lang.core.types.infer

import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.jetbrains.rd.util.concurrentMapOf
import org.sui.cli.MoveProject
import org.sui.lang.core.psi.MvCallExpr
import org.sui.lang.core.psi.MvFunction
import org.sui.lang.core.psi.acquiresPathTypes
import org.sui.lang.core.psi.ext.isInline
import org.sui.lang.core.types.ty.Ty
import org.sui.lang.core.types.ty.TyFunction
import org.sui.lang.moveProject

val ACQUIRES_TYPE_CONTEXT: Key<CachedValue<AcquiresTypeContext>> = Key.create("ACQUIRES_TYPE_CONTEXT")

val MoveProject.acquiresContext: AcquiresTypeContext
    get() {
        val manager = CachedValuesManager.getManager(project)
        return manager.getCachedValue(this, ACQUIRES_TYPE_CONTEXT, {
            val context = AcquiresTypeContext()
            CachedValueProvider.Result.create(
                context,
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }, false)
    }

class AcquiresTypeContext {
    private val functionTypes: MutableMap<MvFunction, List<Ty>> = concurrentMapOf()
    private val callExprTypes: MutableMap<MvCallExpr, List<Ty>> = concurrentMapOf()

    fun getFunctionTypes(function: MvFunction): List<Ty> {
        val inference = function.inference(false)
        return functionTypes.getOrPut(function) {
            if (function.isInline) {
                // collect inner callExpr types
                val allTypes = mutableListOf<Ty>()
                for (innerCallExpr in inference.callExprTypes.keys) {
                    val types = getCallTypes(innerCallExpr, inference)
                    allTypes.addAll(types)
                }
                allTypes
            } else {
                // parse from MvAcquiresType
                function.acquiresPathTypes.map { it.loweredType(false) }
            }
        }
    }

    fun getCallTypes(callExpr: MvCallExpr, inference: InferenceResult): List<Ty> {
        return callExprTypes.getOrPut(callExpr) {
            val callTy = inference.getCallExprType(callExpr) as? TyFunction ?: return emptyList()
            val callItem = callTy.item as? MvFunction ?: return emptyList()
            if (callItem.isInline) {
                val functionTypes = this.getFunctionTypes(callItem)
                val resolvedFunctionTypes = functionTypes
                    .map { it.substituteOrUnknown(callTy.substitution) }
                resolvedFunctionTypes
            } else {
                callTy.acquiresTypes
            }
        }
    }
}

fun MvFunction.getInnerAcquiresTypes(): List<Ty> {
    val typeContext = this.moveProject?.acquiresContext ?: return emptyList()
    return typeContext.getFunctionTypes(this)
}

fun MvCallExpr.getAcquiresTypes(inference: InferenceResult): List<Ty> {
    val typeContext = this.moveProject?.acquiresContext ?: return emptyList()
    return typeContext.getCallTypes(this, inference)
}
