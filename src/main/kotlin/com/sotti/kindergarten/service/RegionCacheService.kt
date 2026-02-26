package com.sotti.kindergarten.service

import com.sotti.kindergarten.repository.RegionRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

@Service
class RegionCacheService(
    private val regionRepository: RegionRepository,
) {
    private lateinit var sidoCodeToName: Map<String, String>
    private lateinit var sggCodeToName: Map<String, String>

    @PostConstruct
    fun init() {
        val regions = regionRepository.findAll()
        sidoCodeToName =
            regions
                .associate { it.sidoCode to it.sidoName }
        sggCodeToName =
            regions
                .filter { it.sggCode.isNotBlank() }
                .associate { it.sggCode to it.sggName }
    }

    fun getSidoName(sidoCode: String): String? = sidoCodeToName[sidoCode]

    fun getSggName(sggCode: String): String? = sggCodeToName[sggCode]
}
