package com.xcq1

import com.squareup.kotlinpoet.*

interface ASTNode<T> {
    fun rhyme(): T
}

data class ASTProperty<E : ASTProperty.Expression>(
    val name: KotlinParameterName,
    val type: TypeName,
    val expression: E
) : ASTNode<PropertySpec> {
    sealed interface Expression {
        data class PrimitiveValue(val format: String, val value: Any?) : Expression
        data class ComplexValue(val format: String, val values: Collection<Any?>) : Expression
        data class DataClassInstanceValue(val dataClassType: KotlinTypeName) : Expression
        data class ListOfDataClassInstancesValue(val dataclassType: KotlinTypeName, val instanceCount: Int) : Expression
        data class ListOfPrimitivesValue(val primitives: List<*>) : Expression
        data object DataClassNoValue : Expression
    }

    override fun rhyme() = PropertySpec.builder(name.toString(), type).run {
        when (expression) {
            is Expression.DataClassNoValue ->
                initializer(name.toString()).build()

            is Expression.DataClassInstanceValue ->
                initializer("%L", "${expression.dataClassType}.instance").build()

            is Expression.ListOfDataClassInstancesValue ->
                initializer(
                    when (expression.instanceCount) {
                        0 -> "emptyList()"
                        else -> "listOf(" + "%L, ".repeat(expression.instanceCount - 1) + "%L)"
                    },
                    *(0 until expression.instanceCount).map { "${expression.dataclassType}.instance$it" }
                        .toTypedArray()
                ).build()

            is Expression.ListOfPrimitivesValue ->
                initializer(
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
                initializer(expression.format, expression.value).build()

            is Expression.ComplexValue ->
                initializer(expression.format, *expression.values.toTypedArray()).build()

            else -> error("Impossible case reached")
        }
    }
}

data class ASTObject(
    val name: KotlinTypeName,
    val properties: List<ASTProperty<out ASTProperty.Expression>>,
    val children: List<ASTDataClass>
) : ASTNode<TypeSpec> {
    override fun rhyme() = TypeSpec.objectBuilder(name.toString())
        .addProperties(properties.map { it.rhyme() })
        .addTypes(children.map { it.rhyme() })
        .build()
}

data class ASTDataClass(
    val name: KotlinTypeName,
    val valueParameters: List<ASTProperty<ASTProperty.Expression.DataClassNoValue>>,
    val children: List<ASTDataClass>,
    val companionObject: ASTCompanionObject
) : ASTNode<TypeSpec> {
    override fun rhyme(): TypeSpec =
        TypeSpec.classBuilder(name.toString()).addModifiers(KModifier.DATA)
            .addProperties(valueParameters.map { it.rhyme() })
            .primaryConstructor(
                FunSpec.constructorBuilder().addModifiers(KModifier.PRIVATE)
                    .addParameters(valueParameters.map { ParameterSpec.builder(it.name.toString(), it.type).build() })
                    .build()
            )
            .addTypes(children.map { it.rhyme() })
            .addType(companionObject.rhyme())
            .build()
}

data class ASTCompanionObject(
    val properties: List<ASTProperty<ASTProperty.Expression>>
) : ASTNode<TypeSpec> {
    override fun rhyme(): TypeSpec =
        TypeSpec.companionObjectBuilder()
            .addProperties(properties.map { it.rhyme() })
            .build()
}