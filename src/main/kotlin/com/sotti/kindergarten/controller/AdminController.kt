package com.sotti.kindergarten.controller

import com.sotti.kindergarten.repository.RegionRepository
import com.sotti.kindergarten.service.DataSyncService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val dataSyncService: DataSyncService,
    private val regionRepository: RegionRepository,
) {
    @PostMapping("/sync")
    fun triggerSync(
        @RequestParam(required = false) sidoCode: String?,
        @RequestParam(required = false) sggCode: String?,
    ): ResponseEntity<Map<String, String>> {
        if (sidoCode != null && sggCode != null) {
            dataSyncService.syncSingleRegion(sidoCode, sggCode)
            return ResponseEntity.ok(mapOf("message" to "Sync completed for sido=$sidoCode, sgg=$sggCode"))
        }

        dataSyncService.syncAllData()
        return ResponseEntity.ok(mapOf("message" to "Full sync completed"))
    }

    @PostMapping("/sync/test")
    fun triggerTestSync(
        @RequestParam(defaultValue = "2") limit: Int,
    ): ResponseEntity<Map<String, Any>> {
        val regions = regionRepository.findAll().take(limit)
        val results = mutableListOf<String>()

        regions.forEach { region ->
            try {
                dataSyncService.syncSingleRegion(region.sidoCode, region.sggCode)
                results.add("${region.sidoName} ${region.sggName}: OK")
            } catch (e: Exception) {
                results.add("${region.sidoName} ${region.sggName}: FAILED - ${e.message}")
            }
        }

        return ResponseEntity.ok(mapOf("message" to "Test sync completed", "results" to results))
    }

    @PostMapping("/sync/api/{apiType}")
    fun triggerApiTypeSync(
        @PathVariable apiType: String,
        @RequestParam(required = false) sidoCode: String?,
        @RequestParam(required = false) sggCode: String?,
    ): ResponseEntity<Map<String, Any>> {
        val count =
            dataSyncService.syncByApiType(apiType, sidoCode, sggCode)
        return ResponseEntity.ok(
            mapOf(
                "message" to "$apiType sync completed",
                "count" to count,
            ),
        )
    }

    @GetMapping("/sync/api/types")
    fun getAvailableApiTypes(): ResponseEntity<List<String>> = ResponseEntity.ok(DataSyncService.AVAILABLE_API_TYPES)
}
