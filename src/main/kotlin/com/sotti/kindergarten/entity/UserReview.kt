package com.sotti.kindergarten.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "user_review",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["device_id", "center_id"]),
    ],
)
class UserReview(
    @Column(nullable = false, length = 255)
    val deviceId: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id", nullable = false)
    val center: Center,
    @Column(nullable = false, length = 20)
    var nickname: String,
    @Column(nullable = false, length = 100)
    var content: String,
) : BaseEntity()
