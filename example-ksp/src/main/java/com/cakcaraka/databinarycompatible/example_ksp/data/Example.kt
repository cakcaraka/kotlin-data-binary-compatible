package com.cakcaraka.databinarycompatible.example_ksp.data

import com.cakcaraka.databinarycompatible.annotation.DataClass
import com.cakcaraka.databinarycompatible.annotation.DefaultValue
import com.cakcaraka.databinarycompatible.annotation.SealedParentDataClass


@com.cakcaraka.databinarycompatible.example_ksp.annotation.DataClass(
    generatedClassName = "ExampleNotTaken" //this should be ignored and not class
)
@DataClass(

)
interface Example {

    val testWithString: String

    val testWithInt: Int

    val testWithFloat: Float

    val testWithDouble: Double

    val testWithStringNullable: String?

    val testWithIntNullable: Int?

    val testWithFloatNullable: Float?

    val testWithDoubleNullable: Double?

    @DefaultValue(stringValue = "Test")
    val testWithDefaultString: String

    @DefaultValue(stringValue = "Test")
    val testWithDefaultString2: String
}


@SealedParentDataClass
interface SealedExample {
    val test: String

    @DataClass
    interface Sealed1: SealedExample {
        @DefaultValue(stringValue = "Test", immutable = true)
        override val test: String
    }

    @DataClass
    interface Sealed2: SealedExample {
        @DefaultValue(stringValue = "Test", immutable = false)
        override val test: String
    }
}

@DataClass
interface ExampleImmutableKeepSetter {
    @DefaultValue(stringValue = "testKeepSetter", mutability = PropertyMutability.IMMUTABLE_KEEP_SETTER)
    val testKeepSetter: String

    val testRequired: String

    @DefaultValue(stringValue = "testOptional")
    val testOptional: String

}

@DataClass(
    imports = [
        "{originalPackageName}.ExampleImmutableKeepSetter",
        "{originalQualifiedName}.Companion",
    ]
)
interface ExampleImmutableAllDefaultValue {
    val testImmutable: String

    @DefaultValue(rawValue = "Companion.DEFAULT_VALUE_TEST_OPTIONAL", mutability = PropertyMutability.IMMUTABLE_KEEP_SETTER)
    val testOptional: String

    companion object {
        internal const val DEFAULT_VALUE_TEST_OPTIONAL = "123456"
    }
}