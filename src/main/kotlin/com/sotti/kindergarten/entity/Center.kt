package com.sotti.kindergarten.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import org.locationtech.jts.geom.Point
import java.time.LocalDateTime

@Entity
@Table(name = "center")
class Center(
    @Column(unique = true, nullable = false, length = 50)
    val kinderCode: String,
    @Column(length = 100)
    var officEdu: String? = null,
    @Column(length = 100)
    var subOfficeEdu: String? = null,
    @Column(nullable = false, length = 200)
    var name: String,
    @Column(length = 50)
    var establishType: String? = null,
    @Column(length = 100)
    var representativeName: String? = null,
    @Column(length = 100)
    var directorName: String? = null,
    @Column(length = 50)
    var establishDate: String? = null,
    @Column(length = 50)
    var openDate: String? = null,
    @Column(length = 500)
    var address: String? = null,
    @Column(length = 50)
    var phone: String? = null,
    @Column(length = 50)
    var fax: String? = null,
    @Column(length = 500)
    var homepage: String? = null,
    @Column(length = 200)
    var operatingHours: String? = null,
    // Class counts
    var classCount3: Int? = null,
    var classCount4: Int? = null,
    var classCount5: Int? = null,
    var mixedClassCount: Int? = null,
    var specialClassCount: Int? = null,
    // Capacity
    var totalCapacity: Int? = null,
    var capacity3: Int? = null,
    var capacity4: Int? = null,
    var capacity5: Int? = null,
    var mixedCapacity: Int? = null,
    var specialCapacity: Int? = null,
    // Enrollment
    var enrollment3: Int? = null,
    var enrollment4: Int? = null,
    var enrollment5: Int? = null,
    var mixedEnrollment: Int? = null,
    var specialEnrollment: Int? = null,
    // Location (PostGIS)
    @Column(columnDefinition = "geography(Point, 4326)")
    var location: Point? = null,
    // Meta
    @Column(length = 50)
    var disclosureTiming: String? = null,
    @Column(length = 50)
    var actingDirector: String? = null,
    var sourceUpdatedAt: LocalDateTime? = null,
    // Admin fields
    @Column(columnDefinition = "boolean not null default false")
    var isVerified: Boolean = false,
    @Column(columnDefinition = "boolean not null default true")
    var isActive: Boolean = true,
    @Column(columnDefinition = "TEXT")
    var adminMemo: String? = null,
    // Relationships
    @OneToOne(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var building: CenterBuilding? = null,
    @OneToOne(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var classroom: CenterClassroom? = null,
    @OneToOne(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var teacher: CenterTeacher? = null,
    @OneToOne(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var lessonDay: CenterLessonDay? = null,
    @OneToOne(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var meal: CenterMeal? = null,
    @OneToOne(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var bus: CenterBus? = null,
    @OneToOne(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var yearOfWork: CenterYearOfWork? = null,
    @OneToOne(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var environment: CenterEnvironment? = null,
    @OneToOne(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var safetyCheck: CenterSafetyCheck? = null,
    @OneToMany(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    @BatchSize(size = 50)
    var safetyEducations: MutableSet<CenterSafetyEducation> = mutableSetOf(),
    @OneToOne(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var mutualAid: CenterMutualAid? = null,
    @OneToMany(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    @BatchSize(size = 50)
    var insurances: MutableSet<CenterInsurance> = mutableSetOf(),
    @OneToOne(mappedBy = "center", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var afterSchool: CenterAfterSchool? = null,
) : BaseEntity()
