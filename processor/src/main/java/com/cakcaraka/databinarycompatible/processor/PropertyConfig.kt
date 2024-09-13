package com.cakcaraka.databinarycompatible.processor

import com.cakcaraka.databinarycompatible.annotation.PropertyMutability
import com.squareup.kotlinpoet.TypeName

data class PropertyConfig(
    val typeName: TypeName,
    val defaultValueConfig: DefaultValueConfig?,
    val hasOverridesModifier: Boolean,
    val kDoc: String,
) {

    val defaultValue: String? = defaultValueConfig?.defaultValue

    val mandatorySinceVersion: Int? = if (defaultValueConfig == null) {
        1
    } else if (defaultValueConfig.newRequiredPropertyConfig != null) {
        defaultValueConfig.newRequiredPropertyConfig.sinceVersion
    } else {
        null
    }

    val mandatoryForBuilderConstructor =
        defaultValueConfig == null || defaultValueConfig.mutability == PropertyMutability.IMMUTABLE_RECENTLY || defaultValueConfig.newRequiredPropertyConfig != null

    val mandatoryForConstructor =
        defaultValueConfig == null || defaultValueConfig.mutability == PropertyMutability.IMMUTABLE_RECENTLY

    val mutability: PropertyMutability =
        defaultValueConfig?.mutability ?: PropertyMutability.MUTABLE

    /**
     * Must not be IMMUTABLE
     * Optional if defaultValueConfig is not null and newRequiredPropertyConfig == null
     * Optional if defaultValueConfig is not null and newRequiredPropertyConfig.previouslyOptional == true
     */
    val optionalBuilderParam =
        (mutability != PropertyMutability.IMMUTABLE) && (defaultValueConfig != null) && (defaultValueConfig.newRequiredPropertyConfig == null || defaultValueConfig.newRequiredPropertyConfig.previouslyOptional)

    val excludesFromEqualsAndGetter = defaultValueConfig?.excludeFromEqualsAndGetter ?: false

    val isMutableEmptySetter: Boolean = mutability != PropertyMutability.MUTABLE

    val isAnnotatedMutable: Boolean =
        mutability == PropertyMutability.MUTABLE || mutability == PropertyMutability.IMMUTABLE_RECENTLY
}
