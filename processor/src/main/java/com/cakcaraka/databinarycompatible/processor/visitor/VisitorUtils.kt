package com.cakcaraka.databinarycompatible.processor.visitor

import com.cakcaraka.databinarycompatible.annotation.PropertyMutability
import com.cakcaraka.databinarycompatible.processor.FieldName
import com.cakcaraka.databinarycompatible.processor.PropertyConfig
import com.cakcaraka.databinarycompatible.processor.visitor.BaseVisitor.ConstructingMechanism
import com.cakcaraka.databinarycompatible.processor.visitor.ConstructorType.*
import com.cakcaraka.databinarycompatible.processor.visitor.utils.ConstructorParamType
import com.cakcaraka.databinarycompatible.processor.visitor.utils.ConstructorUtils
import com.cakcaraka.databinarycompatible.processor.visitor.utils.ConstructorUtils.print
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import java.util.Locale

object VisitorUtils {

    /**
     * Generate Primary Constructor
     * Generate copy method
     * Generate dsl/secondary constructor based on constructingMechanism
     */
    internal fun generateConstructingFunctions(
        logger: KSPLogger,
        classNameObject: ClassName,
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>,
        constructingMechanism: ConstructingMechanism?
    ): ConstructingFunctions? {
        if (constructingMechanism == null) {
            return null
        }

        // Builder
        val builderSpec = generateBuilderTypeSpec(classNameObject, propertyMap)

        // Additional Constructors
        val additionalConstructors = generateAdditionalConstructors(
            logger, classNameObject, propertyMap, constructingMechanism
        )

        // Primary Constructor
        val primaryConstructorBuilder = generatePrimaryConstructor(propertyMap)

        // Copy Method
        val copyMethodBuilder = generateCopyMethod(classNameObject, propertyMap)

        val dslInisalizer = if (constructingMechanism == ConstructingMechanism.DSL) {
            generateInitializer(classNameObject, propertyMap)
        } else {
            null
        }

        return ConstructingFunctions(
            primaryConstructor = primaryConstructorBuilder,
            builderSpec = builderSpec,
            copyMethod = copyMethodBuilder,
            initializer = dslInisalizer,
            additionalConstructors = additionalConstructors
        )
    }

    private fun generateAdditionalConstructors(
        logger: KSPLogger,
        classNameObject: ClassName,
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>,
        constructingMechanism: ConstructingMechanism
    ): List<FunSpec.Builder> {
        val additionalConstructors = mutableListOf<FunSpec.Builder>()


        if (constructingMechanism == ConstructingMechanism.SecondaryConstructor) {
            val constructorData = ConstructorUtils.generateConstructorPerVersion(
                propertyMap.mapKeys { it.key.toString() }
            )

            logger.warn("constructor for $classNameObject")
            constructorData.print {
                logger.warn(it.toString())
            }

            constructorData.forEachIndexed { index, constructorVersion ->

                val mandatoryParams = constructorVersion.params

                //constructor with default params
                if (mandatoryParams.isNotEmpty()) {
                    val hasNonMandatoryParams = propertyMap.any {
                        it.value.optionalBuilderParam
                    }

                    if (hasNonMandatoryParams.not()) {
                        //if all mandatory, primary constructor needs marker
                        //generate secondary constructor with mandatory data
                        //no need generate secondary constructor with mandatory data and builder
                        listOf(NO_OPTIONAL_BUILDER)
                    } else {
                        //generate secondary constructor with mandatory data
                        //generate secondary constructor with mandatory data and builder
                        listOf(
                            OPTIONAL_BUILDER_DEFAULT_VALUE,
                            WITH_OPTIONAL_BUILDER
                        )
                    }.forEach { initializerFlag ->
                        val classConstructorWithParamBuilder = FunSpec.constructorBuilder()
                        classConstructorWithParamBuilder.addModifiers(KModifier.PUBLIC)

                        mandatoryParams.forEach {
                            if (it.paramType.isOnParam) {
                                classConstructorWithParamBuilder.addParameter(
                                    it.fieldName, it.typeName
                                )
                            }
                        }

                        val deprecatedParams = mandatoryParams.filter {
                            it.paramType is ConstructorParamType.ParamDeprecated
                        }

                        if (constructorData.size > 1) {
                            classConstructorWithParamBuilder.addKdoc("Constructor for v${constructorVersion.version}\n")
                        }

                        if (deprecatedParams.isNotEmpty()) {
                            classConstructorWithParamBuilder.addKdoc(
                                """
                                    |"Param(s) [${
                                    deprecatedParams.joinToString(
                                        ",",
                                        transform = { it.fieldName })
                                }] marked with [com.cakcaraka.databinarycompatible.annotation.PropertyMutability.IMMUTABLE_RECENTLY]
                            |Value will be ignored and replaced with it's default value
                                    """.trimMargin()
                            )
                        }
                        if (initializerFlag == WITH_OPTIONAL_BUILDER) {
                            classConstructorWithParamBuilder.addParameter(
                                ParameterSpec.builder(
                                    "optionalPropertiesBuilder", LambdaTypeName.get(
                                        classNameObject.nestedClass("OptionalPropertiesBuilder"),
                                        emptyList(),
                                        ClassName("kotlin", "Unit")
                                    )
                                ).build()
                            )
                        }

                        val lastConstructor = index == (constructorData.size - 1)

                        // call BuilderImpl() or this()
                        val prefixSuffix = if (lastConstructor) "${indent()}BuilderImpl(\n" else ""
                        val prefix = "\n" + when (initializerFlag) {
                            OPTIONAL_BUILDER_DEFAULT_VALUE -> ""
                            NO_OPTIONAL_BUILDER -> "${indent()}\n$prefixSuffix"
                            WITH_OPTIONAL_BUILDER -> "${indent()}$prefixSuffix"
                        }

                        val postfix = when (initializerFlag) {
                            NO_OPTIONAL_BUILDER -> "\n${indent()})\n"
                            OPTIONAL_BUILDER_DEFAULT_VALUE -> ",\n${indent(2)}{ }\n"
                            WITH_OPTIONAL_BUILDER -> if (lastConstructor) {
                                ",\n${indent()}).apply(optionalPropertiesBuilder)\n"
                            } else {
                                ",\n${indent(2)}optionalPropertiesBuilder\n"
                            }
                        }

                        classConstructorWithParamBuilder.callThisConstructor(
                            mandatoryParams.filter {
                                if (lastConstructor || initializerFlag != OPTIONAL_BUILDER_DEFAULT_VALUE) {
                                    true
                                } else {
                                    it.paramType.isOnParam
                                }
                            }.map {
                                when (val type = it.paramType) {
                                    is ConstructorParamType.ParamDeprecated -> if (initializerFlag != OPTIONAL_BUILDER_DEFAULT_VALUE && lastConstructor) {
                                        type.hardcodedValue
                                    } else {
                                        it.fieldName
                                    }

                                    is ConstructorParamType.DefaultValue -> type.defaultValue
                                    is ConstructorParamType.ParamValue -> it.fieldName
                                }
                            }.joinToString(
                                prefix = prefix,
                                transform = {
                                    "${indent(2)}${it}"
                                },
                                separator = ",\n",
                                postfix = postfix
                            )
                        )
                        additionalConstructors.add(
                            classConstructorWithParamBuilder
                        )
                    }
                } else {
                    //have no mandatory param
                    //generate empty constructor
                    //generate constructor with optionalBuilder
                    val classConstructorWithParamBuilder = FunSpec.constructorBuilder()
                    classConstructorWithParamBuilder.addModifiers(KModifier.PUBLIC)
                    classConstructorWithParamBuilder.addParameter(
                        ParameterSpec.builder(
                            "optionalPropertiesBuilder", LambdaTypeName.get(
                                classNameObject.nestedClass("OptionalPropertiesBuilder"),
                                emptyList(),
                                ClassName("kotlin", "Unit")
                            )
                        ).build()
                    )

                    classConstructorWithParamBuilder.callThisConstructor(
                        "BuilderImpl().apply(optionalPropertiesBuilder)\n"
                    )
                    additionalConstructors.add(
                        classConstructorWithParamBuilder
                    )
                }
            }
        }

        val mandatoryImmutableKeepSetterData: MutableMap<FieldName, String> = mutableMapOf()
        val immutableDefaultValueData: MutableMap<FieldName, String> = mutableMapOf()
        propertyMap.forEach {
            val fieldName = FieldName(it.key.toString())
            val defaultValue = it.value.defaultValueConfig
            when (defaultValue?.mutability) {
                PropertyMutability.IMMUTABLE -> {
                    immutableDefaultValueData[fieldName] = defaultValue.defaultValue.orEmpty()
                }

                PropertyMutability.IMMUTABLE_RECENTLY -> {
                    mandatoryImmutableKeepSetterData[fieldName] =
                        defaultValue.defaultValue.orEmpty()
                }

                PropertyMutability.MUTABLE, null -> {

                }
            }
        }

        //constructor that receives builder
        val classConstructorWithBuilder = FunSpec.constructorBuilder()
        classConstructorWithBuilder.addModifiers(KModifier.PRIVATE)
        classConstructorWithBuilder.addParameter(
            "builder", classNameObject.nestedClass("BuilderImpl")
        )

        val kdocs = mutableListOf<String>()
        if (mandatoryImmutableKeepSetterData.isNotEmpty()) {
            kdocs.add(
                "Builder's param(s) [${
                    (mandatoryImmutableKeepSetterData.keys).joinToString(
                        ",",
                        transform = { it.fieldName })
                }] "
            )
            kdocs.add("marked with [com.cakcaraka.databinarycompatible.annotation.PropertyMutability.IMMUTABLE_RECENTLY]\n")
            kdocs.add("Value will be ignored and replaced with it's default value\n")
        }
        if (immutableDefaultValueData.isNotEmpty()) {
            if (kdocs.isNotEmpty()) {
                kdocs.add("\n")
            }
            kdocs.add(
                "Builder's param(s) [${
                    immutableDefaultValueData.keys.joinToString(
                        ",",
                        transform = { it.fieldName })
                }] "
            )
            kdocs.add("marked with [com.cakcaraka.databinarycompatible.annotation.PropertyMutability.IMMUTABLE]\n")
            kdocs.add("Will use default value\n")
        }
        classConstructorWithBuilder.addKdoc(
            CodeBlock.builder().apply
            {
                kdocs.forEach {
                    add(it)
                }
            }.build()
        )
        classConstructorWithBuilder.callThisConstructor(
            propertyMap.keys.map
            {
                val fieldName = FieldName(it.toString())
                if (mandatoryImmutableKeepSetterData[fieldName] != null) {
                    mandatoryImmutableKeepSetterData[fieldName]
                } else if (immutableDefaultValueData[fieldName] != null) {
                    immutableDefaultValueData[fieldName]
                } else {
                    "builder.$it"
                }
            }.plus(
                "null"
            ).joinToString(
                prefix = "\n",
                transform =
                { "${indent()}$it" },
                separator = ",\n",
                postfix = "\n"
            )
        )

        additionalConstructors.add(0, classConstructorWithBuilder)

        return additionalConstructors
    }

    private fun generateBuilderTypeSpec(
        classNameObject: ClassName,
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>
    ): Pair<List<TypeSpec.Builder>, FunSpec.Builder?> {
        // Builder pattern
        val optionalBuilderClassName = classNameObject.nestedClass("OptionalPropertiesBuilder")
        val optionalBuilderBuilder = TypeSpec.interfaceBuilder(optionalBuilderClassName.simpleName)
        injectBuilderMethod(
            optionalBuilderBuilder,
            classNameObject,
            optionalBuilderClassName,
            withImplementation = false,
            withSetterMethodImplementation = false,
            withKDocs = { false },
            propertyMap = propertyMap.filter {
                it.value.optionalBuilderParam
            },
            propertyAnnotations = {
                listOf(KModifier.ABSTRACT)
            }
        )

        val builderClassName = classNameObject.nestedClass("Builder")
        val builderBuilder = TypeSpec.interfaceBuilder(builderClassName.simpleName)
        builderBuilder.addSuperinterface(optionalBuilderClassName)
        injectBuilderMethod(
            builderBuilder,
            classNameObject,
            builderClassName,
            withImplementation = false,
            withSetterMethodImplementation = false,
            withKDocs = { true },
            propertyMap = propertyMap.filterNot {
                it.value.mutability == PropertyMutability.IMMUTABLE
            },
            propertyAnnotations = {
                if (it.optionalBuilderParam.not()) {
                    listOf(KModifier.ABSTRACT)
                } else {
                    listOf(KModifier.OVERRIDE, KModifier.ABSTRACT)
                }
            }
        )

        val builderClassImplName = classNameObject.nestedClass("BuilderImpl")
        val builderBuilderImpl = TypeSpec.classBuilder(builderClassImplName.simpleName)
        builderBuilderImpl.addSuperinterface(builderClassName)
        builderBuilderImpl.addModifiers(KModifier.PRIVATE)
        injectBuilderMethod(
            builderBuilderImpl,
            classNameObject,
            builderClassImplName,
            withImplementation = true,
            withSetterMethodImplementation = true,
            withKDocs = { false },
            propertyMap = propertyMap.filterNot {
                it.value.mutability == PropertyMutability.IMMUTABLE
            },
            propertyAnnotations = {
                listOf(KModifier.OVERRIDE)
            }
        )


        val hasNoMandatoryParam = propertyMap.none {
            it.value.mandatoryForBuilderConstructor
        }

        val classConstructorWithParamBuilder = if (hasNoMandatoryParam) {
            val paramBuilder = FunSpec.constructorBuilder()
            paramBuilder.addModifiers(KModifier.PUBLIC)
            paramBuilder.callThisConstructor(
                "BuilderImpl()"
            )
        } else {
            null
        }

        return Pair(
            listOf(
                optionalBuilderBuilder,
                builderBuilder,
                builderBuilderImpl
            ),
            classConstructorWithParamBuilder
        )
    }


    private fun injectBuilderMethod(
        builderBuilder: TypeSpec.Builder,
        classNameObject: ClassName,
        builderClassNameObject: ClassName,
        withImplementation: Boolean,
        withSetterMethodImplementation: Boolean,
        withKDocs: (PropertyConfig) -> Boolean,
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>,
        propertyAnnotations: (PropertyConfig) -> List<KModifier>,
    ) {
        val constructorBuilder = FunSpec.constructorBuilder()
        val className = builderClassNameObject.simpleName
        for (property in propertyMap) {
            val propertyName = property.key.toString()
            // when no default value provided but property is non nullable -
            // it should be moved to Builder mandatory ctor arguments
            if (property.value.mandatoryForBuilderConstructor) {
                constructorBuilder.addParameter(
                    propertyName,
                    property.value.typeName,
                )
                builderBuilder.addProperty(
                    PropertySpec.builder(propertyName, property.value.typeName)
                        .apply {
                            if (withImplementation) {
                                initializer(propertyName)
                            } else {
                                addAnnotation(
                                    AnnotationSpec.builder(JvmSynthetic::class)
                                        .useSiteTarget(AnnotationSpec.UseSiteTarget.SET).build()
                                )
                            }
                            addModifiers(propertyAnnotations(property.value))
                        }
                        .mutable(true)
                        .build()
                )
            } else {
                builderBuilder.addProperty(
                    PropertySpec.builder(
                        propertyName,
                        property.value.typeName
                    ).apply {
                        if (withKDocs(property.value)) {
                            addKdoc(
                                """
                                    |${property.value.kDoc}
                                    """.trimMargin()
                            )
                        }
                        if (withImplementation) {
                            initializer(
                                CodeBlock.builder().add(
                                    property.value.defaultValue ?: "null"
                                ).build()
                            )
                        } else if (property.value.isAnnotatedMutable) {
                            addAnnotation(
                                AnnotationSpec.builder(JvmSynthetic::class)
                                    .useSiteTarget(AnnotationSpec.UseSiteTarget.SET).build()
                            )
                        }
                        addModifiers(propertyAnnotations(property.value))
                        mutable(property.value.isAnnotatedMutable)
                    }.build()
                )
            }

            if (property.value.isAnnotatedMutable) {
                builderBuilder
                    .addFunction(
                        FunSpec
                            .builder("set${
                                propertyName.replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                                }
                            }")
                            .addParameter(propertyName, property.value.typeName)
                            .returns(builderClassNameObject)
                            .apply {
                                if (property.value.isMutableEmptySetter) {
                                    addAnnotation(
                                        AnnotationSpec.builder(Deprecated::class)
                                            .addMember(
                                                "%S",
                                                "This method is deprecated and won't do anything, kept for backwards compatibility"
                                            )
                                            .build()
                                    )
                                }
                                if (withSetterMethodImplementation) {
                                    if (property.value.isMutableEmptySetter.not()) {
                                        addStatement("this.$propertyName = $propertyName")
                                    }
                                    addStatement("return this")
                                }
                                addModifiers(propertyAnnotations(property.value))

                                if (withKDocs(property.value)) {
                                    addKdoc("""
                                        |Setter for $propertyName: ${
                                        property.value.kDoc.trimEnd('.')
                                            .replaceFirstChar { it.lowercase(Locale.getDefault()) }
                                    }.
                                        |
                                        |@param $propertyName
                                        |@return Builder
                                        """.trimMargin()
                                    )
                                }
                            }
                            .build()
                    )
            }
        }

        val builderConstructorNeeded = propertyMap.any {
            it.value.mandatoryForBuilderConstructor
        }
        if (builderConstructorNeeded && withImplementation) {
            builderBuilder.primaryConstructor(constructorBuilder.build())
        }

        if (withImplementation) {
            val buildFunction = FunSpec.builder("build")
            buildFunction.addKdoc(
                """
                |Returns a [$className] reference to the object being constructed by the builder.
                |
                |@return $className
                """.trimMargin()
            ).returns(classNameObject)
            //buildFunction.addModifiers(KModifier.OVERRIDE)
            buildFunction.addStatement(
                "return ${classNameObject.simpleName}(this)"
            )
            builderBuilder.addKdoc(
                """
                |Composes and builds a [$className] object.
                |
                |This is a concrete implementation of the builder design pattern.
                """.trimMargin()
            )
            builderBuilder.addFunction(buildFunction.build())
        }
    }

    private fun generatePrimaryConstructor(
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>
    ): FunSpec.Builder {
        // Constructor
        val primaryConstructorBuilder = FunSpec.constructorBuilder()
        primaryConstructorBuilder.addModifiers(KModifier.PRIVATE)
        for (entry in propertyMap) {
            val modifiers: MutableSet<KModifier> = mutableSetOf()
            if (entry.value.hasOverridesModifier) {
                modifiers.add(KModifier.OVERRIDE)
            }
            primaryConstructorBuilder.addParameter(
                ParameterSpec.builder(
                    entry.key.toString(), entry.value.typeName, modifiers
                ).apply {
                    addKdoc(entry.value.kDoc.apply {
                        trimEnd('.')
                    })
                    when (entry.value.mutability) {
                        PropertyMutability.IMMUTABLE -> addKdoc("\nImmutable, hardcoded to ${entry.value.defaultValue}")
                        PropertyMutability.IMMUTABLE_RECENTLY -> {
                            addKdoc("\nImmutable (previously mutable), hardcoded to ${entry.value.defaultValue}")
                            addKdoc("\nSetter and constructor kept for backwards compatibility, but won't have any effect")
                        }

                        PropertyMutability.MUTABLE -> if (entry.value.defaultValueConfig != null) {
                            val newRequiredProperty =
                                entry.value.defaultValueConfig!!.newRequiredPropertyConfig
                            if (newRequiredProperty != null) {
                                addKdoc("\nRequired param since v${newRequiredProperty.sinceVersion} (on previous constructor: default value ${entry.value.defaultValue})")
                            } else {
                                addKdoc("\nOptional param (default value ${entry.value.defaultValue})")
                            }
                        } else {
                            addKdoc("\nRequired param")
                        }
                    }

                }.build()
            )
        }

        primaryConstructorBuilder.addParameter(
            ParameterSpec.builder(
                "dbcMarker", ClassName("kotlin", "Unit").copy(
                    nullable = true
                )
            ).apply {
                addKdoc("Hidden marker to ensure this constructor would only be called by the other constructor that has builder as param")
            }.build()
        )

        return primaryConstructorBuilder
    }

    private fun generateCopyMethod(
        classNameObject: ClassName,
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>
    ): FunSpec.Builder {
        val mandatoryParams = propertyMap.filter {
            it.value.mandatoryForBuilderConstructor
        }.map { it.key.toString() }.joinToString(", ")
        return FunSpec.builder("copy")
            .addKdoc(
                """
                            Convert to "copy" function.
                            """.trimIndent()
            )
            .addParameter(
                ParameterSpec.builder(
                    "builder", LambdaTypeName.get(
                        classNameObject.nestedClass("Builder"),
                        emptyList(),
                        ClassName("kotlin", "Unit")
                    )
                ).build()
            )
            .addStatement(
                (propertyMap.filter {
                    it.value.mandatoryForBuilderConstructor.not() && it.value.isAnnotatedMutable
                }.keys.map { str ->
                    "${indent()}.set${
                        str.toString().replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault())
                            else it.toString()
                        }
                    }($str)"
                } + listOf(
                    "${indent()}.apply(builder)", "${indent()}.build()"
                )).joinToString(
                    prefix = "return BuilderImpl($mandatoryParams)\n", separator = "\n"
                )
            ).returns(ClassName("", classNameObject.simpleName))
    }

    internal fun generateToString(
        classNameObject: ClassName,
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>
    ): FunSpec.Builder {
        return FunSpec.builder("toString").addModifiers(KModifier.OVERRIDE).addKdoc(
            """
                            Overloaded toString function.
                            """.trimIndent()
        )
            // using triple quote for long strings
            .addStatement(
                propertyMap.keys.joinToString(
                    prefix = "return \"\"\"${classNameObject.simpleName}(",
                    transform = { "$it=$$it" },
                    postfix = ")\"\"\".trimIndent()"
                )
            )
    }

    internal fun generateEquals(
        classNameObject: ClassName,
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>
    ): FunSpec.Builder {
        return FunSpec.builder("equals").addModifiers(KModifier.OVERRIDE).addKdoc(
            """
                        Overloaded equals function.
                        """.trimIndent()
        ).addParameter("other", ANY.copy(nullable = true))
            .addStatement("if (this === other) return true")
            .addStatement("if (javaClass != other?.javaClass) return false")
            .addStatement("other as ${classNameObject.simpleName}").apply {
                propertyMap.filterNot { property ->
                    property.value.excludesFromEqualsAndGetter
                }.map {
                    addStatement(
                        "if(${it.key}·!=·other.${it.key}) return false"
                    )
                }
            }.addStatement("return true").returns(Boolean::class)
    }

    internal fun generateHashCode(
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>
    ): FunSpec.Builder {
        return FunSpec.builder("hashCode").addKdoc(
            """
                            Overloaded hashCode function based on all class properties.
                            """.trimIndent()
        ).addModifiers(KModifier.OVERRIDE).addStatement(
            propertyMap.filterNot { property ->
                property.value.excludesFromEqualsAndGetter
            }.map {
                it.key
            }.ifEmpty {
                listOf("javaClass")
            }.joinToString(
                prefix = "return Objects.hash(", separator = ", ", postfix = ")"
            )
        ).returns(Int::class)
    }

    private fun generateInitializer(
        classNameObject: ClassName,
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>
    ): FunSpec.Builder {
        val hasMutableParams = propertyMap.any {
            it.value.isAnnotatedMutable
        }

        val mandatoryParams = propertyMap.filter {
            it.value.mandatoryForConstructor
        }.map { it.key.toString() }.joinToString(", ")

        val kdocs = if (hasMutableParams) {
            """
                    |Creates a [${classNameObject.simpleNames.joinToString(".")}] through a DSL-style builder.
                    |
                    |@param initializer the initialisation block
                    |@return ${classNameObject.simpleNames.joinToString(".")}
                    """.trimMargin()
        } else {
            """
                    |Creates a [${classNameObject.simpleNames.joinToString(".")}]
                    |@return ${classNameObject.simpleNames.joinToString(".")}
                    """.trimMargin()
        }

        // initializer function
        val initializerFunctionBuilder = FunSpec.builder(classNameObject.simpleName).addKdoc(
            kdocs
        ).returns(classNameObject)


        if (mandatoryParams.isNotEmpty()) {
            propertyMap.filter {
                it.value.mandatoryForConstructor
            }.forEach {
                initializerFunctionBuilder.addParameter(it.key.toString(), it.value.typeName)
            }
        }

        if (hasMutableParams) {
            initializerFunctionBuilder.addParameter(
                ParameterSpec.builder(
                    "initializer", LambdaTypeName.get(
                        classNameObject.nestedClass("Builder"),
                        emptyList(),
                        ClassName("kotlin", "Unit")
                    )
                ).defaultValue("%L", "{ }").build()
            )
                .addStatement("return ${classNameObject.simpleNames.joinToString(".")}.Builder($mandatoryParams).apply(initializer).build()")
        } else {
            initializerFunctionBuilder.addStatement(
                "return ${
                    classNameObject.simpleNames.joinToString(
                        "."
                    )
                }.Builder($mandatoryParams).build()"
            )
        }

        return initializerFunctionBuilder
    }
}

private const val INDENTATION_SIZE = 2

internal fun indent(repeat: Int = 1): String {
    return " ".repeat(repeat * INDENTATION_SIZE)
}

private enum class ConstructorType {
    NO_OPTIONAL_BUILDER,
    OPTIONAL_BUILDER_DEFAULT_VALUE,
    WITH_OPTIONAL_BUILDER,
}

internal class ConstructingFunctions(
    val initializer: FunSpec.Builder?,
    val copyMethod: FunSpec.Builder,
    val builderSpec: Pair<List<TypeSpec.Builder>, FunSpec.Builder?>,
    val primaryConstructor: FunSpec.Builder,
    val additionalConstructors: List<FunSpec.Builder>
)