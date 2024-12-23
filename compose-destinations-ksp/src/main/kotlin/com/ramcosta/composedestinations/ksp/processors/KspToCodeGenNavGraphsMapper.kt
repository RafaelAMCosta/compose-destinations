package com.ramcosta.composedestinations.ksp.processors

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.ramcosta.composedestinations.codegen.commons.DESTINATION_ANNOTATION_DEEP_LINKS_ARGUMENT
import com.ramcosta.composedestinations.codegen.commons.DESTINATION_ANNOTATION_ROUTE_ARGUMENT
import com.ramcosta.composedestinations.codegen.commons.IllegalDestinationsSetup
import com.ramcosta.composedestinations.codegen.commons.NAV_GRAPH_ANNOTATION
import com.ramcosta.composedestinations.codegen.commons.NAV_GRAPH_ANNOTATION_DEFAULT_NAME
import com.ramcosta.composedestinations.codegen.commons.NAV_HOST_DEFAULT_START_ARGS
import com.ramcosta.composedestinations.codegen.commons.NAV_HOST_GRAPH_ANNOTATION
import com.ramcosta.composedestinations.codegen.commons.addRootDefaultStartArgs
import com.ramcosta.composedestinations.codegen.commons.rootNavGraphType
import com.ramcosta.composedestinations.codegen.model.ExternalRoute
import com.ramcosta.composedestinations.codegen.model.Importable
import com.ramcosta.composedestinations.codegen.model.NavTypeSerializer
import com.ramcosta.composedestinations.codegen.model.RawNavGraphGenParams
import com.ramcosta.composedestinations.ksp.commons.DestinationMappingUtils
import com.ramcosta.composedestinations.ksp.commons.MutableKSFileSourceMapper
import com.ramcosta.composedestinations.ksp.commons.findActualClassDeclaration
import com.ramcosta.composedestinations.ksp.commons.findAllRequireOptInAnnotations
import com.ramcosta.composedestinations.ksp.commons.findArgumentValue
import com.ramcosta.composedestinations.ksp.commons.getNavArgsDelegateType
import com.ramcosta.composedestinations.ksp.commons.isNothing
import com.ramcosta.composedestinations.ksp.commons.toDeepLink
import com.ramcosta.composedestinations.ksp.commons.toGenVisibility
import com.ramcosta.composedestinations.ksp.commons.toImportable
import com.ramcosta.composedestinations.ksp.commons.toNavGraphParentInfo
import com.ramcosta.composedestinations.ksp.commons.toType

internal class KspToCodeGenNavGraphsMapper(
    private val resolver: Resolver,
    private val destinationMappingUtils: DestinationMappingUtils,
    private val mutableKSFileSourceMapper: MutableKSFileSourceMapper,
    private val navTypeSerializersByType: Map<Importable, NavTypeSerializer>,
    private val navHostDefaultStartArgsByGraphAnnotationType: Map<Importable, List<Importable>>,
) {

    init {
        val defaultStartArgs = defaultStartArgs(rootNavGraphType, true)
        if (defaultStartArgs != null) {
            addRootDefaultStartArgs(defaultStartArgs)
        }
    }

    fun map(
        navGraphAnnotations: Sequence<KSClassDeclaration>,
        navHostGraphAnnotations: Sequence<KSClassDeclaration>
    ): List<RawNavGraphGenParams> {
        return navGraphAnnotations.map { it.mapToRawNavGraphGenParams(false) }.toList() +
                navHostGraphAnnotations.map { it.mapToRawNavGraphGenParams(true) }
    }

    private fun KSClassDeclaration.mapToRawNavGraphGenParams(isNavHostGraph: Boolean): RawNavGraphGenParams {
        if (classKind != KSPClassKind.ANNOTATION_CLASS) {
            throw IllegalDestinationsSetup("Classes annotated with `@NavGraph` must be annotation classes!")
        }

        val navGraphAnnotation = if (isNavHostGraph) {
            annotations.first {
                it.shortName.asString() == NAV_HOST_GRAPH_ANNOTATION
            }
        } else {
            annotations.first {
                it.shortName.asString() == NAV_GRAPH_ANNOTATION
            }
        }

        val navGraphAnnotationNameArg = navGraphAnnotation
            .findArgumentValue<String>(DESTINATION_ANNOTATION_ROUTE_ARGUMENT)
        val navGraphVisibility = navGraphAnnotation
            .findArgumentValue<Any>("visibility")!!
            .toGenVisibility()
        val navGraphDefaultTransitions = navGraphAnnotation
            .findArgumentValue<KSType>("defaultTransitions")
            ?.findActualClassDeclaration()
            ?.takeIf { !it.isNothing }
            ?.toImportable()

        val deepLinks = navGraphAnnotation
            .findArgumentValue<ArrayList<KSAnnotation>>(DESTINATION_ANNOTATION_DEEP_LINKS_ARGUMENT)
            ?.map { it.toDeepLink() }
            .orEmpty()

        val externalRoutes = declarations.firstOrNull {
            it is KSClassDeclaration && it.isCompanionObject
        }?.annotations?.flatMap {
            when (it.shortName.asString()) {
                "ExternalModuleDestinations" -> it.getExternalModuleDestinations()
                "ExternalDestination" -> listOf(it.getExternalDestination())
                "ExternalNavGraph" -> listOf(it.getExternalNavGraph())
                else -> emptyList()
            }
        }.orEmpty()

        val navArgs = navGraphAnnotation
            .getNavArgsDelegateType(resolver, navTypeSerializersByType)

        if (isNavHostGraph && navGraphDefaultTransitions == null) {
            throw IllegalDestinationsSetup(
                "A $NAV_HOST_GRAPH_ANNOTATION needs a non Nothing::class as defaultTransitions! " +
                        "Use `NoTransitions` if you wish to have no animations as default for this nav graph ${simpleName.asString()}"
            )
        }

        val parent = if (isNavHostGraph) {
            null
        } else {
            navGraphAnnotation.annotationType.resolve().arguments.firstOrNull()?.type?.resolve()?.let {
                it.toNavGraphParentInfo(
                    errorLocationHint = "NavGraph ${simpleName.asString()}",
                    annotationType = "@NavGraph"
                )?.graphType
            }
        }

        val isParentStart = if (parent != null) {
            navGraphAnnotation.findArgumentValue<Boolean>("start")!!
        } else {
            null
        }

        containingFile?.let { mutableKSFileSourceMapper[it.filePath] = containingFile }
        navArgs?.file?.let { mutableKSFileSourceMapper[it.filePath] = it }

        val annotationType = Importable(
            this.simpleName.asString(),
            this.qualifiedName!!.asString()
        )

        return RawNavGraphGenParams(
            sourceIds = listOfNotNull(containingFile?.filePath, navArgs?.file?.filePath),
            routeOverride = if (navGraphAnnotationNameArg == NAV_GRAPH_ANNOTATION_DEFAULT_NAME) null else navGraphAnnotationNameArg,
            isNavHostGraph = isNavHostGraph,
            defaultStartArgs = defaultStartArgs(annotationType, isNavHostGraph),
            defaultTransitions = navGraphDefaultTransitions,
            annotationType = annotationType,
            parent = parent,
            isParentStart = isParentStart,
            deepLinks = deepLinks,
            navArgs = navArgs?.type,
            visibility = navGraphVisibility,
            externalRoutes = externalRoutes.toList()
        )
    }

    private fun defaultStartArgs(
        annotationType: Importable,
        isNavHostGraph: Boolean
    ): Importable? {
        val defaultStartArgsImportables =
            navHostDefaultStartArgsByGraphAnnotationType[annotationType].orEmpty()

        return if (isNavHostGraph) {
            if (defaultStartArgsImportables.size > 1) {
                throw IllegalDestinationsSetup("You defined multiple '$NAV_HOST_DEFAULT_START_ARGS' for graph '${annotationType.preferredSimpleName}'. Only one is allowed!")
            }

            defaultStartArgsImportables.firstOrNull()
        } else {
            if (defaultStartArgsImportables.isNotEmpty()) {
                throw IllegalDestinationsSetup("You defined a '$NAV_HOST_DEFAULT_START_ARGS' for graph '${annotationType.preferredSimpleName}' which is not a NavHost graph.")
            }
            null
        }
    }

    private fun KSAnnotation.getExternalModuleDestinations(): List<ExternalRoute.Destination> {
        val genModuleDestinationsType = this.annotationType.resolve().arguments.first().type!!.resolve()

        val destinationTypes: List<KSType> = (genModuleDestinationsType.declaration as KSClassDeclaration).declarations
            .first { it.simpleName.asString() == "Includes" }
            .annotations.first { it.shortName.asString() == "GeneratedCodeExternalDestinations" }
            .findArgumentValue<ArrayList<KSType>>("destinations")!!

        val overridingByDestinationType: Map<KSType, KSAnnotation> = findArgumentValue<ArrayList<KSAnnotation>>("overriding")!!
            .associate {
                it.findArgumentValue<KSType>("destination")!! to it.findArgumentValue<KSAnnotation>("with")!!
            }

        val overridingMissingDestinations = overridingByDestinationType.keys.filter { it !in destinationTypes }
        if (overridingMissingDestinations.isNotEmpty()) {
            throw IllegalDestinationsSetup(
                "Trying to override specifics of $overridingMissingDestinations destinations which are not part of ${genModuleDestinationsType.declaration.simpleName.asString()}!"
            )
        }

        return destinationTypes.map { destinationType ->
            externalDestinationFrom(destinationType, overridingByDestinationType[destinationType])
        }
    }

    private fun KSAnnotation.getExternalDestination(): ExternalRoute.Destination {
        val destinationType = this.annotationType.resolve().arguments.first().type!!.resolve()
        return externalDestinationFrom(destinationType, this)
    }

    private fun externalDestinationFrom(destinationType: KSType, overridingStuffAnnotation: KSAnnotation?): ExternalRoute.Destination {
        val importable = Importable(
            destinationType.declaration.simpleName.asString(),
            destinationType.declaration.qualifiedName?.asString() ?: throw IllegalDestinationsSetup("Check ${destinationType.declaration.location} for unresolved symbols.")
        )

        val superType = (destinationType.declaration as KSClassDeclaration).superTypes.take(2).last().resolve()
        val navArgs = if (superType.declaration.simpleName.asString() == "TypedDestinationSpec") {
            superType.arguments.first().type!!.resolve()
                .getNavArgsDelegateType(resolver, navTypeSerializersByType)?.type
        } else {
            null
        }

        val deepLinks = overridingStuffAnnotation?.findArgumentValue<ArrayList<KSAnnotation>>(DESTINATION_ANNOTATION_DEEP_LINKS_ARGUMENT)
            ?.map { it.toDeepLink() }
            .orEmpty()

        return ExternalRoute.Destination(
            superType = superType.toType(superType.declaration.location, resolver, navTypeSerializersByType)!!,
            isStart = overridingStuffAnnotation?.findArgumentValue<Boolean>("start") ?: false,
            generatedType = importable,
            navArgs = navArgs,
            requireOptInAnnotationTypes = destinationType.declaration.findAllRequireOptInAnnotations(),
            additionalDeepLinks = deepLinks,
            overriddenDestinationStyleType = overridingStuffAnnotation?.let { destinationMappingUtils.getDestinationStyleType(overridingStuffAnnotation, "@ExternalDestination of ${importable.preferredSimpleName}", allowNothing = true) },
            additionalComposableWrappers = overridingStuffAnnotation?.let { destinationMappingUtils.getDestinationWrappers(overridingStuffAnnotation)!! }.orEmpty(),
        )
    }

    private fun KSAnnotation.getExternalNavGraph(): ExternalRoute.NavGraph {
        val graphType = this.annotationType.resolve().arguments.first().type!!.resolve()
        val importable = graphType.let {
            Importable(
                it.declaration.simpleName.asString(),
                it.declaration.qualifiedName?.asString() ?: throw IllegalDestinationsSetup(
                    "${it.declaration.simpleName.asString()} symbol, check ${this.shortName.asString()}"
                )
            )
        }

        val superType =
            // these are generated files, a little optimization here is to take the second type right away
            // the first will always be BaseRoute, and the second is the one we want since it contains the types needed
            (graphType.declaration as KSClassDeclaration).superTypes.take(2).last().resolve()

        val navArgs =
            if (superType.declaration.simpleName.asString() == "TypedNavGraphSpec") {
                superType.arguments.first().type!!.resolve()
                    .getNavArgsDelegateType(resolver, navTypeSerializersByType)?.type
            } else {
                null
            }

        val deepLinks = findArgumentValue<ArrayList<KSAnnotation>>(DESTINATION_ANNOTATION_DEEP_LINKS_ARGUMENT)
            ?.map { it.toDeepLink() }
            .orEmpty()

        val navGraphDefaultTransitions = findArgumentValue<KSType>("defaultTransitions")
            ?.findActualClassDeclaration()
            ?.takeIf { !it.isNothing }
            ?.toImportable()

        return ExternalRoute.NavGraph(
            superType = superType.toType(location, resolver, navTypeSerializersByType)!!,
            isStart = findArgumentValue<Boolean>("start")!!,
            generatedType = importable,
            navArgs = navArgs,
            requireOptInAnnotationTypes = graphType.declaration.findAllRequireOptInAnnotations(),
            additionalDeepLinks = deepLinks,
            overriddenDefaultTransitions = if (navGraphDefaultTransitions?.qualifiedName == "com.ramcosta.composedestinations.annotation.ExternalNavGraph.Companion.NoOverride") {
                ExternalRoute.NavGraph.OverrideDefaultTransitions.NoOverride
            } else {
                ExternalRoute.NavGraph.OverrideDefaultTransitions.Override(navGraphDefaultTransitions)
            }
        )
    }
}
