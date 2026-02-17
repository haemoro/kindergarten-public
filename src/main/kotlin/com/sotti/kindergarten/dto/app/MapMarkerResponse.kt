package com.sotti.kindergarten.dto.app

import java.util.UUID

data class MapMarkerResponse(
    val id: UUID,
    val name: String,
    val establishType: String?,
    val address: String?,
    val phone: String?,
    val lat: Double,
    val lng: Double,
)
