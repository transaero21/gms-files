package ru.transaero21

import com.google.protobuf.CodedInputStream
import java.lang.System.out
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

const val sqlFlagsContent = """
SELECT flags_content
FROM param_partitions
         INNER JOIN experiment_states_to_partitions
                    USING (param_partition_id)
         INNER JOIN experiment_states
                    USING (experiment_state_id)
         INNER JOIN config_packages
                    ON experiment_state_id = committed_experiment_state_id
         INNER JOIN accounts
                    USING (account_id)
WHERE config_packages.name = ?1;
"""

fun main() {
    val url = "jdbc:sqlite:${Args.dbPath}"
    var con: Connection? = null
    try {
        con = DriverManager.getConnection(url)

        val ps = con.prepareStatement(sqlFlagsContent.trimIndent()).apply {
            setString(1, Args.packageName)
        }


        val flagsContent: List<ByteArray> = ps.executeQuery().use { resultSet ->
            val result = mutableListOf<ByteArray>()

            while (resultSet.next()) {
                try {
                    result.add(ZipUtils.decompress(resultSet.getBytes(1)))
                } catch (e: Exception) {
                    throw RuntimeException("Failed to decompress row data", e)
                }
            }

            result
        }

        flagsContent.forEach { fc ->
            val `in` = CodedInputStream.newInstance(fc)

            val size = `in`.readUInt32()
            var next = 0L
            val flags = List(size) {
                val theory = `in`.readUInt64()
                val shift = theory ushr 3

                val name = if (shift == 0L) `in`.readString() else (shift + next).toString()

                val type = theory.toInt() and 7
                when (type) {
                    0, 1 -> AbstractFlag.BoolFlag(name, type != 0)
                    2 -> AbstractFlag.IntFlag(name, `in`.readUInt64())
                    3 -> AbstractFlag.FloatFlag(name, java.lang.Double.doubleToRawLongBits(`in`.readDouble()))
                    4 -> AbstractFlag.StringFlag(name, `in`.readString())
                    5 -> AbstractFlag.ExtensionFlag(name, `in`.readByteArray())
                    else -> throw RuntimeException("Unrecognized flag type: $type")
                }.also { next = it.name.toLongOrNull() ?: 0 }
            }

            println("Decoded flags:")
            flags.forEachIndexed { i, f ->
                println("\t$i -> $f")
            }
        }
    } catch (e: SQLException) {
        println(e.message)
    } finally {
        con?.close()
    }
}