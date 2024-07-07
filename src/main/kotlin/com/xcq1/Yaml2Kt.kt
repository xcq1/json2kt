package com.xcq1

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.xcq1.KotlinParameterName.Companion.asKotlinParameterName
import com.xcq1.KotlinTypeName.Companion.asKotlinTypeName
import org.yaml.snakeyaml.Yaml
import java.io.File

class Yaml2Kt(private val source: File, private val destinationFolder: File, private val packageName: String = "") {

    fun convert() {
        val baseObjectName = source.name.removeSuffix(".yaml")
        val parserMap: Map<Any?, Any?> = Yaml().load(source.inputStream())
        val ast = convertParserMapToASTObject(parserMap, baseObjectName)

        FileSpec.builder(packageName, baseObjectName.asKotlinTypeName().toString())
            .addType(ast.rhyme())
            .build().writeTo(destinationFolder)
    }

    data class TypePropertiesSummary(
        val primitives: List<Map.Entry<Any?, Any?>>,
        val maps: List<Map.Entry<Any?, Map<*, *>>>,
        val lists: List<Map.Entry<Any?, List<*>>>,
        val allProperties: List<ASTProperty<out ASTProperty.Expression>>
    )

    private fun partitionTypesAndGenerateProperties(parserMap: Map<*, *>): TypePropertiesSummary {
        val primitives = parserMap.entries.filter { (_, value) -> value !is Map<*, *> && value !is List<*> }

        @Suppress("UNCHECKED_CAST")
        val maps = parserMap.entries.filter { (_, value) -> value is Map<*, *> } as List<Map.Entry<*, Map<*, *>>>

        @Suppress("UNCHECKED_CAST")
        val lists = parserMap.entries.filter { (_, value) -> value is List<*> } as List<Map.Entry<*, List<*>>>

        return TypePropertiesSummary(primitives, maps, lists, primitives.map { (key, value) ->
            ASTProperty(
                key.asKotlinParameterName(),
                value?.let { value::class.asClassName() } ?: ANY.copy(nullable = true),
                ASTProperty.Expression.PrimitiveValue(if (value is String) "%S" else "%L", value)
            )
        } + maps.map { (key, _) ->
            ASTProperty(
                key.asKotlinParameterName(),
                ClassName(packageName, key.asKotlinTypeName().toString()),
                ASTProperty.Expression.DataClassInstanceValue(key.asKotlinTypeName())
            )
        } + lists.map { (key, value) ->
            when {
                value.isEmpty() ->
                    ASTProperty(
                        key.asKotlinParameterName(),
                        LIST.parameterizedBy(ANY.copy(nullable = true)),
                        ASTProperty.Expression.ListOfPrimitivesValue(emptyList<Any?>())
                    )

                value.first() is Map<*, *> -> ASTProperty(
                    key.asKotlinParameterName(),
                    LIST.parameterizedBy(ClassName(packageName, key.asKotlinTypeName(singularize = true).toString())),
                    ASTProperty.Expression.ListOfDataClassInstancesValue(
                        key.asKotlinTypeName(singularize = true), value.size
                    )
                )

                value.first() is List<*> -> error("TODO")

                else -> ASTProperty(
                    key.asKotlinParameterName(),
                    LIST.parameterizedBy(value.first()?.let { it::class.asClassName() }
                        ?: ANY.copy(nullable = true)
                    ),
                    ASTProperty.Expression.ListOfPrimitivesValue(value)
                )
            }

        })
    }

    private fun convertParserMapToASTObject(parserMap: Map<Any?, Any?>, baseObjectName: String): ASTObject {
        val (primitives, maps, lists, properties) = partitionTypesAndGenerateProperties(parserMap)
        return ASTObject(
            name = baseObjectName.asKotlinTypeName(),
            properties = properties,
            children = maps.map { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                convertMapToASTDataClass(value as Map<Any?, Any?>, key.asKotlinTypeName())
            } + lists.filter { (_, value) -> value.firstOrNull() is Map<*, *> }
                .map { (key, value) -> convertListToASTDataClass(value, key.asKotlinTypeName(singularize = true)) }
        )
    }

    private fun convertMapToASTDataClass(parserMap: Map<Any?, Any?>, dataClassType: KotlinTypeName): ASTDataClass {
        val (primitives, maps, lists, properties) = partitionTypesAndGenerateProperties(parserMap)
        return ASTDataClass(
            name = dataClassType,
            valueParameters = listOf(
                ASTProperty(
                    "id".asKotlinParameterName(),
                    String::class.asClassName(),
                    ASTProperty.Expression.DataClassNoValue
                )
            ) + properties.map {
                ASTProperty(it.name, it.type, ASTProperty.Expression.DataClassNoValue)
            },
            children = maps.map { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                convertMapToASTDataClass(value as Map<Any?, Any?>, key.asKotlinTypeName())
            } + lists.filter { (_, value) -> value.firstOrNull() is Map<*, *> }
                .map { (key, value) -> convertListToASTDataClass(value, key.asKotlinTypeName(singularize = true)) },
            companionObject = ASTCompanionObject(
                properties = listOf(
                    ASTProperty(
                        "instance".asKotlinParameterName(),
                        ClassName(packageName, dataClassType.toString()),
                        ASTProperty.Expression.ComplexValue(
                            "${dataClassType}(%S, ${
                                properties.joinToString {
                                    if (it.type == STRING) "%S" else "%L"
                                }
                            })",
                            listOf(dataClassType.originalValue) +
                                    (primitives.map { it.value.toString() }
                                            + maps.map { "${it.key.asKotlinTypeName()}.instance" }
                                            + lists.map { list ->
                                        "listOf(${
                                            if (list.value.first() is Map<*, *>)
                                                (0 until list.value.size).joinToString {
                                                    "${list.key.asKotlinTypeName(singularize = true)}.instance${it}"
                                                }
                                            else
                                                list.value.joinToString {
                                                    if (it is String) "\"$it\"" else it.toString()
                                                }
                                        })"
                                    })
                        )
                    )
                )
            )
        )
    }

    private fun convertListToASTDataClass(parserList: List<Any?>, singularDataClassType: KotlinTypeName): ASTDataClass {
        val elementToMakeTypeOutOf = parserList.firstOrNull() ?: error("TODO handle empty list")
        require(elementToMakeTypeOutOf is Map<*, *>) { "Cannot make primitive list to data class" }
        val (primitives, maps, lists, properties) = @Suppress("UNCHECKED_CAST")
        partitionTypesAndGenerateProperties(elementToMakeTypeOutOf as Map<String, *>)

        return ASTDataClass(
            name = singularDataClassType,
            valueParameters = listOf(
                ASTProperty(
                    "index".asKotlinParameterName(),
                    Int::class.asClassName(),
                    ASTProperty.Expression.DataClassNoValue
                )
            ) + properties.map {
                ASTProperty(it.name, it.type, ASTProperty.Expression.DataClassNoValue)
            },
            children = maps.map { (key, value) ->
                convertMapToASTDataClass(
                    value.mapKeys { (vKey, _) -> vKey.toString() },
                    key.asKotlinTypeName()
                )
            } + lists.filter { (_, value) -> value.firstOrNull() is Map<*, *> }
                .map { (key, value) -> convertListToASTDataClass(value, key.asKotlinTypeName(singularize = true)) },
            companionObject = ASTCompanionObject(
                properties = parserList.mapIndexed { index, it ->
                    val (primitivesOfIt, mapsOfIt, listsOfIt) = @Suppress("UNCHECKED_CAST")
                    partitionTypesAndGenerateProperties(it as Map<String, *>)

                    ASTProperty(
                        "instance$index".asKotlinParameterName(),
                        ClassName(packageName, singularDataClassType.toString()),
                        ASTProperty.Expression.ComplexValue(
                            "${singularDataClassType}(%L, ${
                                properties.joinToString {
                                    if (it.type == STRING) "%S" else "%L"
                                }
                            })",
                            listOf(index) +
                                    (primitivesOfIt.map { it.value.toString() }
                                            + mapsOfIt.map { "${it.key.asKotlinTypeName()}.instance" }
                                            + listsOfIt.map { list ->
                                        "listOf(${
                                            if (list.value.first() is Map<*, *>)
                                                (0 until list.value.size).joinToString {
                                                    "${list.key.asKotlinTypeName(singularize = true)}.instance${it}"
                                                }
                                            else
                                                list.value.joinToString {
                                                    if (it is String) "\"$it\"" else it.toString()
                                                }
                                        })"
                                    })
                        )
                    )
                }
            )
        )
    }
}
