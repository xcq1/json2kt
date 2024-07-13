package com.xcq1.yaml2kt

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.asTypeName
import com.xcq1.yaml2kt.KotlinParameterName.Companion.asKotlinParameterName
import com.xcq1.yaml2kt.KotlinTypeName.Companion.asKotlinTypeName
import org.yaml.snakeyaml.Yaml
import java.io.File

class Yaml2Kt(
    private val source: File,
    private val destinationFolder: File,
    private val packageName: String = "",
    private val preprocessYaml: (Map<Any?, Any?>) -> Map<Any?, Any?> = { it }
) {
    fun convert() {
        val baseObjectName = source.name.removeSuffix(".yaml")
        val parserMap: Map<Any?, Any?> = preprocessYaml(Yaml().load(source.inputStream()))
        val ast = buildASTTypeMap(baseObjectName, parserMap)

        require(ast is ASTObject) { "Map object without ASTObject in tree" }

        FileSpec.builder(packageName, baseObjectName.asKotlinTypeName().toString())
            .addType(ast.toPoetObject(parserMap).build())
            .build().writeTo(destinationFolder)
    }

    private fun buildASTTypeMap(currentKey: Any?, value: Any?): ASTNode =
        when (value) {
            is Map<*, *> -> ASTObject(
                currentKey.asKotlinParameterName(),
                currentKey.asKotlinTypeName(),
                nullable = false,
                value.map { (subKey, subValue) ->
                    subKey.asKotlinParameterName() to buildASTTypeMap(
                        subKey,
                        subValue
                    )
                }.toMap(),
                packageName
            )

            is List<*> -> ASTList(
                currentKey.asKotlinParameterName(),
                value.ifEmpty { listOf(null) }
                    .map { buildASTTypeMap(currentKey.singularize(), it) }
                    .reduce { acc, next -> acc.merge(next) },
                nullable = false
            )

            null -> ASTNull(currentKey.asKotlinParameterName())

            else -> ASTPrimitive(currentKey.asKotlinParameterName(), value::class.asTypeName())
        }

    private fun Any?.singularize(): String =
        if (this is String && endsWith("s"))
            removeSuffix("s")
        else
            "${this}Element"

}