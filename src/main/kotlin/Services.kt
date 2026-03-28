package org.example

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.Properties
import kotlin.random.Random

interface AppEventListener {
    fun onProductAdded(user: User, product: Product, quantity: Int) {}
    fun onProductRemoved(user: User, productName: String, quantity: Int) {}
    fun onCheckout(user: User, invoice: Invoice, emailSent: Boolean) {}
}

class ConsoleEventListener : AppEventListener {
    override fun onProductAdded(user: User, product: Product, quantity: Int) {
        val msg = "Evento: ${user.username} agregó $quantity unidad(es) de ${product.name} al carrito."
        println(msg)
        AppLogger.info(msg)
    }

    override fun onProductRemoved(user: User, productName: String, quantity: Int) {
        val msg = "Evento: ${user.username} eliminó $quantity unidad(es) de $productName del carrito."
        println(msg)
        AppLogger.info(msg)
    }

    override fun onCheckout(user: User, invoice: Invoice, emailSent: Boolean) {
        val msg = "Evento: ${user.username} generó la factura ${invoice.number}. Envío por correo: $emailSent"
        println(msg)
        AppLogger.info(msg)
    }
}

class AuthService(private val userRepository: UserRepository) {
    fun login(username: String, password: String): User? {
        val user = userRepository.findByUsername(username)
        val ok = user != null && user.passwordHash == HashUtil.sha256(password)
        if (!ok) {
            AppLogger.error("Intento fallido de login para usuario: $username")
            return null
        }
        return user
    }

    fun register(username: String, password: String, email: String, role: Role): Boolean {
        val user = User(
            username = username,
            passwordHash = HashUtil.sha256(password),
            email = email,
            role = role
        )
        val saved = userRepository.add(user)
        if (saved) {
            AppLogger.info("Usuario registrado: $username con rol ${role.name}")
        } else {
            AppLogger.error("Intento de registro duplicado para usuario: $username")
        }
        return saved
    }
}

class EmailService {
    fun saveInvoice(invoice: Invoice, body: String): Path {
        Files.createDirectories(AppConfig.invoicesDir)
        val file = AppConfig.invoicesDir.resolve("Factura_${invoice.number}.txt")
        Files.writeString(
            file,
            body,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
        return file
    }

    fun sendInvoiceIfConfigured(to: String, subject: String, body: String): Boolean {
        val host = System.getenv("SMTP_HOST")?.trim().orEmpty()
        val port = System.getenv("SMTP_PORT")?.trim().orEmpty().ifBlank { "587" }
        val user = System.getenv("SMTP_USER")?.trim().orEmpty()
        val pass = System.getenv("SMTP_PASS")?.trim().orEmpty()
        val from = System.getenv("EMAIL_FROM")?.trim().orEmpty().ifBlank { user }

        if (host.isBlank() || user.isBlank() || pass.isBlank() || from.isBlank()) {
            AppLogger.info("SMTP no configurado. La factura se guardó en archivo, pero no se envió por correo.")
            return false
        }

        return try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", host)
                put("mail.smtp.port", port)
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(user, pass)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(from))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                this.subject = subject
                setText(body)
            }

            Transport.send(message)
            AppLogger.info("Factura enviada por correo a $to")
            true
        } catch (ex: Exception) {
            AppLogger.error("Error al enviar la factura por correo a $to", ex)
            false
        }
    }
}

class BillingService(
    private val productRepository: ProductRepository,
    private val emailService: EmailService
) {
    fun checkout(user: User, cart: Cart): CheckoutResult? {
        if (cart.isEmpty()) return null

        for (item in cart.items()) {
            val product = productRepository.findById(item.productId)
                ?: throw IllegalStateException("El producto ${item.productId} ya no existe.")
            if (item.quantity > product.stock) {
                throw IllegalStateException("El producto ${product.name} ya no tiene stock suficiente.")
            }
        }

        val invoice = Invoice(
            number = generateInvoiceNumber(),
            user = user,
            items = cart.items().map { it.copy() },
            subtotal = cart.subtotal(),
            tax = cart.taxTotal(),
            total = cart.total(),
            createdAt = LocalDateTime.now()
        )

        val body = renderInvoice(invoice)
        val file = emailService.saveInvoice(invoice, body)

        for (item in invoice.items) {
            productRepository.decreaseStock(item.productId, item.quantity)
        }

        val emailSent = emailService.sendInvoiceIfConfigured(
            to = user.email,
            subject = "Factura electrónica ${invoice.number}",
            body = body
        )

        //cart.clear()
        AppLogger.info("Checkout completado para ${user.username}, factura ${invoice.number}")
        return CheckoutResult(invoice, file, emailSent)
    }

    fun renderInvoice(invoice: Invoice): String {
        val sb = StringBuilder()
        sb.appendLine("======================================================")
        sb.appendLine("                     FACTURA                           ")
        sb.appendLine("Número : ${invoice.number}")
        sb.appendLine("Fecha  : ${invoice.createdAt.pretty()}")
        sb.appendLine("Cliente: ${invoice.user.username}")
        sb.appendLine("Correo : ${invoice.user.email}")
        sb.appendLine("Cargo  : ${invoice.user.role.label}")
        sb.appendLine("------------------------------------------------------")
        sb.appendLine(
            String.format(
                "%-4s %-28s %7s %12s %12s",
                "ID", "Producto", "Cant", "P.Unit", "Total"
            )
        )
        sb.appendLine("------------------------------------------------------")

        for (item in invoice.items) {
            sb.appendLine(
                String.format(
                    "%-4d %-28s %7d %12s %12s",
                    item.productId,
                    crop(item.name, 28),
                    item.quantity,
                    money(item.unitPrice),
                    money(item.subtotal())
                )
            )
        }

        sb.appendLine("------------------------------------------------------")
        sb.appendLine("Subtotal : ${money(invoice.subtotal)}")
        sb.appendLine("IVA      : ${money(invoice.tax)}")
        sb.appendLine("TOTAL    : ${money(invoice.total)}")
        sb.appendLine("======================================================")
        return sb.toString()
    }

    private fun crop(text: String, limit: Int): String {
        return if (text.length <= limit) text else text.take(limit - 3) + "..."
    }

    private fun generateInvoiceNumber(): String {
        val now = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val random = Random.nextInt(1000, 9999)
        return "FAC-$now-$random"
    }
}