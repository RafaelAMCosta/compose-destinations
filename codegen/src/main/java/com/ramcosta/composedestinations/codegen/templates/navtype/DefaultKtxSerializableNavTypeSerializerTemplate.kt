package com.ramcosta.composedestinations.codegen.templates.navtype

import com.ramcosta.composedestinations.codegen.codeGenBasePackageName
import com.ramcosta.composedestinations.codegen.commons.CORE_PACKAGE_NAME
import com.ramcosta.composedestinations.codegen.templates.core.FileTemplate
import com.ramcosta.composedestinations.codegen.templates.core.setOfImportable

val defaultKtxSerializableNavTypeSerializerTemplatePkg =
    "$codeGenBasePackageName.navargs.ktxserializable"
const val DEFAULT_KTX_SERIALIZABLE_NAV_TYPE_SERIALIZER_TEMPLATE_NAME =
    "DefaultKtxSerializableNavTypeSerializer"

val defaultKtxSerializableNavTypeSerializerTemplate = FileTemplate(
    packageStatement = "package $defaultKtxSerializableNavTypeSerializerTemplatePkg",
    imports = setOfImportable(
        "$CORE_PACKAGE_NAME.navargs.DestinationsNavTypeSerializer",
        "com.ramcosta.composedestinations.navargs.utils.base64ToByteArray",
        "com.ramcosta.composedestinations.navargs.utils.toBase64Str",
        "kotlinx.serialization.ExperimentalSerializationApi",
        "kotlinx.serialization.KSerializer",
        "kotlinx.serialization.Serializable",
        "kotlinx.serialization.json.Json",
        "kotlinx.serialization.json.decodeFromStream",
        "kotlinx.serialization.json.encodeToStream",
        "java.io.ByteArrayInputStream",
        "java.io.ByteArrayOutputStream",
        "java.nio.charset.Charset",
    ),
    sourceCode = """
/**
 * Default [DestinationsNavTypeSerializer] for Kotlin [Serializable]s which converts them to a
 * Base64 string and then parses them back.
 *
 * This gets used by the generated code if you don't provide an explicit
 * [DestinationsNavTypeSerializer] annotated with `@NavTypeSerializer` for the type being
 * passed as navigation argument.
 */
@ExperimentalSerializationApi
public class $DEFAULT_KTX_SERIALIZABLE_NAV_TYPE_SERIALIZER_TEMPLATE_NAME<T : @Serializable Any>(
    private val serializer: KSerializer<T>,
) : DestinationsNavTypeSerializer<T> {

    override fun toRouteString(value: T): String {
        return toByteArray(value).toBase64Str()
    }

    override fun fromRouteString(routeStr: String): T {
        val bytes = routeStr.base64ToByteArray()
        return fromByteArray(bytes)
    }
    
    public fun toByteArray(value: T): ByteArray =
        ByteArrayOutputStream().use {
            Json.encodeToStream(serializer, value, it)
            it.toByteArray()
        }
    
    public fun fromByteArray(bytes: ByteArray): T =
        ByteArrayInputStream(bytes).use {
            Json.decodeFromStream(serializer, it)
        }
}

""".trimIndent()
)
