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
 * @param mutability representation the mutability
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class DefaultValue(
    val rawValue: String = "",
    val stringValue: String = "",
    val mutability: PropertyMutability = PropertyMutability.MUTABLE
)

/**
 * [MUTABLE] property is mutable, Builder will generate setter, Constructor will have parameter
 * [IMMUTABLE] property is immutable, Builder will not generate setter, Constructor will not have
 *  parameter
 * [IMMUTABLE_RECENTLY] property is immutable, but previously mutable. Builder will generate setter, Constructor will
 *  have parameter, but value will not be used, this settings it to keep backwards compatible
 *  if previous immutability is MUTABLE but changed to IMMUTABLE
 */
enum class PropertyMutability {
    MUTABLE,
    IMMUTABLE,
    IMMUTABLE_RECENTLY,
}

/**
 * By default, the parameter annotated with DataClass will be include on equals and hashCode,
 * Add this annotation to exclude the paramater from the equals and hashCode
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class ExcludeFromEqualsHashCode

/**
 * If you have a new mandatory property, annotated the property with this annotation to achieve
 *  - Generate new constructor that has this mandatory property (based on [sinceVersion])
 *  - Previous constructor without this param still exist (to maintain backwards compatibility)
 *   and will use the default value specific in [defaultValue]
 *
 *   @param previouslyOptional if true, property setter will be added in OptionalPropertiesBuilder
 *    to keep backwards compatibility
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class NewRequiredProperty(
    val sinceVersion: Int,
    val defaultValue: DefaultValue,
    val previouslyOptional: Boolean = false
)
