package com.example.campusconnect.core.error

sealed class AppException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthenticationException(message: String = "Authentication failed") : AppException(message)
    class AuthorizationException(message: String = "Access denied") : AppException(message)
    class NotFoundException(message: String = "Requested item not found") : AppException(message)
    class ValidationException(message: String) : AppException(message)
    class NetworkException(message: String = "Network error occurred", cause: Throwable? = null) : AppException(message, cause)
    class DatabaseException(message: String = "Database error occurred", cause: Throwable? = null) : AppException(message, cause)
}
