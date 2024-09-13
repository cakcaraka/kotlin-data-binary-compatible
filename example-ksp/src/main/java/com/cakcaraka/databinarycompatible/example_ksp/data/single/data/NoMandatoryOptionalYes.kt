package com.cakcaraka.databinarycompatible.example_ksp.data.single.data

import com.cakcaraka.databinarycompatible.annotation.DataClass
import com.cakcaraka.databinarycompatible.annotation.DefaultValue
import com.cakcaraka.databinarycompatible.annotation.PropertyMutability

/**
 *
 * MANDATORY    | OPTIONAL  |OPT NULLABLE   |OPT FINAL MUTABLE   | CLASS
 * N            | Y         |Y              | Y                  | [NoMandatoryOptionalNullableFinal]
 * N            | Y         |Y              | N                  | [NoMandatoryOptionalNullableNonFinal]
 * N            | Y         |N              | Y                  | [NoMandatoryOptionalNonNullableFinal]
 * N            | Y         |N              | N                  | [NoMandatoryOptionalNonNullableNonFinal]
 */


/**
 *
 */
@DataClass
interface NoMandatoryOptionalNullableFinal {
    @DefaultValue(stringValue = "Test", mutability = PropertyMutability.IMMUTABLE)
    val test: String?
}

@DataClass
interface NoMandatoryOptionalNullableNonFinal {
    @DefaultValue(rawValue = "null")
    val test: String?
}

@DataClass
interface NoMandatoryOptionalNonNullableFinal {
    @DefaultValue(stringValue = "null", mutability = PropertyMutability.IMMUTABLE)
    val test: String
}

@DataClass
interface NoMandatoryOptionalNonNullableNonFinal {
    @DefaultValue(stringValue = "Testtt")
    val test: String
}