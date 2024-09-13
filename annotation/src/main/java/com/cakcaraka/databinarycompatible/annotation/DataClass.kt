package com.cakcaraka.databinarycompatible.annotation

/**
 * Possible changes:
 * 1) change nullability [cannot support]
 * 2) change data type [cannot support]
 * 3) previously mutable,now Immutable
 *  - Behavior
 *      - need default value
 *      - existing constructor will exist,but parameter ignored and use default value directly
 *      - cannot create new secondary constructor (remove parameter) since may clash signature
 *      - builder method still exist but will do nothing
 *  - How to achieve
 *      - Annotated with [DefaultValue] with mutability IMMUTABLE_RECENTLY
 * 4) previously Immutable,now mutable
 *  - Behavior
 *      - still need default value (used on old constructor)
 *      - (if mandatory) create new constructor
 *      - builder will create new method (on mandatory / optional depends on type)
 *  - How to achieve
 *      - Move property declaration to last field
 *      - Annotate with [DefaultValue] with mutability MUTABLE
 *      - Annotate with [PropertyMetadata] with sinceVersion = {version}
 * 5) new optional field
 *  - Behavior
 *      - no constructor change
 *      - builder will be in optional builder
 *  - How to achieve
 *      - Move property declaration to last field
 *      - Annotate with [DefaultValue] with mutability MUTABLE
 * 6) new mandatory field
 *  - Behavior
 *      - need default value  (For BC)
 *      - previous constructor will use default value
 *      - create new constructor
 *      - builder will be in mandatorybuilder
 *  - How to achieve
 *      - Add property declaration to last field
 *      - Annotate with [DefaultValue] with mutability MUTABLE
 *      - Annotate with [PropertyMetadata] with sinceVersion = {version}
 * 7) change optional to mandatory
 *  - Behavior
 *      - need default value (For BC)
 *      - previous optional constructor still exist  (For BC)
 *      - create constructor for the mandatory
 *      - builder still in Optional Builder (For BC)
 *  - How to achieve
 *      - Move property declaration to last field
 *      - Annotate with [DefaultValue] with mutability MUTABLE and previouslyOptional = true
 *      - Annotate with [PropertyMetadata] with sinceVersion = {version}
 * 8) change mandatory to optional (mutable) // for immutable can option (3)
 *  - Behavior
 *      - previous constructor exist. (For BC)
 *      - cannot create new constructor (remove parameter) since might clash signature
 *      - builder set moved to optional
 *      - Technically kinda useless since can be set on constructor directly --"
 *  - How to achieve
 *      - Not supported since it complicates things.
 */

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
 *  @param version data class version, please increase if there's a breaking change in the data class,
 *   new properties should be annotated with [DefaultValuesPerVersion]
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class DataClass(
    val generatedClassName: String = "",
    val imports: Array<String> = [],
    val annotations: Array<String> = [],
    val version: Int = 1
)