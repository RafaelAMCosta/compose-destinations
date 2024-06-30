package com.ramcosta.composedestinations.codegen.writers.sub

import com.ramcosta.composedestinations.codegen.commons.ANIMATED_VISIBILITY_SCOPE_QUALIFIED_NAME
import com.ramcosta.composedestinations.codegen.commons.ANIMATED_VISIBILITY_SCOPE_SIMPLE_NAME
import com.ramcosta.composedestinations.codegen.commons.COLUMN_SCOPE_QUALIFIED_NAME
import com.ramcosta.composedestinations.codegen.commons.COLUMN_SCOPE_SIMPLE_NAME
import com.ramcosta.composedestinations.codegen.commons.CORE_PACKAGE_NAME
import com.ramcosta.composedestinations.codegen.commons.DESTINATIONS_NAVIGATOR_QUALIFIED_NAME
import com.ramcosta.composedestinations.codegen.commons.NAV_BACK_STACK_ENTRY_QUALIFIED_NAME
import com.ramcosta.composedestinations.codegen.commons.NAV_CONTROLLER_QUALIFIED_NAME
import com.ramcosta.composedestinations.codegen.commons.NAV_HOST_CONTROLLER_QUALIFIED_NAME
import com.ramcosta.composedestinations.codegen.commons.RESULT_BACK_NAVIGATOR_QUALIFIED_NAME
import com.ramcosta.composedestinations.codegen.commons.RESULT_RECIPIENT_QUALIFIED_NAME
import com.ramcosta.composedestinations.codegen.commons.coreTypes
import com.ramcosta.composedestinations.codegen.commons.plusAssign
import com.ramcosta.composedestinations.codegen.model.CodeGenType
import com.ramcosta.composedestinations.codegen.model.CustomNavType
import com.ramcosta.composedestinations.codegen.model.DestinationGeneratingParams
import com.ramcosta.composedestinations.codegen.model.Importable
import com.ramcosta.composedestinations.codegen.model.Parameter
import com.ramcosta.composedestinations.codegen.model.SubModuleInfo
import com.ramcosta.composedestinations.codegen.model.Type
import com.ramcosta.composedestinations.codegen.model.TypeArgument
import com.ramcosta.composedestinations.codegen.writers.helpers.ImportableHelper

class DestinationContentFunctionWriter(
    private val destination: DestinationGeneratingParams,
    private val navArgs: List<Parameter>,
    private val importableHelper: ImportableHelper,
    private val customNavTypeByType: Map<Type, CustomNavType>,
    private val submodules: List<SubModuleInfo>,
) {

    private val submoduleResultNavTypeByTypeQualifiedNames: Map<String, String> by lazy {
        submodules.flatMap { it.publicResultSenders }
            .associateBy { it.resultTypeQualifiedName }
            .mapValues { it.value.resultNavTypeQualifiedName }
    }

    private val requirePlaceholder = importableHelper.addAndGetPlaceholder(
        Importable(
            "require",
            "com.ramcosta.composedestinations.navigation.require"
        )
    )

    private val animatedVisibilityScopePlaceholder = importableHelper.addAndGetPlaceholder(
        Importable(
            ANIMATED_VISIBILITY_SCOPE_SIMPLE_NAME,
            ANIMATED_VISIBILITY_SCOPE_QUALIFIED_NAME
        )
    )

    private val listOfPreSupportedTypes = mapOf<String, (CodeGenType) -> String>(
        ANIMATED_VISIBILITY_SCOPE_QUALIFIED_NAME to { "(this as $animatedVisibilityScopePlaceholder)" },
        NAV_CONTROLLER_QUALIFIED_NAME to { "navController" },
        NAV_HOST_CONTROLLER_QUALIFIED_NAME to { "navController" },
        NAV_BACK_STACK_ENTRY_QUALIFIED_NAME to { "navBackStackEntry" },
        DESTINATIONS_NAVIGATOR_QUALIFIED_NAME to { "destinationsNavigator" },
        RESULT_RECIPIENT_QUALIFIED_NAME to { type ->
            val placeHolder = importableHelper.addAndGetPlaceholder(
                Importable(
                    "resultRecipient",
                    "$CORE_PACKAGE_NAME.scope.resultRecipient"
                )
            )

            "$placeHolder(${type.typeArguments[1].getResultNavTypePlaceholder()})"
        },
        RESULT_BACK_NAVIGATOR_QUALIFIED_NAME to { type ->
            val placeHolder = importableHelper.addAndGetPlaceholder(
                Importable(
                    "resultBackNavigator",
                    "$CORE_PACKAGE_NAME.scope.resultBackNavigator"
                )
            )
            "$placeHolder(${type.typeArguments.first().getResultNavTypePlaceholder()})"
        },
    )

    fun write(): String = with(destination) {
        val functionCallCode = StringBuilder()

        val (args, needsDependencyContainer) = prepareArguments()
        val (receiverCode, receiverNeedsDependencyContainer) = prepareReceiver()
        if (needsDependencyContainer || receiverNeedsDependencyContainer) {
            functionCallCode += "\t\tval dependencyContainer = buildDependencies()\n"
        }

        if (navArgs.isNotEmpty() && destination.destinationNavArgsClass == null) {
            functionCallCode += "\t\tval (${argNamesInLine()}) = navArgs\n"
        }

        functionCallCode += wrappingPrefix()

        val composableCall = "\t\t$receiverCode${annotatedName}($args)"

        functionCallCode += if (composableWrappers.isEmpty()) composableCall
        else "\t" + composableCall.replace("\n", "\n\t")

        functionCallCode += wrappingSuffix()

        return functionCallCode.toString()
    }

    private fun DestinationGeneratingParams.wrappingPrefix(): String {
        val wrappingPrefix = when {
            composableWrappers.size == 1 -> {
                val wrapPlaceholder = importableHelper.addAndGetPlaceholder(
                    Importable("Wrap", "com.ramcosta.composedestinations.wrapper.Wrap")
                )
                "\t\t$wrapPlaceholder(${importableHelper.addAndGetPlaceholder(composableWrappers.first())}) {\n"
            }

            composableWrappers.isNotEmpty() -> {
                val wrapPlaceholder = importableHelper.addAndGetPlaceholder(
                    Importable("Wrap", "com.ramcosta.composedestinations.wrapper.Wrap")
                )
                "\t\t$wrapPlaceholder(${composableWrappers.joinToString(", ") { importableHelper.addAndGetPlaceholder(it) }}) {\n"
            }

            else -> ""
        }
        return wrappingPrefix
    }

    private fun DestinationGeneratingParams.wrappingSuffix(): String {
        return if (composableWrappers.isNotEmpty()) {
            "\n\t\t}"
        } else {
            ""
        }
    }

    private fun argNamesInLine(): String {
        return navArgs.joinToString(", ") { it.name }
    }

    private fun prepareReceiver(): Pair<String, Boolean> {
        val receiverType = destination.composableReceiverType
        return when (receiverType?.importable?.qualifiedName) {
            null -> "" to false
            ANIMATED_VISIBILITY_SCOPE_QUALIFIED_NAME -> {
                "val animatedVisibilityScope = (this as $animatedVisibilityScopePlaceholder)\n" +
                        "\t\tanimatedVisibilityScope." to false
            }

            COLUMN_SCOPE_QUALIFIED_NAME -> {
                val columnScopePlaceholder = importableHelper.addAndGetPlaceholder(
                    Importable(
                        COLUMN_SCOPE_SIMPLE_NAME,
                        COLUMN_SCOPE_QUALIFIED_NAME
                    )
                )
                "val columnScope = (this as $columnScopePlaceholder)\n" +
                        "\t\tcolumnScope." to false
            }

            in listOfPreSupportedTypes.keys -> {
                listOfPreSupportedTypes[receiverType.importable.qualifiedName]!!.invoke(receiverType) + "." to false
            }

            else -> {
                val receiverTypePlaceHolder = importableHelper.addAndGetPlaceholder(receiverType.importable)
                "dependencyContainer.$requirePlaceholder<$receiverTypePlaceHolder>()." to true
            }
        }
    }

    private fun DestinationGeneratingParams.prepareArguments(): Pair<String, Boolean> {
        var argsCode = ""
        var anyArgNeedsDepContainer = false

        val parametersToPass = parameters
            .map {
                val (arg, argNeedsDepContainer) = resolveArgumentForTypeAndName(it)
                anyArgNeedsDepContainer = anyArgNeedsDepContainer || argNeedsDepContainer

                it.name to arg
            }
            .filter { it.second != null }

        parametersToPass
            .forEachIndexed { i, (name, resolvedArgument) ->
                if (i != 0) {
                    argsCode += ", "
                }

                argsCode += "\n\t\t\t$name = $resolvedArgument"

                if (i == parametersToPass.lastIndex) argsCode += "\n\t\t"
            }

        return argsCode to anyArgNeedsDepContainer
    }

    private fun resolveArgumentForTypeAndName(parameter: Parameter): Pair<String?, Boolean> {
        var needsDependencyContainer = false
        val arg = when (val typeQualifiedName = parameter.type.importable.qualifiedName) {
            in listOfPreSupportedTypes.keys -> listOfPreSupportedTypes[typeQualifiedName]!!.invoke(parameter.type)
            destination.destinationNavArgsClass?.type?.qualifiedName -> {
                "navArgs"
            }
            else -> {
                when {
                    navArgs.contains(parameter) -> {
                        parameter.name //this is resolved by argsFrom before the function
                    }

                    !parameter.hasDefault -> {
                        needsDependencyContainer = true

                        if (parameter.isMarkedNavHostParam) {
                            "dependencyContainer.$requirePlaceholder(true)"
                        } else {
                            "dependencyContainer.$requirePlaceholder()"
                        }
                    }

                    else -> null
                }
            }
        }

        return arg to needsDependencyContainer
    }

    private fun TypeArgument.getResultNavTypePlaceholder(): String {
        when (this) {
            is TypeArgument.Error,
            is TypeArgument.GenericType,
            is TypeArgument.Star -> error("Unexpected result type argument $this")
            is TypeArgument.Typed -> {
                val coreNavType = coreTypes[type.value]
                if (coreNavType != null) {
                    return importableHelper.addAndGetPlaceholder(coreNavType)
                }

                val thisModuleCustomNavType = customNavTypeByType[type.value]
                if (thisModuleCustomNavType != null) {
                    return importableHelper.addAndGetPlaceholder(thisModuleCustomNavType.importable)
                }

                val submoduleCustomNavType = submoduleResultNavTypeByTypeQualifiedNames[type.value.importable.qualifiedName]
                if (submoduleCustomNavType != null) {
                    return importableHelper.addAndGetPlaceholder(
                        Importable(
                            submoduleCustomNavType.split(".").last(),
                            submoduleCustomNavType
                        )
                    )
                }

                error("Unknown result nav type $type")
            }
        }
    }
}