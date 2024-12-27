package ru.transaero21

import com.google.protobuf.ByteString

sealed class AbstractFlag {
    abstract val name: String
    abstract val value: Any

    fun toType(): Long = when (this) {
        is BoolFlag -> if (value) 1 else 0
        is IntFlag -> 2
        is FloatFlag -> 3
        is StringFlag -> 4
        is ExtensionFlag -> 5
    }

    data class IntFlag(override val name: String, override val value: Long) : AbstractFlag()

    data class BoolFlag(override val name: String, override val value: Boolean) : AbstractFlag()

    data class FloatFlag(override val name: String, override val value: Long) : AbstractFlag()

    data class StringFlag(override val name: String, override val value: String) : AbstractFlag()

    @Suppress(names = ["ArrayInDataClass"])
    data class ExtensionFlag(override val name: String, override val value: ByteArray) : AbstractFlag()
}