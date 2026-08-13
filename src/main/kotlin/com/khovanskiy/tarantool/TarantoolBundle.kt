package com.khovanskiy.tarantool

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.TarantoolBundle"

/** Строки интерфейса плагина. */
object TarantoolBundle {
    private val instance = DynamicBundle(TarantoolBundle::class.java, BUNDLE)

    @Nls
    fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): String =
        instance.getMessage(key, *params)
}
