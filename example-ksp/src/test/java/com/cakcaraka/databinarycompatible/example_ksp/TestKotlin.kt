package com.cakcaraka.databinarycompatible.example_ksp

import com.cakcaraka.databinarycompatible.example_ksp.data.single.MandatoryNonNullableNoOptional2

class TestKotlin {

    fun test() {
        MandatoryNonNullableNoOptional2("test") {
            setTest2("test")
            test2 = "1213"
        }
    }
}