package com.xcq1.yaml2kt


@JvmInline
value class KotlinParameterName(val originalValue: Any?) {
    override fun toString(): String =
        when (originalValue) {
            is String ->
                originalValue.replace("-([a-z])".toRegex()) { it.groupValues[1].uppercase() }

            else -> "_${originalValue.toString()}"
        }

    companion object {
        fun Any?.asKotlinParameterName() = KotlinParameterName(this)
    }
}

@JvmInline
value class KotlinTypeName(val originalValue: Any?) {
    override fun toString(): String =
        when (originalValue) {
            is String ->
                originalValue.replace("-([a-z])".toRegex()) { it.groupValues[1].uppercase() }.let {
                    it.replaceFirstChar { c -> c.uppercase() }
                }

            else -> "Type${originalValue.toString()}"
        }

    companion object {
        fun Any?.asKotlinTypeName(singularize: Boolean = false) =
            KotlinTypeName(
                if (singularize && this is String)
                    this.removeSuffix("s")
                else
                    this
            )
    }
}