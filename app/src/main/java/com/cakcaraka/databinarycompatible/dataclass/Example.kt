package com.cakcaraka.databinarycompatible.dataclass

import android.os.Parcelable
import com.cakcaraka.databinarycompatible.annotation.DataClass
import com.cakcaraka.databinarycompatible.annotation.DefaultValue
import com.cakcaraka.databinarycompatible.annotation.SealedParentDataClass


@com.cakcaraka.databinarycompatible.dataclass.DataClass(
    generatedClassName = "ExampleNotTaken" //this should be ignored and not class
)
@DataClass(

)
interface Example_dc {

    val testWithString: String

    val testWithInt: Int

    val testWithFloat: Float

    val testWithDouble: Double

    val testWithStringNullable: String?

    val testWithIntNullable: Int?

    val testWithFloatNullable: Float?

    val testWithDoubleNullable: Double?
}

@com.cakcaraka.databinarycompatible.dataclass.DataClass
interface ExampleIgnored_dc {

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
interface ExampleAnnotated_dc: Parcelable {
    val test: String

    @DefaultValue(stringValue = "Test")
    val testWithDefaultString: String

    @DefaultValue(rawValue = "5")
    val testWithDefaultInt: Int
}


@SealedParentDataClass
interface SealedExample_dc {
    val test: String

    @DataClass
    interface Sealed1_dc: SealedExample_dc {
        @DefaultValue(stringValue = "Test")
        override val test: String
    }
}