package com.sotti.kindergarten.service

import com.sotti.kindergarten.dto.AfterSchoolInfo
import com.sotti.kindergarten.dto.BuildingInfo
import com.sotti.kindergarten.dto.BusInfo
import com.sotti.kindergarten.dto.CenterCompareRequest
import com.sotti.kindergarten.dto.CenterCompareResponse
import com.sotti.kindergarten.dto.CenterDetailResponse
import com.sotti.kindergarten.dto.CenterListResponse
import com.sotti.kindergarten.dto.ClassroomInfo
import com.sotti.kindergarten.dto.ComparisonItem
import com.sotti.kindergarten.dto.EnvironmentInfo
import com.sotti.kindergarten.dto.InsuranceInfo
import com.sotti.kindergarten.dto.LessonDayInfo
import com.sotti.kindergarten.dto.MealInfo
import com.sotti.kindergarten.dto.MutualAidInfo
import com.sotti.kindergarten.dto.PageResponse
import com.sotti.kindergarten.dto.SafetyCheckInfo
import com.sotti.kindergarten.dto.SafetyEducationInfo
import com.sotti.kindergarten.dto.TeacherInfo
import com.sotti.kindergarten.dto.YearOfWorkInfo
import com.sotti.kindergarten.dto.app.AfterSchoolSection
import com.sotti.kindergarten.dto.app.AppCompareResponse
import com.sotti.kindergarten.dto.app.AppKindergartenDetailResponse
import com.sotti.kindergarten.dto.app.AppKindergartenSearchResponse
import com.sotti.kindergarten.dto.app.EducationSection
import com.sotti.kindergarten.dto.app.FacilitySection
import com.sotti.kindergarten.dto.app.InsuranceResponse
import com.sotti.kindergarten.dto.app.MapMarkerResponse
import com.sotti.kindergarten.dto.app.MealSection
import com.sotti.kindergarten.dto.app.SafetyEducationResponse
import com.sotti.kindergarten.dto.app.SafetySection
import com.sotti.kindergarten.dto.app.TeacherSection
import com.sotti.kindergarten.entity.Center
import com.sotti.kindergarten.entity.CenterInsurance
import com.sotti.kindergarten.entity.CenterSafetyEducation
import com.sotti.kindergarten.exception.BusinessException
import com.sotti.kindergarten.exception.CenterNotFoundException
import com.sotti.kindergarten.exception.ErrorCode
import com.sotti.kindergarten.exception.InvalidCompareRequestException
import com.sotti.kindergarten.repository.CenterRepository
import com.sotti.kindergarten.repository.CenterSearchFilter
import com.sotti.kindergarten.repository.RegionRepository
import com.sotti.kindergarten.repository.SearchListProjection
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CenterService(
    private val centerRepository: CenterRepository,
    private val regionRepository: RegionRepository,
    private val regionCacheService: RegionCacheService,
) {
    fun listCenters(
        lat: Double?,
        lng: Double?,
        radiusKm: Double?,
        establishType: String?,
        query: String?,
        sort: String?,
        page: Int,
        size: Int,
    ): PageResponse<CenterListResponse> {
        val pageable = PageRequest.of(page, size, getSort(sort))
        val filter = CenterSearchFilter(establishTypes = parseTypes(establishType), name = query)

        val projPage =
            if (lat != null && lng != null && radiusKm != null) {
                centerRepository.findNearby(lat, lng, radiusKm * 1000, filter, pageable)
            } else {
                centerRepository.findAllWithFilters(filter, pageable, lat, lng)
            }

        return PageResponse(
            content = projPage.content.map { it.toCenterListResponse() },
            page = projPage.number,
            size = projPage.size,
            totalElements = projPage.totalElements,
            totalPages = projPage.totalPages,
        )
    }

    @Cacheable("centerDetail", key = "#id")
    fun getCenterDetail(id: UUID): CenterDetailResponse {
        val center = centerRepository.findByIdWithDetails(id) ?: throw CenterNotFoundException(id)
        return toCenterDetailResponse(center)
    }

    fun compareCenters(request: CenterCompareRequest): CenterCompareResponse {
        if (request.centerIds.size !in 2..4) {
            throw InvalidCompareRequestException()
        }

        val projections = centerRepository.findCompareData(request.centerIds, request.lat, request.lng)
        if (projections.size != request.centerIds.size) {
            val foundIds = projections.map { it.id }.toSet()
            val missingId = request.centerIds.firstOrNull { it !in foundIds }
            throw CenterNotFoundException(missingId ?: request.centerIds.first())
        }

        val comparisons =
            projections.map { projection ->
                ComparisonItem(
                    id = projection.id,
                    name = projection.name,
                    establishType = projection.establishType,
                    address = projection.address,
                    distanceKm = projection.distanceKm,
                    capacity = projection.capacity,
                    currentEnrollment = projection.currentEnrollment,
                    teacherCount = projection.teacherCount,
                    classCount = projection.classCount,
                    mealProvided = projection.mealProvided,
                    busAvailable = projection.busAvailable,
                    extendedCare = projection.extendedCare,
                    buildingArea = projection.buildingArea,
                    classroomArea = projection.classroomArea,
                    cctvInstalled = projection.cctvInstalled,
                    cctvTotal = projection.cctvTotal,
                )
            }

        return CenterCompareResponse(centers = comparisons)
    }

    fun searchActiveKindergartens(
        lat: Double?,
        lng: Double?,
        radiusKm: Double?,
        establishType: String?,
        query: String?,
        sidoCode: String?,
        sggCode: String?,
        sort: String?,
        page: Int,
        size: Int,
    ): PageResponse<AppKindergartenSearchResponse> {
        val pageable = PageRequest.of(page, size, getSort(sort))
        val (sidoName, sggName) = resolveRegionNames(sidoCode, sggCode)
        val filter =
            CenterSearchFilter(
                establishTypes = parseTypes(establishType),
                name = query,
                sidoName = sidoName,
                sggName = sggName,
                activeOnly = true,
            )

        val hasTextOrRegion = !query.isNullOrBlank() || sidoCode != null || sggCode != null

        val projPage =
            if (!hasTextOrRegion && lat != null && lng != null && radiusKm != null) {
                centerRepository.findNearby(lat, lng, radiusKm * 1000, filter, pageable, sort)
            } else {
                centerRepository.findAllWithFilters(filter, pageable, lat, lng)
            }

        return PageResponse(
            content = projPage.content.map { it.toAppSearchResponse() },
            page = projPage.number,
            size = projPage.size,
            totalElements = projPage.totalElements,
            totalPages = projPage.totalPages,
        )
    }

    @Cacheable("appCenterDetail", key = "#id")
    fun getActiveKindergartenDetail(id: UUID): AppKindergartenDetailResponse {
        val center =
            centerRepository.findByIdWithDetails(id)
                ?: throw BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND)
        if (!center.isActive) {
            throw BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND)
        }
        return toAppDetailResponse(center)
    }

    fun compareActiveKindergartens(
        ids: List<UUID>,
        lat: Double?,
        lng: Double?,
    ): AppCompareResponse {
        if (ids.size < 2) {
            throw BusinessException(ErrorCode.COMPARE_MINIMUM_REQUIRED)
        }
        if (ids.size > 4) {
            throw BusinessException(ErrorCode.COMPARE_LIMIT_EXCEEDED)
        }

        val projections = centerRepository.findCompareData(ids, lat, lng)
        if (projections.size != ids.size) {
            throw BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND)
        }

        val activeProjections = projections.filter { it.isActive }
        if (activeProjections.size != ids.size) {
            throw BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND)
        }

        val comparisons =
            activeProjections.map { projection ->
                ComparisonItem(
                    id = projection.id,
                    name = projection.name,
                    establishType = projection.establishType,
                    address = projection.address,
                    distanceKm = projection.distanceKm,
                    capacity = projection.capacity,
                    currentEnrollment = projection.currentEnrollment,
                    teacherCount = projection.teacherCount,
                    classCount = projection.classCount,
                    mealProvided = projection.mealProvided,
                    busAvailable = projection.busAvailable,
                    extendedCare = projection.extendedCare,
                    buildingArea = projection.buildingArea,
                    classroomArea = projection.classroomArea,
                    cctvInstalled = projection.cctvInstalled,
                    cctvTotal = projection.cctvTotal,
                )
            }

        return AppCompareResponse(centers = comparisons)
    }

    fun getMapMarkers(
        lat: Double,
        lng: Double,
        radiusKm: Double,
        establishType: String?,
        sidoCode: String?,
        sggCode: String?,
        limit: Int? = null,
    ): List<MapMarkerResponse> {
        val (sidoName, sggName) = resolveRegionNames(sidoCode, sggCode)
        val filter =
            CenterSearchFilter(
                establishTypes = parseTypes(establishType),
                sidoName = sidoName,
                sggName = sggName,
                activeOnly = true,
            )
        return centerRepository.findMapMarkers(lat, lng, radiusKm * 1000, filter, limit).map { projection ->
            MapMarkerResponse(
                id = projection.id,
                name = projection.name,
                establishType = projection.establishType,
                address = projection.address,
                phone = projection.phone,
                lat = projection.lat,
                lng = projection.lng,
            )
        }
    }

    private fun SearchListProjection.toAppSearchResponse() =
        AppKindergartenSearchResponse(
            id = id,
            name = name,
            establishType = establishType,
            address = address,
            phone = phone,
            lat = lat,
            lng = lng,
            distanceKm = distanceKm,
            capacity = capacity,
            currentEnrollment = currentEnrollment,
            totalClassCount = totalClassCount,
            mealProvided = mealProvided,
            busAvailable = busAvailable,
            extendedCare = extendedCare,
        )

    private fun SearchListProjection.toCenterListResponse() =
        CenterListResponse(
            id = id,
            name = name,
            establishType = establishType,
            address = address,
            phone = phone,
            lat = lat,
            lng = lng,
            distanceKm = distanceKm,
            capacity = capacity,
            currentEnrollment = currentEnrollment,
            totalClassCount = totalClassCount,
            mealProvided = mealProvided,
            busAvailable = busAvailable,
            extendedCare = extendedCare,
        )

    private fun toAppDetailResponse(center: Center): AppKindergartenDetailResponse =
        AppKindergartenDetailResponse(
            id = center.id!!,
            name = center.name,
            establishType = center.establishType,
            address = center.address,
            phone = center.phone,
            homepage = center.homepage,
            operatingHours = center.operatingHours,
            lat = center.location?.y,
            lng = center.location?.x,
            representativeName = center.representativeName,
            directorName = center.directorName,
            establishDate = center.establishDate,
            openDate = center.openDate,
            education = buildEducationSection(center),
            meal = buildMealSection(center),
            safety = buildSafetySection(center),
            facility = buildFacilitySection(center),
            teacher = buildTeacherSection(center),
            afterSchool = buildAfterSchoolSection(center),
            sourceUpdatedAt = center.sourceUpdatedAt,
        )

    private fun buildEducationSection(center: Center): EducationSection? {
        val hasClassData =
            listOfNotNull(
                center.classCount3,
                center.classCount4,
                center.classCount5,
                center.mixedClassCount,
                center.specialClassCount,
            ).isNotEmpty()
        val hasLessonDay = center.lessonDay != null

        if (!hasClassData && !hasLessonDay) return null

        return EducationSection(
            classCountByAge =
                mapOf(
                    "age3" to center.classCount3,
                    "age4" to center.classCount4,
                    "age5" to center.classCount5,
                    "mixed" to center.mixedClassCount,
                    "special" to center.specialClassCount,
                ),
            capacityByAge =
                mapOf(
                    "age3" to center.capacity3,
                    "age4" to center.capacity4,
                    "age5" to center.capacity5,
                    "mixed" to center.mixedCapacity,
                    "special" to center.specialCapacity,
                ),
            enrollmentByAge =
                mapOf(
                    "age3" to center.enrollment3,
                    "age4" to center.enrollment4,
                    "age5" to center.enrollment5,
                    "mixed" to center.mixedEnrollment,
                    "special" to center.specialEnrollment,
                ),
            lessonDaysAge3 = center.lessonDay?.lessonDays3,
            lessonDaysAge4 = center.lessonDay?.lessonDays4,
            lessonDaysAge5 = center.lessonDay?.lessonDays5,
            lessonDaysMixed = center.lessonDay?.mixedLessonDays,
            belowLegalDays = center.lessonDay?.belowLegalDays,
        )
    }

    private fun buildMealSection(center: Center): MealSection? =
        center.meal?.let {
            MealSection(
                mealOperationType = it.mealOperationType,
                consignmentCompany = it.consignmentCompany,
                mealChildren = it.mealChildren,
                cookCount = it.cookCount,
            )
        }

    private fun buildSafetySection(center: Center): SafetySection? {
        val hasEnvironment = center.environment != null
        val hasSafetyCheck = center.safetyCheck != null
        val hasMutualAid = center.mutualAid != null

        if (!hasEnvironment && !hasSafetyCheck && !hasMutualAid) return null

        return SafetySection(
            airQualityCheck = center.environment?.airQualityCheckResult,
            disinfectionCheck = center.environment?.regularDisinfectionResult,
            waterQualityCheck = center.environment?.groundwaterTestResult,
            dustMeasurement = center.environment?.dustCheckResult,
            lightMeasurement = center.environment?.lightCheckResult,
            fireInsuranceCheck = normalizeCheckYn(center.safetyCheck?.fireSafetyYn),
            gasCheck = normalizeCheckYn(center.safetyCheck?.gasCheckYn),
            electricCheck = normalizeCheckYn(center.safetyCheck?.electricCheckYn),
            playgroundCheck = normalizeCheckYn(center.safetyCheck?.playgroundCheckYn),
            cctvInstalled = center.safetyCheck?.cctvInstalled,
            cctvTotal = center.safetyCheck?.cctvTotal,
            schoolSafetyEnrolled = center.mutualAid?.schoolSafetyEnrolled,
            educationFacilityEnrolled = center.mutualAid?.educationFacilityEnrolled,
            safetyEducations =
                buildSafetyEducations(center.safetyEducations),
            insurances =
                buildInsurances(center.insurances),
        )
    }

    private fun normalizeCheckYn(value: String?): String =
        when (value?.uppercase()) {
            "Y" -> "점검완료"
            "N" -> "미점검"
            else -> "-"
        }

    private fun buildSafetyEducations(educations: Collection<CenterSafetyEducation>): List<SafetyEducationResponse>? =
        educations
            .filter { edu ->
                val allFields =
                    listOf(
                        edu.lifeSafety,
                        edu.trafficSafety,
                        edu.violencePrevention,
                        edu.drugPrevention,
                        edu.cyberPrevention,
                        edu.disasterSafety,
                        edu.occupationalSafety,
                        edu.firstAid,
                    )
                !(edu.semester == null && allFields.all { it == "0" || it == null })
            }.distinctBy { it.semester }
            .takeIf { it.isNotEmpty() }
            ?.map { edu ->
                SafetyEducationResponse(
                    semester =
                        when (edu.semester) {
                            "1" -> "1학기"
                            "2" -> "2학기"
                            else -> edu.semester
                        },
                    lifeSafety = edu.lifeSafety?.toIntOrNull() ?: 0,
                    trafficSafety = edu.trafficSafety?.toIntOrNull() ?: 0,
                    violencePrevention = edu.violencePrevention?.toIntOrNull() ?: 0,
                    drugPrevention = edu.drugPrevention?.toIntOrNull() ?: 0,
                    cyberPrevention = edu.cyberPrevention?.toIntOrNull() ?: 0,
                    disasterSafety = edu.disasterSafety?.toIntOrNull() ?: 0,
                    occupationalSafety = edu.occupationalSafety?.toIntOrNull() ?: 0,
                    firstAid = edu.firstAid?.toIntOrNull() ?: 0,
                )
            }

    private fun buildInsurances(insurances: Collection<CenterInsurance>): List<InsuranceResponse>? =
        insurances
            .distinctBy { it.insuranceName }
            .takeIf { it.isNotEmpty() }
            ?.map {
                val company =
                    listOfNotNull(it.company1, it.company2, it.company3)
                        .joinToString(", ")
                        .ifEmpty { null }
                InsuranceResponse(
                    insuranceName = it.insuranceName,
                    isTarget = it.targetYn == "Y",
                    isEnrolled = it.enrolledYn == "Y",
                    company = company,
                )
            }

    private fun buildFacilitySection(center: Center): FacilitySection? {
        val hasBuilding = center.building != null
        val hasClassroom = center.classroom != null
        val hasBus = center.bus != null

        if (!hasBuilding && !hasClassroom && !hasBus) return null

        return FacilitySection(
            archYear = center.building?.archYear?.toIntOrNull(),
            floorCount = center.building?.floorCount,
            buildingArea = center.building?.buildingArea,
            totalLandArea = center.building?.totalLandArea,
            classroomCount = center.classroom?.classroomCount,
            classroomArea = center.classroom?.classroomArea,
            playgroundArea = center.classroom?.playgroundArea,
            busOperating = center.bus?.busOperating,
            operatingBusCount = center.bus?.operatingBusCount,
            registeredBusCount = center.bus?.registeredBusCount,
        )
    }

    private fun buildTeacherSection(center: Center): TeacherSection? {
        val hasTeacher = center.teacher != null
        val hasYearOfWork = center.yearOfWork != null

        if (!hasTeacher && !hasYearOfWork) return null

        return TeacherSection(
            directorCount = center.teacher?.directorCount,
            viceDirectorCount = center.teacher?.viceDirectorCount,
            masterTeacherCount = center.teacher?.masterTeacherCount,
            leadTeacherCount = center.teacher?.leadTeacherCount,
            generalTeacherCount = center.teacher?.generalTeacherCount,
            specialTeacherCount = center.teacher?.specialTeacherCount,
            healthTeacherCount = center.teacher?.healthTeacherCount,
            nutritionTeacherCount = center.teacher?.nutritionTeacherCount,
            staffCount = center.teacher?.staffCount,
            masterQualCount = center.teacher?.masterQualCount,
            grade1QualCount = center.teacher?.grade1QualCount,
            grade2QualCount = center.teacher?.grade2QualCount,
            assistantQualCount = center.teacher?.assistantQualCount,
            under1Year = center.yearOfWork?.under1Year,
            between1And2Years = center.yearOfWork?.between1And2Years,
            between2And4Years = center.yearOfWork?.between2And4Years,
            between4And6Years = center.yearOfWork?.between4And6Years,
            over6Years = center.yearOfWork?.over6Years,
        )
    }

    private fun buildAfterSchoolSection(center: Center): AfterSchoolSection? =
        center.afterSchool?.let {
            AfterSchoolSection(
                independentClassCount = it.independentClassCount,
                afternoonClassCount = it.afternoonClassCount,
                independentParticipants = it.independentParticipants,
                afternoonParticipants = it.afternoonParticipants,
                regularTeacherCount = it.regularTeacherCount,
                contractTeacherCount = it.contractTeacherCount,
                dedicatedStaffCount = it.dedicatedStaffCount,
            )
        }

    private fun getSort(sort: String?): Sort =
        when (sort) {
            "name" -> Sort.by("name").ascending()
            "distance" -> Sort.by("distance").ascending()
            "capacity" -> Sort.by(Sort.Order.desc("totalCapacity").nullsLast())
            "enrollment" -> Sort.by(Sort.Order.desc("enrollment").nullsLast())
            "occupancyRate" -> Sort.by(Sort.Order.desc("occupancyRate").nullsLast())
            "updated" -> Sort.by("updatedAt").descending()
            else -> Sort.by("updatedAt").descending()
        }

    private fun toCenterDetailResponse(center: Center): CenterDetailResponse =
        CenterDetailResponse(
            id = center.id!!,
            name = center.name,
            establishType = center.establishType,
            address = center.address,
            phone = center.phone,
            lat = center.location?.y,
            lng = center.location?.x,
            distanceKm = null,
            capacity = center.totalCapacity,
            currentEnrollment = calculateCurrentEnrollment(center),
            totalClassCount = calculateTotalClassCount(center),
            operatingHours = center.operatingHours,
            homepage = center.homepage,
            fax = center.fax,
            representativeName = center.representativeName,
            directorName = center.directorName,
            establishDate = parseDate(center.establishDate),
            openDate = parseDate(center.openDate),
            disclosureTiming = center.disclosureTiming,
            classCountByAge =
                mapOf(
                    "age3" to center.classCount3,
                    "age4" to center.classCount4,
                    "age5" to center.classCount5,
                    "mixed" to center.mixedClassCount,
                    "special" to center.specialClassCount,
                ),
            capacityByAge =
                mapOf(
                    "age3" to center.capacity3,
                    "age4" to center.capacity4,
                    "age5" to center.capacity5,
                    "mixed" to center.mixedCapacity,
                    "special" to center.specialCapacity,
                ),
            enrollmentByAge =
                mapOf(
                    "age3" to center.enrollment3,
                    "age4" to center.enrollment4,
                    "age5" to center.enrollment5,
                    "mixed" to center.mixedEnrollment,
                    "special" to center.specialEnrollment,
                ),
            building =
                center.building?.let {
                    BuildingInfo(
                        archYear = it.archYear?.toIntOrNull(),
                        floorCount = it.floorCount,
                        buildingArea = it.buildingArea,
                        totalLandArea = it.totalLandArea,
                    )
                },
            classroom =
                center.classroom?.let {
                    ClassroomInfo(
                        classroomCount = it.classroomCount,
                        classroomArea = it.classroomArea,
                        playgroundArea = it.playgroundArea,
                        healthArea = it.healthArea,
                        kitchenArea = it.kitchenArea,
                        otherArea = it.otherArea,
                    )
                },
            teacher =
                center.teacher?.let {
                    TeacherInfo(
                        directorCount = it.directorCount,
                        headTeacherCount = it.leadTeacherCount,
                        assistantTeacherCount = it.generalTeacherCount,
                        nurseCount = it.healthTeacherCount,
                        nutritionistCount = it.nutritionTeacherCount,
                        cookCount = it.staffCount,
                        teacherQualified = it.grade1QualCount,
                        teacherUnderQualified = it.grade2QualCount,
                        teacherNonQualified = it.assistantQualCount,
                        eduCourseQualified = it.masterQualCount,
                        eduCourseUnderQualified = null,
                        eduCourseNonQualified = null,
                        childCareQualified = null,
                        childCareUnderQualified = null,
                        childCareNonQualified = null,
                    )
                },
            lessonDay =
                center.lessonDay?.let {
                    LessonDayInfo(
                        lessonDaysAge3 = it.lessonDays3,
                        lessonDaysAge4 = it.lessonDays4,
                        lessonDaysAge5 = it.lessonDays5,
                        lessonDaysMixed = it.mixedLessonDays,
                        belowLegalDays = it.belowLegalDays,
                    )
                },
            meal =
                center.meal?.let {
                    MealInfo(
                        mealOperationType = it.mealOperationType,
                        consignmentCompany = it.consignmentCompany,
                        mealChildren = it.mealChildren,
                        cookCount = it.cookCount,
                        lunchProvided = null,
                        dinnerProvided = null,
                        mealCost = null,
                        mealCostReason = null,
                    )
                },
            bus =
                center.bus?.let {
                    BusInfo(
                        busOperating = it.busOperating,
                        operatingCount = it.operatingBusCount,
                        registeredCount = it.registeredBusCount,
                        seat9Count = it.bus9Seat,
                        seat12Count = it.bus12Seat,
                        seat24Count = null,
                        seat35Count = null,
                        seat45Count = null,
                    )
                },
            yearOfWork =
                center.yearOfWork?.let {
                    YearOfWorkInfo(
                        teachersUnder1Year = it.under1Year,
                        teachers1To2Years = it.between1And2Years,
                        teachers2To3Years = null,
                        teachers3To4Years = null,
                        teachers4To5Years = it.between4And6Years,
                        teachers5To6Years = null,
                        teachers6PlusYears = it.over6Years,
                    )
                },
            environment =
                center.environment?.let {
                    EnvironmentInfo(
                        airQualityCheck = it.airQualityCheckResult,
                        disinfectionCheck = it.regularDisinfectionResult,
                        waterQualityCheck = it.groundwaterTestResult,
                        dustMeasurement = it.dustCheckResult,
                        lightMeasurement = it.lightCheckResult,
                    )
                },
            safetyCheck =
                center.safetyCheck?.let {
                    SafetyCheckInfo(
                        fireInsuranceCheck = it.fireSafetyYn,
                        gasCheck = it.gasCheckYn,
                        electricCheck = it.electricCheckYn,
                        playgroundCheck = it.playgroundCheckYn,
                        cctvInstalled = it.cctvInstalled,
                        cctvTotal = it.cctvTotal,
                    )
                },
            safetyEducation =
                center.safetyEducations
                    .map {
                        SafetyEducationInfo(
                            semester = it.semester,
                            trafficSafety = it.trafficSafety?.toIntOrNull(),
                            preventionAbuse = null,
                            kidnappingPrevention = null,
                            sexualAbusePrevention = null,
                            disasterSafety = it.disasterSafety?.toIntOrNull(),
                            violencePrevention = it.violencePrevention?.toIntOrNull(),
                            drugPrevention = it.drugPrevention?.toIntOrNull(),
                            firstAid = it.firstAid?.toIntOrNull(),
                        )
                    }.ifEmpty { null },
            mutualAid =
                center.mutualAid?.let {
                    MutualAidInfo(
                        schoolSafetyEnrolled = it.schoolSafetyEnrolled,
                        educationFacilityEnrolled = it.educationFacilityEnrolled,
                    )
                },
            insurance =
                center.insurances
                    .map {
                        val companies = listOfNotNull(it.company1, it.company2, it.company3).joinToString(", ")
                        InsuranceInfo(
                            insuranceName = it.insuranceName,
                            enrolled = it.enrolledYn,
                            companies = companies.ifEmpty { null },
                        )
                    }.ifEmpty { null },
            afterSchool =
                center.afterSchool?.let {
                    AfterSchoolInfo(
                        classCountAge3 = it.independentClassCount,
                        classCountAge4 = it.afternoonClassCount,
                        classCountAge5 = null,
                        classCountMixed = null,
                        participantsAge3 = it.independentParticipants,
                        participantsAge4 = it.afternoonParticipants,
                        participantsAge5 = null,
                        participantsMixed = null,
                        teachersAge3 = it.regularTeacherCount,
                        teachersAge4 = it.contractTeacherCount,
                        teachersAge5 = it.dedicatedStaffCount,
                        teachersMixed = null,
                    )
                },
            sourceUpdatedAt = center.sourceUpdatedAt,
        )

    private fun calculateCurrentEnrollment(center: Center): Int? {
        val enrollments =
            listOf(
                center.enrollment3,
                center.enrollment4,
                center.enrollment5,
                center.mixedEnrollment,
                center.specialEnrollment,
            ).filterNotNull()

        return if (enrollments.isEmpty()) null else enrollments.sum()
    }

    private fun calculateTotalClassCount(center: Center): Int? {
        val counts =
            listOf(
                center.classCount3,
                center.classCount4,
                center.classCount5,
                center.mixedClassCount,
                center.specialClassCount,
            ).filterNotNull()

        return if (counts.isEmpty()) null else counts.sum()
    }

    private fun parseDate(dateString: String?): LocalDate? =
        try {
            dateString?.let { LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
        } catch (e: Exception) {
            null
        }

    private fun parseTypes(establishType: String?): List<String>? =
        establishType
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }

    private fun resolveRegionNames(
        sidoCode: String?,
        sggCode: String?,
    ): Pair<String?, String?> {
        val sidoName = sidoCode?.let { regionCacheService.getSidoName(it) }
        val sggName = sggCode?.let { regionCacheService.getSggName(it) }
        return sidoName to sggName
    }
}
