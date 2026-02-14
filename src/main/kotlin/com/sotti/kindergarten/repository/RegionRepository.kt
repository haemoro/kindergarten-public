package com.sotti.kindergarten.repository

import com.sotti.kindergarten.entity.Region
import org.springframework.data.jpa.repository.JpaRepository

interface RegionRepository : JpaRepository<Region, String> {
    fun findAllByOrderBySidoCodeAscSggCodeAsc(): List<Region>

    fun findBySggCode(sggCode: String): Region?

    fun findFirstBySidoCode(sidoCode: String): Region?
}
