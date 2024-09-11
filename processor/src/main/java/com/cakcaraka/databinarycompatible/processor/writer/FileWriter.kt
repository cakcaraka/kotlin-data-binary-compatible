package com.cakcaraka.databinarycompatible.processor.writer

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

object FileWriter {

    fun write(
        logger: KSPLogger,
        codeGenerator: CodeGenerator,
        fileGeneratorSpec: FileGeneratorSpec
    ) {
        writeFile(
            codeGenerator,
            fileGeneratorSpec.packageName,
            fileGeneratorSpec.className,
            fileGeneratorSpec.classBuilder,
            fileGeneratorSpec.initializer?.let {
                listOf(it)
            } ?: emptyList(),
            fileGeneratorSpec.imports
        )
    }


    fun writeNested(
        logger: KSPLogger,
        codeGenerator: CodeGenerator,
        nestedClassNameMapping: MutableMap<ClassName, ClassName>,
        fileGeneratorSpecMap: MutableMap<KSAnnotated, FileGeneratorSpec>
    ) {
        fun getFinalClassName(className: ClassName): ClassName {
            return nestedClassNameMapping[className] ?: className
        }

        val mergedMap: MutableMap<String, ParentWithChild> = mutableMapOf()

        fileGeneratorSpecMap.values.sortedBy {
            it.parent != null
        }.forEach { spec ->
            if (spec.parent == null) {
                val finalClassName = getFinalClassName(spec.classDeclaration.toClassName())
                val key = finalClassName.let {
                    it.packageName + "." + it.simpleName
                }
                mergedMap[key] = ParentWithChild(
                    spec,
                    mutableListOf()
                )
            } else {
                val finalClassName = getFinalClassName(spec.parent.toClassName())
                val key = finalClassName.let {
                    it.packageName + "." + it.simpleName
                }
                if (mergedMap[key] != null) {
                    mergedMap[key]?.apply {
                        children.add(spec)
                    }
                } else {
                    logger.error("MergedMap with $key not shown")
                }
            }
        }

        mergedMap.forEach { _, spec ->
            val parentSpec = spec.parent ?: return@forEach

            val initializationSpecs = mutableListOf<FunSpec.Builder>().apply {
                if (parentSpec.initializer != null) {
                    add(parentSpec.initializer)
                }
            }

            val importSpecs = mutableListOf<String>().apply {
                addAll(parentSpec.imports)
            }

            val parentSpecClassBuilder = parentSpec.classBuilder

            val childInitializer = mutableListOf<FunSpec.Builder>()

            spec.children.forEach { child ->
                child.initializer?.also {
                    childInitializer.add(it)
                }
                importSpecs.addAll(child.imports)

                parentSpecClassBuilder.addType(
                    child.classBuilder.build()
                )
            }

            if(childInitializer.isNotEmpty()) {
                parentSpecClassBuilder.addType(
                    TypeSpec.companionObjectBuilder()
                        .addKdoc(
                            """
                            Public Companion Object of [${parentSpec.className}].
                            """.trimIndent()
                        ).apply {
                            childInitializer.forEach {
                                addFunction(
                                    it.addAnnotation(JvmStatic::class).build()
                                )
                            }
                        }
                        .build()
                )
            }

            writeFile(
                codeGenerator,
                parentSpec.packageName,
                parentSpec.className,
                parentSpecClassBuilder,
                initializationSpecs,
                importSpecs
            )
        }
    }


    private fun writeFile(
        codeGenerator: CodeGenerator,
        packageName: String,
        className: String,
        classBuilder: TypeSpec.Builder,
        initializationSpecs: List<FunSpec.Builder>,
        importSpecs: List<String>
    ) {
        val fileBuilder = FileSpec.builder(packageName, className)
            .addImport("java.util", "Objects")
            .addType(classBuilder.build())
            .apply {
                initializationSpecs.forEach {
                    addFunction(
                        it.addAnnotation(JvmSynthetic::class).build()
                    )
                }
            }

        importSpecs.forEach {
            fileBuilder
                .addImport(
                    it.split(".").dropLast(1).joinToString("."),
                    it.split(".").last()
                )
        }

        fileBuilder.build().writeTo(codeGenerator = codeGenerator, aggregating = false)
    }

    private data class ParentWithChild(
        val parent: FileGeneratorSpec?,
        val children: MutableList<FileGeneratorSpec>
    )
}