package com.sotti.kindergarten.service

import com.sotti.kindergarten.dto.AfterSchoolInfo
import com.sotti.kindergarten.dto.BuildingInfo
import com.sotti.kindergarten.dto.BusInfo
import com.sotti.kindergarten.dto.ClassroomInfo
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
import com.sotti.kindergarten.dto.admin.AdminBatchStatusRequest
import com.sotti.kindergarten.dto.admin.AdminBatchStatusResponse
import com.sotti.kindergarten.dto.admin.AdminKindergartenDetailResponse
import com.sotti.kindergarten.dto.admin.AdminKindergartenListResponse
import com.sotti.kindergarten.dto.admin.AdminKindergartenUpdateRequest
import com.sotti.kindergarten.dto.admin.CrawlHistoryResponse
import com.sotti.kindergarten.dto.admin.CrawlTriggerRequest
import com.sotti.kindergarten.dto.admin.DashboardStatsResponse
import com.sotti.kindergarten.entity.Center
import com.sotti.kindergarten.entity.CrawlHistory
import com.sotti.kindergarten.entity.CrawlStatus
import com.sotti.kindergarten.exception.BusinessException
import com.sotti.kindergarten.exception.ErrorCode
import com.sotti.kindergarten.repository.CenterRepository
import com.sotti.kindergarten.repository.CrawlHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AdminService(
    private val centerRepository: CenterRepository,
    private val crawlHistoryRepository: CrawlHistoryRepository,
    private val dataSyncService: DataSyncService,
) {
    private val logger = LoggerFactory.getLogger(AdminService::class.java)

    fun listKindergartens(
        keyword: String?,
        establishType: String?,
        isVerified: Boolean?,
        isActive: Boolean?,
        page: Int,
        size: Int,
    ): PageResponse<AdminKindergartenListResponse> {
        val pageable = PageRequest.of(page, size)
        val centersPage =
            centerRepository.findAllWithAdminFilters(
                keyword = keyword,
                establishType = establishType,
                isVerified = isVerified,
                isActive = isActive,
                pageable = pageable,
            )

        val content = centersPage.content.map { it.toAdminListResponse() }

        return PageResponse(
            content = content,
            page = centersPage.number,
            size = centersPage.size,
            totalElements = centersPage.totalElements,
            totalPages = centersPage.totalPages,
        )
    }

    fun getKindergartenDetail(id: UUID): AdminKindergartenDetailResponse {
        val center =
            centerRepository
                .findById(id)
                .orElseThrow { BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND) }
        return center.toAdminDetailResponse()
    }

    @Transactional
    fun updateKindergarten(
        id: UUID,
        request: AdminKindergartenUpdateRequest,
    ): AdminKindergartenDetailResponse {
        val center =
            centerRepository
                .findById(id)
                .orElseThrow { BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND) }

        request.isVerified?.let { center.isVerified = it }
        request.isActive?.let { center.isActive = it }
        request.adminMemo?.let { center.adminMemo = it }

        val saved = centerRepository.save(center)
        return saved.toAdminDetailResponse()
    }

    @Transactional
    fun batchUpdateStatus(request: AdminBatchStatusRequest): AdminBatchStatusResponse {
        var updatedCount = 0

        request.isActive?.let { isActive ->
            updatedCount += centerRepository.batchUpdateIsActive(request.ids, isActive)
        }

        request.isVerified?.let { isVerified ->
            updatedCount += centerRepository.batchUpdateIsVerified(request.ids, isVerified)
        }

        return AdminBatchStatusResponse(updatedCount = updatedCount)
    }

    fun getCrawlHistories(
        page: Int,
        size: Int,
    ): PageResponse<CrawlHistoryResponse> {
        val pageable = PageRequest.of(page, size)
        val historiesPage = crawlHistoryRepository.findAllByOrderByStartedAtDesc(pageable)

        val content = historiesPage.content.map { it.toCrawlHistoryResponse() }

        return PageResponse(
            content = content,
            page = historiesPage.number,
            size = historiesPage.size,
            totalElements = historiesPage.totalElements,
            totalPages = historiesPage.totalPages,
        )
    }

    fun triggerCrawl(request: CrawlTriggerRequest): CrawlHistoryResponse {
        if (crawlHistoryRepository.existsByStatus(CrawlStatus.RUNNING)) {
            throw BusinessException(ErrorCode.CRAWL_ALREADY_RUNNING)
        }

        val crawlHistory =
            crawlHistoryRepository.save(
                CrawlHistory(
                    source = "e-childschoolinfo",
                    status = CrawlStatus.RUNNING,
                    startedAt = LocalDateTime.now(),
                ),
            )
        val crawlHistoryId = crawlHistory.id!!

        Thread {
            try {
                val count =
                    if (request.sidoCode != null && request.sggCode != null) {
                        dataSyncService.syncSingleRegion(request.sidoCode, request.sggCode)
                    } else {
                        dataSyncService.syncAllData()
                    }
                val history = crawlHistoryRepository.findById(crawlHistoryId).get()
                history.status = CrawlStatus.SUCCESS
                history.itemCount = count
                history.finishedAt = LocalDateTime.now()
                crawlHistoryRepository.save(history)
            } catch (e: Exception) {
                logger.error("Crawl failed: ${e.message}", e)
                try {
                    val history = crawlHistoryRepository.findById(crawlHistoryId).get()
                    history.status = CrawlStatus.FAILED
                    history.errorMessage = e.message
                    history.finishedAt = LocalDateTime.now()
                    crawlHistoryRepository.save(history)
                } catch (saveError: Exception) {
                    logger.error(
                        "Failed to update crawl history: ${saveError.message}",
                        saveError,
                    )
                }
            }
        }.start()

        return crawlHistory.toCrawlHistoryResponse()
    }

    fun getDashboardStats(): DashboardStatsResponse {
        val totalCount = centerRepository.count()
        val verifiedCount = centerRepository.countByIsVerifiedTrue()
        val unverifiedCount = centerRepository.countByIsVerifiedFalse()
        val activeCount = centerRepository.countByIsActiveTrue()

        val establishTypes = listOf("공립(단설)", "공립(병설)", "사립")
        val byEstablishType =
            establishTypes.associateWith { type ->
                centerRepository.countByEstablishType(type)
            }

        return DashboardStatsResponse(
            totalCount = totalCount,
            verifiedCount = verifiedCount,
            unverifiedCount = unverifiedCount,
            activeCount = activeCount,
            byEstablishType = byEstablishType,
        )
    }

    private fun Center.toAdminListResponse(): AdminKindergartenListResponse =
        AdminKindergartenListResponse(
            id = id!!,
            kinderCode = kinderCode,
            name = name,
            establishType = establishType,
            officEdu = officEdu,
            subOfficeEdu = subOfficeEdu,
            address = address,
            isVerified = isVerified,
            isActive = isActive,
            updatedAt = updatedAt,
        )

    private fun Center.toAdminDetailResponse(): AdminKindergartenDetailResponse {
        val currentEnrollment = calculateCurrentEnrollment()
        val totalClassCount = calculateTotalClassCount()

        return AdminKindergartenDetailResponse(
            id = id!!,
            kinderCode = kinderCode,
            name = name,
            establishType = establishType,
            officEdu = officEdu,
            subOfficeEdu = subOfficeEdu,
            address = address,
            phone = phone,
            homepage = homepage,
            operatingHours = operatingHours,
            representativeName = representativeName,
            directorName = directorName,
            establishDate = establishDate,
            openDate = openDate,
            isVerified = isVerified,
            isActive = isActive,
            adminMemo = adminMemo,
            building =
                building?.let {
                    BuildingInfo(
                        archYear = it.archYear?.toIntOrNull(),
                        floorCount = it.floorCount,
                        buildingArea = it.buildingArea,
                        totalLandArea = it.totalLandArea,
                    )
                },
            classroom =
                classroom?.let {
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
                teacher?.let {
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
                lessonDay?.let {
                    LessonDayInfo(
                        lessonDaysAge3 = it.lessonDays3,
                        lessonDaysAge4 = it.lessonDays4,
                        lessonDaysAge5 = it.lessonDays5,
                        lessonDaysMixed = it.mixedLessonDays,
                        belowLegalDays = it.belowLegalDays,
                    )
                },
            meal =
                meal?.let {
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
                bus?.let {
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
                yearOfWork?.let {
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
                environment?.let {
                    EnvironmentInfo(
                        airQualityCheck = it.airQualityCheckResult,
                        disinfectionCheck = it.regularDisinfectionResult,
                        waterQualityCheck = it.groundwaterTestResult,
                        dustMeasurement = it.dustCheckResult,
                        lightMeasurement = it.lightCheckResult,
                    )
                },
            safetyCheck =
                safetyCheck?.let {
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
                safetyEducations
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
                mutualAid?.let {
                    MutualAidInfo(
                        schoolSafetyEnrolled = it.schoolSafetyEnrolled,
                        educationFacilityEnrolled = it.educationFacilityEnrolled,
                    )
                },
            insurance =
                insurances
                    .map {
                        val companies = listOfNotNull(it.company1, it.company2, it.company3).joinToString(", ")
                        InsuranceInfo(
                            insuranceName = it.insuranceName,
                            enrolled = it.enrolledYn,
                            companies = companies.ifEmpty { null },
                        )
                    }.ifEmpty { null },
            afterSchool =
                afterSchool?.let {
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
            capacity = totalCapacity,
            currentEnrollment = currentEnrollment,
            totalClassCount = totalClassCount,
            classCountByAge =
                mapOf(
                    "age3" to classCount3,
                    "age4" to classCount4,
                    "age5" to classCount5,
                    "mixed" to mixedClassCount,
                    "special" to specialClassCount,
                ),
            capacityByAge =
                mapOf(
                    "age3" to capacity3,
                    "age4" to capacity4,
                    "age5" to capacity5,
                    "mixed" to mixedCapacity,
                    "special" to specialCapacity,
                ),
            enrollmentByAge =
                mapOf(
                    "age3" to enrollment3,
                    "age4" to enrollment4,
                    "age5" to enrollment5,
                    "mixed" to mixedEnrollment,
                    "special" to specialEnrollment,
                ),
            sourceUpdatedAt = sourceUpdatedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun Center.calculateCurrentEnrollment(): Int? {
        val enrollments =
            listOf(
                enrollment3,
                enrollment4,
                enrollment5,
                mixedEnrollment,
                specialEnrollment,
            ).filterNotNull()

        return if (enrollments.isEmpty()) null else enrollments.sum()
    }

    private fun Center.calculateTotalClassCount(): Int? {
        val counts =
            listOf(
                classCount3,
                classCount4,
                classCount5,
                mixedClassCount,
                specialClassCount,
            ).filterNotNull()

        return if (counts.isEmpty()) null else counts.sum()
    }

    private fun CrawlHistory.toCrawlHistoryResponse(): CrawlHistoryResponse =
        CrawlHistoryResponse(
            id = id!!,
            source = source,
            status = status.name,
            errorMessage = errorMessage,
            itemCount = itemCount,
            startedAt = startedAt,
            finishedAt = finishedAt,
        )
}
