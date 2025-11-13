package com.example.farsilandtv.data.api

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Backend API Service (FUTURE IMPLEMENTATION)
 *
 * ⚠️ THIS IS A PLACEHOLDER INTERFACE ⚠️
 *
 * Currently, FarsiPlex has no backend server. This interface defines
 * the API contract for when a backend is implemented.
 *
 * ## Why This Exists
 *
 * FCM (Firebase Cloud Messaging) tokens need to be registered with a backend
 * server to enable targeted push notifications. Without backend registration,
 * the app can still receive broadcast notifications, but cannot:
 * - Send personalized notifications based on user preferences
 * - Track which devices should receive which notifications
 * - Remove invalid/expired tokens
 * - Associate tokens with user accounts
 *
 * ## Current Status
 *
 * - ✅ FCM tokens are generated and stored locally
 * - ✅ Tokens are ready to be sent to backend when available
 * - ❌ No backend server exists yet
 * - ❌ No API endpoint to register tokens
 *
 * ## Backend Implementation Guide
 *
 * When you create a backend server (Node.js, Python, Java, etc.), implement:
 *
 * ### 1. Database Schema
 * ```sql
 * CREATE TABLE device_tokens (
 *     id SERIAL PRIMARY KEY,
 *     fcm_token VARCHAR(255) UNIQUE NOT NULL,
 *     device_id VARCHAR(100),
 *     platform VARCHAR(20) DEFAULT 'android',
 *     app_version VARCHAR(20),
 *     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *     last_seen TIMESTAMP,
 *     is_active BOOLEAN DEFAULT true
 * );
 *
 * CREATE INDEX idx_fcm_token ON device_tokens(fcm_token);
 * CREATE INDEX idx_device_id ON device_tokens(device_id);
 * CREATE INDEX idx_active ON device_tokens(is_active);
 * ```
 *
 * ### 2. API Endpoint
 * ```
 * POST /api/devices/register
 * Content-Type: application/json
 *
 * Request Body:
 * {
 *     "fcm_token": "string (FCM registration token)",
 *     "device_id": "string (Android device ID)",
 *     "platform": "android",
 *     "app_version": "string (e.g., 1.0.0)"
 * }
 *
 * Response (200 OK):
 * {
 *     "success": true,
 *     "message": "Device registered successfully",
 *     "device_id": "uuid"
 * }
 *
 * Response (400 Bad Request):
 * {
 *     "success": false,
 *     "error": "Invalid token format"
 * }
 * ```
 *
 * ### 3. Backend Logic
 * - Validate token format
 * - Check if token already exists (UPDATE) or create new (INSERT)
 * - Handle duplicate tokens gracefully
 * - Mark old tokens as inactive when new token received
 * - Log registration for debugging
 *
 * ### 4. Sending Notifications
 * Once tokens are registered, use Firebase Admin SDK to send notifications:
 *
 * ```javascript
 * // Node.js example
 * const admin = require('firebase-admin');
 *
 * // Send to specific device
 * admin.messaging().send({
 *     token: 'user_fcm_token',
 *     notification: {
 *         title: 'New Episode',
 *         body: 'Breaking Bad - S05E16'
 *     },
 *     data: {
 *         type: 'new_episode',
 *         series_title: 'Breaking Bad',
 *         episode_title: 'Felina',
 *         content_id: '12345'
 *     }
 * });
 * ```
 *
 * ### 5. Android Integration
 * Once backend is ready, in FCMTokenManager.kt:
 * 1. Uncomment registerWithBackend() method
 * 2. Create Retrofit instance for your backend
 * 3. Call BackendApiService.registerDevice()
 *
 * ## Technology Recommendations
 *
 * Choose based on your expertise:
 *
 * - **Node.js + Express**: Fast, simple, good Firebase SDK support
 * - **Python + FastAPI**: Easy to learn, clean code, async support
 * - **Java + Spring Boot**: Enterprise-ready, type-safe
 * - **Go + Gin**: High performance, efficient for high traffic
 *
 * ## Security Considerations
 *
 * - Use HTTPS only (no cleartext traffic)
 * - Validate all inputs
 * - Rate limit the registration endpoint
 * - Implement authentication if linking to user accounts
 * - Regularly clean up inactive tokens
 * - Monitor for suspicious activity
 *
 * @see FCMTokenManager for client-side token management
 * @see FarsilandMessagingService for FCM message handling
 */
interface BackendApiService {

    /**
     * Register device with FCM token
     *
     * @param request Device registration details
     * @return Registration response with success status
     */
    @POST("api/devices/register")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequest): DeviceRegistrationResponse

    /**
     * Device registration request
     *
     * @property fcmToken Firebase Cloud Messaging registration token
     * @property deviceId Unique device identifier (Android ID)
     * @property platform Device platform (always "android" for this app)
     * @property appVersion Current app version (e.g., "1.0.0")
     */
    data class DeviceRegistrationRequest(
        val fcmToken: String,
        val deviceId: String,
        val platform: String = "android",
        val appVersion: String
    )

    /**
     * Device registration response
     *
     * @property success Whether registration was successful
     * @property message Human-readable message
     * @property deviceId Server-assigned device ID (optional)
     * @property error Error message if success is false (optional)
     */
    data class DeviceRegistrationResponse(
        val success: Boolean,
        val message: String,
        val deviceId: String? = null,
        val error: String? = null
    )
}
