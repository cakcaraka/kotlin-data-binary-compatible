package com.cakcaraka.databinarycompatible.processor.visitor

import com.cakcaraka.databinarycompatible.processor.DataBinaryCompatibleProcessor.Config
import com.cakcaraka.databinarycompatible.processor.PropertyConfig
import com.cakcaraka.databinarycompatible.processor.writer.FileGeneratorSpec
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Modifier
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
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import java.util.Locale
import kotlin.reflect.KClass

internal abstract class BaseVisitor(
    private val logger: KSPLogger,
    private val defaultValuesMap: Map<KSClassDeclaration, MutableMap<String, Pair<String?, Boolean>>>,
    private val nestedClassMapping: MutableMap<ClassName, ClassName>,
    private val config: Config,
) : KSVisitorVoid() {
    private val INDENTATION_SIZE = 2

    abstract val annotationClass: KClass<*>

    abstract val isAddOverrideModifierToProperties: Boolean

    abstract val additionalClassModifier: KModifier?

    abstract val isAddSelfAsSuperInterface: Boolean

    abstract val isGeneratePrimaryConstructor: Boolean

    abstract val initializeProperty: Boolean

    abstract val isGenerateCopyMethod: Boolean

    abstract val isGenerateBuilder: Boolean

    abstract val isGenerateInitializer: Boolean

    abstract fun writeFileSpec(
        classDeclaration: KSClassDeclaration,
        fileGeneratorSpec: FileGeneratorSpec
    )


    @Suppress("LongMethod", "MaxLineLength", "ComplexMethod")
    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
        super.visitClassDeclaration(classDeclaration, data)

        val annotatedClassData = classDeclaration.annotations.firstOrNull {
            it.annotationType.resolve().toString() == annotationClass.simpleName
        }

        val originalClassName = classDeclaration.qualifiedName

        val generatedClassName: String? = (annotatedClassData?.arguments?.firstOrNull {
            it.name?.getShortName() == "generatedClassName"
        }?.value as? String).takeIf { it.isNullOrEmpty().not() }

        if (isInvalidAnnotatedSetup(
                annotationClass.simpleName,
                classDeclaration,
                config,
                generatedClassName
            )
        ) {
            return
        }

        val className = generatedClassName ?: classDeclaration.simpleName.asString()
            .dropLast(config.defaultRequiredSuffix.length)
        val classKdoc = classDeclaration.docString
        val packageName = classDeclaration.packageName.asString()

        // Resolve list of imports]
        val imports = ArrayList<String>()
        annotatedClassData?.arguments?.firstOrNull {
            it.name?.getShortName() == "imports"
        }?.value?.let { imports.addAll(it as ArrayList<String>) }

        val otherAnnotations = classDeclaration.annotations
            .filter {
                it.annotationType.resolve()
                    .toString() != annotationClass.simpleName
            }

        val additionalAnnotations = annotatedClassData?.arguments?.firstOrNull {
            it.name?.getShortName() == "annotations"
        }?.value?.let { it as? ArrayList<String> }?.map { annotation ->
            val importPath = imports.find {
                it != annotation && it.endsWith(annotation)
            }

            val packagePath = importPath
                ?.split('.')
                ?.dropLast(1)
                ?.joinToString(".")
                ?: ""

            ClassName(packagePath, annotation)
        }

        val implementedInterfaces = classDeclaration
            .superTypes
            .filter { (it.resolve().declaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE }

        // Map KSP properties with KoltinPoet TypeNames
        val propertyMap = mutableMapOf<KSPropertyDeclaration, PropertyConfig>()
        for (property in classDeclaration.getAllProperties()) {
            val classTypeParams = classDeclaration.typeParameters.toTypeParameterResolver()
            val typeName = property.type.resolve().toTypeName(classTypeParams)

            val addOverridesModifier =
                Modifier.OVERRIDE in property.modifiers || isAddOverrideModifierToProperties

            propertyMap[property] = PropertyConfig(
                typeName = typeName,
                mandatoryForConstructor = defaultValuesMap[classDeclaration]
                    ?.get(property.toString()) == null && !typeName.isNullable,
                defaultValue = defaultValuesMap[classDeclaration]?.get(property.toString())?.first,
                isMutable = defaultValuesMap[classDeclaration]?.get(property.toString())?.second?.not()
                    ?: true,
                hasOverridesModifier = addOverridesModifier,
                kDoc = property.docString?.trim(' ', '\n') ?: property.toString()
                    .capitalizeAndAddSpaces(),
            )
        }

        // Build mandatory param list for toBuilder and DSL function
        val mandatoryParams = propertyMap.filter {
            it.value.mandatoryForConstructor
        }.map { it.key.toString() }.joinToString(", ")

        var classNameObject = ClassName(packageName, className)

        val classBuilder = TypeSpec.classBuilder(className).apply {
            additionalClassModifier?.also {
                addModifiers(it)
            }

            addKdoc("\nGenerated from [${originalClassName?.asString()}]\n")
            classKdoc?.let {
                addKdoc(
                    classKdoc.split("\n")
                        .filter { it.isNotEmpty() }.joinToString(
                            separator = "\n",
                            transform = {
                                if (it.startsWith(" ")) {
                                    it.substring(1)
                                } else {
                                    it
                                }
                            }
                        )
                )
            }

            otherAnnotations.forEach {
                addAnnotation(
                    it.annotationType.resolve().toClassName()
                )
            }

            additionalAnnotations?.forEach { annotation ->
                addAnnotation(annotation)
            }

            implementedInterfaces.forEach {
                val nestedClassMappedName = nestedClassMapping[it.resolve().toClassName()]
                if (nestedClassMappedName != null) {
                    classNameObject = nestedClassMappedName.nestedClass(className)
                    superclass(nestedClassMappedName)
                } else {
                    addSuperinterface(
                        it.resolve().toClassName()
                    )
                }
            }

            if (isAddSelfAsSuperInterface) {
                addSuperinterface(
                    classDeclaration.toClassName()
                )
            }

            if (isGeneratePrimaryConstructor) {
                // Constructor
                val constructorBuilder = FunSpec.constructorBuilder()
                constructorBuilder.addModifiers(KModifier.PRIVATE)
                for (entry in propertyMap) {
                    val modifiers: MutableSet<KModifier> = mutableSetOf()
                    if (entry.value.hasOverridesModifier) {
                        modifiers.add(KModifier.OVERRIDE)
                    }
                    constructorBuilder.addParameter(
                        entry.key.toString(),
                        entry.value.typeName,
                        modifiers
                    )

                }
                primaryConstructor(constructorBuilder.build())
            }

            // Property Initializer
            for (entry in propertyMap) {
                addProperty(
                    PropertySpec.builder(entry.key.toString(), entry.value.typeName)
                        .addKdoc(
                            """
                                |${entry.value.kDoc}
                                """.trimMargin()
                        ).apply {
                            if (initializeProperty) {
                                initializer(entry.key.toString())
                            } else {
                                addModifiers(KModifier.ABSTRACT)
                            }
                        }
                        .build()
                )
            }

            // Function toString
            addFunction(
                FunSpec.builder("toString")
                    .addModifiers(KModifier.OVERRIDE)
                    .addKdoc(
                        """
                            Overloaded toString function.
                            """.trimIndent()
                    )
                    // using triple quote for long strings
                    .addStatement(
                        propertyMap.keys.joinToString(
                            prefix = "return \"\"\"$className(",
                            transform = { "$it=$$it" },
                            postfix = ")\"\"\".trimIndent()"
                        )
                    )
                    .build()
            )

            // Function equals
            val equalsBuilder = FunSpec.builder("equals")
                .addModifiers(KModifier.OVERRIDE)
                .addKdoc(
                    """
                        Overloaded equals function.
                        """.trimIndent()
                )
                .addParameter("other", ANY.copy(nullable = true))
                .addStatement("if (this === other) return true")
                .addStatement("if (javaClass != other?.javaClass) return false")
                .addStatement("other as $className")
                .apply {
                    propertyMap.keys.map {
                        addStatement(
                            "if($it·!=·other.$it) return false"
                        )
                    }
                }
                .addStatement("return true")
                .returns(Boolean::class)
            addFunction(equalsBuilder.build())

            // Function hashCode
            addFunction(
                FunSpec.builder("hashCode")
                    .addKdoc(
                        """
                            Overloaded hashCode function based on all class properties.
                            """.trimIndent()
                    )
                    .addModifiers(KModifier.OVERRIDE)
                    .addStatement(
                        propertyMap.keys.ifEmpty {
                            listOf("javaClass")
                        }.joinToString(
                            prefix = "return Objects.hash(",
                            separator = ", ",
                            postfix = ")"
                        )
                    )
                    .returns(Int::class)
                    .build()
            )

            if (isGenerateCopyMethod) {
                // Function copy
                addFunction(
                    FunSpec.builder("copy")
                        .addKdoc(
                            """
                            Convert to "copy" function.
                            """.trimIndent()
                        )
                        .addParameter(
                            ParameterSpec.builder(
                                "builder",
                                LambdaTypeName.get(
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
                                "${" ".repeat(INDENTATION_SIZE)}.set${
                                    str.toString().replaceFirstChar {
                                        if (it.isLowerCase())
                                            it.titlecase(Locale.getDefault())
                                        else it.toString()
                                    }
                                }($str)"
                            } + listOf(
                                "${" ".repeat(INDENTATION_SIZE)}.apply(builder)",
                                "${" ".repeat(INDENTATION_SIZE)}.build()"
                            )).joinToString(
                                prefix = "return Builder($mandatoryParams)\n",
                                separator = "\n"
                            )
                        )
                        .returns(ClassName("", className))
                        .build()
                )
            }
        }

        if (isGenerateBuilder) {
            // Builder pattern
            val builderBuilder = TypeSpec.classBuilder("Builder")
            var builderConstructorNeeded = false
            val constructorBuilder = FunSpec.constructorBuilder()
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
                            .initializer(propertyName)
                            .addKdoc(
                                """
                            |${property.value.kDoc}
                            """.trimMargin()
                            )
                            .addAnnotation(
                                AnnotationSpec.builder(JvmSynthetic::class)
                                    .useSiteTarget(AnnotationSpec.UseSiteTarget.SET)
                                    .build()
                            )
                            .mutable(true)
                            .build()
                    )
                } else {
                    builderBuilder.addProperty(
                        PropertySpec.builder(propertyName, property.value.typeName)
                            .initializer(
                                CodeBlock.builder()
                                    .add(
                                        property.value.defaultValue ?: "null"
                                    )
                                    .build()
                            )
                            .addKdoc(
                                """
                            |${property.value.kDoc}
                            """.trimMargin()
                            )
                            .apply {
                                if (property.value.isMutable) {
                                    addAnnotation(
                                        AnnotationSpec.builder(JvmSynthetic::class)
                                            .useSiteTarget(AnnotationSpec.UseSiteTarget.SET)
                                            .build()
                                    )
                                }
                            }.apply {
                                if (property.value.isMutable) {
                                    mutable(true)
                                } else {
                                    mutable(false)
                                    addModifiers(KModifier.PRIVATE)
                                }
                            }
                            .build()
                    )
                }

                if (property.value.isMutable) {
                    builderBuilder.addFunction(
                        FunSpec
                            .builder(
                                "set${
                                    propertyName.replaceFirstChar {
                                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                                    }
                                }"
                            )
                            .addKdoc(
                                """
                            |Setter for $propertyName: ${
                                    property.value.kDoc.trimEnd('.')
                                        .replaceFirstChar { it.lowercase(Locale.getDefault()) }
                                }.
                            |
                            |@param $propertyName
                            |@return Builder
                            """.trimMargin()
                            )
                            .addParameter(propertyName, property.value.typeName)
                            .addStatement("this.$propertyName = $propertyName")
                            .addStatement("return this")
                            .returns(classNameObject.nestedClass("Builder"))
                            .build()
                    )
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
                propertyMap.keys.joinToString(
                    prefix = "return $className(",
                    transform = { "$it" },
                    separator = ", ",
                    postfix = ")"
                )
            )
                .returns(classNameObject)

            builderBuilder.addKdoc(
                """
                |Composes and builds a [$className] object.
                |
                |This is a concrete implementation of the builder design pattern.
                """.trimMargin()
            )
            builderBuilder.addFunction(buildFunction.build())
            classBuilder.addType(builderBuilder.build())
        }

        val initializerFunction = if (isGenerateInitializer) {
            val hasMutableParams = propertyMap.any {
                it.value.isMutable
            }

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
            val initializerFunctionBuilder = FunSpec.builder(className)
                .addKdoc(
                    kdocs
                )
                .returns(classNameObject)
                .addAnnotation(JvmSynthetic::class)

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
                        "initializer",
                        LambdaTypeName.get(
                            classNameObject.nestedClass("Builder"),
                            emptyList(),
                            ClassName("kotlin", "Unit")
                        )
                    )
                        .defaultValue("%L", "{ }")
                        .build()
                )
                    .addStatement("return ${classNameObject.simpleNames.joinToString(".")}.Builder($mandatoryParams).apply(initializer).build()")
            } else {
                initializerFunctionBuilder
                    .addStatement("return ${classNameObject.simpleNames.joinToString(".")}.Builder($mandatoryParams).build()")
            }

            initializerFunctionBuilder.build()
        } else {
            null
        }


        val fileSpec = FileGeneratorSpec(
            classDeclaration,
            packageName,
            className,
            imports,
            classBuilder,
            initializerFunction,
            (classDeclaration.parentDeclaration as? KSClassDeclaration)
        )

        writeFileSpec(classDeclaration, fileSpec)
    }


    protected open fun isInvalidAnnotatedSetup(
        annotationName: String?,
        classDeclaration: KSClassDeclaration,
        config: Config,
        generatedClassName: String?
    ): Boolean {
        val qualifiedName = classDeclaration.qualifiedName?.asString() ?: run {
            logger.error(
                "@{$annotationName} must target classes with a qualified name",
                classDeclaration
            )
            return true
        }


        if (classDeclaration.isInterface().not()) {
            logger.error(
                "@{$annotationName} cannot target a non-interface class $qualifiedName",
                classDeclaration
            )
            return true
        }

        if (classDeclaration.typeParameters.any()) {
            logger.error(
                "@{$annotationName} target shouldn't have type parameters",
                classDeclaration
            )
            return true
        }

        if (generatedClassName.isNullOrEmpty() && !classDeclaration.simpleName.asString()
                .endsWith(config.defaultRequiredSuffix)
        ) {
            logger.error(
                "@{$annotationName} target must end with ${config.defaultRequiredSuffix}" +
                        " suffix naming or has generatedClassName property, to change suffix " +
                        "please use ksp args data_binary_compatible_required_suffix",
                classDeclaration
            )
            return true
        }
        return false
    }

    private fun String.capitalizeAndAddSpaces(): String {
        val tmpStr = replace(Regex("[A-Z]")) { " " + it.value.lowercase(Locale.getDefault()) }
        return tmpStr.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        } + "."
    }

    private fun KSClassDeclaration.isInterface() = classKind == ClassKind.INTERFACE
}
