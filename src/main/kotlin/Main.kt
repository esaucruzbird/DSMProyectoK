package org.example

class StoreApp {
    private val userRepository = UserRepository()
    private val productRepository = ProductRepository()
    private val authService = AuthService(userRepository)
    private val emailService = EmailService()
    private val billingService = BillingService(productRepository, emailService)
    private val eventListener: AppEventListener = ConsoleEventListener()

    fun run() {
        println("==============================================")
        println("   SISTEMA DE CARRO DE COMPRAS EN KOTLIN")
        println("==============================================")

        while (true) {
            try {
                println()
                println("1) Iniciar sesión")
                println("2) Registrar usuario")
                println("0) Salir")

                when (InputUtil.readInt("Seleccione una opción: ", 0, 2)) {
                    1 -> loginFlow()
                    2 -> registerFlow()
                    0 -> {
                        println("Saliendo del sistema...")
                        AppLogger.info("Aplicación cerrada por el usuario.")
                        return
                    }
                }
            } catch (ex: Exception) {
                AppLogger.error("Error en menú principal", ex)
                println("Ocurrió un error inesperado. Revise el log.")
            }
        }
    }

    private fun loginFlow() {
        val username = InputUtil.readNonBlank("Usuario: ")
        val password = InputUtil.readNonBlank("Contraseña: ")

        val user = authService.login(username, password)
        if (user == null) {
            println("Credenciales incorrectas.")
            return
        }

        println("Bienvenido, ${user.username} (${user.role.label})")
        AppLogger.info("Inicio de sesión correcto: ${user.username}")
        sessionMenu(user)
    }

    private fun registerFlow() {
        println()
        println("=== Registro de usuario ===")

        val username = InputUtil.readNonBlank("Nuevo usuario: ")
        if (userRepository.findByUsername(username) != null) {
            println("Ese usuario ya existe.")
            return
        }

        val password = InputUtil.readNonBlank("Contraseña: ")
        val confirm = InputUtil.readNonBlank("Confirmar contraseña: ")
        if (password != confirm) {
            println("Las contraseñas no coinciden.")
            return
        }

        val email = InputUtil.readEmail("Correo electrónico: ")

        println("Cargo / rol:")
        println("1) ADMIN")
        println("2) EMPLEADO")
        println("3) CLIENTE")

        val role = when (InputUtil.readInt("Seleccione un rol: ", 1, 3)) {
            1 -> Role.ADMIN
            2 -> Role.EMPLEADO
            else -> Role.CLIENTE
        }

        val saved = authService.register(username, password, email, role)
        if (saved) {
            println("Usuario registrado correctamente.")
        } else {
            println("No se pudo registrar el usuario.")
        }
    }

    private fun sessionMenu(user: User) {
        val cart = Cart()

        while (true) {
            try {
                println()
                println("==============================================")
                println("USUARIO: ${user.username}  |  CARGO: ${user.role.label}")
                println("==============================================")
                println("1) Ver productos")
                println("2) Agregar producto al carrito")
                println("3) Ver carrito")
                println("4) Eliminar producto del carrito")
                println("5) Confirmar compra / Generar factura")
                if (user.role.canManageProducts) {
                    println("6) Administrar productos")
                    println("0) Cerrar sesión")
                } else {
                    println("0) Cerrar sesión")
                }

                val option = InputUtil.readInt("Seleccione una opción: ", 0, if (user.role.canManageProducts) 6 else 5)

                when (option) {
                    1 -> showProducts()
                    2 -> addProductToCart(user, cart)
                    3 -> showCart(cart)
                    4 -> removeFromCart(user, cart)
                    5 -> checkout(user, cart)
                    6 -> if (user.role.canManageProducts) manageProductsMenu() else Unit
                    0 -> {
                        println("Sesión cerrada.")
                        AppLogger.info("Cierre de sesión: ${user.username}")
                        return
                    }
                }
            } catch (ex: Exception) {
                AppLogger.error("Error en menú de sesión para ${user.username}", ex)
                println("Ocurrió un error. Revise el log.")
            }
        }
    }

    private fun showProducts() {
        val products = productRepository.all()
        if (products.isEmpty()) {
            println("No hay productos disponibles.")
            return
        }

        println()
        println("=== Catálogo de productos ===")
        println(String.format("%-4s %-30s %12s %8s %14s", "ID", "Nombre", "Precio", "Stock", "Categoría"))
        println("--------------------------------------------------------------------------")

        for (product in products) {
            println(
                String.format(
                    "%-4d %-30s %12s %8d %14s",
                    product.id,
                    crop(product.name, 30),
                    money(product.price),
                    product.stock,
                    crop(product.category, 14)
                )
            )
        }
    }

    private fun addProductToCart(user: User, cart: Cart) {
        showProducts()

        val productId = InputUtil.readPositiveInt("Ingrese el ID del producto: ")
        val product = productRepository.findById(productId)

        if (product == null) {
            println("Producto no encontrado.")
            return
        }

        val available = product.stock - cart.quantityOf(product.id)
        if (available <= 0) {
            println("No hay stock disponible para ese producto.")
            return
        }

        while (true) {
            val quantity = InputUtil.readPositiveInt("Cantidad deseada (disponible: $available): ")

            if (quantity <= available) {
                cart.add(product, quantity)
                eventListener.onProductAdded(user, product, quantity)
                println("Producto agregado al carrito.")
                return
            }

            println("La cantidad solicitada supera el stock disponible.")
            println("1) Cambiar la cantidad")
            println("2) Volver al menú principal sin cambiar el carrito")

            when (InputUtil.readInt("Seleccione: ", 1, 2)) {
                1 -> continue
                2 -> return
            }
        }
    }

    private fun showCart(cart: Cart) {
        println()
        println("=== Carrito actual ===")

        if (cart.isEmpty()) {
            println("El carrito está vacío.")
            return
        }

        println(String.format("%-4s %-28s %8s %12s %12s %12s", "ID", "Producto", "Cant", "P.Unit", "Subtot", "IVA"))
        println("----------------------------------------------------------------------------------")

        for (item in cart.items()) {
            println(
                String.format(
                    "%-4d %-28s %8d %12s %12s %12s",
                    item.productId,
                    crop(item.name, 28),
                    item.quantity,
                    money(item.unitPrice),
                    money(item.subtotal()),
                    money(item.tax())
                )
            )
        }

        println("----------------------------------------------------------------------------------")
        println("Subtotal: ${money(cart.subtotal())}")
        println("IVA     : ${money(cart.taxTotal())}")
        println("TOTAL   : ${money(cart.total())}")
    }

    private fun removeFromCart(user: User, cart: Cart) {
        if (cart.isEmpty()) {
            println("El carrito está vacío.")
            return
        }

        showCart(cart)
        val productId = InputUtil.readPositiveInt("Ingrese el ID del producto a eliminar: ")
        val item = cart.items().firstOrNull { it.productId == productId }

        if (item == null) {
            println("Ese producto no está en el carrito.")
            return
        }

        if (cart.remove(productId)) {
            eventListener.onProductRemoved(user, item.name, item.quantity)
            println("Producto eliminado del carrito.")
        } else {
            println("No se pudo eliminar el producto.")
        }
    }

    private fun checkout(user: User, cart: Cart) {
        if (cart.isEmpty()) {
            println("El carrito está vacío.")
            return
        }

        try {
            val result = billingService.checkout(user, cart)
            if (result == null) {
                println("No se pudo generar la factura.")
                return
            }

            println()
            println(billingService.renderInvoice(result.invoice))
            println("Factura guardada en: ${result.invoicePath}")

            if (result.emailSent) {
                println("La factura fue enviada al correo: ${user.email}")
            } else {
                println("La factura no se pudo enviar por correo. Revise la configuración SMTP.")
            }

            eventListener.onCheckout(user, result.invoice, result.emailSent)
            println("Puede seguir comprando desde el menú.")
        } catch (ex: Exception) {
            AppLogger.error("Error al confirmar compra para ${user.username}", ex)
            println("No fue posible completar la compra: ${ex.message}")
        }
    }

    private fun manageProductsMenu() {
        while (true) {
            println()
            println("=== Administración de productos ===")
            println("1) Registrar nuevo producto")
            println("2) Editar precio y stock")
            println("3) Eliminar producto")
            println("4) Ver catálogo")
            println("0) Volver")

            when (InputUtil.readInt("Seleccione: ", 0, 4)) {
                1 -> addNewProduct()
                2 -> editProduct()
                3 -> deleteProduct()
                4 -> showProducts()
                0 -> return
            }
        }
    }

    private fun addNewProduct() {
        println()
        println("=== Nuevo producto ===")
        println("Solo son obligatorios: nombre, precio y stock.")

        val name = InputUtil.readNonBlank("Nombre: ")
        val price = InputUtil.readPositiveDouble("Precio: ")
        val stock = InputUtil.readPositiveInt("Stock: ")

        print("Categoría [Enter para 'Informática']: ")
        val category = readlnOrNull()?.trim().orEmpty()

        print("Descripción [Enter para vacío]: ")
        val description = readlnOrNull()?.trim().orEmpty()

        val taxRate = InputUtil.readOptionalDouble("IVA opcional [Enter para 13%]: ") ?: AppConfig.defaultTaxRate

        val product = productRepository.addProduct(
            name = name,
            price = price,
            stock = stock,
            category = category,
            description = description,
            taxRate = taxRate
        )

        println("Producto registrado con ID ${product.id}.")
        AppLogger.info("Producto nuevo registrado: ${product.name} (ID ${product.id})")
    }

    private fun editProduct() {
        showProducts()
        val productId = InputUtil.readPositiveInt("ID del producto a editar: ")
        val product = productRepository.findById(productId)

        if (product == null) {
            println("Producto no encontrado.")
            return
        }

        println("Producto actual: ${product.name}")
        val newPrice = InputUtil.readPositiveDouble("Nuevo precio: ")
        val newStock = InputUtil.readPositiveInt("Nuevo stock: ")

        product.price = newPrice
        product.stock = newStock
        productRepository.updateProduct(product)

        println("Producto actualizado correctamente.")
        AppLogger.info("Producto actualizado: ${product.name} (ID ${product.id})")
    }

    private fun deleteProduct() {
        showProducts()
        val productId = InputUtil.readPositiveInt("ID del producto a eliminar: ")
        val removed = productRepository.removeProduct(productId)
        if (removed) {
            println("Producto eliminado.")
            AppLogger.info("Producto eliminado con ID $productId")
        } else {
            println("No se encontró el producto.")
        }
    }

    private fun crop(text: String, limit: Int): String {
        return if (text.length <= limit) text else text.take(limit - 3) + "..."
    }
}

fun main() {
    StoreApp().run()
}