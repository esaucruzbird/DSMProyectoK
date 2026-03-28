package org.example

import java.nio.file.Files
import java.nio.file.StandardOpenOption

class UserRepository {
    private val file = AppConfig.dataDir.resolve("users.csv")
    private val users = mutableListOf<User>()

    init {
        load()
    }

    fun all(): List<User> = users.toList()

    fun findByUsername(username: String): User? =
        users.firstOrNull { it.username.equals(username, ignoreCase = true) }

    fun add(user: User): Boolean {
        if (findByUsername(user.username) != null) return false
        users += user
        save()
        return true
    }

    private fun load() {
        Files.createDirectories(AppConfig.dataDir)

        if (!Files.exists(file)) {
            seedDefaultUsers()
            save()
            return
        }

        val lines = Files.readAllLines(file).filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            seedDefaultUsers()
            save()
            return
        }

        users.clear()
        for (line in lines) {
            val parts = line.split(';')
            if (parts.size >= 4) {
                val role = runCatching { Role.valueOf(parts[3]) }.getOrDefault(Role.CLIENTE)
                users += User(
                    username = parts[0],
                    passwordHash = parts[1],
                    email = parts[2],
                    role = role
                )
            }
        }
    }

    private fun save() {
        val content = users.joinToString(System.lineSeparator()) {
            listOf(it.username, it.passwordHash, it.email, it.role.name).joinToString(";")
        }
        Files.writeString(
            file,
            if (content.isBlank()) "" else content + System.lineSeparator(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }

    private fun seedDefaultUsers() {
        users.clear()
        users += User("admin", HashUtil.sha256("admin123"), "admin@demo.com", Role.ADMIN)
        users += User("cliente", HashUtil.sha256("cliente123"), "cliente@demo.com", Role.CLIENTE)
        users += User("empleado", HashUtil.sha256("empleado123"), "empleado@demo.com", Role.EMPLEADO)
    }
}

class ProductRepository {
    private val file = AppConfig.dataDir.resolve("products.csv")
    private val products = mutableListOf<Product>()

    init {
        load()
    }

    fun all(): List<Product> = products.sortedBy { it.id }

    fun findById(id: Int): Product? = products.firstOrNull { it.id == id }

    fun addProduct(
        name: String,
        price: Double,
        stock: Int,
        category: String = "Informática",
        description: String = "",
        taxRate: Double? = null
    ): Product {
        val product = Product(
            id = nextId(),
            name = name,
            price = price,
            stock = stock,
            category = if (category.isBlank()) "Informática" else category,
            description = description,
            taxRate = taxRate ?: AppConfig.defaultTaxRate
        )
        products += product
        save()
        return product
    }

    fun updateProduct(product: Product) {
        val index = products.indexOfFirst { it.id == product.id }
        if (index >= 0) {
            products[index] = product
            save()
        }
    }

    fun removeProduct(id: Int): Boolean {
        val removed = products.removeIf { it.id == id }
        if (removed) save()
        return removed
    }

    fun decreaseStock(productId: Int, quantity: Int): Boolean {
        val product = findById(productId) ?: return false
        if (quantity > product.stock) return false
        product.stock -= quantity
        save()
        return true
    }

    private fun nextId(): Int = (products.maxOfOrNull { it.id } ?: 0) + 1

    private fun load() {
        Files.createDirectories(AppConfig.dataDir)

        if (!Files.exists(file)) {
            seedDefaultProducts()
            save()
            return
        }

        val lines = Files.readAllLines(file).filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            seedDefaultProducts()
            save()
            return
        }

        products.clear()
        for (line in lines) {
            val parts = line.split(';', limit = 7)
            if (parts.size >= 4) {
                products += Product(
                    id = parts[0].toIntOrNull() ?: continue,
                    name = parts[1],
                    price = parts[2].toDoubleOrNull() ?: continue,
                    stock = parts[3].toIntOrNull() ?: continue,
                    category = parts.getOrNull(4).orEmpty().ifBlank { "Informática" },
                    description = parts.getOrNull(5).orEmpty(),
                    taxRate = parts.getOrNull(6)?.toDoubleOrNull() ?: AppConfig.defaultTaxRate
                )
            }
        }
    }

    private fun save() {
        val content = products.sortedBy { it.id }.joinToString(System.lineSeparator()) { p ->
            listOf(
                p.id,
                p.name,
                p.price,
                p.stock,
                p.category,
                p.description,
                p.taxRate
            ).joinToString(";")
        }
        Files.writeString(
            file,
            if (content.isBlank()) "" else content + System.lineSeparator(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }

    private fun seedDefaultProducts() {
        products.clear()
        products += Product(1, "Laptop Lenovo IdeaPad 3", 699.99, 8, "Laptops", "Equipo portátil de uso general", 0.13)
        products += Product(2, "Mouse inalámbrico Logitech", 24.50, 20, "Periféricos", "Mouse óptico USB", 0.13)
        products += Product(3, "Teclado mecánico RGB", 59.99, 15, "Periféricos", "Teclado gamer", 0.13)
        products += Product(4, "Monitor 24 pulgadas", 129.99, 10, "Monitores", "Pantalla Full HD", 0.13)
        products += Product(5, "Disco SSD 1TB", 89.99, 12, "Almacenamiento", "Unidad SSD NVMe", 0.13)
        products += Product(6, "Memoria RAM 16GB DDR4", 54.99, 18, "Memoria", "Módulo DDR4", 0.13)
    }
}