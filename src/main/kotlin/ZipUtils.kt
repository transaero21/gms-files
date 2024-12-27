package ru.transaero21

import java.util.zip.Deflater
import java.util.zip.Inflater

object ZipUtils {
    fun decompress(compressedData: ByteArray): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(compressedData)

        val buffer = ByteArray(1024)
        val output = mutableListOf<Byte>()

        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            output.addAll(buffer.take(count))
        }
        inflater.end()

        return output.toByteArray()
    }

    fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater(1, true)
        deflater.setInput(data)
        deflater.finish()

        val buffer = ByteArray(1024)
        val output = mutableListOf<Byte>()

        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.addAll(buffer.take(count))
        }
        deflater.end()

        return output.toByteArray()
    }
}