package com.cakcaraka.databinarycompatible.processor.writer

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec


data class FileGeneratorSpec(
    val classDeclaration: KSClassDeclaration,
    val packageName: String,
    val className: String,
    val imports: List<String>,
    val classBuilder: TypeSpec.Builder,
    val initializer: FunSpec?,
    val parent: KSClassDeclaration?
)
