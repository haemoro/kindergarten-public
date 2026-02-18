package com.sotti.kindergarten.service

import com.sotti.kindergarten.client.KindergartenApiClient
import com.sotti.kindergarten.client.dto.AfterSchool
import com.sotti.kindergarten.client.dto.BasicInfo
import com.sotti.kindergarten.client.dto.Building
import com.sotti.kindergarten.client.dto.Bus
import com.sotti.kindergarten.client.dto.ClassArea
import com.sotti.kindergarten.client.dto.Environment
import com.sotti.kindergarten.client.dto.LessonDay
import com.sotti.kindergarten.client.dto.Meal
import com.sotti.kindergarten.client.dto.MutualAid
import com.sotti.kindergarten.client.dto.SafetyCheck
import com.sotti.kindergarten.client.dto.Teacher
import com.sotti.kindergarten.client.dto.YearOfWork
import com.sotti.kindergarten.entity.Center
import com.sotti.kindergarten.entity.CenterAfterSchool
import com.sotti.kindergarten.entity.CenterBuilding
import com.sotti.kindergarten.entity.CenterBus
import com.sotti.kindergarten.entity.CenterClassroom
import com.sotti.kindergarten.entity.CenterEnvironment
import com.sotti.kindergarten.entity.CenterInsurance
import com.sotti.kindergarten.entity.CenterLessonDay
import com.sotti.kindergarten.entity.CenterMeal
import com.sotti.kindergarten.entity.CenterMutualAid
import com.sotti.kindergarten.entity.CenterSafetyCheck
import com.sotti.kindergarten.entity.CenterSafetyEducation
import com.sotti.kindergarten.entity.CenterTeacher
import com.sotti.kindergarten.entity.CenterYearOfWork
import com.sotti.kindergarten.repository.CenterAfterSchoolRepository
import com.sotti.kindergarten.repository.CenterBuildingRepository
import com.sotti.kindergarten.repository.CenterBusRepository
import com.sotti.kindergarten.repository.CenterClassroomRepository
import com.sotti.kindergarten.repository.CenterEnvironmentRepository
import com.sotti.kindergarten.repository.CenterInsuranceRepository
import com.sotti.kindergarten.repository.CenterLessonDayRepository
import com.sotti.kindergarten.repository.CenterMealRepository
import com.sotti.kindergarten.repository.CenterMutualAidRepository
import com.sotti.kindergarten.repository.CenterRepository
import com.sotti.kindergarten.repository.CenterSafetyCheckRepository
import com.sotti.kindergarten.repository.CenterSafetyEducationRepository
import com.sotti.kindergarten.repository.CenterTeacherRepository
import com.sotti.kindergarten.repository.CenterYearOfWorkRepository
import com.sotti.kindergarten.repository.RegionRepository
import com.sotti.kindergarten.util.DataHashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

@Service
class DataSyncService(
    private val apiClient: KindergartenApiClient,
    private val regionRepository: RegionRepository,
    private val centerRepository: CenterRepository,
    private val buildingRepository: CenterBuildingRepository,
    private val classroomRepository: CenterClassroomRepository,
    private val teacherRepository: CenterTeacherRepository,
    private val lessonDayRepository: CenterLessonDayRepository,
    private val mealRepository: CenterMealRepository,
    private val busRepository: CenterBusRepository,
    private val yearOfWorkRepository: CenterYearOfWorkRepository,
    private val environmentRepository: CenterEnvironmentRepository,
    private val safetyCheckRepository: CenterSafetyCheckRepository,
    private val safetyEducationRepository: CenterSafetyEducationRepository,
    private val mutualAidRepository: CenterMutualAidRepository,
    private val insuranceRepository: CenterInsuranceRepository,
    private val afterSchoolRepository: CenterAfterSchoolRepository,
) {
    private val logger = LoggerFactory.getLogger(DataSyncService::class.java)
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val PARALLEL_REGION_COUNT = 5
    }

    @Scheduled(cron = "0 0 3 * * *")
    fun scheduledSync() {
        logger.info("Starting scheduled data sync at ${LocalDateTime.now()}")
        val count = syncAllData()
        logger.info("Completed scheduled data sync at ${LocalDateTime.now()} ($count items)")
    }

    fun syncAllData(): Int {
        val regions = regionRepository.findAll()
        val totalRegions = regions.size
        val completedCount = AtomicInteger(0)
        val startTime = LocalDateTime.now()

        logger.info(
            "=== Sync started: $totalRegions regions, " +
                "parallelism=$PARALLEL_REGION_COUNT ===",
        )

        val totalItems =
            runBlocking(Dispatchers.IO) {
                val semaphore = Semaphore(PARALLEL_REGION_COUNT)
                val results =
                    regions.map { region ->
                        async {
                            semaphore.withPermit {
                                try {
                                    val regionStart = System.currentTimeMillis()
                                    val count =
                                        doSyncRegion(
                                            region.sidoCode,
                                            region.sggCode,
                                        )
                                    val elapsed =
                                        System.currentTimeMillis() - regionStart
                                    val done = completedCount.incrementAndGet()
                                    logger.info(
                                        "[{}/{}] {} {} 완료 " +
                                            "({}건, {}ms)",
                                        done,
                                        totalRegions,
                                        region.sidoName,
                                        region.sggName,
                                        count,
                                        elapsed,
                                    )
                                    count
                                } catch (e: Exception) {
                                    val done = completedCount.incrementAndGet()
                                    logger.error(
                                        "[{}/{}] {} {} 실패: {}",
                                        done,
                                        totalRegions,
                                        region.sidoName,
                                        region.sggName,
                                        e.message,
                                        e,
                                    )
                                    0
                                }
                            }
                        }
                    }
                results.awaitAll().sum()
            }

        val duration = Duration.between(startTime, LocalDateTime.now())
        val minutes = duration.toMinutes()
        val seconds = duration.seconds % 60
        logger.info(
            "=== Sync finished: $totalItems items, " +
                "${minutes}m ${seconds}s elapsed ===",
        )
        return totalItems
    }

    fun syncSingleRegion(
        sidoCode: String,
        sggCode: String,
    ): Int {
        logger.info("Starting single region sync for sido=$sidoCode, sgg=$sggCode")
        val count = syncRegion(sidoCode, sggCode)
        logger.info("Completed single region sync for sido=$sidoCode, sgg=$sggCode ($count items)")
        return count
    }

    fun syncRegion(
        sidoCode: String,
        sggCode: String,
    ): Int =
        runBlocking {
            doSyncRegion(sidoCode, sggCode)
        }

    private suspend fun doSyncRegion(
        sidoCode: String,
        sggCode: String,
    ): Int {
        var count = 0
        count += syncBasicInfo(sidoCode, sggCode)
        count += syncBuilding(sidoCode, sggCode)
        count += syncClassArea(sidoCode, sggCode)
        count += syncTeacher(sidoCode, sggCode)
        count += syncLessonDay(sidoCode, sggCode)
        count += syncMeal(sidoCode, sggCode)
        count += syncBus(sidoCode, sggCode)
        count += syncYearOfWork(sidoCode, sggCode)
        count += syncEnvironment(sidoCode, sggCode)
        count += syncSafetyCheck(sidoCode, sggCode)
        count += syncSafetyEducation(sidoCode, sggCode)
        count += syncMutualAid(sidoCode, sggCode)
        count += syncInsurance(sidoCode, sggCode)
        count += syncAfterSchool(sidoCode, sggCode)
        return count
    }

    // region Hash computation functions

    private fun computeBasicInfoHash(info: BasicInfo): String =
        DataHashUtils.computeHash(
            info.kindername,
            info.officeedu,
            info.subofficeedu,
            info.establish,
            info.rppnname,
            info.ldgrname,
            info.edate,
            info.odate,
            info.addr,
            info.telno,
            info.faxno,
            info.hpaddr,
            info.opertime,
            info.clcnt3?.toIntOrNull(),
            info.clcnt4?.toIntOrNull(),
            info.clcnt5?.toIntOrNull(),
            info.mixclcnt?.toIntOrNull(),
            info.shclcnt?.toIntOrNull(),
            info.prmstfcnt?.toIntOrNull(),
            info.ag3fpcnt?.toIntOrNull(),
            info.ag4fpcnt?.toIntOrNull(),
            info.ag5fpcnt?.toIntOrNull(),
            info.mixfpcnt?.toIntOrNull(),
            info.spcnfpcnt?.toIntOrNull(),
            info.ppcnt3?.toIntOrNull(),
            info.ppcnt4?.toIntOrNull(),
            info.ppcnt5?.toIntOrNull(),
            info.mixppcnt?.toIntOrNull(),
            info.shppcnt?.toIntOrNull(),
            info.lttdcdnt,
            info.lngtcdnt,
            info.pbnttmng,
            info.rpstYn,
        )

    private fun computeBuildingHash(b: Building): String =
        DataHashUtils.computeHash(
            b.archyy,
            b.floorcnt?.replace(Regex("[^0-9]"), "")?.toIntOrNull(),
            b.bldgprusarea?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull(),
            b.grottar?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull(),
            b.pbnttmng,
        )

    private fun computeClassAreaHash(a: ClassArea): String =
        DataHashUtils.computeHash(
            a.crcnt?.replace(Regex("[^0-9]"), "")?.toIntOrNull(),
            a.clsrarea?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull(),
            a.phgrindrarea?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull(),
            a.hlsparea?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull(),
            a.ktchmssparea?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull(),
            a.otsparea?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull(),
            a.pbnttmng,
        )

    private fun computeTeacherHash(t: Teacher): String =
        DataHashUtils.computeHash(
            t.drcnt?.toIntOrNull(),
            t.adcnt?.toIntOrNull(),
            t.hdst_thcnt?.toIntOrNull(),
            t.asps_thcnt?.toIntOrNull(),
            t.gnrl_thcnt?.toIntOrNull(),
            t.spcn_thcnt?.toIntOrNull(),
            t.ntcnt?.toIntOrNull(),
            t.ntrt_thcnt?.toIntOrNull(),
            t.shcnt_thcnt?.toIntOrNull(),
            t.owcnt?.toIntOrNull(),
            t.hdst_tchr_qacnt?.toIntOrNull(),
            t.rgth_gd1_qacnt?.toIntOrNull(),
            t.rgth_gd2_qacnt?.toIntOrNull(),
            t.asth_qacnt?.toIntOrNull(),
            t.spsc_tchr_qacnt?.toIntOrNull(),
            t.nth_qacnt?.toIntOrNull(),
            t.ntth_qacnt?.toIntOrNull(),
            t.pbnttmng,
        )

    private fun computeLessonDayHash(l: LessonDay): String =
        DataHashUtils.computeHash(
            l.ag3_lsn_dcnt?.toIntOrNull(),
            l.ag4_lsn_dcnt?.toIntOrNull(),
            l.ag5_lsn_dcnt?.toIntOrNull(),
            l.mix_age_lsn_dcnt?.toIntOrNull(),
            l.spcl_lsn_dcnt?.toIntOrNull(),
            l.afsc_pros_lsn_dcnt?.toIntOrNull(),
            l.ldnum_blw_yn,
            l.pbnttmng,
        )

    private fun computeMealHash(m: Meal): String =
        DataHashUtils.computeHash(
            m.mlsr_oprn_way_tp_cd,
            m.cons_ents_nm,
            m.al_kpcnt?.toIntOrNull(),
            m.mlsr_kpcnt?.toIntOrNull(),
            m.ntrt_tchr_agmt_yn,
            m.snge_agmt_ntrt_thcnt?.toIntOrNull(),
            m.cprt_agmt_ntrt_thcnt?.toIntOrNull(),
            m.cprt_agmt_itt_nm,
            m.ckcnt?.toIntOrNull(),
            m.cmcnt?.toIntOrNull(),
            m.mas_mspl_dclr_yn,
            m.pbnttmng,
        )

    private fun computeBusHash(b: Bus): String =
        DataHashUtils.computeHash(
            b.vhcl_oprn_yn,
            b.opra_vhcnt?.toIntOrNull(),
            b.dclr_vhcnt?.toIntOrNull(),
            b.psg9_dclr_vhcnt?.toIntOrNull(),
            b.psg12_dclr_vhcnt?.toIntOrNull(),
            b.psg15_dclr_vhcnt?.toIntOrNull(),
            b.pbnttmng,
        )

    private fun computeYearOfWorkHash(y: YearOfWork): String =
        DataHashUtils.computeHash(
            y.yy1_undr_thcnt?.toIntOrNull(),
            y.yy1_abv_yy2_undr_thcnt?.toIntOrNull(),
            y.yy2_abv_yy4_undr_thcnt?.toIntOrNull(),
            y.yy4_abv_yy6_undr_thcnt?.toIntOrNull(),
            y.yy6_abv_thcnt?.toIntOrNull(),
            y.pbnttmng,
        )

    private fun computeEnvironmentHash(e: Environment): String =
        DataHashUtils.computeHash(
            e.arql_chk_dt,
            e.arql_chk_rslt_tp_cd,
            e.fxtm_dsnf_trgt_yn,
            e.fxtm_dsnf_chk_dt,
            e.fxtm_dsnf_chk_rslt_tp_cd,
            e.tp_01,
            e.tp_02,
            e.tp_03,
            e.tp_04,
            e.unwt_qlwt_insc_yn,
            e.qlwt_insc_dt,
            e.qlwt_insc_stby_yn,
            e.mdst_chk_dt,
            e.mdst_chk_rslt_cd,
            e.ilmn_chk_dt,
            e.ilmn_chk_rslt_cd,
            e.pbnttmng,
        )

    private fun computeSafetyCheckHash(s: SafetyCheck): String =
        DataHashUtils.computeHash(
            s.fire_avd_yn,
            s.fire_avd_dt,
            s.gas_ck_yn,
            s.gas_ck_dt,
            s.fire_safe_yn,
            s.fire_safe_dt,
            s.elect_ck_yn,
            s.elect_ck_dt,
            s.plyg_ck_yn,
            s.plyg_ck_dt,
            s.plyg_ck_rs_cd,
            s.cctv_ist_yn,
            s.cctv_ist_total?.toIntOrNull(),
            s.cctv_ist_in?.toIntOrNull(),
            s.cctv_ist_out?.toIntOrNull(),
            s.pbnttmng,
        )

    private fun computeMutualAidHash(a: MutualAid): String =
        DataHashUtils.computeHash(
            a.school_ds_yn,
            a.school_ds_en,
            a.educate_ds_yn,
            a.educate_ds_en,
            a.pbnttmng,
        )

    private fun computeAfterSchoolHash(a: AfterSchool): String =
        DataHashUtils.computeHash(
            a.inor_clcnt?.toIntOrNull(),
            a.pm_rrgn_clcnt?.toIntOrNull(),
            a.oper_time,
            a.inor_ptcn_kpcnt?.toIntOrNull(),
            a.pm_rrgn_ptcn_kpcnt?.toIntOrNull(),
            a.fxrl_thcnt?.toIntOrNull(),
            a.shcnt_thcnt?.toIntOrNull(),
            a.incnt?.toIntOrNull(),
            a.pbnttmng,
        )

    // endregion

    private suspend fun syncBasicInfo(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("BasicInfo", sidoCode, sggCode) {
            val responses = apiClient.getAllBasicInfo(sidoCode, sggCode)
            val allInfos = responses.flatMap { it.kinderInfo ?: emptyList() }

            val kinderCodes = allInfos.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }
                    .toMutableMap()

            val centersToSave = mutableListOf<Center>()
            val unchangedCenterIds = mutableListOf<java.util.UUID>()

            allInfos.forEach { info ->
                info.kinderCode?.let { kinderCode ->
                    info.kindername?.let { name ->
                        val newHash = computeBasicInfoHash(info)
                        val existing = centerMap[kinderCode]

                        if (existing != null && existing.dataHash == newHash) {
                            existing.id?.let { unchangedCenterIds.add(it) }
                            return@let
                        }

                        val center = existing ?: Center(kinderCode = kinderCode, name = name)

                        center.apply {
                            this.name = info.kindername ?: this.name
                            officEdu = info.officeedu
                            subOfficeEdu = info.subofficeedu
                            establishType = info.establish
                            representativeName = info.rppnname
                            directorName = info.ldgrname
                            establishDate = info.edate
                            openDate = info.odate
                            address = info.addr
                            phone = info.telno
                            fax = info.faxno
                            homepage = info.hpaddr
                            operatingHours = info.opertime
                            classCount3 = info.clcnt3?.toIntOrNull()
                            classCount4 = info.clcnt4?.toIntOrNull()
                            classCount5 = info.clcnt5?.toIntOrNull()
                            mixedClassCount = info.mixclcnt?.toIntOrNull()
                            specialClassCount = info.shclcnt?.toIntOrNull()
                            totalCapacity = info.prmstfcnt?.toIntOrNull()
                            capacity3 = info.ag3fpcnt?.toIntOrNull()
                            capacity4 = info.ag4fpcnt?.toIntOrNull()
                            capacity5 = info.ag5fpcnt?.toIntOrNull()
                            mixedCapacity = info.mixfpcnt?.toIntOrNull()
                            specialCapacity = info.spcnfpcnt?.toIntOrNull()
                            enrollment3 = info.ppcnt3?.toIntOrNull()
                            enrollment4 = info.ppcnt4?.toIntOrNull()
                            enrollment5 = info.ppcnt5?.toIntOrNull()
                            mixedEnrollment = info.mixppcnt?.toIntOrNull()
                            specialEnrollment = info.shppcnt?.toIntOrNull()
                            location = parseLocation(info.lttdcdnt, info.lngtcdnt)
                            disclosureTiming = info.pbnttmng
                            actingDirector = info.rpstYn
                            sourceUpdatedAt = LocalDateTime.now()
                            dataHash = newHash
                        }

                        centersToSave.add(center)
                        centerMap[kinderCode] = center
                    }
                }
            }

            centerRepository.saveAll(centersToSave)

            if (unchangedCenterIds.isNotEmpty()) {
                val now = LocalDateTime.now()
                unchangedCenterIds.chunked(1000).forEach { chunk ->
                    centerRepository.updateSourceUpdatedAt(chunk, now)
                }
            }

            logger.info(
                "BasicInfo: ${centersToSave.size} changed/new, " +
                    "${unchangedCenterIds.size} unchanged out of ${allInfos.size} total " +
                    "for $sidoCode-$sggCode",
            )
            centersToSave.size
        }

    private suspend fun syncBuilding(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("Building", sidoCode, sggCode) {
            val responses = apiClient.getAllBuilding(sidoCode, sggCode)
            val allBuildings = responses.flatMap { it.kinderInfo ?: emptyList() }

            val kinderCodes = allBuildings.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }
            val centerIds = centerMap.values.mapNotNull { it.id }
            val existingMap =
                buildingRepository
                    .findAllByCenterIdIn(centerIds)
                    .associateBy { it.center.id }

            val entitiesToSave = mutableListOf<CenterBuilding>()
            var unchangedCount = 0

            allBuildings.forEach { building ->
                building.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        center.id?.let { centerId ->
                            val newHash = computeBuildingHash(building)
                            val existing = existingMap[centerId]

                            if (existing != null && existing.dataHash == newHash) {
                                unchangedCount++
                                return@let
                            }

                            val entity = existing ?: CenterBuilding(center = center)

                            entity.apply {
                                archYear = building.archyy
                                floorCount =
                                    building.floorcnt
                                        ?.replace(Regex("[^0-9]"), "")
                                        ?.toIntOrNull()
                                buildingArea =
                                    building.bldgprusarea
                                        ?.replace(Regex("[^0-9.]"), "")
                                        ?.toDoubleOrNull()
                                totalLandArea =
                                    building.grottar
                                        ?.replace(Regex("[^0-9.]"), "")
                                        ?.toDoubleOrNull()
                                disclosureTiming = building.pbnttmng
                                dataHash = newHash
                            }

                            entitiesToSave.add(entity)
                        }
                    }
                }
            }

            buildingRepository.saveAll(entitiesToSave)
            logger.info(
                "Building: ${entitiesToSave.size} changed/new, " +
                    "$unchangedCount unchanged out of ${allBuildings.size} total " +
                    "for $sidoCode-$sggCode",
            )
            entitiesToSave.size
        }

    private suspend fun syncClassArea(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("ClassArea", sidoCode, sggCode) {
            val responses = apiClient.getAllClassArea(sidoCode, sggCode)
            val allAreas = responses.flatMap { it.kinderInfo ?: emptyList() }

            val kinderCodes = allAreas.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }
            val centerIds = centerMap.values.mapNotNull { it.id }
            val existingMap =
                classroomRepository
                    .findAllByCenterIdIn(centerIds)
                    .associateBy { it.center.id }

            val entitiesToSave = mutableListOf<CenterClassroom>()
            var unchangedCount = 0

            allAreas.forEach { area ->
                area.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        center.id?.let { centerId ->
                            val newHash = computeClassAreaHash(area)
                            val existing = existingMap[centerId]

                            if (existing != null && existing.dataHash == newHash) {
                                unchangedCount++
                                return@let
                            }

                            val entity = existing ?: CenterClassroom(center = center)

                            entity.apply {
                                classroomCount =
                                    area.crcnt
                                        ?.replace(Regex("[^0-9]"), "")
                                        ?.toIntOrNull()
                                classroomArea =
                                    area.clsrarea
                                        ?.replace(Regex("[^0-9.]"), "")
                                        ?.toDoubleOrNull()
                                playgroundArea =
                                    area.phgrindrarea
                                        ?.replace(Regex("[^0-9.]"), "")
                                        ?.toDoubleOrNull()
                                healthArea =
                                    area.hlsparea
                                        ?.replace(Regex("[^0-9.]"), "")
                                        ?.toDoubleOrNull()
                                kitchenArea =
                                    area.ktchmssparea
                                        ?.replace(Regex("[^0-9.]"), "")
                                        ?.toDoubleOrNull()
                                otherArea =
                                    area.otsparea
                                        ?.replace(Regex("[^0-9.]"), "")
                                        ?.toDoubleOrNull()
                                disclosureTiming = area.pbnttmng
                                dataHash = newHash
                            }

                            entitiesToSave.add(entity)
                        }
                    }
                }
            }

            classroomRepository.saveAll(entitiesToSave)
            logger.info(
                "ClassArea: ${entitiesToSave.size} changed/new, " +
                    "$unchangedCount unchanged out of ${allAreas.size} total " +
                    "for $sidoCode-$sggCode",
            )
            entitiesToSave.size
        }

    private suspend fun syncTeacher(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("Teacher", sidoCode, sggCode) {
            val responses = apiClient.getAllTeacher(sidoCode, sggCode)
            val allTeachers = responses.flatMap { it.kinderInfo ?: emptyList() }

            val kinderCodes = allTeachers.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }
            val centerIds = centerMap.values.mapNotNull { it.id }
            val existingMap =
                teacherRepository
                    .findAllByCenterIdIn(centerIds)
                    .associateBy { it.center.id }

            val entitiesToSave = mutableListOf<CenterTeacher>()
            var unchangedCount = 0

            allTeachers.forEach { teacher ->
                teacher.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        center.id?.let { centerId ->
                            val newHash = computeTeacherHash(teacher)
                            val existing = existingMap[centerId]

                            if (existing != null && existing.dataHash == newHash) {
                                unchangedCount++
                                return@let
                            }

                            val entity = existing ?: CenterTeacher(center = center)

                            entity.apply {
                                directorCount = teacher.drcnt?.toIntOrNull()
                                viceDirectorCount = teacher.adcnt?.toIntOrNull()
                                masterTeacherCount = teacher.hdst_thcnt?.toIntOrNull()
                                leadTeacherCount = teacher.asps_thcnt?.toIntOrNull()
                                generalTeacherCount = teacher.gnrl_thcnt?.toIntOrNull()
                                specialTeacherCount = teacher.spcn_thcnt?.toIntOrNull()
                                healthTeacherCount = teacher.ntcnt?.toIntOrNull()
                                nutritionTeacherCount = teacher.ntrt_thcnt?.toIntOrNull()
                                contractTeacherCount = teacher.shcnt_thcnt?.toIntOrNull()
                                staffCount = teacher.owcnt?.toIntOrNull()
                                masterQualCount = teacher.hdst_tchr_qacnt?.toIntOrNull()
                                grade1QualCount = teacher.rgth_gd1_qacnt?.toIntOrNull()
                                grade2QualCount = teacher.rgth_gd2_qacnt?.toIntOrNull()
                                assistantQualCount = teacher.asth_qacnt?.toIntOrNull()
                                specialSchoolQualCount =
                                    teacher.spsc_tchr_qacnt?.toIntOrNull()
                                healthQualCount = teacher.nth_qacnt?.toIntOrNull()
                                nutritionQualCount = teacher.ntth_qacnt?.toIntOrNull()
                                disclosureTiming = teacher.pbnttmng
                                dataHash = newHash
                            }

                            entitiesToSave.add(entity)
                        }
                    }
                }
            }

            teacherRepository.saveAll(entitiesToSave)
            logger.info(
                "Teacher: ${entitiesToSave.size} changed/new, " +
                    "$unchangedCount unchanged out of ${allTeachers.size} total " +
                    "for $sidoCode-$sggCode",
            )
            entitiesToSave.size
        }

    private suspend fun syncLessonDay(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("LessonDay", sidoCode, sggCode) {
            val responses = apiClient.getAllLessonDay(sidoCode, sggCode)
            val allLessonDays = responses.flatMap { it.kinderInfo ?: emptyList() }

            val kinderCodes = allLessonDays.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }
            val centerIds = centerMap.values.mapNotNull { it.id }
            val existingMap =
                lessonDayRepository
                    .findAllByCenterIdIn(centerIds)
                    .associateBy { it.center.id }

            val entitiesToSave = mutableListOf<CenterLessonDay>()
            var unchangedCount = 0

            allLessonDays.forEach { lessonDay ->
                lessonDay.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        center.id?.let { centerId ->
                            val newHash = computeLessonDayHash(lessonDay)
                            val existing = existingMap[centerId]

                            if (existing != null && existing.dataHash == newHash) {
                                unchangedCount++
                                return@let
                            }

                            val entity = existing ?: CenterLessonDay(center = center)

                            entity.apply {
                                lessonDays3 = lessonDay.ag3_lsn_dcnt?.toIntOrNull()
                                lessonDays4 = lessonDay.ag4_lsn_dcnt?.toIntOrNull()
                                lessonDays5 = lessonDay.ag5_lsn_dcnt?.toIntOrNull()
                                mixedLessonDays =
                                    lessonDay.mix_age_lsn_dcnt?.toIntOrNull()
                                specialLessonDays =
                                    lessonDay.spcl_lsn_dcnt?.toIntOrNull()
                                afterSchoolLessonDays =
                                    lessonDay.afsc_pros_lsn_dcnt?.toIntOrNull()
                                belowLegalDays = lessonDay.ldnum_blw_yn
                                disclosureTiming = lessonDay.pbnttmng
                                dataHash = newHash
                            }

                            entitiesToSave.add(entity)
                        }
                    }
                }
            }

            lessonDayRepository.saveAll(entitiesToSave)
            logger.info(
                "LessonDay: ${entitiesToSave.size} changed/new, " +
                    "$unchangedCount unchanged out of ${allLessonDays.size} total " +
                    "for $sidoCode-$sggCode",
            )
            entitiesToSave.size
        }

    private suspend fun syncMeal(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("Meal", sidoCode, sggCode) {
            val responses = apiClient.getAllMeal(sidoCode, sggCode)
            val allMeals = responses.flatMap { it.kinderInfo ?: emptyList() }

            val kinderCodes = allMeals.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }
            val centerIds = centerMap.values.mapNotNull { it.id }
            val existingMap =
                mealRepository
                    .findAllByCenterIdIn(centerIds)
                    .associateBy { it.center.id }

            val entitiesToSave = mutableListOf<CenterMeal>()
            var unchangedCount = 0

            allMeals.forEach { meal ->
                meal.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        center.id?.let { centerId ->
                            val newHash = computeMealHash(meal)
                            val existing = existingMap[centerId]

                            if (existing != null && existing.dataHash == newHash) {
                                unchangedCount++
                                return@let
                            }

                            val entity = existing ?: CenterMeal(center = center)

                            entity.apply {
                                mealOperationType = meal.mlsr_oprn_way_tp_cd
                                consignmentCompany = meal.cons_ents_nm
                                totalChildren = meal.al_kpcnt?.toIntOrNull()
                                mealChildren = meal.mlsr_kpcnt?.toIntOrNull()
                                nutritionTeacherAssigned = meal.ntrt_tchr_agmt_yn
                                singleNutritionTeacherCount =
                                    meal.snge_agmt_ntrt_thcnt?.toIntOrNull()
                                jointNutritionTeacherCount =
                                    meal.cprt_agmt_ntrt_thcnt?.toIntOrNull()
                                jointInstitutionName = meal.cprt_agmt_itt_nm
                                cookCount = meal.ckcnt?.toIntOrNull()
                                cookingStaffCount = meal.cmcnt?.toIntOrNull()
                                massKitchenRegistered = meal.mas_mspl_dclr_yn
                                disclosureTiming = meal.pbnttmng
                                dataHash = newHash
                            }

                            entitiesToSave.add(entity)
                        }
                    }
                }
            }

            mealRepository.saveAll(entitiesToSave)
            logger.info(
                "Meal: ${entitiesToSave.size} changed/new, " +
                    "$unchangedCount unchanged out of ${allMeals.size} total " +
                    "for $sidoCode-$sggCode",
            )
            entitiesToSave.size
        }

    private suspend fun syncBus(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("Bus", sidoCode, sggCode) {
            val responses = apiClient.getAllBus(sidoCode, sggCode)
            val allBuses = responses.flatMap { it.kinderInfo ?: emptyList() }

            val kinderCodes = allBuses.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }
            val centerIds = centerMap.values.mapNotNull { it.id }
            val existingMap =
                busRepository
                    .findAllByCenterIdIn(centerIds)
                    .associateBy { it.center.id }

            val entitiesToSave = mutableListOf<CenterBus>()
            var unchangedCount = 0

            allBuses.forEach { bus ->
                bus.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        center.id?.let { centerId ->
                            val newHash = computeBusHash(bus)
                            val existing = existingMap[centerId]

                            if (existing != null && existing.dataHash == newHash) {
                                unchangedCount++
                                return@let
                            }

                            val entity = existing ?: CenterBus(center = center)

                            entity.apply {
                                busOperating = bus.vhcl_oprn_yn
                                operatingBusCount = bus.opra_vhcnt?.toIntOrNull()
                                registeredBusCount = bus.dclr_vhcnt?.toIntOrNull()
                                bus9Seat = bus.psg9_dclr_vhcnt?.toIntOrNull()
                                bus12Seat = bus.psg12_dclr_vhcnt?.toIntOrNull()
                                bus15Seat = bus.psg15_dclr_vhcnt?.toIntOrNull()
                                disclosureTiming = bus.pbnttmng
                                dataHash = newHash
                            }

                            entitiesToSave.add(entity)
                        }
                    }
                }
            }

            busRepository.saveAll(entitiesToSave)
            logger.info(
                "Bus: ${entitiesToSave.size} changed/new, " +
                    "$unchangedCount unchanged out of ${allBuses.size} total " +
                    "for $sidoCode-$sggCode",
            )
            entitiesToSave.size
        }

    private suspend fun syncYearOfWork(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("YearOfWork", sidoCode, sggCode) {
            val responses = apiClient.getAllYearOfWork(sidoCode, sggCode)
            val allYearOfWorks = responses.flatMap { it.kinderInfo ?: emptyList() }

            val kinderCodes = allYearOfWorks.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }
            val centerIds = centerMap.values.mapNotNull { it.id }
            val existingMap =
                yearOfWorkRepository
                    .findAllByCenterIdIn(centerIds)
                    .associateBy { it.center.id }

            val entitiesToSave = mutableListOf<CenterYearOfWork>()
            var unchangedCount = 0

            allYearOfWorks.forEach { yearOfWork ->
                yearOfWork.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        center.id?.let { centerId ->
                            val newHash = computeYearOfWorkHash(yearOfWork)
                            val existing = existingMap[centerId]

                            if (existing != null && existing.dataHash == newHash) {
                                unchangedCount++
                                return@let
                            }

                            val entity =
                                existing ?: CenterYearOfWork(center = center)

                            entity.apply {
                                under1Year =
                                    yearOfWork.yy1_undr_thcnt?.toIntOrNull()
                                between1And2Years =
                                    yearOfWork.yy1_abv_yy2_undr_thcnt?.toIntOrNull()
                                between2And4Years =
                                    yearOfWork.yy2_abv_yy4_undr_thcnt?.toIntOrNull()
                                between4And6Years =
                                    yearOfWork.yy4_abv_yy6_undr_thcnt?.toIntOrNull()
                                over6Years =
                                    yearOfWork.yy6_abv_thcnt?.toIntOrNull()
                                disclosureTiming = yearOfWork.pbnttmng
                                dataHash = newHash
                            }

                            entitiesToSave.add(entity)
                        }
                    }
                }
            }

            yearOfWorkRepository.saveAll(entitiesToSave)
            logger.info(
                "YearOfWork: ${entitiesToSave.size} changed/new, " +
                    "$unchangedCount unchanged out of ${allYearOfWorks.size} total " +
                    "for $sidoCode-$sggCode",
            )
            entitiesToSave.size
        }

    private suspend fun syncEnvironment(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("Environment", sidoCode, sggCode) {
            val responses = apiClient.getAllEnvironment(sidoCode, sggCode)
            val allEnvironments = responses.flatMap { it.kinderInfo ?: emptyList() }

            val kinderCodes = allEnvironments.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }
            val centerIds = centerMap.values.mapNotNull { it.id }
            val existingMap =
                environmentRepository
                    .findAllByCenterIdIn(centerIds)
                    .associateBy { it.center.id }

            val entitiesToSave = mutableListOf<CenterEnvironment>()
            var unchangedCount = 0

            allEnvironments.forEach { env ->
                env.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        center.id?.let { centerId ->
                            val newHash = computeEnvironmentHash(env)
                            val existing = existingMap[centerId]

                            if (existing != null && existing.dataHash == newHash) {
                                unchangedCount++
                                return@let
                            }

                            val entity =
                                existing ?: CenterEnvironment(center = center)

                            entity.apply {
                                airQualityCheckDate = env.arql_chk_dt
                                airQualityCheckResult = env.arql_chk_rslt_tp_cd
                                regularDisinfectionRequired = env.fxtm_dsnf_trgt_yn
                                regularDisinfectionDate = env.fxtm_dsnf_chk_dt
                                regularDisinfectionResult =
                                    env.fxtm_dsnf_chk_rslt_tp_cd
                                waterType01 = env.tp_01
                                waterType02 = env.tp_02
                                waterType03 = env.tp_03
                                waterType04 = env.tp_04
                                groundwaterTestRequired = env.unwt_qlwt_insc_yn
                                groundwaterTestDate = env.qlwt_insc_dt
                                groundwaterTestResult = env.qlwt_insc_stby_yn
                                dustCheckDate = env.mdst_chk_dt
                                dustCheckResult = env.mdst_chk_rslt_cd
                                lightCheckDate = env.ilmn_chk_dt
                                lightCheckResult = env.ilmn_chk_rslt_cd
                                disclosureTiming = env.pbnttmng
                                dataHash = newHash
                            }

                            entitiesToSave.add(entity)
                        }
                    }
                }
            }

            environmentRepository.saveAll(entitiesToSave)
            logger.info(
                "Environment: ${entitiesToSave.size} changed/new, " +
                    "$unchangedCount unchanged out of ${allEnvironments.size} total " +
                    "for $sidoCode-$sggCode",
            )
            entitiesToSave.size
        }

    private suspend fun syncSafetyCheck(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("SafetyCheck", sidoCode, sggCode) {
            val responses = apiClient.getAllSafetyCheck(sidoCode, sggCode)
            val allSafetyChecks = responses.flatMap { it.kinderInfo ?: emptyList() }

            val kinderCodes = allSafetyChecks.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }
            val centerIds = centerMap.values.mapNotNull { it.id }
            val existingMap =
                safetyCheckRepository
                    .findAllByCenterIdIn(centerIds)
                    .associateBy { it.center.id }

            val entitiesToSave = mutableListOf<CenterSafetyCheck>()
            var unchangedCount = 0

            allSafetyChecks.forEach { safety ->
                safety.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        center.id?.let { centerId ->
                            val newHash = computeSafetyCheckHash(safety)
                            val existing = existingMap[centerId]

                            if (existing != null && existing.dataHash == newHash) {
                                unchangedCount++
                                return@let
                            }

                            val entity =
                                existing ?: CenterSafetyCheck(center = center)

                            entity.apply {
                                fireEvacuationYn = safety.fire_avd_yn
                                fireEvacuationDate = safety.fire_avd_dt
                                gasCheckYn = safety.gas_ck_yn
                                gasCheckDate = safety.gas_ck_dt
                                fireSafetyYn = safety.fire_safe_yn
                                fireSafetyDate = safety.fire_safe_dt
                                electricCheckYn = safety.elect_ck_yn
                                electricCheckDate = safety.elect_ck_dt
                                playgroundCheckYn = safety.plyg_ck_yn
                                playgroundCheckDate = safety.plyg_ck_dt
                                playgroundCheckResult = safety.plyg_ck_rs_cd
                                cctvInstalled = safety.cctv_ist_yn
                                cctvTotal = safety.cctv_ist_total?.toIntOrNull()
                                cctvIndoor = safety.cctv_ist_in?.toIntOrNull()
                                cctvOutdoor = safety.cctv_ist_out?.toIntOrNull()
                                disclosureTiming = safety.pbnttmng
                                dataHash = newHash
                            }

                            entitiesToSave.add(entity)
                        }
                    }
                }
            }

            safetyCheckRepository.saveAll(entitiesToSave)
            logger.info(
                "SafetyCheck: ${entitiesToSave.size} changed/new, " +
                    "$unchangedCount unchanged out of ${allSafetyChecks.size} total " +
                    "for $sidoCode-$sggCode",
            )
            entitiesToSave.size
        }

    private suspend fun syncSafetyEducation(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("SafetyEducation", sidoCode, sggCode) {
            val responses = apiClient.getAllSafetyEducation(sidoCode, sggCode)
            val allSafetyEducations = responses.flatMap { it.kinderInfo ?: emptyList() }
            logger.info(
                "Processing ${allSafetyEducations.size} safety education records " +
                    "for $sidoCode-$sggCode",
            )

            val kinderCodes = allSafetyEducations.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }

            // Bulk delete existing records for all affected centers
            centerMap.values.mapNotNull { it.id }.distinct().forEach { centerId ->
                safetyEducationRepository.deleteAllByCenterId(centerId)
            }

            val entitiesToSave = mutableListOf<CenterSafetyEducation>()

            allSafetyEducations.forEach { edu ->
                edu.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        val entity = CenterSafetyEducation(center = center)

                        entity.apply {
                            semester = edu.pbnt_sem_sc_cd
                            lifeSafety = edu.safe_tp_cd1
                            trafficSafety = edu.safe_tp_cd2
                            violencePrevention = edu.safe_tp_cd3
                            drugPrevention = edu.safe_tp_cd4
                            cyberPrevention = edu.safe_tp_cd5
                            disasterSafety = edu.safe_tp_cd6
                            occupationalSafety = edu.safe_tp_cd7
                            firstAid = edu.safe_tp_cd8
                            disclosureTiming = edu.pbnttmng
                        }

                        entitiesToSave.add(entity)
                    }
                }
            }

            safetyEducationRepository.saveAll(entitiesToSave)
            entitiesToSave.size
        }

    private suspend fun syncMutualAid(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("MutualAid", sidoCode, sggCode) {
            val responses = apiClient.getAllMutualAid(sidoCode, sggCode)
            val allMutualAids = responses.flatMap { it.kinderInfo ?: emptyList() }

            val kinderCodes = allMutualAids.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }
            val centerIds = centerMap.values.mapNotNull { it.id }
            val existingMap =
                mutualAidRepository
                    .findAllByCenterIdIn(centerIds)
                    .associateBy { it.center.id }

            val entitiesToSave = mutableListOf<CenterMutualAid>()
            var unchangedCount = 0

            allMutualAids.forEach { aid ->
                aid.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        center.id?.let { centerId ->
                            val newHash = computeMutualAidHash(aid)
                            val existing = existingMap[centerId]

                            if (existing != null && existing.dataHash == newHash) {
                                unchangedCount++
                                return@let
                            }

                            val entity =
                                existing ?: CenterMutualAid(center = center)

                            entity.apply {
                                schoolSafetyTarget = aid.school_ds_yn
                                schoolSafetyEnrolled = aid.school_ds_en
                                educationFacilityTarget = aid.educate_ds_yn
                                educationFacilityEnrolled = aid.educate_ds_en
                                disclosureTiming = aid.pbnttmng
                                dataHash = newHash
                            }

                            entitiesToSave.add(entity)
                        }
                    }
                }
            }

            mutualAidRepository.saveAll(entitiesToSave)
            logger.info(
                "MutualAid: ${entitiesToSave.size} changed/new, " +
                    "$unchangedCount unchanged out of ${allMutualAids.size} total " +
                    "for $sidoCode-$sggCode",
            )
            entitiesToSave.size
        }

    private suspend fun syncInsurance(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("Insurance", sidoCode, sggCode) {
            val responses = apiClient.getAllInsurance(sidoCode, sggCode)
            val allInsurances = responses.flatMap { it.kinderInfo ?: emptyList() }
            logger.info(
                "Processing ${allInsurances.size} insurance records for $sidoCode-$sggCode",
            )

            val kinderCodes = allInsurances.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }

            // Bulk delete existing records for all affected centers
            centerMap.values.mapNotNull { it.id }.distinct().forEach { centerId ->
                insuranceRepository.deleteAllByCenterId(centerId)
            }

            val entitiesToSave = mutableListOf<CenterInsurance>()

            allInsurances.forEach { ins ->
                ins.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        val entity = CenterInsurance(center = center)

                        entity.apply {
                            insuranceName = ins.insurance_nm
                            targetYn = ins.insurance_yn
                            enrolledYn = ins.insurance_en
                            company1 = ins.company1
                            company2 = ins.company2
                            company3 = ins.company3
                            disclosureTiming = ins.pbnttmng
                        }

                        entitiesToSave.add(entity)
                    }
                }
            }

            insuranceRepository.saveAll(entitiesToSave)
            entitiesToSave.size
        }

    private suspend fun syncAfterSchool(
        sidoCode: String,
        sggCode: String,
    ): Int =
        retryOnFailure("AfterSchool", sidoCode, sggCode) {
            val responses = apiClient.getAllAfterSchool(sidoCode, sggCode)
            val allAfterSchools = responses.flatMap { it.kinderInfo ?: emptyList() }

            val kinderCodes = allAfterSchools.mapNotNull { it.kinderCode }
            val centerMap =
                centerRepository
                    .findAllByKinderCodeIn(kinderCodes)
                    .associateBy { it.kinderCode }
            val centerIds = centerMap.values.mapNotNull { it.id }
            val existingMap =
                afterSchoolRepository
                    .findAllByCenterIdIn(centerIds)
                    .associateBy { it.center.id }

            val entitiesToSave = mutableListOf<CenterAfterSchool>()
            var unchangedCount = 0

            allAfterSchools.forEach { afterSchool ->
                afterSchool.kinderCode?.let { kinderCode ->
                    centerMap[kinderCode]?.let { center ->
                        center.id?.let { centerId ->
                            val newHash = computeAfterSchoolHash(afterSchool)
                            val existing = existingMap[centerId]

                            if (existing != null && existing.dataHash == newHash) {
                                unchangedCount++
                                return@let
                            }

                            val entity =
                                existing ?: CenterAfterSchool(center = center)

                            entity.apply {
                                independentClassCount =
                                    afterSchool.inor_clcnt?.toIntOrNull()
                                afternoonClassCount =
                                    afterSchool.pm_rrgn_clcnt?.toIntOrNull()
                                operatingHours = afterSchool.oper_time
                                independentParticipants =
                                    afterSchool.inor_ptcn_kpcnt?.toIntOrNull()
                                afternoonParticipants =
                                    afterSchool.pm_rrgn_ptcn_kpcnt?.toIntOrNull()
                                regularTeacherCount =
                                    afterSchool.fxrl_thcnt?.toIntOrNull()
                                contractTeacherCount =
                                    afterSchool.shcnt_thcnt?.toIntOrNull()
                                dedicatedStaffCount =
                                    afterSchool.incnt?.toIntOrNull()
                                disclosureTiming = afterSchool.pbnttmng
                                dataHash = newHash
                            }

                            entitiesToSave.add(entity)
                        }
                    }
                }
            }

            afterSchoolRepository.saveAll(entitiesToSave)
            logger.info(
                "AfterSchool: ${entitiesToSave.size} changed/new, " +
                    "$unchangedCount unchanged out of ${allAfterSchools.size} total " +
                    "for $sidoCode-$sggCode",
            )
            entitiesToSave.size
        }

    private fun parseLocation(
        latitude: String?,
        longitude: String?,
    ): org.locationtech.jts.geom.Point? {
        if (latitude.isNullOrBlank() || longitude.isNullOrBlank()) return null

        return try {
            val lat = latitude.toDouble()
            val lng = longitude.toDouble()
            geometryFactory.createPoint(Coordinate(lng, lat))
        } catch (e: Exception) {
            logger.warn("Failed to parse location: lat=$latitude, lng=$longitude", e)
            null
        }
    }

    private suspend fun <T> retryOnFailure(
        apiName: String,
        sidoCode: String,
        sggCode: String,
        block: suspend () -> T,
    ): T {
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                logger.error(
                    "$apiName sync failed for $sidoCode-$sggCode " +
                        "(attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS): ${e.message}",
                    e,
                )
                if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                    throw e
                }
                delay(1000L * (attempt + 1))
            }
        }
        throw IllegalStateException("Retry loop completed without success")
    }
}
