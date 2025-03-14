package com.example.messenger.model

open class AppException : RuntimeException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
}

class EmptyFieldException(
    val field: Field
) : AppException()

class PasswordMismatchException : AppException()

class AccountAlreadyExistsException(
    cause: Throwable
) : AppException(cause = cause)

// BackendException with statusCode=401 is usually mapped to this exception
class AuthException(
    cause: Throwable
) : AppException(cause = cause)

class InvalidCredentialsException(cause: Exception) : AppException(cause = cause)

class ConnectionException(cause: Throwable) : AppException(cause = cause)

class UserNotFoundException(cause: Throwable) : AppException(cause = cause)

class DialogNotFoundException(cause: Throwable) : AppException(cause = cause)

class DialogAlreadyExistsException(cause: Throwable) : AppException(cause = cause)

class InvalidStartEndValuesException(cause: Throwable) : AppException(cause = cause)

class NoPermissionException(cause: Throwable) : AppException(cause = cause)

class MessageNotFoundException(cause: Throwable) : AppException(cause = cause)

class InvalidIdsException(cause: Throwable) : AppException(cause = cause)

class GroupNotFoundException(cause: Throwable) : AppException(cause = cause)

class UserAlreadyInGroupException(cause: Throwable) : AppException(cause = cause)

class UserIsNotAMemberOfGroupException(cause: Throwable) : AppException(cause = cause)

class FileNotFoundException(cause: Throwable) : AppException(cause = cause)

class NoChangedMadeException(cause: Throwable) : AppException(cause = cause)

class InvalidKeyException(cause: Throwable) : AppException(cause = cause)

open class BackendException(
    val code: Int,
    message: String
) : AppException(message)

class ParseBackendResponseException(
    cause: Throwable
) : AppException(cause = cause)

internal inline fun <T> wrapBackendExceptions(block: () -> T): T {
    try {
        return block.invoke()
    } catch (e: BackendException) {
        if (e.code == 401) {
            throw AuthException(e)
        } else {
            throw e
        }
    }
}

suspend fun <T> safeApiCall(apiCall: suspend () -> T): Result<T> {
    return try {
        Result.success(apiCall())
    } catch (e: Exception) {
        Result.failure(e)
    }
}