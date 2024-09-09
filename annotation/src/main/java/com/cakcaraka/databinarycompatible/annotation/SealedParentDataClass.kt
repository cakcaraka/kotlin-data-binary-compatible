package com.cakcaraka.databinarycompatible.annotation

/**
 * Annotation class of Data Binary Compatible for sealed parent of other [DataClass] annotated classes.
 * Classes annotated with this annotation will generate sealed class that will be extended by
 *  other [DataClass] that extends the annotated class
 * Classes annotated with this annotation are required to be Kotlin interface, and cannot be
 *  nested class.
 * The Generated class will implements all interface and annotations added on the annotated class
 *  if you need to add an annotation that would only be applied to the generated class, use [annotations]
 * @param generatedClassName if filled, the generatedClassName would be using this value, if empty,
 *  the class name would need to have a "DBC" postfix
 * @param imports if the generated class needs additional imports
 * @param annotations additional annotations that you want to add to generated class
 *  can be full qualified name, or simple name combined with [imports]
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class SealedParentDataClass(
    val generatedClassName: String = "",
    val imports: Array<String> = [],
    val annotations: Array<String> = []
)