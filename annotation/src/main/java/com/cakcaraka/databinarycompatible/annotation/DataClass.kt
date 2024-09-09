package com.cakcaraka.databinarycompatible.annotation


/**
 * Annotation class of Data Binary Compatible.
 * Classes annotated with this annotation will generate class containing "hashCode", "equals" , "toString"
 *  and "copy" similar to data class, but the generated class will be Binary Compatible.
 * Classes annotated with this annotation are required to be Kotlin interfacee, and cannot be
 *  nested class except if it's nested under [SealedParentDataClass] for sealed class
 * The Generated class will implements all interface and annotations added on the annotated class,
 *  if you need to add an annotation that would only be applied to the generated class, use [annotations]
 * @param generatedClassName if filled, the generatedClassName would be using this value, if empty,
 *  the class name would need to have a "DBC" postfix
 * @param imports if the generated class needs additional imports
 * @param annotations additional annotations that you want to add to generated class
 *  can be full qualified name, or simple name combined with [imports]
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class DataClass(
    val generatedClassName: String = "",
    val imports: Array<String> = [],
    val annotations: Array<String> = []
)