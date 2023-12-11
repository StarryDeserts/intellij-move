package org.sui.openapiext.common

import com.intellij.openapi.application.ApplicationManager

val isUnitTestMode: Boolean get() = ApplicationManager.getApplication().isUnitTestMode
fun checkUnitTestMode() = check(isUnitTestMode) { "UnitTestMode needed" }

val isHeadlessEnvironment: Boolean get() = ApplicationManager.getApplication().isHeadlessEnvironment

val isDispatchThread: Boolean get() = ApplicationManager.getApplication().isDispatchThread

val isInternal: Boolean get() = ApplicationManager.getApplication().isInternal
