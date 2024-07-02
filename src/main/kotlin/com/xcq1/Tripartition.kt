package com.xcq1

data class Tripartition<T>(val a: Collection<T>, val b: Collection<T>, val c: Collection<T>)

fun <T> Collection<T>.tripartition(isA: (T) -> Boolean, isB: (T) -> Boolean): Tripartition<T> {
    val a = mutableListOf<T>()
    val b = mutableListOf<T>()
    val c = mutableListOf<T>()
    forEach { e ->
        if (isA(e))
            a += e
        else if (isB(e))
            b += e
        else
            c += e
    }
    return Tripartition(a, b, c)
}