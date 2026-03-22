package com.sotti.kindergarten.util

import java.util.UUID

fun parseUUIDs(csvString: String?): List<UUID>? =
    csvString?.split(",")?.mapNotNull {
        runCatching { UUID.fromString(it.trim()) }.getOrNull()
    }

fun parseTypes(csvString: String?): List<String>? =
    csvString
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.takeIf { it.isNotEmpty() }
