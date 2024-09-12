package com.cakcaraka.databinarycompatible.example_ksp.data

import android.os.Parcelable
import com.cakcaraka.databinarycompatible.annotation.DataClass
import com.cakcaraka.databinarycompatible.annotation.DefaultValue
import com.cakcaraka.databinarycompatible.annotation.SealedParentDataClass


@DataClass
interface ExampleWithNoMandatory {
    @DefaultValue(stringValue = "Test")
    val test: String
}

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
}

@com.cakcaraka.databinarycompatible.example_ksp.annotation.DataClass
interface ExampleIgnored {

    val testWithString: String

    val testWithInt: Int

    val testWithFloat: Float

    val testWithDouble: Double

    val testWithStringNullable: String?

    val testWithIntNullable: Int?

    val testWithFloatNullable: Float?

    val testWithDoubleNullable: Double?

}

@DataClass(
    annotations = ["Parcelize"],
    imports = ["kotlinx.parcelize.Parcelize"]
)
interface ExampleAnnotated: Parcelable {
    val test: String

    @DefaultValue(stringValue = "Test")
    val testWithDefaultString: String

    @DefaultValue(rawValue = "5")
    val testWithDefaultInt: Int
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