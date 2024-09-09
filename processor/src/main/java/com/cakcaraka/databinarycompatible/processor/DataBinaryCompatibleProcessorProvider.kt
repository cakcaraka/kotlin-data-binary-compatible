package com.cakcaraka.databinarycompatible.processor

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider


@AutoService(SymbolProcessorProvider::class)
class DataBinaryCompatibleProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return DataBinaryCompatibleProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}


