package ru.transaero21

import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

const val getTotalFlags = """
SELECT
    CASE WHEN committed THEN -1 ELSE account_id END,
    Flags.partitionId,
    Flags.name,
    CASE
        WHEN boolVal NOT NULL THEN boolVal
        WHEN intVal NOT NULL THEN 2
        WHEN floatVal NOT NULL THEN 3
        WHEN stringVal NOT NULL THEN 4
        ELSE 5
        END,
    COALESCE(intVal, floatVal, stringVal, extensionVal)
FROM Flags
         CROSS JOIN (
    SELECT
        accounts.name AS tokenUser,
        0 as tokenCommitted,
        account_id,
        IFNULL(MAX(ExperimentTokens.version), ?2) AS tokenVersion
    FROM accounts
             LEFT JOIN ExperimentTokens ON accounts.name = ExperimentTokens.user
    WHERE
        ExperimentTokens.packageName = ?1
      AND
        ExperimentTokens.version <= ?2
      AND
        ExperimentTokens.isCommitted = 0
    GROUP BY accounts.name
    UNION ALL
    SELECT
        accounts.name AS tokenUser,
        1 as tokenCommitted,
        account_id,
        ApplicationStates.version AS tokenVersion
    FROM ApplicationStates
             INNER JOIN accounts ON accounts.name = ApplicationStates.user
    WHERE ApplicationStates.packageName = ?1
) AS accounts_tokens_versions
                    ON (
                        Flags.committed = tokenCommitted
                            AND
                        Flags.user = tokenUser
                            AND
                        Flags.version = tokenVersion
                        )
WHERE Flags.packageName = ?1
"""

@OptIn(ExperimentalStdlibApi::class)
fun main() {
    val url = "jdbc:sqlite:${Args.dbPath}"
    var con: Connection? = null
    try {
        con = DriverManager.getConnection(url)

        val ps1 = con.prepareStatement(getTotalFlags.trimIndent()).apply {
            setString(1, Args.packageName)
            setInt(2, Args.version)
        }

        val flags = mutableListOf<AbstractFlag>()



        ps1.executeQuery().use { rs ->
            while (rs.next()) {
                if (rs.getInt(1) != Args.paritionId || rs.getInt(2) != Args.userId)
                    continue

                val name = rs.getString(3)
                val flag = when (rs.getInt(4)) {
                    0, 1 -> AbstractFlag.BoolFlag(name, rs.getInt(4) != 0)
                    2 -> AbstractFlag.IntFlag(name, rs.getLong(5))
                    3 -> AbstractFlag.FloatFlag(name, java.lang.Double.doubleToRawLongBits(rs.getDouble(5)))
                    4 -> AbstractFlag.StringFlag(name, rs.getString(5))
                    5 -> AbstractFlag.ExtensionFlag(name, rs.getBytes(5))
                    else -> throw IllegalArgumentException()
                }
                flags.add(flag)

            }

            println("Selected flags:")
            flags.forEachIndexed { i, f ->
                println("\t$i -> $f")
            }
        }

        val out = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(out)

        var next = 0L
        cos.writeUInt32NoTag(flags.size)
        flags.forEachIndexed { i, f ->
            f.name.toLongOrNull()?.let { ln ->
                cos.writeUInt64NoTag(((ln - next) shl 3) or f.toType())
                next = ln
            } ?: run {
                cos.writeUInt64NoTag(f.toType())
                cos.writeStringNoTag(f.name)
            }

            when (f) {
                is AbstractFlag.BoolFlag -> { }
                is AbstractFlag.IntFlag -> cos.writeUInt64NoTag(f.value)
                is AbstractFlag.FloatFlag -> cos.writeDoubleNoTag(java.lang.Double.longBitsToDouble(f.value))
                is AbstractFlag.StringFlag -> cos.writeStringNoTag(f.value)
                is AbstractFlag.ExtensionFlag -> cos.writeByteArrayNoTag(f.value)
            }
        }

        println("Result: ${ZipUtils.compress(out.toByteArray()).toHexString()}")

    } catch (e: SQLException) {
        println(e.message)
    } finally {
        con?.close()
    }
}