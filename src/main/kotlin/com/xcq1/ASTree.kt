package com.xcq1

import com.squareup.kotlinpoet.*
import java.lang.reflect.Type

interface ASTNode<T> {
    val name: String
    fun rhyme(): T
}

data class ASTProperty<E : ASTProperty.Expression>(
    override val name: String,
    val type: TypeName,
    val expression: E
) : ASTNode<PropertySpec> {
    sealed interface Expression {
        data class PrimitiveValue(val format: String, val value: Any?) : Expression
        data class ComplexValue(val format: String, val values: Collection<Any?>) : Expression
        data class DataClassInstanceValue(val dataClassName: String) : Expression
        data class ListOfDataClassInstancesValue(val dataClassName: String, val instanceCount: Int) : Expression
        data class ListOfPrimitivesValue(val primitives: List<*>) : Expression
        data object DataClassNoValue : Expression
    }

    override fun rhyme() = when (expression) {
        is Expression.DataClassNoValue ->
            PropertySpec.builder(name, type).initializer(name).build()

        is Expression.DataClassInstanceValue ->
            PropertySpec.builder(name, type).initializer("%L", "${expression.dataClassName}.instance").build()

        is Expression.ListOfDataClassInstancesValue ->
            PropertySpec.builder(name, type)
                .initializer(
                    when (expression.instanceCount) {
                        0 -> "emptyList()"
                        else -> "listOf(" + "%L, ".repeat(expression.instanceCount - 1) + "%L)"
                    },
                    *(0 until expression.instanceCount).map { "${expression.dataClassName}.instance$it" }.toTypedArray()
                ).build()

        is Expression.ListOfPrimitivesValue ->
            PropertySpec.builder(name, type)
                .initializer(
                    when (expression.primitives.size) {
                        0 -> "emptyList()"
                        else -> if (expression.primitives.first() is String)
                            "listOf(" + "%S, ".repeat(expression.primitives.size - 1) + "%S)"
                            else
                            "listOf(" + "%L, ".repeat(expression.primitives.size - 1) + "%L)"
                    },
                    *(expression.primitives.map { it.toString() }).toTypedArray()
                ).build()

        is Expression.PrimitiveValue ->
            PropertySpec.builder(name, type).initializer(expression.format, expression.value).build()

        is Expression.ComplexValue ->
            PropertySpec.builder(name, type).initializer(expression.format, *expression.values.toTypedArray()).build()

        else -> error("Impossible case reached")
    }
}

data class ASTObject(
    override val name: String,
    val properties: List<ASTProperty<out ASTProperty.Expression>>,
    val children: List<ASTDataClass>
) : ASTNode<TypeSpec> {
    override fun rhyme() = TypeSpec.objectBuilder(name)
        .addProperties(properties.map { it.rhyme() })
        .addTypes(children.map { it.rhyme() })
        .build()
}

data class ASTDataClass(
    override val name: String,
    val valueParameters: List<ASTProperty<ASTProperty.Expression.DataClassNoValue>>,
    val children: List<ASTDataClass>,
    val companionObject: ASTCompanionObject
) : ASTNode<TypeSpec> {
    override fun rhyme(): TypeSpec =
        TypeSpec.classBuilder(name).addModifiers(KModifier.DATA)
            .addProperties(valueParameters.map { it.rhyme() })
            .primaryConstructor(
                FunSpec.constructorBuilder().addModifiers(KModifier.PRIVATE)
                    .addParameters(valueParameters.map { ParameterSpec.builder(it.name, it.type).build() })
                    .build()
            )
            .addTypes(children.map { it.rhyme() })
            .addType(companionObject.rhyme())
            .build()
}

data class ASTCompanionObject(
    override val name: String = "", // ignored
    val properties: List<ASTProperty<ASTProperty.Expression>>
) : ASTNode<TypeSpec> {
    override fun rhyme(): TypeSpec =
        TypeSpec.companionObjectBuilder()
            .addProperties(properties.map { it.rhyme() })
            .build()
}