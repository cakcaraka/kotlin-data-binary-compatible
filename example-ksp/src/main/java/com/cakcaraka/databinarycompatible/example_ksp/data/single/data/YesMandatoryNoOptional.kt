package com.cakcaraka.databinarycompatible.example_ksp.data.single.data

import com.cakcaraka.databinarycompatible.annotation.DataClass
import com.cakcaraka.databinarycompatible.annotation.DefaultValue

/**
 *
 * MANDATORY    | OPTIONAL  |MAN NULLABLE   |MAN FINAL MUTABLE   | CLASS
 * Y            | N         |Y              | -                  | [MandatoryNullableNoOptional]
 * N            | N         |N              | -                  | [MandatoryNonNullableNoOptional]
 */


/**
 *
 */
@DataClass
interface MandatoryNullableNoOptional {
    val test: String?
}

@DataClass
interface MandatoryNonNullableNoOptional {
    val test: String
}

@DataClass
interface MandatoryNonNullableNoOptional2 {
    val test: String

    @DefaultValue(stringValue = "test")
    val test2: String
}