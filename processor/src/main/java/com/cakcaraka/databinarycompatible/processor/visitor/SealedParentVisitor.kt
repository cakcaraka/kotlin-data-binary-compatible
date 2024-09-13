package com.cakcaraka.databinarycompatible.processor.visitor

import com.cakcaraka.databinarycompatible.annotation.SealedParentDataClass
import com.cakcaraka.databinarycompatible.processor.DataBinaryCompatibleProcessor.Config
import com.cakcaraka.databinarycompatible.processor.writer.FileGeneratorSpec
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ksp.toClassName
import kotlin.reflect.KClass


internal class SealedParentVisitor(
    private val logger: KSPLogger,
    defaultValuesMap: Map<KSClassDeclaration, MutableMap<String, Pair<String?, Boolean>>>,
    private val nestedClassNameMapping: MutableMap<ClassName, ClassName>,
    private val fileGeneratorSpecMap: MutableMap<KSAnnotated, FileGeneratorSpec>,
    config: Config
) : BaseVisitor(logger, defaultValuesMap, nestedClassNameMapping, config) {

    override val annotationClass: KClass<*> = SealedParentDataClass::class

    override val additionalClassModifier: KModifier? = KModifier.SEALED

    override val isAddSelfAsSuperInterface: Boolean = false

    override val constructingMechanism: ConstructingMechanism? = null

    override fun writeFileSpec(
        classDeclaration: KSClassDeclaration,
        fileGeneratorSpec: FileGeneratorSpec
    ) {
        fileGeneratorSpecMap[classDeclaration] = fileGeneratorSpec

        nestedClassNameMapping[classDeclaration.toClassName()] =
            ClassName(fileGeneratorSpec.packageName, fileGeneratorSpec.className)
    }

    override fun isInvalidAnnotatedSetup(
        annotationName: String?,
        classDeclaration: KSClassDeclaration,
        config: Config,
        generatedClassName: String?
    ): Boolean {
        if (classDeclaration.parentDeclaration is KSClassDeclaration) {
            logger.error(
                "@SealedParentDataClass cannot be nested class ${classDeclaration.parentDeclaration.toString()}",
                classDeclaration
            )
            return true
        }

        return super.isInvalidAnnotatedSetup(
            annotationName,
            classDeclaration,
            config,
            generatedClassName
        )
    }
}
