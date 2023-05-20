package com.ramcosta.composedestinations.codegen.templates

import com.ramcosta.composedestinations.codegen.codeGenBasePackageName
import com.ramcosta.composedestinations.codegen.commons.*
import com.ramcosta.composedestinations.codegen.templates.core.FileTemplate
import com.ramcosta.composedestinations.codegen.templates.core.setOfImportable

const val START_NO_NAV_GRAPHS_NAV_DESTINATION_ANCHOR = "[START_NO_NAV_GRAPHS_NAV_DESTINATION_ANCHOR]"
const val END_NO_NAV_GRAPHS_NAV_DESTINATION_ANCHOR = "[END_NO_NAV_GRAPHS_NAV_DESTINATION_ANCHOR]"
const val START_NAV_DESTINATION_DEPRECATED_ROOT_DEFAULT_ANCHOR = "[START_NAV_DESTINATION_DEPRECATED_ROOT_DEFAULT_ANCHOR]"
const val END_NAV_DESTINATION_DEPRECATED_ROOT_DEFAULT_ANCHOR = "[END_NAV_DESTINATION_DEPRECATED_ROOT_DEFAULT_ANCHOR]"
const val START_NAV_DESTINATION_ROOT_DEFAULT_ANCHOR = "[START_NAV_DESTINATION_ROOT_DEFAULT_ANCHOR]"
const val END_NAV_DESTINATION_ROOT_DEFAULT_ANCHOR = "[END_NAV_DESTINATION_ROOT_DEFAULT_ANCHOR]"

val singleModuleExtensionsTemplate = FileTemplate(
    packageStatement = "package $codeGenBasePackageName",
    imports = setOfImportable(
        "androidx.compose.runtime.Composable",
        "androidx.compose.runtime.State",
        "androidx.compose.runtime.collectAsState",
        "androidx.navigation.NavBackStackEntry",
        "androidx.navigation.NavController",
        "$codeGenBasePackageName.destinations.*",
        "$codeGenBasePackageName.navgraphs.*",
        "$CORE_PACKAGE_NAME.spec.*",
        "$CORE_PACKAGE_NAME.utils.startDestination",
        "$CORE_PACKAGE_NAME.utils.destination",
        "$CORE_PACKAGE_NAME.utils.currentDestinationFlow",
        "kotlinx.coroutines.flow.Flow",
        "kotlinx.coroutines.flow.map",
    ),
    sourceCode = """

/**
 * If this [Route] is a [$typeAliasDestination], returns it
 *
 * If this [Route] is a [$GENERATED_NAV_GRAPH], returns its
 * start [$typeAliasDestination].
 */
val Route.startAppDestination: $typeAliasDestination
    get() = startDestination as $typeAliasDestination

/**
 * Finds the [$typeAliasDestination] correspondent to this [NavBackStackEntry].
 */
fun NavBackStackEntry.appDestination(): $typeAliasDestination {
    return destination() as $typeAliasDestination
}

/**
 * Emits the currently active [$typeAliasDestination] whenever it changes. If
 * there is no active [$typeAliasDestination], no item will be emitted.
 */
val NavController.appCurrentDestinationFlow: Flow<$typeAliasDestination>
    get() = currentDestinationFlow.map { it as $typeAliasDestination }

/**
 * Gets the current [$typeAliasDestination] as a [State].
 */
@Composable
fun NavController.appCurrentDestinationAsState(): State<$typeAliasDestination?> {
    return appCurrentDestinationFlow.collectAsState(initial = null)
}

// region deprecated APIs

/**
 * If this [Route] is a [$typeAliasDestination], returns it
 *
 * If this [Route] is a [$GENERATED_NAV_GRAPH], returns its
 * start [$typeAliasDestination].
 */
@Deprecated(
    message = "Api will be removed! Use `startAppDestination` instead.",
    replaceWith = ReplaceWith("startAppDestination")
)
val Route.startDestination: $typeAliasDestination
    get() = startDestination as $typeAliasDestination
$START_NO_NAV_GRAPHS_NAV_DESTINATION_ANCHOR
/**
 * Finds the destination correspondent to this [NavBackStackEntry] in the root NavGraph, null if none is found
 * or if no route is set in this back stack entry's destination.
 */
@Deprecated(
    message = "Api will be removed! Use `appDestination()` instead.",
    replaceWith = ReplaceWith("appDestination()")
)
val NavBackStackEntry.navDestination: $typeAliasDestination?
    get() = appDestination()
$END_NO_NAV_GRAPHS_NAV_DESTINATION_ANCHOR
/**
 * Finds the destination correspondent to this [NavBackStackEntry] in [navGraph], null if none is found
 * or if no route is set in this back stack entry's destination.
 */
@Deprecated(
    message = "Api will be removed! Use `appDestination()` instead.",
    replaceWith = ReplaceWith("appDestination")
)
${REQUIRE_OPT_IN_ANNOTATIONS_PLACEHOLDER}fun NavBackStackEntry.navDestination(navGraph: $GENERATED_NAV_GRAPH$START_NAV_DESTINATION_DEPRECATED_ROOT_DEFAULT_ANCHOR = $GENERATED_NAV_GRAPHS_OBJECT.root$END_NAV_DESTINATION_DEPRECATED_ROOT_DEFAULT_ANCHOR): $typeAliasDestination? {
    @Suppress("DEPRECATION")
    return destination(navGraph) as $typeAliasDestination
}

/**
 * Finds the destination correspondent to this [NavBackStackEntry] in [navGraph], null if none is found
 * or if no route is set in this back stack entry's destination.
 */
 @Deprecated(
     message = "Api will be removed! Use `appDestination()` instead.",
     replaceWith = ReplaceWith("appDestination")
 )
${REQUIRE_OPT_IN_ANNOTATIONS_PLACEHOLDER}fun NavBackStackEntry.appDestination(navGraph: $GENERATED_NAV_GRAPH$START_NAV_DESTINATION_ROOT_DEFAULT_ANCHOR = $GENERATED_NAV_GRAPHS_OBJECT.root$END_NAV_DESTINATION_ROOT_DEFAULT_ANCHOR): $typeAliasDestination? {
    @Suppress("DEPRECATION")
    return destination(navGraph) as $typeAliasDestination
}

// endregion

""".trimIndent()
)
