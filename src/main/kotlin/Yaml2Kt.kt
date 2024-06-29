package org.example

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.yaml.snakeyaml.Yaml
import java.io.File

class Yaml2Kt(private val source: File, private val destinationFolder: File) {
    fun convert() {
        val baseObjectName = source.name.removeSuffix(".yaml").replaceFirstChar { it.uppercase() }
        val baseMap: Map<String, Any> = Yaml().load(source.inputStream())

        FileSpec.builder("", baseObjectName).apply {
            val baseObj = buildTypeFromMap(baseMap, baseObjectName, TypeType.OBJECT)
            addType(baseObj.typeSpec)
            baseObj.propertySpec?.let { addProperty(baseObj.propertySpec) }
        }.build().writeTo(destinationFolder)
    }

    enum class TypeType {
        OBJECT, DATA_CLASS
    }

    private fun String.kotlinifyIdentifier(uppercase: Boolean = false) =
        replace("-([a-z])".toRegex()) { it.groupValues[1].uppercase() }.let {
            if (uppercase)
                it.replaceFirstChar { c -> c.uppercase() }
            else
                it
        }

    data class BuildTypeFromMapResult(
        val typeSpec: TypeSpec,
        val propertySpec: PropertySpec?
    )

    @Suppress("UNCHECKED_CAST")
    private fun buildTypeFromMap(
        map: Map<String, Any>,
        name: String,
        type: TypeType
    ): BuildTypeFromMapResult {
        val typeBuilder = when (type) {
            TypeType.OBJECT ->
                TypeSpec.objectBuilder(name.kotlinifyIdentifier(true))

            TypeType.DATA_CLASS ->
                TypeSpec.classBuilder(name.kotlinifyIdentifier(true)).addModifiers(KModifier.DATA)
        }

        val properties = buildList {
            add(PropertySpec.builder("id", String::class).initializer("%S", name).build())
            map.forEach { (key, value) ->
                when (value) {
                    is Map<*, *> -> {
                        val subType = buildTypeFromMap(
                            value.mapKeys { (key, _) -> key.toString() } as Map<String, Any>,
                            key,
                            TypeType.DATA_CLASS
                        )
                        typeBuilder.addType(subType.typeSpec)
                        subType.propertySpec?.let { add(it) }
                    }

                    is List<*> -> {
                        val subType = buildListOfDataClassFromList(
                            value as List<Any>,
                            key
                        )
                        subType.typeSpec?.let { typeBuilder.addType(it) }
                        add(subType.propertySpec)
                    }

                    is String -> {
                        add(
                            PropertySpec.builder(key.kotlinifyIdentifier(), value::class)
                                .initializer("%S", value).build()
                        )
                    }

                    else -> {
                        add(
                            PropertySpec.builder(key.kotlinifyIdentifier(), value::class)
                                .initializer("%L", value.toString()).build()
                        )

                    }
                }
            }
        }

        return when (type) {
            TypeType.DATA_CLASS -> {
                // create the data class
                val primaryConstructor = FunSpec.constructorBuilder().apply {
                    addModifiers(KModifier.PRIVATE)
                    properties.forEach {
                        this.addParameter(ParameterSpec.builder(it.name, it.type).build())
                    }
                }
                typeBuilder.primaryConstructor(primaryConstructor.build())
                typeBuilder.addProperties(properties.map { it.toBuilder().initializer(it.name).build() })

                // create the actual property
                BuildTypeFromMapResult(
                    typeBuilder.build(),
                    PropertySpec.builder(name.kotlinifyIdentifier(), ClassName("", name.kotlinifyIdentifier(true)))
                        .initializer(
                            "%L",
                            "${name.kotlinifyIdentifier(true)}(${properties.joinToString { it.initializer.toString() }})"
                        )
                        .build()
                )
            }

            TypeType.OBJECT -> {
                typeBuilder.addProperties(properties)
                BuildTypeFromMapResult(typeBuilder.build(), null)
            }
        }
    }

    data class BuildTypeFromListResult(
        val typeSpec: TypeSpec?,
        val propertySpec: PropertySpec
    )

    @Suppress("UNCHECKED_CAST")
    private fun buildListOfDataClassFromList(
        list: List<Any>,
        name: String
    ): BuildTypeFromListResult {
        val typeOfList: TypeSpec? = if (list.isEmpty())
            TypeSpec.objectBuilder(name.kotlinifyIdentifier(true)).build()
        else when (val value = list.first()) {
            is Map<*, *> -> {
                val subType = buildTypeFromMap(
                    value.mapKeys { (key, _) -> key.toString() } as Map<String, Any>,
                    name,
                    TypeType.DATA_CLASS
                )
                subType.typeSpec
            }

            is List<*> -> {
                val subType = buildListOfDataClassFromList(
                    value as List<Any>,
                    name
                )
                subType.typeSpec
            }

            else -> {
                null
            }
        }

        val propOfList = PropertySpec.builder(
            name.kotlinifyIdentifier(),
            LIST.parameterizedBy(
                if (typeOfList != null)
                    ClassName("", name.kotlinifyIdentifier(true))
                else
                    list.first()::class.asClassName()
            )
        ).let { propOfList ->
            if (list.isEmpty())
                propOfList.initializer(CodeBlock.of("emptyList()"))
            else {
                val listPropsCodeBlocks = when (list.first()) {
                    is Map<*, *> -> {
                        list.map {
                            buildTypeFromMap(
                                (it as Map<Any, Any>).mapKeys { (key, _) -> key.toString() },
                                name,
                                TypeType.DATA_CLASS
                            ).propertySpec?.initializer
                        }
                    }

                    is List<*> -> {
                        list.map {
                            buildListOfDataClassFromList(
                                it as List<Any>,
                                name
                            ).propertySpec.initializer
                        }
                    }

                    is String -> {
                        list.map { CodeBlock.of("%S", it) }
                    }

                    else -> {
                        list.map { CodeBlock.of("%L", it) }
                    }
                }
                propOfList.initializer("listOf(" + "%L, ".repeat(list.size - 1) + "%L)", *listPropsCodeBlocks.toTypedArray())
            }
        }.build()

        return BuildTypeFromListResult(
            typeSpec = typeOfList,
            propertySpec = propOfList
        )
    }

}
