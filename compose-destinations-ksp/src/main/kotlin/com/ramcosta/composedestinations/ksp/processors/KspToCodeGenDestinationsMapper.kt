package com.ramcosta.composedestinations.ksp.processors

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.ramcosta.composedestinations.codegen.commons.ACTIVITY_DESTINATION_ANNOTATION
import com.ramcosta.composedestinations.codegen.commons.ACTIVITY_DESTINATION_ANNOTATION_DEFAULT_NULL
import com.ramcosta.composedestinations.codegen.commons.DESTINATION_ANNOTATION_DEEP_LINKS_ARGUMENT
import com.ramcosta.composedestinations.codegen.commons.DESTINATION_ANNOTATION_DEFAULT_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.codegen.commons.DESTINATION_ANNOTATION_ROUTE_ARGUMENT
import com.ramcosta.composedestinations.codegen.commons.IllegalDestinationsSetup
import com.ramcosta.composedestinations.codegen.commons.JAVA_ACTIVITY_DESTINATION_ANNOTATION
import com.ramcosta.composedestinations.codegen.model.ActivityDestinationParams
import com.ramcosta.composedestinations.codegen.model.DestinationStyleType
import com.ramcosta.composedestinations.codegen.model.Importable
import com.ramcosta.composedestinations.codegen.model.NavGraphInfo
import com.ramcosta.composedestinations.codegen.model.NavTypeSerializer
import com.ramcosta.composedestinations.codegen.model.RawDestinationGenParams
import com.ramcosta.composedestinations.codegen.model.Visibility
import com.ramcosta.composedestinations.ksp.commons.DestinationMappingUtils
import com.ramcosta.composedestinations.ksp.commons.MutableKSFileSourceMapper
import com.ramcosta.composedestinations.ksp.commons.findAllRequireOptInAnnotations
import com.ramcosta.composedestinations.ksp.commons.findAnnotationPathRecursively
import com.ramcosta.composedestinations.ksp.commons.findArgumentValue
import com.ramcosta.composedestinations.ksp.commons.getNavArgsDelegateType
import com.ramcosta.composedestinations.ksp.commons.isNothing
import com.ramcosta.composedestinations.ksp.commons.toDeepLink
import com.ramcosta.composedestinations.ksp.commons.toGenVisibility
import com.ramcosta.composedestinations.ksp.commons.toNavGraphParentInfo
import com.ramcosta.composedestinations.ksp.commons.toParameter
import com.ramcosta.composedestinations.ksp.commons.toType

internal class KspToCodeGenDestinationsMapper(
    private val resolver: Resolver,
    private val destinationMappingUtils: DestinationMappingUtils,
    private val sourceFileMapper: MutableKSFileSourceMapper,
    private val navTypeSerializersByType: Map<Importable, NavTypeSerializer>,
) {

    fun map(
        composableDestinationPaths: Sequence<DestinationAnnotationsPath>,
        activityDestinations: List<KSClassDeclaration> //TODO RACOSTA do we need to have a path as well for activity? 🤔
    ): List<RawDestinationGenParams> {
        val composableDestinations = composableDestinationPaths.groupBy { it.function.qualifiedName?.asString() }
            .values.flatMap { destinationsWithSameFunction ->
                destinationsWithSameFunction.map {
                    it.toDestination(destinationsWithSameFunction.size > 1)
                }
            }
        return composableDestinations + activityDestinations.map { it.toActivityDestination() }.toList()
    }

    private fun DestinationAnnotationsPath.toDestination(hasMultipleDestinations: Boolean): RawDestinationGenParams {
        val composableName = function.simpleName.asString()

        val deepLinksAnnotations = annotations.findCumulativeArgumentValue {
            findArgumentValue<ArrayList<KSAnnotation>>(DESTINATION_ANNOTATION_DEEP_LINKS_ARGUMENT)
        }

        val isStart = annotations.findOverridingArgumentValue { findArgumentValue<Boolean>("start") }!!
        val navGraphInfo = annotations.getNavGraphInfo("Composable '$composableName'")
        val route = annotations.findOverridingArgumentValue { findArgumentValue<String>(DESTINATION_ANNOTATION_ROUTE_ARGUMENT) }!!

        val navArgsDelegateTypeAndFile = annotations.findOverridingArgumentValue { getNavArgsDelegateType(resolver, navTypeSerializersByType) }
        if (navArgsDelegateTypeAndFile?.file != null) {
            sourceFileMapper[navArgsDelegateTypeAndFile.file.filePath] = navArgsDelegateTypeAndFile.file
        }
        sourceFileMapper[function.containingFile!!.filePath] = function.containingFile

        return RawDestinationGenParams(
            sourceIds = listOfNotNull(function.containingFile!!.filePath, navArgsDelegateTypeAndFile?.file?.filePath),
            isParentStart = isStart,
            annotatedName = composableName,
            annotatedQualifiedName = function.qualifiedName!!.asString(),
            visibility = annotations.findOverridingArgumentValue { getDestinationVisibility() }!!,
            routeOverride = route.takeIf { it != DESTINATION_ANNOTATION_DEFAULT_ROUTE_PLACEHOLDER },
            hasMultipleDestinations = hasMultipleDestinations,
            destinationStyleType = annotations.findOverridingArgumentValue { destinationMappingUtils.getDestinationStyleType(this, "composable $composableName") }!!,
            parameters = function.parameters.map { it.toParameter(resolver, navTypeSerializersByType) },
            composableWrappers = annotations.findCumulativeArgumentValue { destinationMappingUtils.getDestinationWrappers(this) },
            deepLinks = deepLinksAnnotations.map { it.toDeepLink() },
            navGraphInfo = navGraphInfo,
            composableReceiverType = function.extensionReceiver?.resolve()?.toType(function.extensionReceiver!!.location, resolver, navTypeSerializersByType),
            requireOptInAnnotationTypes = function.findAllRequireOptInAnnotations(),
            destinationNavArgsClass = navArgsDelegateTypeAndFile?.type,
        )
    }

    private fun List<KSAnnotation>.getNavGraphInfo(
        errorLocationHint: String
    ): NavGraphInfo? {
        return findOverridingArgumentValue {
            if (shortName.asString() == JAVA_ACTIVITY_DESTINATION_ANNOTATION) {
                findArgumentValue<KSType>("navGraph")
            } else {
                annotationType.resolve().arguments.firstOrNull()?.type?.resolve()
            }
        }!!.toNavGraphParentInfo(
            errorLocationHint = errorLocationHint,
            annotationType = "@Destination"
        )
    }

    private fun KSClassDeclaration.toActivityDestination(): RawDestinationGenParams {
        val activityDestinationAnnotations = findAnnotationPathRecursively(listOf(ACTIVITY_DESTINATION_ANNOTATION, JAVA_ACTIVITY_DESTINATION_ANNOTATION))!!.reversed()
        val deepLinksAnnotations = activityDestinationAnnotations.findCumulativeArgumentValue { findArgumentValue<ArrayList<KSAnnotation>>(DESTINATION_ANNOTATION_DEEP_LINKS_ARGUMENT) }
        val explicitActivityClass = activityDestinationAnnotations.findOverridingArgumentValue { findArgumentValue<KSType>("activityClass") }!!
            .declaration as KSClassDeclaration

        val isActivityClass = activityType.isAssignableFrom(this.asType(emptyList()))

        val finalActivityClass = getFinalActivityClass(isActivityClass, explicitActivityClass)

        val navArgsDelegateTypeAndFile =
            activityDestinationAnnotations.findOverridingArgumentValue { getNavArgsDelegateType(resolver, navTypeSerializersByType) }
        if (navArgsDelegateTypeAndFile?.file != null) {
            sourceFileMapper[navArgsDelegateTypeAndFile.file.filePath] = navArgsDelegateTypeAndFile.file
        }
        sourceFileMapper[containingFile!!.filePath] = containingFile

        val isStart = activityDestinationAnnotations.findOverridingArgumentValue { findArgumentValue<Boolean>("start") }!!

        val route = activityDestinationAnnotations.findOverridingArgumentValue { findArgumentValue<String>(DESTINATION_ANNOTATION_ROUTE_ARGUMENT) }!!

        return RawDestinationGenParams(
            sourceIds = listOf(containingFile!!.filePath),
            annotatedName = finalActivityClass.simpleName,
            annotatedQualifiedName = finalActivityClass.qualifiedName,
            visibility = activityDestinationAnnotations.findOverridingArgumentValue { getDestinationVisibility() }!!,
            parameters = emptyList(),
            deepLinks = deepLinksAnnotations.map { it.toDeepLink() },
            navGraphInfo = activityDestinationAnnotations.getNavGraphInfo("Activity '${simpleName.asString()}'"),
            destinationStyleType = DestinationStyleType.Activity,
            composableReceiverType = null,
            requireOptInAnnotationTypes = emptyList(),
            destinationNavArgsClass = navArgsDelegateTypeAndFile?.type,
            activityDestinationParams = ActivityDestinationParams(
                targetPackage = activityDestinationAnnotations.findOverridingArgumentValue { getNullableString("targetPackage") },
                action = activityDestinationAnnotations.findOverridingArgumentValue { getNullableString("action") },
                dataUri = activityDestinationAnnotations.findOverridingArgumentValue { getNullableString("dataUri") },
                dataPattern = activityDestinationAnnotations.findOverridingArgumentValue { getNullableString("dataPattern") }
            ),
            composableWrappers = emptyList(),
            isParentStart = isStart,
            routeOverride = route.takeIf { it != DESTINATION_ANNOTATION_DEFAULT_ROUTE_PLACEHOLDER },
            hasMultipleDestinations = false
        )
    }

    private fun KSAnnotation.getNullableString(name: String): String? {
        return findArgumentValue<String>(name).takeIf {
            it != ACTIVITY_DESTINATION_ANNOTATION_DEFAULT_NULL
        }
    }

    private fun KSClassDeclaration.getFinalActivityClass(
        isActivityClass: Boolean,
        explicitActivityClass: KSClassDeclaration
    ) = if (isActivityClass) {
        if (!explicitActivityClass.isNothing) {
            throw IllegalDestinationsSetup("When annotating Activity classes with \"@$ACTIVITY_DESTINATION_ANNOTATION\", you must not specify an \"activityClass\" in the annotation! (check ${this.simpleName.asString()})")
        }

        Importable(
            simpleName.asString(),
            qualifiedName!!.asString()
        )
    } else {
        if (explicitActivityClass.isNothing) {
            throw IllegalDestinationsSetup("When annotating non-Activity classes with \"@$ACTIVITY_DESTINATION_ANNOTATION\", you need to specify an \"activityClass\" in the annotation!")
        }

        Importable(
            explicitActivityClass.simpleName.asString(),
            explicitActivityClass.qualifiedName!!.asString()
        )
    }

    private inline fun <reified T: Any> List<KSAnnotation>.findOverridingArgumentValue(findArg: KSAnnotation.() -> T?): T? {
        var lastFound: T? = null

        forEach {
            findArg(it)?.let { foundArg ->
                lastFound = foundArg
            }
        }

        return lastFound
    }


    private inline fun <reified T> List<KSAnnotation>.findCumulativeArgumentValue(findArg: KSAnnotation.() -> List<T>?): List<T> {
        var cumulative: ArrayList<T>? = null

        forEach {
            findArg(it)?.let {
                cumulative = (cumulative ?: ArrayList()).apply { addAll(it) }
            }
        }

        return cumulative!!
    }

    private fun KSAnnotation.getDestinationVisibility(): Visibility? {
        return findArgumentValue<Any>("visibility")?.toGenVisibility()
    }

    private val activityType by lazy {
        resolver.getClassDeclarationByName("android.app.Activity")!!.asType(emptyList())
    }
}
