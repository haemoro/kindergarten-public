package com.sotti.kindergarten.repository

import com.sotti.kindergarten.entity.Center
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

data class CenterSearchFilter(
    val establishTypes: List<String>? = null,
    val name: String? = null,
    val sidoName: String? = null,
    val sggName: String? = null,
    val activeOnly: Boolean = false,
)

interface CenterRepositoryCustom {
    fun findNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        filter: CenterSearchFilter,
        pageable: Pageable,
    ): Page<Center>

    fun findAllWithFilters(
        filter: CenterSearchFilter,
        pageable: Pageable,
    ): Page<Center>

    fun findAllWithAdminFilters(
        keyword: String?,
        establishType: String?,
        isVerified: Boolean?,
        isActive: Boolean?,
        pageable: Pageable,
    ): Page<Center>

    fun findMapMarkers(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        filter: CenterSearchFilter,
    ): List<MapMarkerProjection>
}

data class MapMarkerProjection(
    val id: UUID,
    val name: String,
    val establishType: String?,
    val address: String?,
    val phone: String?,
    val lat: Double,
    val lng: Double,
)
