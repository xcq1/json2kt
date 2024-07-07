package com.xcq1

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.yaml.snakeyaml.Yaml
import java.io.File

class Yaml2Kt(private val source: File, private val destinationFolder: File, private val packageName: String = "") {

    fun convert() {
        val baseObjectName = source.name.removeSuffix(".yaml")
        val parserMap: Map<String, Any> = Yaml().load(source.inputStream())
        val ast = convertParserMapToASTObject(parserMap, baseObjectName)

        FileSpec.builder(packageName, baseObjectName)
            .addType(ast.rhyme())
            .build().writeTo(destinationFolder)
    }


    data class TypePropertiesSummary(
        val primitives: List<Map.Entry<String, Any?>>,
        val maps: List<Map.Entry<String, Map<*, *>>>,
        val lists: List<Map.Entry<String, List<*>>>,
        val allProperties: List<ASTProperty<out ASTProperty.Expression>>
    )

    private fun partitionTypesAndGenerateProperties(parserMap: Map<String, *>): TypePropertiesSummary {
        val primitives = parserMap.entries.filter { (_, value) -> value !is Map<*, *> && value !is List<*> }

        @Suppress("UNCHECKED_CAST")
        val maps = parserMap.entries.filter { (_, value) -> value is Map<*, *> } as List<Map.Entry<String, Map<*, *>>>

        @Suppress("UNCHECKED_CAST")
        val lists = parserMap.entries.filter { (_, value) -> value is List<*> } as List<Map.Entry<String, List<*>>>

        return TypePropertiesSummary(primitives, maps, lists, primitives.map { (key, value) ->
            ASTProperty(
                key.kotlinifyIdentifier(),
                value?.let { value::class.asClassName() } ?: ANY.copy(nullable = true),
                ASTProperty.Expression.PrimitiveValue(if (value is String) "%S" else "%L", value)
            )
        } + maps.map { (key, _) ->
            ASTProperty(
                key.kotlinifyIdentifier(),
                ClassName(packageName, key.kotlinifyIdentifier(true)),
                ASTProperty.Expression.DataClassInstanceValue(key.kotlinifyIdentifier(true))
            )
        } + lists.map { (key, value) ->
            when {
                value.isEmpty() ->
                    ASTProperty(
                        key.kotlinifyIdentifier(),
                        LIST.parameterizedBy(ANY.copy(nullable = true)),
                        ASTProperty.Expression.ListOfPrimitivesValue(emptyList<Any?>())
                    )

                value.first() is Map<*, *> -> ASTProperty(
                    key.kotlinifyIdentifier(),
                    LIST.parameterizedBy(ClassName(packageName, key.kotlinifyIdentifier(true).removeSuffix("s"))),
                    ASTProperty.Expression.ListOfDataClassInstancesValue(
                        key.kotlinifyIdentifier(true).removeSuffix("s"), value.size
                    )
                )

                value.first() is List<*> -> error("TODO")

                else -> ASTProperty(
                    key.kotlinifyIdentifier(),
                    LIST.parameterizedBy(value.first()?.let { it::class.asClassName() }
                        ?: error("first list item is null")),
                    ASTProperty.Expression.ListOfPrimitivesValue(value)
                )
            }

        })
    }

    private fun convertParserMapToASTObject(parserMap: Map<String, Any?>, baseObjectName: String): ASTObject {
        val (primitives, maps, lists, properties) = partitionTypesAndGenerateProperties(parserMap)
        return ASTObject(
            name = baseObjectName.kotlinifyIdentifier(true),
            properties = properties,
            children = maps.map { (key, value) ->
                convertMapToASTDataClass(
                    value.mapKeys { (vKey, _) -> vKey.toString() },
                    key
                )
            } + lists.filter { (_, value) -> value.firstOrNull() is Map<*, *> }
                .map { (key, value) -> convertListToASTDataClass(value, key.removeSuffix("s")) }
        )
    }

    private fun convertMapToASTDataClass(parserMap: Map<String, Any?>, dataClassName: String): ASTDataClass {
        val (primitives, maps, lists, properties) = partitionTypesAndGenerateProperties(parserMap)
        return ASTDataClass(
            name = dataClassName.kotlinifyIdentifier(true),
            valueParameters = listOf(
                ASTProperty(
                    "id",
                    String::class.asClassName(),
                    ASTProperty.Expression.DataClassNoValue
                )
            ) + properties.map {
                ASTProperty(it.name, it.type, ASTProperty.Expression.DataClassNoValue)
            },
            children = maps.map { (key, value) ->
                convertMapToASTDataClass(
                    value.mapKeys { (vKey, _) -> vKey.toString() },
                    key
                )
            } + lists.filter { (_, value) -> value.firstOrNull() is Map<*, *> }
                .map { (key, value) -> convertListToASTDataClass(value, key.removeSuffix("s")) },
            companionObject = ASTCompanionObject(
                properties = listOf(
                    ASTProperty(
                        "instance",
                        ClassName(packageName, dataClassName.kotlinifyIdentifier(true)),
                        ASTProperty.Expression.ComplexValue(
                            "${dataClassName.kotlinifyIdentifier(true)}(%S, ${
                                properties.joinToString {
                                    if (it.type == STRING) "%S" else "%L"
                                }
                            })",
                            listOf(dataClassName) +
                                    (primitives.map { it.value.toString() }
                                            + maps.map { it.key.kotlinifyIdentifier(true) + ".instance" }
                                            + lists.map { list ->
                                        "listOf(${
                                            if (list.value.first() is Map<*, *>)
                                                (0 until list.value.size).joinToString {
                                                    list.key.kotlinifyIdentifier(true)
                                                        .removeSuffix("s") + ".instance${it}"
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

    private fun convertListToASTDataClass(parserList: List<Any?>, dataClassName: String): ASTDataClass {
        val elementToMakeTypeOutOf = parserList.firstOrNull() ?: error("TODO handle empty list")
        require(elementToMakeTypeOutOf is Map<*, *>) { "Cannot make primitive list to data class" }
        val (primitives, maps, lists, properties) = @Suppress("UNCHECKED_CAST")
        partitionTypesAndGenerateProperties(elementToMakeTypeOutOf as Map<String, *>)

        return ASTDataClass(
            name = dataClassName.kotlinifyIdentifier(true),
            valueParameters = listOf(
                ASTProperty(
                    "index",
                    Int::class.asClassName(),
                    ASTProperty.Expression.DataClassNoValue
                )
            ) + properties.map {
                ASTProperty(it.name, it.type, ASTProperty.Expression.DataClassNoValue)
            },
            children = maps.map { (key, value) ->
                convertMapToASTDataClass(
                    value.mapKeys { (vKey, _) -> vKey.toString() },
                    key
                )
            } + lists.filter { (_, value) -> value.firstOrNull() is Map<*, *> }
                .map { (key, value) -> convertListToASTDataClass(value, key.removeSuffix("s")) },
            companionObject = ASTCompanionObject(
                properties = parserList.mapIndexed { index, it ->
                    val (primitivesOfIt, mapsOfIt, listsOfIt) = @Suppress("UNCHECKED_CAST")
                    partitionTypesAndGenerateProperties(it as Map<String, *>)

                    ASTProperty(
                        "instance$index",
                        ClassName(packageName, dataClassName.kotlinifyIdentifier(true)),
                        ASTProperty.Expression.ComplexValue(
                            "${dataClassName.kotlinifyIdentifier(true)}(%L, ${
                                properties.joinToString {
                                    if (it.type == STRING) "%S" else "%L"
                                }
                            })",
                            listOf(index) +
                                    (primitivesOfIt.map { it.value.toString() }
                                            + mapsOfIt.map { it.key.kotlinifyIdentifier(true) + ".instance" }
                                            + listsOfIt.map { list ->
                                        "listOf(${
                                            if (list.value.first() is Map<*, *>)
                                                (0 until list.value.size).joinToString {
                                                    list.key.kotlinifyIdentifier(true)
                                                        .removeSuffix("s") + ".instance${it}"
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

    private fun String.kotlinifyIdentifier(uppercase: Boolean = false) =
        replace("-([a-z])".toRegex()) { it.groupValues[1].uppercase() }.let {
            if (uppercase)
                it.replaceFirstChar { c -> c.uppercase() }
            else
                it
        }
}
