package com.cakcaraka.databinarycompatible.annotation

/**
 * Default value represented as a String.
 * Workaround for KSP not supporting class default parameters:
 * https://github.com/google/ksp/issues/642
 *
 * Could be applied only for passing default values in the [DataClass] or [SealedParentDataClass] property.
 *
 * @param rawValue raw value representation of the default value, will be copy pasted as default
 * value in the generated code, if the default value is string, can use [stringValue] instead of [rawValue]
 * @param stringValue default value if it's string
 * @param immutable representation of whether the value can be changed or not, if true, setter will be removed
 * from Builder method
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class DefaultValue(
    val rawValue: String = "",
    val stringValue: String = "",
    val immutable: Boolean = false
)
