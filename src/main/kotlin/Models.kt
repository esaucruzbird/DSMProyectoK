package org.example

import java.time.LocalDateTime

enum class Role(val label: String, val canManageProducts: Boolean) {
    ADMIN("Administrador", true),
    EMPLEADO("Empleado", false),
    CLIENTE("Cliente", false)
}

data class User(
    val username: String,
    val passwordHash: String,
    val email: String,
    val role: Role
)

data class Product(
    val id: Int,
    var name: String,
    var price: Double,
    var stock: Int,
    var category: String = "Informática",
    var description: String = "",
    var taxRate: Double = AppConfig.defaultTaxRate
)

data class CartItem(
    val productId: Int,
    val name: String,
    val unitPrice: Double,
    var quantity: Int,
    val taxRate: Double
) {
    fun subtotal(): Double = unitPrice * quantity
    fun tax(): Double = subtotal() * taxRate
    fun total(): Double = subtotal() + tax()
}

class Cart {
    private val items = linkedMapOf<Int, CartItem>()

    fun add(product: Product, quantity: Int) {
        val current = items[product.id]
        if (current == null) {
            items[product.id] = CartItem(
                productId = product.id,
                name = product.name,
                unitPrice = product.price,
                quantity = quantity,
                taxRate = product.taxRate
            )
        } else {
            current.quantity += quantity
        }
    }

    fun remove(productId: Int): Boolean = items.remove(productId) != null

    fun clear() {
        items.clear()
    }

    fun isEmpty(): Boolean = items.isEmpty()

    fun items(): List<CartItem> = items.values.sortedBy { it.productId }

    fun quantityOf(productId: Int): Int = items[productId]?.quantity ?: 0

    fun subtotal(): Double = items.values.sumOf { it.subtotal() }

    fun taxTotal(): Double = items.values.sumOf { it.tax() }

    fun total(): Double = subtotal() + taxTotal()
}

data class Invoice(
    val number: String,
    val user: User,
    val items: List<CartItem>,
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

data class CheckoutResult(
    val invoice: Invoice,
    val invoicePath: java.nio.file.Path,
    val emailSent: Boolean
)