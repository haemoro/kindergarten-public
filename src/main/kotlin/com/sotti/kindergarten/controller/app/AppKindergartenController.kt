package com.sotti.kindergarten.controller.app

import com.sotti.kindergarten.dto.PageResponse
import com.sotti.kindergarten.dto.app.AppCompareResponse
import com.sotti.kindergarten.dto.app.AppKindergartenDetailResponse
import com.sotti.kindergarten.dto.app.AppKindergartenSearchResponse
import com.sotti.kindergarten.dto.app.CenterReviewResponse
import com.sotti.kindergarten.dto.app.MapMarkerResponse
import com.sotti.kindergarten.service.CenterReviewService
import com.sotti.kindergarten.service.CenterService
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/app/kindergartens")
@Validated
class AppKindergartenController(
    private val centerService: CenterService,
    private val centerReviewService: CenterReviewService,
) {
    @GetMapping("/search")
    fun search(
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lng: Double?,
        @RequestParam(required = false, defaultValue = "2") radiusKm: Double,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) sidoCode: String?,
        @RequestParam(required = false) sggCode: String?,
        @RequestParam(required = false, defaultValue = "distance") sort: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
    ): PageResponse<AppKindergartenSearchResponse> =
        centerService.searchActiveKindergartens(
            lat = lat,
            lng = lng,
            radiusKm = radiusKm,
            establishType = type,
            query = q,
            sidoCode = sidoCode,
            sggCode = sggCode,
            sort = sort,
            page = page,
            size = size,
        )

    @GetMapping("/{id}")
    fun getDetail(
        @PathVariable id: UUID,
    ): AppKindergartenDetailResponse = centerService.getActiveKindergartenDetail(id)

    @GetMapping("/compare")
    fun compare(
        @RequestParam ids: String,
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lng: Double?,
    ): AppCompareResponse {
        val centerIds = ids.split(",").mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }
        return centerService.compareActiveKindergartens(centerIds, lat, lng)
    }

    @GetMapping("/{id}/reviews")
    fun getReviews(
        @PathVariable id: UUID,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
    ): PageResponse<CenterReviewResponse> = centerReviewService.getReviews(id, page, size)

    @GetMapping("/map-markers")
    fun getMapMarkers(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(required = false, defaultValue = "2") radiusKm: Double,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) sidoCode: String?,
        @RequestParam(required = false) sggCode: String?,
        @RequestParam(required = false) limit: Int?,
    ): List<MapMarkerResponse> =
        centerService.getMapMarkers(
            lat = lat,
            lng = lng,
            radiusKm = radiusKm,
            establishType = type,
            sidoCode = sidoCode,
            sggCode = sggCode,
            limit = limit,
        )
}
