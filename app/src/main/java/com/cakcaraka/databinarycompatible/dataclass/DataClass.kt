package com.cakcaraka.databinarycompatible.dataclass


/**
 * Test that annotation same name but different package shouldn't be picked up
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class DataClass(
    val generatedClassName: String = ""
)