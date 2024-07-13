package com.xcq1.yaml2kt

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

sealed interface ASTNode {
    val name: KotlinParameterName
    fun merge(other: ASTNode): ASTNode

    fun getPoetType(): TypeName
    fun getPoetCodeBlockToInitProperty(propertyValue: Any?): CodeBlock
}

data class ASTObject(
    override val name: KotlinParameterName,
    val typeName: KotlinTypeName,
    val nullable: Boolean,
    val properties: Map<KotlinParameterName, ASTNode>,
    val packageName: String
) : ASTNode {
    override fun merge(other: ASTNode): ASTNode =
        when (other) {
            is ASTList -> error("Cannot merge list with object under $typeName")
            is ASTNull -> copy(nullable = true)
            is ASTObject -> copy(
                nullable = nullable || other.nullable,
                properties = (properties.keys + other.properties.keys).associateWith {
                    (properties[it] ?: ASTNull(it)).merge(other.properties[it] ?: ASTNull(it))
                }
            )

            is ASTPrimitive -> error("Cannot merge primitive with object under $typeName")
        }

    override fun getPoetType(): TypeName = ClassName(packageName, typeName.toString()).copy(nullable = nullable)

    @OptIn(ExperimentalStdlibApi::class)
    override fun getPoetCodeBlockToInitProperty(propertyValue: Any?): CodeBlock =
        if (nullable && propertyValue == null)
            CodeBlock.of("null")
        else {
            require(propertyValue is Map<*, *>) { "Cannot get object code block without Map value" }
            if (propertyValue.isEmpty()) // data object
                CodeBlock.of("%L", typeName.toString())
            else
                CodeBlock.of("%L.i%L", typeName.toString(), propertyValue.hashCode().toHexString())
        }

    private fun determineSubObjectsOfNode(node: ASTNode, value: Any?): Pair<ASTObject, Collection<Any?>>? =
        when {
            node is ASTObject && value != null -> Pair(node, listOf(value))
            node is ASTList && value != null && value is List<*> && value.isNotEmpty() ->
                value.mapNotNull { determineSubObjectsOfNode(node.of, it) }
                    .reduceOrNull { (aNode, aValues), (bNode, bValues) ->
                        require(aNode == bNode) { "List with different ASTObjects inside?" }
                        aNode to (aValues + bValues)
                    }

            else -> null
        }

    fun toPoetObject(value: Map<Any?, Any?>): TypeSpec.Builder =
        TypeSpec.objectBuilder(typeName.toString())
            .addProperties(properties
                .map { (propKey, propNode) ->
                    PropertySpec.builder(propNode.name.toString(), propNode.getPoetType())
                        .initializer(
                            propNode.getPoetCodeBlockToInitProperty(
                                propertyValue = value[propKey.originalValue]
                            )
                        )
                        .build()
                })
            .addTypes(properties.mapNotNull { (nodeName, node) ->
                determineSubObjectsOfNode(
                    node,
                    value[nodeName.originalValue]
                )?.let { (subObjectNode, subObjectValues) ->
                    @Suppress("UNCHECKED_CAST")
                    subObjectNode.toPoetDataClass(values = subObjectValues as Collection<Map<Any?, Any?>>).build()
                }
            })

    @OptIn(ExperimentalStdlibApi::class)
    fun toPoetDataClass(values: Collection<Map<Any?, Any?>>): TypeSpec.Builder =
        if (properties.isEmpty())
            TypeSpec.objectBuilder(typeName.toString()).addModifiers(KModifier.DATA)
                .addProperty(getIdProperty())
        else
            TypeSpec.classBuilder(typeName.toString()).addModifiers(KModifier.DATA)
                .addProperties(properties
                    .map { (name, node) ->
                        PropertySpec.builder(node.name.toString(), node.getPoetType())
                            .initializer(node.name.toString())
                            .build()
                    } + getIdProperty())
                .primaryConstructor(
                    FunSpec.constructorBuilder().addModifiers(KModifier.PRIVATE)
                        .addParameters(properties
                            .map { (name, node) ->
                                ParameterSpec.builder(node.name.toString(), node.getPoetType())
                                    .build()
                            })
                        .build()
                )
                .addTypes(properties.mapNotNull { (nodeName, node) ->
                    values.mapNotNull {
                        determineSubObjectsOfNode(
                            node,
                            it[nodeName.originalValue]
                        )
                    }.reduceOrNull { (aNode, aValues), (bNode, bValues) ->
                        require(aNode == bNode) { "List with different ASTObjects inside?" }
                        aNode to (aValues + bValues)
                    }?.let { (subObjectNode, subObjectValues) ->
                        @Suppress("UNCHECKED_CAST")
                        subObjectNode.toPoetDataClass(values = subObjectValues as Collection<Map<Any?, Any?>>).build()
                    }
                })
                .addType(
                    TypeSpec.companionObjectBuilder()
                        .addProperties(
                            values.map { value ->
                                PropertySpec.builder(
                                    "i${value.hashCode().toHexString()}",
                                    ClassName(packageName, typeName.toString())
                                ).initializer(
                                    "%L(%L)",
                                    typeName.toString(),
                                    properties.entries.map { (propKey, propNode) ->
                                        propNode.getPoetCodeBlockToInitProperty(
                                            propertyValue = value[propKey.originalValue]
                                        )
                                    }.joinToCode()
                                ).build()
                            }
                        )
                        .build()
                )

    private fun getIdProperty() =
        PropertySpec.builder(properties.keys.map { it.toString() }.fold("id") { acc, next ->
            if (acc == next)
                "_$acc"
            else
                acc
        }, STRING).initializer("%S", name.originalValue).build()
}

data class ASTList(
    override val name: KotlinParameterName,
    val of: ASTNode,
    val nullable: Boolean
) : ASTNode {
    override fun merge(other: ASTNode): ASTNode =
        when (other) {
            is ASTList -> copy(of = of.merge(other.of), nullable = nullable || other.nullable)
            is ASTNull -> copy(nullable = true)
            is ASTObject -> error("Cannot merge list with object under ${other.typeName}")
            is ASTPrimitive -> error("Cannot merge primitive with list under ${other.name}")
        }

    override fun getPoetType(): TypeName = LIST.parameterizedBy(of.getPoetType()).copy(nullable = nullable)
    override fun getPoetCodeBlockToInitProperty(propertyValue: Any?): CodeBlock =
        if (nullable && propertyValue == null)
            CodeBlock.of("null")
        else {
            require(propertyValue is List<*>) { "Cannot get list code block without List value" }
            val allListValues = propertyValue.map { of.getPoetCodeBlockToInitProperty(it) }
            CodeBlock.of(
                "listOf(" + allListValues.joinToCode() + ")"
            )
        }
}

data class ASTPrimitive(
    override val name: KotlinParameterName,
    val type: TypeName
) : ASTNode {
    companion object {
        private val numberTypes = setOf(
            NUMBER, BYTE, SHORT, INT, LONG, DOUBLE, FLOAT
        )
    }

    override fun merge(other: ASTNode): ASTNode =
        when (other) {
            is ASTList -> error("Cannot merge primitive with list under $name")
            is ASTNull -> copy(type = type.copy(nullable = true))
            is ASTObject -> error("Cannot merge primitive with object under $name")
            is ASTPrimitive -> when {
                type == other.type -> this
                type.copy(nullable = true) == other.type.copy(nullable = true) -> copy(type = type.copy(nullable = true))

                type is ClassName && type.copy(nullable = false) in numberTypes &&
                        other.type is ClassName && other.type.copy(nullable = false) in numberTypes ->
                    copy(type = NUMBER.copy(nullable = type.isNullable || other.type.isNullable))

                else -> copy(type = ANY.copy(nullable = type.isNullable || other.type.isNullable))
            }
        }

    override fun getPoetType(): TypeName = type
    override fun getPoetCodeBlockToInitProperty(propertyValue: Any?): CodeBlock =
        when {
            type.copy(nullable = false) == STRING -> CodeBlock.of("%S", propertyValue)
            else -> CodeBlock.of("%L", propertyValue)
        }
}

data class ASTNull(
    override val name: KotlinParameterName
) : ASTNode {
    override fun merge(other: ASTNode): ASTNode =
        when (other) {
            is ASTList -> other.copy(nullable = true)
            is ASTNull -> this
            is ASTObject -> other.copy(nullable = true)
            is ASTPrimitive -> other.copy(type = other.type.copy(nullable = true))
        }

    override fun getPoetType(): TypeName = ANY.copy(nullable = true)
    override fun getPoetCodeBlockToInitProperty(propertyValue: Any?): CodeBlock {
        require(propertyValue == null) { "Cannot get null code block without null value" }
        return CodeBlock.of("null")
    }
}