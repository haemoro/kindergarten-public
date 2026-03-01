package com.sotti.kindergarten.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "review_seed_state")
class ReviewSeedState(
    @Column(name = "state_key", nullable = false, unique = true, length = 50)
    val key: String,
    @Column(nullable = false)
    var currentSidoIndex: Int = 0,
    @Column(nullable = false)
    var currentOffset: Int = 0,
) : BaseEntity()
