package com.sotti.kindergarten.util

import com.sotti.kindergarten.dto.PageResponse
import org.springframework.data.domain.Page

fun <T, R> Page<T>.toPageResponse(mapper: (T) -> R): PageResponse<R> =
    PageResponse(
        content = content.map(mapper),
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )
