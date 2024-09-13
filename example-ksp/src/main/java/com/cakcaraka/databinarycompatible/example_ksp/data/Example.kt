package com.cakcaraka.databinarycompatible.example_ksp.data

import com.cakcaraka.databinarycompatible.annotation.DataClass
import com.cakcaraka.databinarycompatible.annotation.DefaultValue
import com.cakcaraka.databinarycompatible.annotation.NewRequiredProperty
import com.cakcaraka.databinarycompatible.annotation.PropertyMutability
import com.cakcaraka.databinarycompatible.annotation.SealedParentDataClass


@com.cakcaraka.databinarycompatible.example_ksp.annotation.DataClass(
    generatedClassName = "ExampleNotTaken" //this should be ignored and not class
)
@DataClass(
    version = 1231312
)
interface Example {

    /**
     * Huba huba
     */
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
    interface Sealed1 : SealedExample {
        @DefaultValue(stringValue = "Test", mutability = PropertyMutability.IMMUTABLE_RECENTLY)
        override val test: String
    }

    @DataClass
    interface Sealed2 : SealedExample {
        @DefaultValue(stringValue = "Test", mutability = PropertyMutability.IMMUTABLE)
        override val test: String
    }
}

@DataClass
interface ExampleImmutableRecently {
    @DefaultValue(
        stringValue = "testKeepSetter",
        mutability = PropertyMutability.IMMUTABLE_RECENTLY
    )
    val testImmutableRecently: String

    val testRequired: String

    @DefaultValue(stringValue = "testOptional")
    val testOptional: String

}

@DataClass(
    imports = [
        "{originalPackageName}.ExampleImmutableRecently",
        "{originalQualifiedName}.Companion",
    ]
)
interface ExampleImmutableAllDefaultValue {
    val testImmutable: String

    @DefaultValue(
        rawValue = "Companion.DEFAULT_VALUE_TEST_OPTIONAL",
        mutability = PropertyMutability.IMMUTABLE_RECENTLY
    )
    val testOptional: String

    companion object {
        internal const val DEFAULT_VALUE_TEST_OPTIONAL = "123456"
    }
}


@DataClass()
interface ExampleEmpty {
    @DefaultValue(stringValue = "TestAA", mutability = PropertyMutability.IMMUTABLE_RECENTLY)
    val testA: String

    @DefaultValue(stringValue = "Test", mutability = PropertyMutability.IMMUTABLE)
    val test: String

    @NewRequiredProperty(
        2,
        DefaultValue(stringValue = "Test3", mutability = PropertyMutability.IMMUTABLE_RECENTLY)
    )
    val test3: String

    @NewRequiredProperty(
        2,
        DefaultValue(stringValue = "Test4", mutability = PropertyMutability.MUTABLE),
        previouslyOptional = true
    )
    val test4: String
}