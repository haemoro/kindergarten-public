package com.sotti.kindergarten.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val httpStatus: HttpStatus,
    val message: String,
) {
    KINDERGARTEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Kindergarten not found"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Invalid parameter"),
    COMPARE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Comparison is limited to a maximum of 4 kindergartens"),
    COMPARE_MINIMUM_REQUIRED(HttpStatus.BAD_REQUEST, "At least 2 kindergartens are required for comparison"),
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "Favorite not found"),
    DUPLICATE_FAVORITE(HttpStatus.CONFLICT, "Favorite already exists"),
    CRAWL_ALREADY_RUNNING(HttpStatus.CONFLICT, "Crawl is already running"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "Admin not found"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Email already exists"),
    ADMIN_INACTIVE(HttpStatus.FORBIDDEN, "Admin account is inactive"),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "Current password is incorrect"),
    CANNOT_DELETE_SELF(HttpStatus.BAD_REQUEST, "Cannot delete your own account"),
    USER_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "User review not found"),
    DUPLICATE_USER_REVIEW(HttpStatus.CONFLICT, "이미 이 유치원에 한줄평을 작성했습니다"),
    PROFANITY_DETECTED(HttpStatus.BAD_REQUEST, "부적절한 표현이 포함되어 있습니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
}
