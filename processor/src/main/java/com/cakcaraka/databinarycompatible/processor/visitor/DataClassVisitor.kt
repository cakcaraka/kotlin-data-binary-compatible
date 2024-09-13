package com.cakcaraka.databinarycompatible.processor.visitor

import com.cakcaraka.databinarycompatible.annotation.DataClass
import com.cakcaraka.databinarycompatible.processor.DataBinaryCompatibleProcessor.Config
import com.cakcaraka.databinarycompatible.processor.DefaultValueConfig
import com.cakcaraka.databinarycompatible.processor.FieldName
import com.cakcaraka.databinarycompatible.processor.writer.FileGeneratorSpec
import com.cakcaraka.databinarycompatible.processor.writer.FileWriter
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import kotlin.reflect.KClass

internal class DataClassVisitor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    defaultValuesMap: Map<KSClassDeclaration, MutableMap<FieldName, DefaultValueConfig>>,
    nestedClassMapping: MutableMap<ClassName, ClassName>,
    private val fileGeneratorSpecMap: MutableMap<KSAnnotated, FileGeneratorSpec>,
    config: Config
) : BaseVisitor(logger, defaultValuesMap, nestedClassMapping, config) {

    override val annotationClass: KClass<*> = DataClass::class

    override val additionalClassModifier: KModifier? = null

    override val isAddSelfAsSuperInterface: Boolean = true

    override val constructingMechanism: ConstructingMechanism = if(config.constructViaConstructorInsteadOfDsl) {
        ConstructingMechanism.SecondaryConstructor
    } else {
        ConstructingMechanism.DSL
    }


    override fun writeFileSpec(
        classDeclaration: KSClassDeclaration,
        fileGeneratorSpec: FileGeneratorSpec
    ) {
        //if has parent class, will be written together with it's parent class
        if (fileGeneratorSpec.parent != null) {
            fileGeneratorSpecMap[classDeclaration] = fileGeneratorSpec
        } else {
            FileWriter.write(
                logger,
                codeGenerator,
                fileGeneratorSpec
            )
        }
    }
}