package com.cakcaraka.databinarycompatible.processor.visitor.utils

import com.cakcaraka.databinarycompatible.annotation.PropertyMutability
import com.cakcaraka.databinarycompatible.processor.DefaultValueConfig
import com.cakcaraka.databinarycompatible.processor.FieldName
import com.cakcaraka.databinarycompatible.processor.NewRequiredPropertyConfig
import com.cakcaraka.databinarycompatible.processor.PropertyConfig
import com.cakcaraka.databinarycompatible.processor.visitor.utils.ConstructorUtils.print
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

internal object ConstructorUtils {
    fun generateConstructorPerVersion(
        map: Map<String, PropertyConfig>
    ): List<ConstructorVersions> {
        val constructorVersions = map.values.map {
            it.mandatorySinceVersion ?: 1
        }.distinct().sortedDescending()

        val mapParams = map.filter {
            it.value.mutability != PropertyMutability.IMMUTABLE
        }

        return constructorVersions.mapIndexed { index, version ->
            ConstructorVersions(
                version = version,
                params = mapParams.filter {
                    it.value.defaultValueConfig == null || it.value.mandatorySinceVersion != null || it.value.mutability == PropertyMutability.IMMUTABLE_RECENTLY
                }.mapNotNull { (fieldName, config) ->
                    val mandatorySinceVersion = (config.mandatorySinceVersion ?: 1)
                    val nextVersion = (constructorVersions.getOrNull(index - 1))
                    if (mandatorySinceVersion == nextVersion) {
                        //use default value
                        ConstructorParams(
                            fieldName,
                            config.typeName,
                            ConstructorParamType.DefaultValue(config.defaultValue, config.optionalBuilderParam)
                        )
                    } else if (mandatorySinceVersion <= version) {
                        //use param if not immutable_recently
                        if (config.mutability == PropertyMutability.IMMUTABLE_RECENTLY) {
                            //use Param deprecated, use default value (hardcoded)
                            ConstructorParams(
                                fieldName,
                                config.typeName,
                                ConstructorParamType.ParamDeprecated(config.defaultValue, config.optionalBuilderParam)
                            )
                        } else {
                            ConstructorParams(
                                fieldName,
                                config.typeName,
                                ConstructorParamType.ParamValue(config.optionalBuilderParam)
                            )
                        }
                    } else {
                        //use don't use
                        null
                    }
                }
            )
        }.sortedBy {
            it.version
        }
    }

    fun List<ConstructorVersions>.print(printer: (String?) -> Unit) {
        printer(map {
            val constructor = "${it.version}." + it.params.filter {
                it.paramType.isOnParam
            }.joinToString(
                prefix = "(",
                postfix = ")",
                transform = { entry ->
                    entry.fieldName
                },
                separator = ","
            )

            val callSuper = it.params.joinToString(
                prefix = "(",
                postfix = ")",
                transform = { entry ->
                    val overloadValue = entry.paramType.overloadValue
                    if (overloadValue != null) {
                        "\"${overloadValue.hardcodedValue}\""
                    } else {
                        entry.fieldName
                    }
                },
                separator = ","
            )

            "$constructor: $callSuper"
        }.joinToString("\n"))

    }
}


internal data class ConstructorVersions(
    val version: Int,
    val params: List<ConstructorParams>
)

internal data class ConstructorParams(
    val fieldName: String,
    val typeName: TypeName,
    val paramType: ConstructorParamType,
)

internal sealed class ConstructorParamType {
    abstract val isOnParam: Boolean
    abstract val overloadValue: OverloadHarcodedValue?
    abstract val isOnOptionalBuilder: Boolean

    data class ParamValue(override val isOnOptionalBuilder: Boolean) : ConstructorParamType() {
        override val overloadValue: OverloadHarcodedValue? = null
        override val isOnParam: Boolean = true
    }

    data class ParamDeprecated(
        val hardcodedValue: String?,
        override val isOnOptionalBuilder: Boolean
    ) : ConstructorParamType() {
        override val overloadValue: OverloadHarcodedValue? = OverloadHarcodedValue(hardcodedValue)
        override val isOnParam: Boolean = true
    }

    data class DefaultValue(val defaultValue: String?, override val isOnOptionalBuilder: Boolean) :
        ConstructorParamType() {
        override val overloadValue: OverloadHarcodedValue? = OverloadHarcodedValue(defaultValue)
        override val isOnParam: Boolean = false
    }
}

@JvmInline
internal value class OverloadHarcodedValue(
    val hardcodedValue: String?
)

fun main() {
    val map: Map<String, PropertyConfig> = mutableMapOf(
        "field1" to PropertyConfig(
            typeName = ClassName("test", "test"),
            defaultValueConfig = null,
            hasOverridesModifier = false,
            kDoc = ""
        ),
        "field2" to PropertyConfig(
            typeName = ClassName("test", "test"),
            defaultValueConfig = DefaultValueConfig(
                fieldName = FieldName("field2"),
                defaultValue = "defaultField2",
                mutability = PropertyMutability.MUTABLE,
                excludeFromEqualsAndGetter = false,
                newRequiredPropertyConfig = NewRequiredPropertyConfig(
                    2,
                    previouslyOptional = false
                )
            ),
            hasOverridesModifier = false,
            kDoc = ""
        ),
        "field3" to PropertyConfig(
            typeName = ClassName("test", "test"),
            defaultValueConfig = DefaultValueConfig(
                fieldName = FieldName("field3"),
                defaultValue = "defaultField3",
                mutability = PropertyMutability.MUTABLE,
                excludeFromEqualsAndGetter = false,
                newRequiredPropertyConfig = NewRequiredPropertyConfig(
                    3,
                    previouslyOptional = false
                )
            ),
            hasOverridesModifier = false,
            kDoc = ""
        ),
        "field3.5" to PropertyConfig(
            typeName = ClassName("test", "test"),
            defaultValueConfig = DefaultValueConfig(
                fieldName = FieldName("field3.5"),
                defaultValue = "field3.5",
                mutability = PropertyMutability.IMMUTABLE,
                excludeFromEqualsAndGetter = false,
                newRequiredPropertyConfig = NewRequiredPropertyConfig(
                    3,
                    previouslyOptional = false
                )
            ),
            hasOverridesModifier = false,
            kDoc = ""
        ),
        "field4" to PropertyConfig(
            typeName = ClassName("test", "test"),
            defaultValueConfig = DefaultValueConfig(
                fieldName = FieldName("field4"),
                defaultValue = "defaultField4",
                mutability = PropertyMutability.IMMUTABLE_RECENTLY,
                excludeFromEqualsAndGetter = false,
                newRequiredPropertyConfig = NewRequiredPropertyConfig(
                    3,
                    previouslyOptional = false
                )
            ),
            hasOverridesModifier = false,
            kDoc = ""
        ),
        "field5" to PropertyConfig(
            typeName = ClassName("test", "test"),
            defaultValueConfig = DefaultValueConfig(
                fieldName = FieldName("field5"),
                defaultValue = "defaultField5",
                mutability = PropertyMutability.MUTABLE,
                excludeFromEqualsAndGetter = false,
                newRequiredPropertyConfig = NewRequiredPropertyConfig(
                    5,
                    previouslyOptional = false
                )
            ),
            hasOverridesModifier = false,
            kDoc = ""
        ),
    )

    val generatedMap = ConstructorUtils.generateConstructorPerVersion(map)
    generatedMap.print{
        println(it)
    }
}

