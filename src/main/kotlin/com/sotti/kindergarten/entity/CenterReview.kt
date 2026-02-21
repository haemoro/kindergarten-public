package com.sotti.kindergarten.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "center_review",
    uniqueConstraints = [UniqueConstraint(columnNames = ["link"])],
)
class CenterReview(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id", nullable = false)
    val center: Center,
    @Column(nullable = false, length = 500)
    val title: String,
    @Column(nullable = false, length = 1000)
    val link: String,
    @Column(nullable = false, length = 1000)
    val snippet: String,
    @Column(nullable = false, length = 10)
    val source: String,
    val postDate: LocalDate? = null,
) : BaseEntity()
