package com.ramcosta.composedestinations.codegen.writers

import com.ramcosta.composedestinations.codegen.codeGenActivityDestination
import com.ramcosta.composedestinations.codegen.codeGenBasePackageName
import com.ramcosta.composedestinations.codegen.codeGenDestination
import com.ramcosta.composedestinations.codegen.codeGenNoArgsActivityDestination
import com.ramcosta.composedestinations.codegen.codeGenNoArgsDestination
import com.ramcosta.composedestinations.codegen.commons.ANIMATED_VISIBILITY_SCOPE_QUALIFIED_NAME
import com.ramcosta.composedestinations.codegen.commons.ANIMATED_VISIBILITY_SCOPE_SIMPLE_NAME
import com.ramcosta.composedestinations.codegen.commons.BOTTOM_SHEET_DEPENDENCY
import com.ramcosta.composedestinations.codegen.commons.CORE_BOTTOM_SHEET_DESTINATION_STYLE
import com.ramcosta.composedestinations.codegen.commons.CORE_DIRECTION
import com.ramcosta.composedestinations.codegen.commons.CORE_PACKAGE_NAME
import com.ramcosta.composedestinations.codegen.commons.CORE_STRING_NAV_TYPE
import com.ramcosta.composedestinations.codegen.commons.DEEP_LINK_ANNOTATION_FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.codegen.commons.IllegalDestinationsSetup
import com.ramcosta.composedestinations.codegen.commons.MissingRequiredDependency
import com.ramcosta.composedestinations.codegen.commons.SAVED_STATE_HANDLE_QUALIFIED_NAME
import com.ramcosta.composedestinations.codegen.commons.SAVED_STATE_HANDLE_SIMPLE_NAME
import com.ramcosta.composedestinations.codegen.commons.bundleImportable
import com.ramcosta.composedestinations.codegen.commons.coreTypes
import com.ramcosta.composedestinations.codegen.commons.experimentalAnimationApiType
import com.ramcosta.composedestinations.codegen.commons.isCustomTypeNavArg
import com.ramcosta.composedestinations.codegen.commons.isEnumTypeOrTypeArg
import com.ramcosta.composedestinations.codegen.commons.plusAssign
import com.ramcosta.composedestinations.codegen.commons.recursiveRequireOptInAnnotations
import com.ramcosta.composedestinations.codegen.commons.removeInstancesOf
import com.ramcosta.composedestinations.codegen.commons.toCoreNavTypeImportableOrNull
import com.ramcosta.composedestinations.codegen.commons.toTypeCode
import com.ramcosta.composedestinations.codegen.facades.CodeOutputStreamMaker
import com.ramcosta.composedestinations.codegen.model.CodeGenConfig
import com.ramcosta.composedestinations.codegen.model.CustomNavType
import com.ramcosta.composedestinations.codegen.model.DestinationGeneratingParamsWithNavArgs
import com.ramcosta.composedestinations.codegen.model.DestinationStyleType
import com.ramcosta.composedestinations.codegen.model.GeneratedDestination
import com.ramcosta.composedestinations.codegen.model.Importable
import com.ramcosta.composedestinations.codegen.model.Parameter
import com.ramcosta.composedestinations.codegen.model.Type
import com.ramcosta.composedestinations.codegen.model.TypeArgument
import com.ramcosta.composedestinations.codegen.model.TypeInfo
import com.ramcosta.composedestinations.codegen.model.Visibility
import com.ramcosta.composedestinations.codegen.templates.ACTIVITY_DESTINATION_FIELDS
import com.ramcosta.composedestinations.codegen.templates.ARGS_FROM_METHODS
import com.ramcosta.composedestinations.codegen.templates.ARGS_TO_DIRECTION_METHOD
import com.ramcosta.composedestinations.codegen.templates.BASE_ROUTE
import com.ramcosta.composedestinations.codegen.templates.COMPOSED_ROUTE
import com.ramcosta.composedestinations.codegen.templates.CONTENT_FUNCTION_CODE
import com.ramcosta.composedestinations.codegen.templates.DEEP_LINKS
import com.ramcosta.composedestinations.codegen.templates.DESTINATION_NAME
import com.ramcosta.composedestinations.codegen.templates.DESTINATION_STYLE
import com.ramcosta.composedestinations.codegen.templates.DESTINATION_VISIBILITY_PLACEHOLDER
import com.ramcosta.composedestinations.codegen.templates.NAV_ARGS_DATA_CLASS
import com.ramcosta.composedestinations.codegen.templates.NAV_ARGUMENTS
import com.ramcosta.composedestinations.codegen.templates.REQUIRE_OPT_IN_ANNOTATIONS_PLACEHOLDER
import com.ramcosta.composedestinations.codegen.templates.SUPERTYPE
import com.ramcosta.composedestinations.codegen.templates.destinationTemplate
import com.ramcosta.composedestinations.codegen.writers.helpers.ImportableHelper
import com.ramcosta.composedestinations.codegen.writers.helpers.NavArgResolver
import com.ramcosta.composedestinations.codegen.writers.helpers.writeSourceFile
import com.ramcosta.composedestinations.codegen.writers.sub.DestinationContentFunctionWriter

class SingleDestinationWriter(
    private val codeGenConfig: CodeGenConfig,
    private val codeGenerator: CodeOutputStreamMaker,
    private val isBottomSheetDependencyPresent: Boolean,
    private val navArgResolver: NavArgResolver,
    private val destination: DestinationGeneratingParamsWithNavArgs,
    private val customNavTypeByType: Map<Type, CustomNavType>,
    private val importableHelper: ImportableHelper
) {

    private val packageName = "$codeGenBasePackageName.destinations"
    private val navArgs get() = destination.navArgs

    init {
        if (destination.navGraphInfo.start && destination.navGraphInfo.isNavHostGraph && destination.navArgs.any { it.isMandatory }) {
            throw IllegalDestinationsSetup("\"'${destination.composableName}' composable: Start destinations of NavHostGraphs cannot have mandatory navigation arguments!")
        }

        importableHelper.addAll(destinationTemplate.imports)
        importableHelper.addPriorityQualifiedImport(destination.composableQualifiedName, destination.composableName)
    }

    fun write(): GeneratedDestination = with(destination) {
        codeGenerator.makeFile(
            packageName = packageName,
            name = name,
            sourceIds = sourceIds.toTypedArray()
        ).writeSourceFile(
            packageStatement = destinationTemplate.packageStatement,
            importableHelper = importableHelper,
            sourceCode = destinationTemplate.sourceCode
                .replace(DESTINATION_NAME, name)
                .replaceSuperclassDestination()
                .addNavArgsDataClass()
                .replace(REQUIRE_OPT_IN_ANNOTATIONS_PLACEHOLDER, objectWideRequireOptInAnnotationsCode())
                .replace(DESTINATION_VISIBILITY_PLACEHOLDER, getDestinationVisibilityModifier())
                .replace(BASE_ROUTE, destination.cleanRoute)
                .replace(COMPOSED_ROUTE, constructRouteFieldCode())
                .replace(NAV_ARGUMENTS, navArgumentsDeclarationCode())
                .replace(DEEP_LINKS, deepLinksDeclarationCode())
                .replace(DESTINATION_STYLE, destinationStyle())
                .replace(CONTENT_FUNCTION_CODE, contentFunctionCode())
                .replace(ARGS_TO_DIRECTION_METHOD, invokeMethodsCode())
                .replace(ARGS_FROM_METHODS, argsFromFunctions())
                .replace(ACTIVITY_DESTINATION_FIELDS, activityDestinationFields())
        )

        return GeneratedDestination(
            sourceIds = sourceIds,
            qualifiedName = "$packageName.$name",
            simpleName = name,
            navArgsImportable = navArgsDataClassImportable()?.let {
                if (navArgsDelegateType == null) {
                    it.copy(simpleName = "NavArgs", qualifiedName = "$packageName.$name.NavArgs")
                } else {
                    it
                }
            },
            navGraphInfo = navGraphInfo,
            requireOptInAnnotationTypes = gatherOptInAnnotations()
                .filter { !it.isOptedIn }
                .map { it.importable }
                .toList(),
        )
    }

    private fun getDestinationVisibilityModifier(): String {
        return if (codeGenConfig.useComposableVisibility && destination.visibility == Visibility.INTERNAL) "internal"
        else "public"
    }

    private fun String.replaceSuperclassDestination(): String {
        if (destination.destinationStyleType is DestinationStyleType.Activity) {
            return replace(
                SUPERTYPE, if (navArgs.isEmpty()) {
                    codeGenNoArgsActivityDestination
                } else {
                    "$codeGenActivityDestination<${destination.navArgsDelegateType!!.type.getCodePlaceHolder()}>"
                }
            )
        }

        if (navArgs.isEmpty()) {
            return replace(SUPERTYPE, codeGenNoArgsDestination)
        }

        val superType = if (destination.navArgsDelegateType != null) {
            "${codeGenDestination}<${destination.navArgsDelegateType.type.getCodePlaceHolder()}>"
        } else {
            "${codeGenDestination}<${destination.name}.NavArgs>"
        }

        return replace(SUPERTYPE, superType)
    }

    private fun String.addNavArgsDataClass(): String {
        if (navArgs.isEmpty() || destination.navArgsDelegateType != null) {
            return removeInstancesOf(NAV_ARGS_DATA_CLASS)
        }

        val code = StringBuilder()
        code += "\n\n"
        code += "\tpublic data class NavArgs(\n"
        code += "${innerNavArgsParametersCode(true)}\n"
        code += "\t)"

        return replace(NAV_ARGS_DATA_CLASS, code.toString())
    }

    private fun gatherOptInAnnotations(): List<OptInAnnotation> {
        val optInByAnnotation = destination.requireOptInAnnotationTypes.associateWithTo(mutableMapOf()) { false }

        destination.parameters.forEach { param ->
            optInByAnnotation.putAll(
                param.type.recursiveRequireOptInAnnotations().associateWith { requireOptInType ->
                    // if the destination itself doesn't need this annotation, then it was opted in
                    !destination.requireOptInAnnotationTypes.contains(requireOptInType)
                }
            )
        }

        if (destination.destinationStyleType is DestinationStyleType.Animated) {
            optInByAnnotation.putAll(destination.destinationStyleType.requireOptInAnnotations.associateWithTo(mutableMapOf()) { false })
        }

        if (isRequiredReceiverExperimentalOptedIn() || isRequiredAnimationExperimentalOptedIn()) {
            // user has opted in, so we will too
            experimentalAnimationApiType.addImport()
            optInByAnnotation[experimentalAnimationApiType] = true
        }

        return optInByAnnotation.map { OptInAnnotation(it.key, it.value) }
    }

    private fun isRequiredAnimationExperimentalOptedIn(): Boolean {
        return destination.destinationStyleType is DestinationStyleType.Animated
                && !destination.destinationStyleType.requireOptInAnnotations.contains(experimentalAnimationApiType)
    }

    private fun isRequiredReceiverExperimentalOptedIn(): Boolean {
        return destination.composableReceiverSimpleName == ANIMATED_VISIBILITY_SCOPE_SIMPLE_NAME
                && !destination.requireOptInAnnotationTypes.contains(experimentalAnimationApiType)
    }

    private fun objectWideRequireOptInAnnotationsCode(): String {
        val code = StringBuilder()
        val optInByAnnotation = gatherOptInAnnotations()

        val (optedIns, nonOptedIns) = optInByAnnotation
            .onEach { it.importable.addImport() }
            .partition { it.isOptedIn }

        nonOptedIns.forEach {
            code += "@${it.importable.getCodePlaceHolder()}\n"
        }

        if (optedIns.isNotEmpty()) {
            code += "@OptIn(${optedIns.joinToString(", ") { "${it.importable.simpleName}::class" }})\n"
        }

        return code.toString()
    }

    private fun invokeMethodsCode(): String {
        if (navArgs.isEmpty()) {

            return """
            |     
            |    public operator fun invoke(): $CORE_DIRECTION = this
            |    
            """.trimMargin()
        }

        val template = """
        |
        |    override fun invoke(navArgs: %s1): $CORE_DIRECTION = with(navArgs) {
		|        invoke(%s2)
	    |    }
        |     
        |    public operator fun invoke(
        |%s3
        |    ): $CORE_DIRECTION {
        |        return $CORE_DIRECTION(
        |            route = %s4
        |        )
        |    }
        |    
        """.trimMargin()

        var route = "\"${constructRoute(true)}\""
            .replace("/", "\" + \n\t\t\t\t\t\"/")
            .replace("?", "\" + \n\t\t\t\t\t\"?")
            .replace("&", "\" + \n\t\t\t\t\t\"&")

        navArgs.forEach {
            route = route.replace("{${it.name}}", "\${${it.stringifyForNavigation()}}")
        }

        return template
            .replace("%s1", navArgsDataClassName())
            .replace("%s2", navArgs.joinToString(", ") { it.name })
            .replace("%s3", innerNavArgsParametersCode())
            .replace("%s4", route)
    }

    private fun innerNavArgsParametersCode(prefixWithVal: Boolean = false): String {
        val args = StringBuilder()
        val argPrefix = if (prefixWithVal) {
            "val "
        } else ""

        navArgs.forEachIndexed { i, it ->
            args += "\t\t$argPrefix${it.name}: ${it.type.toTypeCode(importableHelper)}${defaultValueForInvokeFunction(it)},"

            if (i != navArgs.lastIndex) {
                args += "\n"
            }
        }

        return args.toString()
    }

    private fun Parameter.stringifyForNavigation(): String {
        return type.stringifyForNavigation(name)
    }

    private fun TypeInfo.stringifyForNavigation(
        argumentName: String,
        argumentReference: String = argumentName,
    ): String {
        if (isCustomTypeNavArg()) {
            val codePlaceHolder = navArgResolver.customNavTypeCode(this)

            return "$codePlaceHolder.serializeValue($argumentReference)"
        }

        if (importable.qualifiedName == String::class.qualifiedName) {
            return "${CORE_STRING_NAV_TYPE.getCodePlaceHolder()}.serializeValue(\"$argumentName\", $argumentReference)"
        } else if (value in coreTypes.keys) {
            return "${coreTypes[value]!!.getCodePlaceHolder()}.serializeValue($argumentReference)"
        }

        if (valueClassInnerInfo != null) {
            return valueClassInnerInfo.typeInfo.stringifyForNavigation(
                argumentName = argumentName,
                argumentReference = "$argumentName${if (isNullable) "?." else "."}${valueClassInnerInfo.publicNonNullableField}"
            )
        }

        val isNullable = isNullable || argumentReference.contains("?.")
        val ifNullBeforeToString = if (isNullable) "?" else ""
        val ifNullSuffix = if (isNullable) {
            " ?: \"{${argumentName}}\""
        } else {
            ""
        }

        return "${argumentReference}$ifNullBeforeToString${".toString()"}$ifNullSuffix"
    }

    private fun activityDestinationFields(): String = with(destination) {
        if (activityDestinationParams == null) {
            return ""
        }

        val uriImportable = Importable(
            "Uri",
            "android.net.Uri"
        )

        val activityImportable = Importable(
            "Activity",
            "android.app.Activity"
        )

        val activityClassImportable = Importable(
            composableName,
            composableQualifiedName
        )

        return """override val targetPackage: String? = @targetPackage@ 
        |    
        |    override val action: String? = @action@ 
        |    
        |    override val data: ${uriImportable.getCodePlaceHolder()}? = @data@ 
        |    
        |    override val dataPattern: String? = @dataPattern@ 
        |    
        |    override val activityClass: Class<out ${activityImportable.getCodePlaceHolder()}>? = @activityClass@::class.java
        |    
        """.trimMargin()
            .replace("@targetPackage@", activityDestinationParams.targetPackage?.let { "\"${it}\"" } ?: "null")
            .replace("@action@", activityDestinationParams.action?.let { "\"${it}\"" } ?: "null")
            .replace("@data@", activityDestinationParams.dataUri?.let { "${uriImportable.getCodePlaceHolder()}.parse(\"${it}\")" } ?: "null")
            .replace("@dataPattern@", activityDestinationParams.dataPattern?.let { "\"${it}\"" } ?: "null")
            .replace("@activityClass@", activityClassImportable.getCodePlaceHolder())
    }

    private fun argsFromFunctions(): String = with(destination) {
        if (navArgs.isEmpty()) {
            return ""
        }

        val argsType = navArgsDataClassName()

        return argsFromNavBackStackEntry(argsType) + "\n" + argsFromSavedStateHandle(argsType)
    }

    private fun navArgsDataClassImportable(): Importable? = with(destination) {
        return navArgsDelegateType?.type
            ?: if (navArgs.isEmpty()) {
                null
            } else {
                Importable(
                    "NavArgs",
                    "$packageName.${destination.name}.NavArgs"
                )
            }
    }

    private fun navArgsDataClassName(): String =
        navArgsDataClassImportable()?.getCodePlaceHolder() ?: "Unit"

    private fun argsFromNavBackStackEntry(argsType: String): String {
        val code = StringBuilder()
        code += """
                
           |override fun argsFrom(bundle: ${bundleImportable.getCodePlaceHolder()}?): $argsType {
           |    return ${argsType}(%s2
           |    )
           |}
            """.trimMargin()

        val arguments = StringBuilder()
        navArgs.forEach {
            arguments += "\n\t\t${it.name} = "
            arguments += navArgResolver.resolve(destination, it)
            arguments += ","
        }

        return code.toString()
            .replace("%s2", arguments.toString())
            .prependIndent("\t")
    }

    private fun argsFromSavedStateHandle(argsType: String): String {
        val savedStateHandlePlaceholder = Importable(
            SAVED_STATE_HANDLE_SIMPLE_NAME,
            SAVED_STATE_HANDLE_QUALIFIED_NAME
        ).getCodePlaceHolder()

        val code = StringBuilder()
        code += """
                
           |override fun argsFrom(savedStateHandle: $savedStateHandlePlaceholder): $argsType {
           |    return ${argsType}(%s2
           |    )
           |}
            """.trimMargin()

        val arguments = StringBuilder()
        navArgs.forEach {
            arguments += "\n\t\t${it.name} = "
            arguments += navArgResolver.resolveFromSavedStateHandle(destination, it)
            arguments += ","
        }

        return code.toString()
            .replace("%s2", arguments.toString())
            .prependIndent("\t")
    }

    private fun defaultValueForInvokeFunction(it: Parameter): String {
        return if (it.hasDefault) " = ${it.defaultValue?.code}"
        else ""
    }

    private fun constructRouteFieldCode(): String {
        return if (navArgs.isEmpty()) {
            constructRoute(false)
        } else {
            "\"${constructRoute(true)}\""
        }
    }

    private fun constructRoute(
        isConcatenatingInString: Boolean,
        args: List<Parameter> = navArgs
    ): String {
        val mandatoryArgs = StringBuilder()
        val optionalArgs = StringBuilder()
        args.forEach {
            if (it.isMandatory) {
                mandatoryArgs += "/{${it.name}}"
            } else {
                val leadingSign = if (optionalArgs.isEmpty()) "?" else "&"
                optionalArgs += "$leadingSign${it.name}={${it.name}}"
            }
        }

        val baseRoutePrefix = if (isConcatenatingInString) {
            "\$baseRoute"
        } else {
            "baseRoute"
        }

        return if (args.isEmpty()) baseRoutePrefix
        else "$baseRoutePrefix$mandatoryArgs$optionalArgs"
    }

    private fun contentFunctionCode(): String {
        if (destination.destinationStyleType is DestinationStyleType.Activity) {
            return ""
        }

        return """
    @Composable
    override fun DestinationScope<${navArgsDataClassName()}>.Content() {
%s1
    }
        """.trimIndent()
            .replace(
                "%s1", DestinationContentFunctionWriter(
                    destination,
                    navArgs,
                    importableHelper
                ).write()
            )
    }

    private fun navArgumentsDeclarationCode(): String {
        val code = StringBuilder()

        navArgs.forEachIndexed { i, it ->
            if (i == 0) {
                code += "\n\toverride val arguments: List<NamedNavArgument> get() = listOf(\n\t\t"
            }

            val toNavTypeCode = it.type.toNavTypeCode()
            code += "navArgument(\"${it.name}\") {\n\t\t\t"
            code += "type = $toNavTypeCode\n\t\t"
            if (it.type.isNullable) {
                code += "\tnullable = true\n\t\t"
            }
            code += navArgDefaultCode(it)
            code += "}"

            code += if (i != navArgs.lastIndex) {
                ",\n\t\t"
            } else {
                "\n\t)\n"
            }
        }

        return code.toString()
    }

    private fun deepLinksDeclarationCode(): String {
        val code = StringBuilder()
        val navDeepLinkPlaceholder = Importable(
            "navDeepLink",
            "androidx.navigation.navDeepLink"
        ).getCodePlaceHolder()

        destination.deepLinks.forEachIndexed { i, it ->
            if (i == 0) {
                code += "\n\toverride val deepLinks: List<NavDeepLink> get() = listOf(\n\t\t"
            }

            code += "$navDeepLinkPlaceholder {\n\t\t"

            if (it.action.isNotEmpty()) {
                code += "\taction = \"${it.action}\"\n\t\t"
            }
            if (it.mimeType.isNotEmpty()) {
                code += "\tmimeType = \"${it.mimeType}\"\n\t\t"
            }
            if (it.uriPattern.isNotEmpty()) {
                val uriPattern = if (it.uriPattern.contains(DEEP_LINK_ANNOTATION_FULL_ROUTE_PLACEHOLDER)) {
                    if (it.uriPattern.endsWith(DEEP_LINK_ANNOTATION_FULL_ROUTE_PLACEHOLDER)) {
                        it.uriPattern.replace(DEEP_LINK_ANNOTATION_FULL_ROUTE_PLACEHOLDER, constructRouteForDeepLinkPlaceholder())
                    } else {
                        throw IllegalDestinationsSetup("Composable '${destination.composableName}': deep link usage of 'FULL_ROUTE_PLACEHOLDER' must be as a suffix")
                    }
                } else {
                    it.uriPattern
                }
                code += "\turiPattern = \"$uriPattern\"\n\t\t"
            }
            code += "}"

            code += if (i != destination.deepLinks.lastIndex) {
                ",\n\t\t"
            } else {
                "\n\t)\n"
            }
        }

        return code.toString()
    }

    private fun constructRouteForDeepLinkPlaceholder(): String {
        val args = navArgs
            .toMutableList()
            .apply {
                removeAll {
                    val needsCustomSerializer = it.isCustomTypeNavArg() && !it.isEnumTypeOrTypeArg()
                    val hasCustomSerializer = customNavTypeByType[it.type.value]?.serializer != null
                    if (it.isMandatory && needsCustomSerializer && !hasCustomSerializer) {
                        throw IllegalDestinationsSetup(
                            "Composable '${destination.composableName}', arg name= '${it.name}': " +
                                    "deep links cannot contain mandatory navigation types of custom type unless you define" +
                                    "a custom serializer with @NavTypeSerializer. " +
                                    "This lets you control how the custom type class is defined in the string route."
                        )
                    }

                    needsCustomSerializer && !it.isMandatory && !hasCustomSerializer
                }
            }

        return constructRoute(true, args)
    }

    private fun destinationStyle(): String {
        return when (destination.destinationStyleType) {
            is DestinationStyleType.Activity,
            is DestinationStyleType.Default -> ""

            is DestinationStyleType.BottomSheet -> destinationStyleBottomSheet()

            is DestinationStyleType.Animated -> destinationStyleAnimated(destination.destinationStyleType)

            is DestinationStyleType.Dialog -> destinationStyleDialog(destination.destinationStyleType)

            is DestinationStyleType.Runtime -> destinationStyleRuntime()
        }
    }

    private fun destinationStyleRuntime(): String {
        return """
                            
            private var _style: DestinationStyle? = null

            override var style: DestinationStyle
                set(value) {
                    if (value is DestinationStyle.Runtime) {
                        error("You cannot use `DestinationStyle.Runtime` other than in the `@Destination`" +
                            "annotation 'style' parameter!")
                    }
                    _style = value
                }
                get() {
                    return _style ?: error("For annotated Composables with `style = DestinationStyle.Runtime`, " +
                            "you need to explicitly set the style before calling `DestinationsNavHost`")
                }
                
        """.trimIndent()
            .prependIndent("\t")
    }

    private fun destinationStyleDialog(destinationStyleType: DestinationStyleType.Dialog): String {
        return "\n\toverride val style: DestinationStyle = ${destinationStyleType.importable.getCodePlaceHolder()}\n"
    }

    private fun destinationStyleAnimated(destinationStyleType: DestinationStyleType.Animated): String {
        experimentalAnimationApiType.addImport()

        if (destination.composableReceiverSimpleName == ANIMATED_VISIBILITY_SCOPE_SIMPLE_NAME) {
            Importable(
                ANIMATED_VISIBILITY_SCOPE_SIMPLE_NAME,
                ANIMATED_VISIBILITY_SCOPE_QUALIFIED_NAME
            ).addImport()
        }

        return "\n\toverride val style: DestinationStyle = ${destinationStyleType.importable.getCodePlaceHolder()}\n"
    }

    private fun destinationStyleBottomSheet(): String {
        if (!isBottomSheetDependencyPresent) {
            throw MissingRequiredDependency("You need to include '$BOTTOM_SHEET_DEPENDENCY' to use $CORE_BOTTOM_SHEET_DESTINATION_STYLE!")
        }

        val bottomSheetImportable = Importable(
            CORE_BOTTOM_SHEET_DESTINATION_STYLE,
            "$CORE_PACKAGE_NAME.bottomsheet.spec.$CORE_BOTTOM_SHEET_DESTINATION_STYLE",
        )

        return "\n\toverride val style: DestinationStyle = ${bottomSheetImportable.getCodePlaceHolder()}\n"
    }

    private fun navArgDefaultCode(param: Parameter): String = param.defaultValue.let { defaultValue ->
        if (defaultValue == null) {
            return ""
        }

        defaultValue.imports.forEach { importableHelper.addPriorityQualifiedImport(it) }

        if (defaultValue.code == "null") {
            return "\tdefaultValue = null\n\t\t"
        }

        // we always have a val with the type of the param to avoid wrong types to be inferred by kotlin
        return "\tval defValue: ${param.type.toTypeCode(importableHelper)} = ${defaultValue.code}\n\t\t" +
                "\tdefaultValue = defValue\n\t\t"
    }

    private fun TypeInfo.toNavTypeCode(): String {
        val coreNavTypeCode = toCoreNavTypeImportableOrNull()
        if (coreNavTypeCode != null) {
            return coreNavTypeCode.getCodePlaceHolder()
        }

        if (isCustomTypeNavArg()) {
            importable.addImport()
            typeArguments.forEach {
                if (it is TypeArgument.Typed) it.type.importable.addImport()
            }
            return navArgResolver.customNavTypeCode(this)
        }

        if (valueClassInnerInfo != null) {
            return valueClassInnerInfo.typeInfo.toNavTypeCode()
        }

        throw IllegalDestinationsSetup("Composable '${destination.composableName}': Unknown type ${importable.qualifiedName}")
    }

    private class OptInAnnotation(
        val importable: Importable,
        val isOptedIn: Boolean,
    )

    private fun Importable.getCodePlaceHolder(): String {
        return importableHelper.addAndGetPlaceholder(this)
    }

    private fun Importable.addImport() {
        importableHelper.addAndGetPlaceholder(this)
    }
}
