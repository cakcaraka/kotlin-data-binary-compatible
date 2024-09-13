package com.cakcaraka.databinarycompatible.processor

import com.cakcaraka.databinarycompatible.annotation.PropertyMutability

@JvmInline
value class FieldName(
    val fieldName: String
)

data class DefaultValueConfig(
    val fieldName: FieldName,
    val defaultValue: String?,
    val mutability: PropertyMutability,
    val excludeFromEqualsAndGetter: Boolean,
    //if null, means it will be mandatory since version X
    val newRequiredPropertyConfig: NewRequiredPropertyConfig?
)


data class NewRequiredPropertyConfig(
    val sinceVersion: Int,
    // if true, setter will be in optionalBuilder
    val previouslyOptional: Boolean
)