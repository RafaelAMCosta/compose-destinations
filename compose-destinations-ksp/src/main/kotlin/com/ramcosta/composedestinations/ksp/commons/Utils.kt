package com.ramcosta.composedestinations.ksp.commons

import com.google.devtools.ksp.findActualType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance
import com.ramcosta.composedestinations.codegen.commons.CORE_PACKAGE_NAME
import com.ramcosta.composedestinations.codegen.commons.DESTINATION_ANNOTATION_NAV_ARGS_DELEGATE_ARGUMENT
import com.ramcosta.composedestinations.codegen.commons.IllegalDestinationsSetup
import com.ramcosta.composedestinations.codegen.commons.NAV_GRAPH_ANNOTATION
import com.ramcosta.composedestinations.codegen.commons.NAV_GRAPH_ANNOTATION_QUALIFIED
import com.ramcosta.composedestinations.codegen.commons.NAV_HOST_GRAPH_ANNOTATION
import com.ramcosta.composedestinations.codegen.commons.NAV_HOST_GRAPH_ANNOTATION_QUALIFIED
import com.ramcosta.composedestinations.codegen.commons.NAV_HOST_PARAM_ANNOTATION_QUALIFIED
import com.ramcosta.composedestinations.codegen.model.DeepLink
import com.ramcosta.composedestinations.codegen.model.Importable
import com.ramcosta.composedestinations.codegen.model.NavGraphInfo
import com.ramcosta.composedestinations.codegen.model.NavTypeSerializer
import com.ramcosta.composedestinations.codegen.model.Parameter
import com.ramcosta.composedestinations.codegen.model.RawNavArgsClass
import com.ramcosta.composedestinations.codegen.model.Type
import com.ramcosta.composedestinations.codegen.model.TypeArgument
import com.ramcosta.composedestinations.codegen.model.TypeInfo
import com.ramcosta.composedestinations.codegen.model.ValueClassInnerInfo
import com.ramcosta.composedestinations.codegen.model.Visibility
import com.ramcosta.composedestinations.ksp.processors.KSPClassKind
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader


val ignoreAnnotations = listOf(
    "Composable",
    "Target",
    "Retention",
    "Suppress",
    "MustBeDocumented",
    "OptIn",
    "RequiresOptIn"
)

fun KSAnnotated.findAnnotation(name: String): KSAnnotation {
    return annotations.find { it.shortName.asString() == name }!!
}

fun KSAnnotated.findAnnotationPathRecursively(names: List<String>, path: List<KSAnnotation> = emptyList()): List<KSAnnotation>? {
    val relevantAnnotations = annotations.filter { it.shortName.asString() !in ignoreAnnotations}
    val foundAnnotation = relevantAnnotations.find { it.shortName.asString() in names }
    if (foundAnnotation != null) {
        return path + foundAnnotation
    }

    relevantAnnotations.forEach { annotation ->
        val found = annotation.annotationType.resolve().declaration.findAnnotationPathRecursively(names, path + annotation)
        if (found != null) {
            return found
        }
    }

    return null
}

inline fun <reified T> KSAnnotation.findArgumentValue(name: String): T? {
    return arguments.find { it.name?.asString() == name }?.value as T?
}

fun File.readLine(lineNumber: Int): String {
    val bufferedReader = BufferedReader(InputStreamReader(FileInputStream(this), Charsets.UTF_8))
    return bufferedReader
        .useLines { lines: Sequence<String> ->
            lines
                .take(lineNumber)
                .last()
        }
}

fun File.readLines(startLineNumber: Int, endLineNumber: Int): List<String> {
    val bufferedReader = BufferedReader(InputStreamReader(FileInputStream(this), Charsets.UTF_8))
    return bufferedReader
        .useLines { lines: Sequence<String> ->
            val linesList = lines
                .take(endLineNumber)
                .toList()
            linesList
                .takeLast(linesList.size - (startLineNumber - 1))
        }
}

fun File.readLinesAndImports(startLineNumber: Int, endLineNumber: Int): Pair<List<String>, List<String>> {
    val bufferedReader = BufferedReader(InputStreamReader(FileInputStream(this), Charsets.UTF_8))
    return bufferedReader
        .useLines { lines: Sequence<String> ->
            val linesList = lines
                .take(endLineNumber)
                .toList()

            val linesRes = linesList.takeLast(linesList.size - (startLineNumber - 1))
            val imports = linesList.filter { it.startsWith("import") }
                .map { it.removePrefix("import ") }

            linesRes to imports
        }
}

fun KSDeclaration.findAllRequireOptInAnnotations(): List<Importable> {
    val requireOptInAnnotations = mutableListOf<Importable>()
    annotations.forEach { annotation ->
        val annotationShortName = annotation.shortName.asString()
        if (annotationShortName == "Composable" || annotationShortName == "Destination") {
            return@forEach
        }

        val ksType = annotation.annotationType.resolve()
        if (ksType.isRequireOptInAnnotation()) {
            requireOptInAnnotations.add(Importable(annotationShortName, ksType.declaration.qualifiedName!!.asString()))
        }
    }

    return requireOptInAnnotations
}

fun KSType.isRequireOptInAnnotation(): Boolean {
    return declaration.annotations.any { annotation ->
        annotation.shortName.asString() == "RequiresOptIn"
                || annotation.annotationType.annotations.any {
            annotation.annotationType.resolve().isRequireOptInAnnotation()
        }
    }
}

fun KSType.findActualClassDeclaration(): KSClassDeclaration? {
    if (this.declaration is KSTypeAlias) {
        return (this.declaration as KSTypeAlias).findActualType()
    }

    return declaration as? KSClassDeclaration?
}

fun KSClassDeclaration.toImportable(): Importable? {
    return qualifiedName?.let { nonNullQualifiedName ->
        Importable(
            simpleName.asString(),
            nonNullQualifiedName.asString()
        )
    }
}

val KSClassDeclaration.isNothing get() =
    qualifiedName?.asString() == "java.lang.Void" || qualifiedName?.asString() == "kotlin.Nothing"

fun KSAnnotation.toDeepLink(): DeepLink {
    return DeepLink(
        findArgumentValue("action")!!,
        findArgumentValue("mimeType")!!,
        findArgumentValue("uriPattern")!!,
    )
}

data class NavArgsTypeWithFile(val type: RawNavArgsClass, val file: KSFile?)

fun KSAnnotation.getNavArgsDelegateType(
    resolver: Resolver,
    navTypeSerializersByType: Map<Importable, NavTypeSerializer>
): NavArgsTypeWithFile? = kotlin.runCatching {
    val ksType = findArgumentValue<KSType>(DESTINATION_ANNOTATION_NAV_ARGS_DELEGATE_ARGUMENT)
        ?: return null

    return ksType.getNavArgsDelegateType(resolver, navTypeSerializersByType)
}.getOrElse {
    throw IllegalDestinationsSetup("There was an issue with '$DESTINATION_ANNOTATION_NAV_ARGS_DELEGATE_ARGUMENT'" +
            " of annotation ${shortName.asString()}: make sure it is a class with a primary constructor.", it)
}

fun KSType.getNavArgsDelegateType(
    resolver: Resolver,
    navTypeSerializersByType: Map<Importable, NavTypeSerializer>
): NavArgsTypeWithFile? {
    val ksClassDeclaration = declaration as KSClassDeclaration
    if (ksClassDeclaration.isNothing) {
        return null
    }

    val parameters = ksClassDeclaration.primaryConstructor!!
        .parameters
        .map { it.toParameter(resolver, navTypeSerializersByType) }

    val visibility = ksClassDeclaration.getVisibility().also {
        check(it != Visibility.PRIVATE) {
            "${ksClassDeclaration.simpleName.asString()} cannot be private!"
        }
    }

    return NavArgsTypeWithFile(
        RawNavArgsClass(
            parameters,
            visibility,
            Importable(
                ksClassDeclaration.simpleName.asString(),
                ksClassDeclaration.qualifiedName!!.asString(),
            )
        ),
        ksClassDeclaration.containingFile
    )
}

fun KSDeclaration.getVisibility(): Visibility {
    return when {
        isPrivate() -> Visibility.PRIVATE
        isInternal() -> Visibility.INTERNAL
        else -> Visibility.PUBLIC
    }
}

fun KSValueParameter.toParameter(
    resolver: Resolver,
    navTypeSerializersByType: Map<Importable, NavTypeSerializer>
): Parameter {
    val resolvedType = type.resolve()
    val type = resolvedType.toType(location, resolver, navTypeSerializersByType)
        ?: throw IllegalDestinationsSetup("Parameter \"${name!!.asString()}\" of " +
                "was not resolvable: please review it.")

    return Parameter(
        name = name!!.asString(),
        type = type,
        hasDefault = hasDefault,
        isMarkedNavHostParam = this.annotations.any {
            it.shortName.asString() == "NavHostParam" &&
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == NAV_HOST_PARAM_ANNOTATION_QUALIFIED
        },
        defaultValue = getDefaultValue(resolver)
    )
}

fun Any.toGenVisibility(): Visibility {
    // Enum entries in annotation arguments are evaluated as KSClassDeclaration instead of KSType in KSP2
    // https://github.com/google/ksp/blob/main/docs/ksp2api.md#evaluation-of-enum-entries-in-annotation-arguments
    val declaration = when (this) {
        is KSType -> declaration
        is KSClassDeclaration -> this
        else -> error("Unexpected type for 'visibility' param $this")
    }
    return when (val visibility = declaration.simpleName.asString()) {
        "PUBLIC" -> Visibility.PUBLIC
        "INTERNAL" -> Visibility.INTERNAL
        else -> error("Unexpected value for 'visibility' param $visibility")
    }
}

fun KSType.toType(
    location: Location,
    resolver: Resolver,
    navTypeSerializersByType: Map<Importable, NavTypeSerializer>
): TypeInfo? {
    val qualifiedName = declaration.qualifiedName ?: return null

    val ksClassDeclaration = findActualClassDeclaration()
    val classDeclarationType = ksClassDeclaration?.asType(emptyList())

    val importable = Importable(
        ksClassDeclaration?.simpleName?.asString() ?: declaration.simpleName.asString(),
        ksClassDeclaration?.qualifiedName?.asString() ?: qualifiedName.asString()
    )

    val typeArgs: List<TypeArgument> = if (declaration is KSTypeAlias) {
        val aliasTypes = (declaration as KSTypeAlias).type.resolve()
            .argumentTypes((declaration as KSTypeAlias).location, resolver, navTypeSerializersByType)

        if (aliasTypes.any { it is TypeArgument.GenericType }) {
            val siteArgs = argumentTypes(location, resolver, navTypeSerializersByType).toMutableList()

            aliasTypes.toMutableList().apply {
                mapIndexed { idx, it ->
                    if (it is TypeArgument.GenericType) {
                        this[idx] = siteArgs.removeAt(0)
                    }
                }
            }
        } else {
            aliasTypes
        }
    } else {
        argumentTypes(location, resolver, navTypeSerializersByType)
    }

    return TypeInfo(
        value = Type(
            importable = importable,
            typeArguments = typeArgs,
            requireOptInAnnotations = ksClassDeclaration?.findAllRequireOptInAnnotations() ?: emptyList(),
            visibility = declaration.getVisibility(),
            isEnum = ksClassDeclaration?.classKind == KSPClassKind.ENUM_CLASS,
            isParcelable = classDeclarationType?.let { resolver.parcelableType().isAssignableFrom(it) } ?: false,
            isSerializable = classDeclarationType?.let { resolver.serializableType().isAssignableFrom(it) } ?: false,
            isKtxSerializable = isKtxSerializable(),
            valueClassInnerInfo = ksClassDeclaration?.valueClassInnerInfo(resolver, navTypeSerializersByType),
        ),
        isNullable = isMarkedNullable,
        hasCustomTypeSerializer = navTypeSerializersByType[importable] != null,
    )
}


fun KSType.toNavGraphParentInfo(
    errorLocationHint: String,
    annotationType: String
): NavGraphInfo? {
    val isNoParent = isNoParent()
    if (isNoParent) {
        return null
    }
    val isNavGraphAnnotation = isNavGraphAnnotation()
    val isNavHostGraphAnnotation = isNavHostGraphAnnotation()
    if (!isNavGraphAnnotation && !isNavHostGraphAnnotation) {
        throw IllegalDestinationsSetup("Type argument of annotation $annotationType needs to be \"ExternalModuleGraph\" or an annotation class which is itself annotated with \"@NavGraph\" or \"@NavHostGraph\".Check $errorLocationHint.")
    }

    return NavGraphInfo(
        isNavHostGraphAnnotation,
        Importable(
            declaration.simpleName.asString(),
            declaration.qualifiedName!!.asString()
        )
    )
}

private fun KSType.isNavGraphAnnotation(): Boolean {
    return declaration.annotations.any { annotationOfAnnotation ->
        annotationOfAnnotation.shortName.asString() == NAV_GRAPH_ANNOTATION
                && annotationOfAnnotation.annotationType.resolve().declaration.qualifiedName?.asString() == NAV_GRAPH_ANNOTATION_QUALIFIED
    }
}

private fun KSType.isNavHostGraphAnnotation(): Boolean {
    return declaration.annotations.any { annotationOfAnnotation ->
        annotationOfAnnotation.shortName.asString() == NAV_HOST_GRAPH_ANNOTATION
                && annotationOfAnnotation.annotationType.resolve().declaration.qualifiedName?.asString() == NAV_HOST_GRAPH_ANNOTATION_QUALIFIED
    }
}

private fun KSType.isNoParent(): Boolean {
    return declaration.qualifiedName?.asString() == "$CORE_PACKAGE_NAME.annotation.ExternalModuleGraph"
}

private fun KSType.argumentTypes(location: Location, resolver: Resolver, navTypeSerializersByType: Map<Importable, NavTypeSerializer>): List<TypeArgument> {
    return arguments.mapNotNull { typeArg ->
        if (typeArg.variance == Variance.STAR) {
            return@mapNotNull TypeArgument.Star
        }
        val resolvedType = typeArg.type?.resolve()

        if (resolvedType?.isError == true) {
            return@mapNotNull TypeArgument.Error(getErrorLines(location))
        }

        if (resolvedType?.declaration is KSTypeParameter) return@mapNotNull TypeArgument.GenericType

        resolvedType?.toType(location, resolver, navTypeSerializersByType)?.let { TypeArgument.Typed(it, typeArg.variance.label) }
    }
}

private fun getErrorLines(location: Location): String {
    val fileLocation = location as? FileLocation ?: return "NonExistentLocation"
    return File(fileLocation.filePath)
        .readLines(fileLocation.lineNumber, fileLocation.lineNumber + 10)
        .joinToString("\n")
}

private var _parcelableType: KSType? = null
private fun Resolver.parcelableType(): KSType {
    return _parcelableType ?: getClassDeclarationByName("android.os.Parcelable")!!.asType(emptyList()).also {
        _parcelableType = it
    }
}

private var _serializableType: KSType? = null
private fun Resolver.serializableType(): KSType {
    return _serializableType ?: getClassDeclarationByName("java.io.Serializable")!!.asType(emptyList()).also {
        _serializableType = it
    }
}


private fun KSType.isKtxSerializable(): Boolean {
    // Check current type annotations
    if (declaration.annotations.isKtxSerializable()) {
        return true
    }

    if (declaration is KSTypeAlias) {
        val typeAlias = declaration as KSTypeAlias
        // For alias check type alias annotations or annotations of reference type
        return typeAlias.type.annotations.isKtxSerializable() || typeAlias.type.resolve().isKtxSerializable()
    }

    return false
}

private fun Sequence<KSAnnotation>.isKtxSerializable(): Boolean =
    any {
        it.annotationType.resolve().declaration.qualifiedName?.asString()
            ?.let { qualifiedName ->
                qualifiedName == "kotlinx.serialization.Serializable"
            } ?: false
    }

private fun KSClassDeclaration.valueClassInnerInfo(resolver: Resolver, navTypeSerializersByType: Map<Importable, NavTypeSerializer>): ValueClassInnerInfo? {
    return if (modifiers.contains(Modifier.VALUE)) {
        // This is a value class, get the inner type's type only once (not recursively)
        val firstArg: KSValueParameter = primaryConstructor!!.parameters.first()
        val valueClassArgType = firstArg.type.resolve()

        val firstArgType = valueClassArgType.toType(valueClassArgType.declaration.location, resolver, navTypeSerializersByType)
        val firstPublicNonNullableArgName = this.getDeclaredProperties().firstOrNull {
            it.isPublic() && it.simpleName.asString() == firstArg.name?.asString() && !it.type.resolve().isMarkedNullable
        }?.simpleName?.asString()

        firstArgType?.let {
            ValueClassInnerInfo(it, primaryConstructor!!.isPublic(), firstPublicNonNullableArgName)
        }
    } else {
        null
    }
}