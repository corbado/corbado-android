package com.corbado.connect.example

import java.util.*

/**
 * Factory for generating test data, mirroring the Swift TestDataFactory structure.
 */
object TestDataFactory {
    
    const val phoneNumber = "+1234567890"
    const val password = "TestPassword123!"
    
    /**
     * Generate a unique email address for testing.
     * Format: integration-test+{timestamp}@corbado.com
     */
    fun createEmail(): String {
        val timestamp = System.currentTimeMillis()
        return "integration-test+$timestamp@corbado.com"
    }
} 