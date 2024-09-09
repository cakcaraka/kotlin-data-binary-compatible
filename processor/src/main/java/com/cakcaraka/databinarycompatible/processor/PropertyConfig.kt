package com.cakcaraka.databinarycompatible.processor

import com.squareup.kotlinpoet.TypeName

data class PropertyConfig(
    val typeName: TypeName,
    val mandatoryForConstructor: Boolean,
    val hasOverridesModifier: Boolean,
    val defaultValue: String?,
    val isMutable: Boolean,
    val kDoc: String,
)
