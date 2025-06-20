package io.corbado.connect.example

import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.codec.binary.Base32
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Data class representing a wrapped TOTP generator with timing information.
 * Equivalent to Swift's WrappedTOTP struct.
 */
private data class WrappedTOTP(
    val generator: GoogleAuthenticator,
    var nextBoundary: Long // Timestamp in milliseconds
)

/**
 * Mock authenticator app for generating TOTP codes, mirroring the Swift AuthenticatorApp.
 * Supports proper timing boundaries and prevents reuse within 30-second intervals.
 * 
 * This implementation splits the logic into:
 * - addBySetupKey: Set up an OTP for the first time and return current code
 * - getCode: Get OTPs after they have been setup, waiting for next boundary if needed
 */
class AuthenticatorApp {
    private val existing = ConcurrentHashMap<String, WrappedTOTP>()
    private val mutex = Mutex()

    suspend fun addBySecret(secret: String): String? = mutex.withLock {
        return try {
            // Create TOTP generator
            val generator = GoogleAuthenticator(secret.toByteArray())
            
            val now = System.currentTimeMillis()
            val currentCode = generator.generate()
            
            // Calculate the next boundary (start of next 30-second period)
            val boundary = calculateNextBoundary(now, 30_000L)

            // Store the wrapped TOTP for future use
            existing[secret] = WrappedTOTP(generator, boundary)
            
            currentCode
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets the next code for a previously registered setup key.
     * If called too early, it will suspend until the next TOTP period starts.
     * 
     * @param setupKey The setup key that was previously registered
     * @return The next TOTP code, or null if the setup key is not found
     */
    suspend fun getCode(setupKey: String): String? {
        // First, get the wrapped TOTP and check timing with lock
        val (generator, waitTime) = mutex.withLock {
            val wrapped = existing[setupKey] ?: return null
            val now = System.currentTimeMillis()
            
            if (now < wrapped.nextBoundary) {
                val waitTime = wrapped.nextBoundary - now
                println("AuthenticatorApp: Waiting ${waitTime}ms for next TOTP period")
                Pair(wrapped.generator, waitTime)
            } else {
                Pair(wrapped.generator, 0L)
            }
        }
        
        // Release the lock and wait if needed
        if (waitTime > 0) {
            Thread.sleep(waitTime)
        }
        
        // Generate the code and update the boundary with lock
        return mutex.withLock {
            val wrapped = existing[setupKey] ?: return null
            val then = System.currentTimeMillis()
            val code = wrapped.generator.generate()
            
            // Schedule for exactly the next boundary
            val nextBoundary = calculateNextBoundary(then, 30_000L)
            wrapped.nextBoundary = nextBoundary
            
            code
        }
    }
    
    /**
     * Calculate the next period boundary after the given time.
     * 
     * @param timeMillis Current time in milliseconds
     * @param periodMillis Period length in milliseconds (30000 for 30 seconds)
     * @return The timestamp of the next period boundary
     */
    private fun calculateNextBoundary(timeMillis: Long, periodMillis: Long): Long {
        val periods = timeMillis / periodMillis
        return (periods + 1) * periodMillis
    }
    
    /**
     * Clear all stored TOTP generators (for testing purposes).
     */
    fun clear() {
        existing.clear()
    }
    
    /**
     * Check if a setup key is already registered.
     */
    fun hasSetupKey(setupKey: String): Boolean {
        return existing.containsKey(setupKey)
    }
} 