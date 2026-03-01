package com.sotti.kindergarten.repository

import com.sotti.kindergarten.entity.ReviewSeedState
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReviewSeedStateRepository : JpaRepository<ReviewSeedState, UUID> {
    fun findByKey(key: String): ReviewSeedState?
}
