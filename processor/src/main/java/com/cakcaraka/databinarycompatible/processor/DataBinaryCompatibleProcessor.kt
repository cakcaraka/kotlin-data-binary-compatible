package com.cakcaraka.databinarycompatible.processor

import com.cakcaraka.databinarycompatible.annotation.DataClass
import com.cakcaraka.databinarycompatible.annotation.DefaultValue
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
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName

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

        val config = Config(
            defaultRequiredSuffix = options["data_binary_compatible_required_suffix"] ?: "DBC",
            defaultDropPackagesSuffix = options["data_binary_compatible_drop_packages_suffix"]?.split("|").orEmpty()
        )

        // symbols returned by
        // resolver.getSymbolsWithAnnotation(DataClass::class.qualifiedName!!, true)
        // don't expose their annotations.
        // Therefore we can't access the @DefaultValue annotation required to build the code
        // for default values.
        val classToDefaultValuesMap =
            mutableMapOf<KSClassDeclaration, MutableMap<String, Pair<String?, Boolean>>>()

        val symbolsPropertyWithDefaultAnnotation =
            resolver.getSymbolsWithAnnotation(DefaultValue::class.qualifiedName!!, true)
        symbolsPropertyWithDefaultAnnotation.forEach { annotatedProperty ->
            if (annotatedProperty is KSPropertyDeclaration) {
                val parentClass = annotatedProperty.findParentClass()!!
                var defaultValueMap = classToDefaultValuesMap[parentClass]
                if (defaultValueMap == null) {
                    defaultValueMap = mutableMapOf()
                }
                val defaultAnnotationsParams =
                    annotatedProperty.annotations.firstOrNull()?.arguments

                val defaultValue = (defaultAnnotationsParams?.firstOrNull {
                    it.name?.asString() == "rawValue"
                }?.value as? String?).let {
                    if (it.isNullOrEmpty()) {
                        (defaultAnnotationsParams?.firstOrNull {
                            it.name?.asString() == "stringValue"
                        }?.value as? String?)?.let {
                            "\"$it\""
                        }
                    } else {
                        it
                    }
                }

                val isImmutable = (defaultAnnotationsParams?.firstOrNull {
                    it.name?.asString() == "immutable"
                }?.value as? Boolean) ?: false

                defaultValueMap[annotatedProperty.simpleName.getShortName()] =
                    defaultValue to isImmutable
                classToDefaultValuesMap[parentClass] = defaultValueMap
            }
        }


        val sealedParentDataAnnotated =
            resolver.getSymbolsWithAnnotation(SealedParentDataClass::class.qualifiedName!!, true)
        val unableToProcess = sealedParentDataAnnotated.filterNot { it.validate() }.toMutableList()

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

        unableToProcess += dataClassAnnotated.filterNot { it.validate() }

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
        val defaultDropPackagesSuffix: List<String>
    )
}
