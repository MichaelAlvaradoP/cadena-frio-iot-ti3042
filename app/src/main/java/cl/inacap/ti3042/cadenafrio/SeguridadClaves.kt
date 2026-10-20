package cl.inacap.ti3042.cadenafrio

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Reglas y criptografía de las credenciales locales (lineamiento ISO/IEC 27400:
 * cifrado de credenciales y control de acceso).
 *
 * - La contraseña nunca se guarda: se guarda un hash PBKDF2-HMAC-SHA256 con una
 *   sal aleatoria propia de cada usuario.
 * - La comparación del hash es de tiempo constante (MessageDigest.isEqual).
 *
 * No depende de Android, por eso se puede probar con tests unitarios en el PC.
 */
object SeguridadClaves {

    /** Motivo por el que un nombre de usuario o una contraseña no cumplen la política. */
    enum class ErrorCredencial {
        USUARIO_FORMATO,       // 4 a 20 letras o números, sin espacios
        CLAVE_CORTA,           // menos de 8 caracteres
        CLAVE_SIN_LETRA,
        CLAVE_SIN_NUMERO,
        CLAVE_CONTIENE_USUARIO,
        CLAVES_NO_COINCIDEN
    }

    const val ITERACIONES = 120_000
    private const val ALGORITMO = "PBKDF2WithHmacSHA256"
    private const val LARGO_HASH_BITS = 256
    private const val LARGO_SAL_BYTES = 16
    private const val LARGO_MIN_CLAVE = 8

    private val patronUsuario = Regex("^[A-Za-z0-9]{4,20}$")
    private val aleatorio = SecureRandom()

    /** null si el nombre de usuario es válido. */
    fun validarUsuario(usuario: String): ErrorCredencial? =
        if (patronUsuario.matches(usuario)) null else ErrorCredencial.USUARIO_FORMATO

    /** null si la contraseña cumple la política. */
    fun validarClave(usuario: String, clave: String, confirmacion: String): ErrorCredencial? = when {
        clave.length < LARGO_MIN_CLAVE -> ErrorCredencial.CLAVE_CORTA
        clave.none { it.isLetter() } -> ErrorCredencial.CLAVE_SIN_LETRA
        clave.none { it.isDigit() } -> ErrorCredencial.CLAVE_SIN_NUMERO
        usuario.isNotEmpty() && clave.contains(usuario, ignoreCase = true) -> ErrorCredencial.CLAVE_CONTIENE_USUARIO
        clave != confirmacion -> ErrorCredencial.CLAVES_NO_COINCIDEN
        else -> null
    }

    /** Sal aleatoria nueva para un usuario. */
    fun generarSal(): ByteArray = ByteArray(LARGO_SAL_BYTES).also { aleatorio.nextBytes(it) }

    /** Deriva el hash de la contraseña con PBKDF2. */
    fun calcularHash(clave: String, sal: ByteArray, iteraciones: Int = ITERACIONES): ByteArray {
        val especificacion = PBEKeySpec(clave.toCharArray(), sal, iteraciones, LARGO_HASH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(especificacion).encoded
        } finally {
            // Borra la copia de la contraseña que guarda la especificación
            especificacion.clearPassword()
        }
    }

    /** true si la contraseña ingresada corresponde al hash guardado. */
    fun verificar(clave: String, sal: ByteArray, iteraciones: Int, hashGuardado: ByteArray): Boolean =
        MessageDigest.isEqual(calcularHash(clave, sal, iteraciones), hashGuardado)
}
