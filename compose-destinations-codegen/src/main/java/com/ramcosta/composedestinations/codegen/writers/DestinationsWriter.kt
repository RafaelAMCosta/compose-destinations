package com.ramcosta.composedestinations.codegen.writers

import com.ramcosta.composedestinations.codegen.facades.CodeOutputStreamMaker
import com.ramcosta.composedestinations.codegen.model.*
import com.ramcosta.composedestinations.codegen.writers.helpers.ImportableHelper
import com.ramcosta.composedestinations.codegen.writers.helpers.NavArgResolver

class DestinationsWriter(
    private val codeGenConfig: CodeGenConfig,
    private val codeGenerator: CodeOutputStreamMaker,
    private val isBottomSheetDependencyPresent: Boolean,
    private val customNavTypeByType: Map<Type, CustomNavType>,
) {

    fun write(
        destinations: List<DestinationGeneratingParamsWithNavArgs>,
    ): List<GeneratedDestination> {
        val generatedFiles = mutableListOf<GeneratedDestination>()

        destinations.forEach { destination ->
            val importableHelper = ImportableHelper()
            val generatedDestination = SingleDestinationWriter(
                codeGenConfig,
                codeGenerator,
                isBottomSheetDependencyPresent,
                NavArgResolver(customNavTypeByType, importableHelper),
                destination,
                customNavTypeByType,
                importableHelper
            ).write()

            generatedFiles.add(generatedDestination)
        }

        return generatedFiles
    }
}