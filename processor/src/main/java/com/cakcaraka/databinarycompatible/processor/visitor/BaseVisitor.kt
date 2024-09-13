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
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
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

    abstract val annotationClass: KClass<*>

    abstract val additionalClassModifier: KModifier?

    abstract val isAddSelfAsSuperInterface: Boolean

    abstract val constructingMechanism: ConstructingMechanism?

    private fun isAddOverrideModifierToProperties(): Boolean {
        return constructingMechanism != null
    }

    private fun initializeProperty(): Boolean {
        return constructingMechanism != null
    }

    abstract fun writeFileSpec(
        classDeclaration: KSClassDeclaration, fileGeneratorSpec: FileGeneratorSpec
    )


    @Suppress("LongMethod", "MaxLineLength", "ComplexMethod")
    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
        super.visitClassDeclaration(classDeclaration, data)

        val annotatedClassData = classDeclaration.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationClass.qualifiedName
        }

        val originalClassName = classDeclaration.qualifiedName

        val generatedClassName: String? = (annotatedClassData?.arguments?.firstOrNull {
            it.name?.getShortName() == "generatedClassName"
        }?.value as? String).takeIf { it.isNullOrEmpty().not() }

        if (isInvalidAnnotatedSetup(
                annotationClass.simpleName, classDeclaration, config, generatedClassName
            )
        ) {
            return
        }

        val className = generatedClassName ?: classDeclaration.simpleName.asString()
            .dropLast(config.defaultRequiredSuffix.length)
        val classKdoc = classDeclaration.docString
        val packageName = classDeclaration.packageName.asString().removeSuffix(
            config.defaultDropPackagesSuffix.firstOrNull {
                classDeclaration.packageName.asString().endsWith(it)
            }.orEmpty()
        )

        // Resolve list of imports]
        val imports = ArrayList<String>()
        annotatedClassData?.arguments?.firstOrNull {
            it.name?.getShortName() == "imports"
        }?.value?.let { it as ArrayList<String>}?.let {
            it.forEach {
                imports.add(
                    it.replace(
                        "{originalPackageName}",
                        classDeclaration.packageName.asString()
                    ).replace(
                        "{originalQualifiedName}",
                        classDeclaration.qualifiedName?.asString().orEmpty()
                    )
                )
            }
        }


        val otherAnnotations = classDeclaration.annotations.filter {
            it.annotationType.resolve().declaration.qualifiedName?.asString() != annotationClass.qualifiedName
        }

        val additionalAnnotations = annotatedClassData?.arguments?.firstOrNull {
            it.name?.getShortName() == "annotations"
        }?.value?.let { it as? ArrayList<String> }?.map { annotation ->
            val importPath = imports.find {
                it != annotation && it.endsWith(annotation)
            }

            val packagePath = importPath?.split('.')?.dropLast(1)?.joinToString(".") ?: ""

            ClassName(packagePath, annotation)
        }

        val implementedInterfaces =
            classDeclaration.superTypes.filter { (it.resolve().declaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE }

        // Map KSP properties with KoltinPoet TypeNames
        val propertyMap = mutableMapOf<KSPropertyDeclaration, PropertyConfig>()
        for (property in classDeclaration.getAllProperties()) {
            val classTypeParams = classDeclaration.typeParameters.toTypeParameterResolver()
            val typeName = property.type.resolve().toTypeName(classTypeParams)

            val addOverridesModifier =
                Modifier.OVERRIDE in property.modifiers || isAddOverrideModifierToProperties()

            propertyMap[property] = PropertyConfig(
                typeName = typeName,
                mandatoryForConstructor = defaultValuesMap[classDeclaration]?.get(property.toString()) == null,
                defaultValue = defaultValuesMap[classDeclaration]?.get(property.toString())?.first,
                isMutable = defaultValuesMap[classDeclaration]?.get(property.toString())?.second?.not()
                    ?: true,
                hasOverridesModifier = addOverridesModifier,
                kDoc = property.docString?.trim(' ', '\n') ?: property.toString()
                    .capitalizeAndAddSpaces(),
            )
        }

        var classNameObject = ClassName(packageName, className)
        val classBuilder = TypeSpec.classBuilder(className).apply {
            additionalClassModifier?.also {
                addModifiers(it)
            }

            addKdoc("\nGenerated from [${originalClassName?.asString()}]\n")
            classKdoc?.let {
                addKdoc(classKdoc.split("\n").filter { it.isNotEmpty() }
                    .joinToString(separator = "\n", transform = {
                        if (it.startsWith(" ")) {
                            it.substring(1)
                        } else {
                            it
                        }
                    }))
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

            // Property Initializer
            for (entry in propertyMap) {
                addProperty(
                    PropertySpec.builder(entry.key.toString(), entry.value.typeName)
                        .addKdoc(
                            """
                                |${entry.value.kDoc}
                                """.trimMargin()
                        ).apply {
                            if (initializeProperty()) {
                                initializer(entry.key.toString())
                            } else {
                                addModifiers(KModifier.ABSTRACT)
                            }
                        }.build()
                )
            }

            // Function toString
            addFunction(
                VisitorUtils.generateToString(
                    classNameObject, propertyMap
                ).build()
            )

            // Function equals
            addFunction(
                VisitorUtils.generateEquals(
                    classNameObject, propertyMap
                ).build()
            )

            // Function hashCode
            addFunction(
                VisitorUtils.generateHashCode(
                    propertyMap
                ).build()
            )
        }

        val constructingFunctions = VisitorUtils.generateConstructingFunctions(
            classNameObject,
            propertyMap,
            constructingMechanism
        )?.also {
            it.builderSpec.first.forEach { builder ->
                classBuilder.addType(builder.build())
            }
            it.builderSpec.second?.also { builderFunc ->
                classBuilder.addFunction(builderFunc.build())
            }
            classBuilder.primaryConstructor(it.primaryConstructor.build())
            classBuilder.addFunction(it.copyMethod.build())

            it.additionalConstructors.forEach { additionalConstructor ->
                classBuilder.addFunction(additionalConstructor.build())
            }

            it.primaryConstructor.also {
                classBuilder.primaryConstructor(it.build())
            }
        }

        val fileSpec = FileGeneratorSpec(
            classDeclaration,
            packageName,
            className,
            imports,
            classBuilder,
            constructingFunctions?.initializer,
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
                "@{$annotationName} must target classes with a qualified name", classDeclaration
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
                "@{$annotationName} target shouldn't have type parameters", classDeclaration
            )
            return true
        }

        if (config.defaultDropPackagesSuffix.isNotEmpty() && config.defaultDropPackagesSuffix.none {
                classDeclaration.packageName.asString().endsWith(it)
            }) {
            logger.error(
                "@{$annotationName} target's package must end with either ${config.defaultDropPackagesSuffix}" + " suffix naming, to change please use ksp args data_binary_compatible_drop_packages_suffix",
                classDeclaration
            )
            return true
        }

        if (generatedClassName.isNullOrEmpty() && !classDeclaration.simpleName.asString()
                .endsWith(config.defaultRequiredSuffix)
        ) {
            logger.error(
                "@{$annotationName} target must end with ${config.defaultRequiredSuffix}" + " suffix naming or has generatedClassName property, to change suffix " + "please use ksp args data_binary_compatible_required_suffix",
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

    internal sealed class ConstructingMechanism {
        data object DSL : ConstructingMechanism()
        data object SecondaryConstructor : ConstructingMechanism()
    }
}
