package com.cakcaraka.databinarycompatible.processor

import com.cakcaraka.databinarycompatible.annotation.DataClass
import com.cakcaraka.databinarycompatible.annotation.DefaultValue
import com.cakcaraka.databinarycompatible.annotation.ExcludeFromEqualsHashCode
import com.cakcaraka.databinarycompatible.annotation.NewRequiredProperty
import com.cakcaraka.databinarycompatible.annotation.PropertyMutability
import com.cakcaraka.databinarycompatible.annotation.SealedParentDataClass
import com.cakcaraka.databinarycompatible.processor.visitor.DataClassVisitor
import com.cakcaraka.databinarycompatible.processor.visitor.SealedParentVisitor
import com.cakcaraka.databinarycompatible.processor.writer.FileGeneratorSpec
import com.cakcaraka.databinarycompatible.processor.writer.FileWriter
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import kotlin.math.log

class DataBinaryCompatibleProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("DataBinaryCompatibleProcessor: process")
        val dataClassAnnotated =
            resolver.getSymbolsWithAnnotation(DataClass::class.qualifiedName!!, true)
        if (dataClassAnnotated.count() == 0) {
            logger.info("DataBinaryCompatibleProcessor: No DataClass annotations found for processing")
            return emptyList()
        }

        val sealedParentDataAnnotated =
            resolver.getSymbolsWithAnnotation(SealedParentDataClass::class.qualifiedName!!, true)
        val unableToProcess = sealedParentDataAnnotated.filterNot { it.validate() }.toMutableList()
        unableToProcess += dataClassAnnotated.filterNot { it.validate() }


        val config = Config(
            defaultRequiredSuffix = options["data_binary_compatible_required_suffix"] ?: "DBC",
            defaultDropPackagesSuffix = options["data_binary_compatible_drop_packages_suffix"]?.split(
                "|"
            ).orEmpty(),
            constructViaConstructorInsteadOfDsl = options["data_binary_compatible_construct_mechanism"] == "constructor"
        )

        // symbols returned by
        // resolver.getSymbolsWithAnnotation(DataClass::class.qualifiedName!!, true)
        // don't expose their annotations.
        // Therefore we can't access the @DefaultValue annotation required to build the code
        // for default values.
        val classToDefaultValuesMap =
            mutableMapOf<KSClassDeclaration, MutableMap<FieldName, DefaultValueConfig>>()

        processDefaultValuesAnnotations(
            resolver,
            classToDefaultValuesMap
        )

        processExcludeEqualsAndHashCode(
            resolver,
            classToDefaultValuesMap
        )

        processNewRequiredProperty(
            resolver, classToDefaultValuesMap
        )

        val nestedClassNameMapping = mutableMapOf<ClassName, ClassName>()

        val nestedClassFileGeneratorSpec = mutableMapOf<KSAnnotated, FileGeneratorSpec>()

        sealedParentDataAnnotated.forEach {
            it.accept(
                SealedParentVisitor(
                    logger,
                    classToDefaultValuesMap,
                    nestedClassNameMapping,
                    nestedClassFileGeneratorSpec,
                    config
                ), Unit
            )
        }

        dataClassAnnotated.filter { it is KSClassDeclaration && it.validate() }
            .forEach {
                it.accept(
                    DataClassVisitor(
                        codeGenerator,
                        logger,
                        classToDefaultValuesMap,
                        nestedClassNameMapping,
                        nestedClassFileGeneratorSpec,
                        config
                    ), Unit
                )
            }

        FileWriter.writeNested(
            logger, codeGenerator, nestedClassNameMapping, nestedClassFileGeneratorSpec
        )
        return unableToProcess.toMutableList()
    }

    private fun processDefaultValuesAnnotations(
        resolver: Resolver,
        classToDefaultValuesMap: MutableMap<KSClassDeclaration, MutableMap<FieldName, DefaultValueConfig>>
    ) {
        val symbolsPropertyWithDefaultAnnotation =
            resolver.getSymbolsWithAnnotation(DefaultValue::class.qualifiedName!!, true)
        symbolsPropertyWithDefaultAnnotation.forEach { annotatedProperty ->
            if (annotatedProperty is KSPropertyDeclaration) {
                val parentClass = annotatedProperty.findParentClass()!!
                val defaultValueMap = classToDefaultValuesMap[parentClass] ?: mutableMapOf()

                annotatedProperty.annotations.firstOrNull {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == DefaultValue::class.qualifiedName
                }?.let {
                    val fieldName = FieldName(
                        annotatedProperty.simpleName.getShortName()
                    )
                    val defaultValue = processDefaultValueAnnotations(
                        fieldName,
                        it,
                        null
                    )

                    defaultValueMap[fieldName] = defaultValue
                    classToDefaultValuesMap[parentClass] = defaultValueMap
                }
            }
        }
    }

    private fun processExcludeEqualsAndHashCode(
        resolver: Resolver,
        classToDefaultValuesMap: MutableMap<KSClassDeclaration, MutableMap<FieldName, DefaultValueConfig>>
    ) {
        val symbolsPropertyWithVersionGetterAnnotation =
            resolver.getSymbolsWithAnnotation(
                ExcludeFromEqualsHashCode::class.qualifiedName!!,
                true
            )
        symbolsPropertyWithVersionGetterAnnotation.forEach { annotatedProperty ->
            if (annotatedProperty is KSPropertyDeclaration) {
                val parentClass = annotatedProperty.findParentClass()!!
                val defaultValueMap = classToDefaultValuesMap[parentClass] ?: mutableMapOf()
                val fieldName = FieldName(
                    annotatedProperty.simpleName.getShortName()
                )

                defaultValueMap[fieldName] = defaultValueMap[fieldName]?.copy(
                    excludeFromEqualsAndGetter = true
                ) ?: DefaultValueConfig(
                    fieldName,
                    null,
                    mutability = PropertyMutability.MUTABLE,
                    true,
                    null
                )
                classToDefaultValuesMap[parentClass] = defaultValueMap
            }
        }
    }

    private fun processNewRequiredProperty(
        resolver: Resolver,
        classToDefaultValuesMap: MutableMap<KSClassDeclaration, MutableMap<FieldName, DefaultValueConfig>>
    ) {
        val symbolsPropertyWithDefaultAnnotation =
            resolver.getSymbolsWithAnnotation(NewRequiredProperty::class.qualifiedName!!, true)
        symbolsPropertyWithDefaultAnnotation.forEach { annotatedProperty ->
            if (annotatedProperty is KSPropertyDeclaration) {

                val defaultValueAnnotation =
                    annotatedProperty.annotations.firstOrNull {
                        it.annotationType.resolve().declaration.qualifiedName?.asString() == DefaultValue::class.qualifiedName
                    }

                if (defaultValueAnnotation != null) {
                    logger.error(
                        "Property cannot be declared with both DefaultValue and NewRequiredProperty",
                        annotatedProperty
                    )
                }

                val newRequiredAnnotationsParams =
                    annotatedProperty.annotations.firstOrNull {
                        it.annotationType.resolve().declaration.qualifiedName?.asString() == NewRequiredProperty::class.qualifiedName
                    }?.arguments

                val sinceVersion = (newRequiredAnnotationsParams?.firstOrNull {
                    it.name?.asString() == "sinceVersion"
                }?.value.let {
                    if (it !is Int) {
                        logger.error(
                            "sinceVersion is null", annotatedProperty
                        )
                        return
                    } else if (it < 2) {
                        logger.error(
                            "sinceVersion must be > 1", annotatedProperty
                        )
                        return
                    } else {
                        it
                    }
                })

                val previouslyOptionalParams = (newRequiredAnnotationsParams?.firstOrNull {
                    it.name?.asString() == "previouslyOptional"
                }?.value.let {
                    if (it !is Boolean) {
                        logger.error(
                            "previouslyOptional is null", annotatedProperty
                        )
                        return
                    } else {
                        it
                    }
                })

                val defaultAnnotationsParams = (newRequiredAnnotationsParams?.firstOrNull {
                    it.name?.asString() == "defaultValue"
                }?.value.let {
                    if (it !is KSAnnotation) {
                        logger.error(
                            "defaultValue is null", annotatedProperty
                        )
                        return
                    } else if (it.annotationType.resolve().declaration.qualifiedName?.asString() != DefaultValue::class.qualifiedName) {
                        logger.error(
                            "defaultValue must be instance of DefaultValue", annotatedProperty
                        )
                        return
                    } else {
                        it
                    }
                })


                val fieldName = FieldName(
                    annotatedProperty.simpleName.getShortName()
                )

                val defaultValue = processDefaultValueAnnotations(
                    fieldName,
                    defaultAnnotationsParams,
                    NewRequiredPropertyConfig(
                        sinceVersion,
                        previouslyOptionalParams
                    )
                )

                val parentClass = annotatedProperty.findParentClass()!!
                val defaultValueMap = (classToDefaultValuesMap[parentClass] ?: mutableMapOf())

                defaultValueMap[fieldName] = defaultValue
                classToDefaultValuesMap[parentClass] = defaultValueMap
            }
        }
    }



    private fun processDefaultValueAnnotations(
        fieldName: FieldName,
        ksAnnotation: KSAnnotation,
        newRequiredPropertyConfig: NewRequiredPropertyConfig?
    ): DefaultValueConfig {
        val defaultAnnotationsParams = ksAnnotation.arguments
        val defaultValue = (defaultAnnotationsParams.firstOrNull {
            it.name?.asString() == "rawValue"
        }?.value as? String?).let {
            if (it.isNullOrEmpty()) {
                (defaultAnnotationsParams.firstOrNull {
                    it.name?.asString() == "stringValue"
                }?.value as? String?)?.let {
                    "\"$it\""
                }
            } else {
                it
            }
        }

        val mutabilityArg = defaultAnnotationsParams.firstOrNull {
            it.name?.asString() == "mutability"
        }?.value as? KSType

        // If you need to check which enum value it corresponds to:
        val mutabilityName = mutabilityArg?.declaration?.simpleName?.asString()

        val mutability = kotlin.runCatching {
            PropertyMutability.valueOf(mutabilityName.orEmpty())
        }.getOrElse {
            PropertyMutability.MUTABLE
        }

        return DefaultValueConfig(
            fieldName,
            defaultValue,
            mutability,
            false,
            newRequiredPropertyConfig
        )
    }

    private fun KSNode.findParentClass(): KSClassDeclaration? {
        var currentParent: KSNode? = parent
        while (currentParent !is KSClassDeclaration) {
            currentParent = parent?.parent
            if (currentParent == null) {
                return null
            }
        }
        return currentParent
    }


    internal class Config(
        val defaultRequiredSuffix: String,
        val defaultDropPackagesSuffix: List<String>,
        val constructViaConstructorInsteadOfDsl: Boolean
    )
}
