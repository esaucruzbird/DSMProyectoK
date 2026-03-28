package org.example

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object AppConfig {
    val dataDir: Path = Paths.get("data")
    val logsDir: Path = Paths.get("logs")
    val invoicesDir: Path = dataDir.resolve("invoices")
    const val defaultTaxRate: Double = 0.13
}

object AppLogger {
    private val logFile: Path = AppConfig.logsDir.resolve("app.log")
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    init {
        Files.createDirectories(AppConfig.dataDir)
        Files.createDirectories(AppConfig.logsDir)
        Files.createDirectories(AppConfig.invoicesDir)
    }

    fun info(message: String) = append("INFO", message)

    fun error(message: String, ex: Throwable? = null) {
        append("ERROR", if (ex == null) message else "$message | ${ex.message}")
    }

    private fun append(level: String, message: String) {
        val line = "${LocalDateTime.now().format(formatter)} [$level] $message${System.lineSeparator()}"
        Files.writeString(
            logFile,
            line,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }
}

object HashUtil {
    fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

object InputUtil {
    fun readNonBlank(prompt: String): String {
        while (true) {
            print(prompt)
            val value = readlnOrNull()?.trim().orEmpty()
            if (value.isNotBlank()) return value
            println("El valor no puede estar vacío.")
        }
    }

    fun readEmail(prompt: String): String {
        while (true) {
            val value = readNonBlank(prompt)
            if (value.contains("@") && value.contains(".")) return value
            println("Correo inválido.")
        }
    }

    fun readInt(prompt: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int {
        while (true) {
            print(prompt)
            val raw = readlnOrNull()?.trim().orEmpty()
            val value = raw.toIntOrNull()
            if (value != null && value in min..max) return value
            println("Ingrese un número entero válido entre $min y $max.")
        }
    }

    fun readPositiveInt(prompt: String): Int = readInt(prompt, 1, Int.MAX_VALUE)

    fun readPositiveDouble(prompt: String): Double {
        while (true) {
            print(prompt)
            val raw = readlnOrNull()?.trim().orEmpty().replace(",", ".")
            val value = raw.toDoubleOrNull()
            if (value != null && value > 0) return value
            println("Ingrese un número decimal mayor que 0.")
        }
    }

    fun readOptionalDouble(prompt: String): Double? {
        while (true) {
            print(prompt)
            val raw = readlnOrNull()?.trim().orEmpty()
            if (raw.isBlank()) return null
            val value = raw.replace(",", ".").toDoubleOrNull()
            if (value != null && value > 0) return value
            println("Ingrese un decimal válido mayor que 0, o deje vacío para usar el valor por defecto.")
        }
    }

    fun readYesNo(prompt: String): Boolean {
        while (true) {
            print("$prompt (S/N): ")
            val raw = readlnOrNull()?.trim()?.lowercase().orEmpty()
            when (raw) {
                "s", "si", "sí" -> return true
                "n", "no" -> return false
                else -> println("Responda con S o N.")
            }
        }
    }
}

fun money(value: Double): String = NumberFormat.getCurrencyInstance(Locale.US).format(value)

fun LocalDateTime.pretty(): String = format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))