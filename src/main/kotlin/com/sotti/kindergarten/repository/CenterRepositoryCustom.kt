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

data class SearchListProjection(
    val id: UUID,
    val name: String,
    val establishType: String?,
    val address: String?,
    val phone: String?,
    val lat: Double?,
    val lng: Double?,
    val distanceKm: Double?,
    val capacity: Int?,
    val currentEnrollment: Int?,
    val totalClassCount: Int?,
    val mealProvided: Boolean,
    val busAvailable: Boolean,
    val extendedCare: Boolean,
    val totalCount: Long,
)

interface CenterRepositoryCustom {
    fun findNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        filter: CenterSearchFilter,
        pageable: Pageable,
        sortType: String? = null,
    ): Page<SearchListProjection>

    fun findAllWithFilters(
        filter: CenterSearchFilter,
        pageable: Pageable,
        lat: Double? = null,
        lng: Double? = null,
    ): Page<SearchListProjection>

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
        limit: Int? = null,
    ): List<MapMarkerProjection>

    fun findCompareData(
        ids: List<UUID>,
        lat: Double?,
        lng: Double?,
    ): List<CompareProjection>
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

data class CompareProjection(
    val id: UUID,
    val name: String,
    val establishType: String?,
    val address: String?,
    val distanceKm: Double?,
    val capacity: Int?,
    val currentEnrollment: Int?,
    val teacherCount: Int?,
    val classCount: Int?,
    val mealProvided: Boolean,
    val busAvailable: Boolean,
    val extendedCare: Boolean,
    val buildingArea: Double?,
    val classroomArea: Double?,
    val cctvInstalled: Boolean,
    val cctvTotal: Int?,
    val isActive: Boolean,
)
