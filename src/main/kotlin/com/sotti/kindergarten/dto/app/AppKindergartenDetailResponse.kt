package com.sotti.kindergarten.dto.app

import java.time.LocalDateTime
import java.util.UUID

data class AppKindergartenDetailResponse(
    val id: UUID,
    val name: String,
    val establishType: String?,
    val address: String?,
    val phone: String?,
    val homepage: String?,
    val operatingHours: String?,
    val lat: Double?,
    val lng: Double?,
    val representativeName: String?,
    val directorName: String?,
    val establishDate: String?,
    val openDate: String?,
    val education: EducationSection?,
    val meal: MealSection?,
    val safety: SafetySection?,
    val facility: FacilitySection?,
    val teacher: TeacherSection?,
    val afterSchool: AfterSchoolSection?,
    val sourceUpdatedAt: LocalDateTime?,
)

data class EducationSection(
    val classCountByAge: Map<String, Int?>?,
    val capacityByAge: Map<String, Int?>?,
    val enrollmentByAge: Map<String, Int?>?,
    val lessonDaysAge3: Int?,
    val lessonDaysAge4: Int?,
    val lessonDaysAge5: Int?,
    val lessonDaysMixed: Int?,
    val belowLegalDays: String?,
)

data class MealSection(
    val mealOperationType: String?,
    val consignmentCompany: String?,
    val mealChildren: Int?,
    val cookCount: Int?,
)

data class SafetySection(
    val airQualityCheck: String?,
    val disinfectionCheck: String?,
    val waterQualityCheck: String?,
    val dustMeasurement: String?,
    val lightMeasurement: String?,
    val fireInsuranceCheck: String?,
    val gasCheck: String?,
    val electricCheck: String?,
    val playgroundCheck: String?,
    val cctvInstalled: String?,
    val cctvTotal: Int?,
    val schoolSafetyEnrolled: String?,
    val educationFacilityEnrolled: String?,
    val safetyEducations: List<SafetyEducationResponse>?,
    val insurances: List<InsuranceResponse>?,
)

data class SafetyEducationResponse(
    val semester: String?,
    val lifeSafety: String?,
    val trafficSafety: String?,
    val violencePrevention: String?,
    val drugPrevention: String?,
    val cyberPrevention: String?,
    val disasterSafety: String?,
    val occupationalSafety: String?,
    val firstAid: String?,
)

data class InsuranceResponse(
    val insuranceName: String?,
    val targetYn: String?,
    val enrolledYn: String?,
    val company: String?,
)

data class FacilitySection(
    val archYear: Int?,
    val floorCount: Int?,
    val buildingArea: Double?,
    val totalLandArea: Double?,
    val classroomCount: Int?,
    val classroomArea: Double?,
    val playgroundArea: Double?,
    val busOperating: String?,
    val operatingBusCount: Int?,
    val registeredBusCount: Int?,
)

data class TeacherSection(
    val directorCount: Int?,
    val viceDirectorCount: Int?,
    val masterTeacherCount: Int?,
    val leadTeacherCount: Int?,
    val generalTeacherCount: Int?,
    val specialTeacherCount: Int?,
    val healthTeacherCount: Int?,
    val nutritionTeacherCount: Int?,
    val staffCount: Int?,
    val masterQualCount: Int?,
    val grade1QualCount: Int?,
    val grade2QualCount: Int?,
    val assistantQualCount: Int?,
    val under1Year: Int?,
    val between1And2Years: Int?,
    val between2And4Years: Int?,
    val between4And6Years: Int?,
    val over6Years: Int?,
)

data class AfterSchoolSection(
    val independentClassCount: Int?,
    val afternoonClassCount: Int?,
    val independentParticipants: Int?,
    val afternoonParticipants: Int?,
    val regularTeacherCount: Int?,
    val contractTeacherCount: Int?,
    val dedicatedStaffCount: Int?,
)
