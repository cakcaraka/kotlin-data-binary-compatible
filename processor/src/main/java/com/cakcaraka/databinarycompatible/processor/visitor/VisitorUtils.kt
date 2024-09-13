package com.cakcaraka.databinarycompatible.processor.visitor

import com.cakcaraka.databinarycompatible.processor.PropertyConfig
import com.cakcaraka.databinarycompatible.processor.visitor.BaseVisitor.ConstructingMechanism
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
        val (additionalConstructors, isPrimaryConstructorPrivate) = generateAdditionalConstructors(
            classNameObject, propertyMap, constructingMechanism
        )

        // Primary Constructor
        val primaryConstructorBuilder =
            generatePrimaryConstructor(isPrimaryConstructorPrivate, propertyMap)

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
        classNameObject: ClassName,
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>,
        constructingMechanism: ConstructingMechanism
    ): Pair<List<FunSpec.Builder>, Boolean> {
        val additionalConstructors = mutableListOf<FunSpec.Builder>()
        var isPrimaryConstructorPrivate = true

        //constructor that receives builder
        val classConstructorWithBuilder = FunSpec.constructorBuilder()
        classConstructorWithBuilder.addModifiers(KModifier.PRIVATE)
        classConstructorWithBuilder.addParameter(
            "builder", classNameObject.nestedClass("Builder")
        )
        classConstructorWithBuilder.callThisConstructor(
            propertyMap.keys.joinToString(
                prefix = "\n",
                transform = { "${indent()}builder.$it" },
                separator = ",\n",
                postfix = "\n"
            )
        )

        additionalConstructors.add(classConstructorWithBuilder)

        if (constructingMechanism == ConstructingMechanism.SecondaryConstructor) {
            val mandatoryParams = propertyMap.filter {
                it.value.mandatoryForConstructor
            }.map { it.key.toString() }.joinToString(", ")
            //constructor with default params
            if (mandatoryParams.isNotEmpty()) {
                //if all mandatory, just change primary constructor to public
                val hasNonMandatoryParams = propertyMap.filter {
                    it.value.mandatoryForConstructor.not()
                }.isNotEmpty()

                if (hasNonMandatoryParams) {
                    listOf(true, false).forEach { withInitializer ->
                        //constructor that receives mandatory param and then builder
                        val classConstructorWithParamBuilder = FunSpec.constructorBuilder()
                        classConstructorWithParamBuilder.addModifiers(KModifier.PUBLIC)

                        val mandatoryProperty = propertyMap.filter {
                            it.value.mandatoryForConstructor
                        }

                        mandatoryProperty.forEach {
                            classConstructorWithParamBuilder.addParameter(
                                it.key.toString(), it.value.typeName
                            )
                        }

                        if (withInitializer) {
                            classConstructorWithParamBuilder.addParameter(
                                ParameterSpec.builder(
                                    "initializer", LambdaTypeName.get(
                                        classNameObject.nestedClass("Builder"),
                                        emptyList(),
                                        ClassName("kotlin", "Unit")
                                    )
                                ).build()
                            )
                        }
                        classConstructorWithParamBuilder.callThisConstructor(
                            mandatoryProperty.keys.joinToString(
                                prefix = "\n${indent()}Builder(\n",
                                transform = { "${indent(2)}$it" },
                                separator = ",\n",
                                postfix = "\n${indent()})" + if (withInitializer) ".apply(initializer)\n" else "\n"
                            )
                        )
                        additionalConstructors.add(
                            classConstructorWithParamBuilder
                        )
                    }
                } else {
                    isPrimaryConstructorPrivate = false
                }
            } else {
                isPrimaryConstructorPrivate = false
            }
        }
        return Pair(additionalConstructors, isPrimaryConstructorPrivate)
    }

    private fun generateBuilderTypeSpec(
        classNameObject: ClassName,
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>
    ): Pair<TypeSpec.Builder, FunSpec.Builder?> {
        // Builder pattern
        val builderBuilder = TypeSpec.classBuilder("Builder")
        var builderConstructorNeeded = false
        val constructorBuilder = FunSpec.constructorBuilder()
        val className = classNameObject.simpleName
        for (property in propertyMap) {
            val propertyName = property.key.toString()

            // when no default value provided but property is non nullable -
            // it should be moved to Builder mandatory ctor arguments
            if (property.value.mandatoryForConstructor) {
                builderConstructorNeeded = true
                constructorBuilder.addParameter(
                    propertyName,
                    property.value.typeName,
                )
                builderBuilder.addProperty(
                    PropertySpec.builder(propertyName, property.value.typeName)
                        .initializer(propertyName).addKdoc(
                            """
                            |${property.value.kDoc}
                            """.trimMargin()
                        ).addAnnotation(
                            AnnotationSpec.builder(JvmSynthetic::class)
                                .useSiteTarget(AnnotationSpec.UseSiteTarget.SET).build()
                        ).mutable(true).build()
                )
            } else {
                builderBuilder.addProperty(
                    PropertySpec.builder(
                        propertyName,
                        property.value.typeName
                    ).initializer(
                        CodeBlock.builder().add(
                            property.value.defaultValue ?: "null"
                        ).build()
                    ).addKdoc(
                        """
                            |${property.value.kDoc}
                            """.trimMargin()
                    ).apply {
                        if (property.value.isMutable) {
                            addAnnotation(
                                AnnotationSpec.builder(JvmSynthetic::class)
                                    .useSiteTarget(AnnotationSpec.UseSiteTarget.SET).build()
                            )
                        }
                    }.apply {
                        if (property.value.isMutable) {
                            mutable(true)
                        } else {
                            mutable(false)
                            addModifiers(KModifier.INTERNAL)
                        }
                    }.build()
                )
            }

            if (property.value.isMutable) {
                builderBuilder.addFunction(FunSpec.builder("set${
                    propertyName.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    }
                }").addKdoc("""
                            |Setter for $propertyName: ${
                    property.value.kDoc.trimEnd('.')
                        .replaceFirstChar { it.lowercase(Locale.getDefault()) }
                }.
                            |
                            |@param $propertyName
                            |@return Builder
                            """.trimMargin()).addParameter(propertyName, property.value.typeName)
                    .addStatement("this.$propertyName = $propertyName")
                    .addStatement("return this").returns(classNameObject.nestedClass("Builder"))
                    .build())
            }
        }
        if (builderConstructorNeeded) {
            builderBuilder.primaryConstructor(constructorBuilder.build())
        }

        val buildFunction = FunSpec.builder("build")
        buildFunction.addKdoc(
            """
                |Returns a [$className] reference to the object being constructed by the builder.
                |
                |@return $className
                """.trimMargin()
        )
        buildFunction.addStatement(
            "return $className(this)"
        ).returns(classNameObject)

        builderBuilder.addKdoc(
            """
                |Composes and builds a [$className] object.
                |
                |This is a concrete implementation of the builder design pattern.
                """.trimMargin()
        )
        builderBuilder.addFunction(buildFunction.build())

        val classConstructorWithParamBuilder = if (builderConstructorNeeded.not()) {
            val paramBuilder = FunSpec.constructorBuilder()
            paramBuilder.addModifiers(KModifier.PUBLIC)
            paramBuilder.callThisConstructor(
                "Builder()"
            )
        } else {
            null
        }

        return Pair(builderBuilder, classConstructorWithParamBuilder)
    }

    private fun generatePrimaryConstructor(
        isPrimaryConstructorPrivate: Boolean,
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>
    ): FunSpec.Builder {
        // Constructor
        val primaryConstructorBuilder = FunSpec.constructorBuilder()
        if (isPrimaryConstructorPrivate) {
            primaryConstructorBuilder.addModifiers(KModifier.PRIVATE)
        }
        for (entry in propertyMap) {
            val modifiers: MutableSet<KModifier> = mutableSetOf()
            if (entry.value.hasOverridesModifier) {
                modifiers.add(KModifier.OVERRIDE)
            }
            primaryConstructorBuilder.addParameter(
                entry.key.toString(), entry.value.typeName, modifiers
            )
        }

        return primaryConstructorBuilder
    }

    private fun generateCopyMethod(
        classNameObject: ClassName,
        propertyMap: Map<KSPropertyDeclaration, PropertyConfig>
    ): FunSpec.Builder {
        val mandatoryParams = propertyMap.filter {
            it.value.mandatoryForConstructor
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
                    it.value.isMutable
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
                    prefix = "return Builder($mandatoryParams)\n", separator = "\n"
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
                propertyMap.keys.map {
                    addStatement(
                        "if($it·!=·other.$it) return false"
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
            propertyMap.keys.ifEmpty {
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
            it.value.isMutable
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


internal class ConstructingFunctions(
    val initializer: FunSpec.Builder?,
    val copyMethod: FunSpec.Builder,
    val builderSpec: Pair<TypeSpec.Builder, FunSpec.Builder?>,
    val primaryConstructor: FunSpec.Builder,
    val additionalConstructors: List<FunSpec.Builder>
)