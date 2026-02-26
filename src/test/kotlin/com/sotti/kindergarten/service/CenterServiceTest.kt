package com.sotti.kindergarten.service

import com.sotti.kindergarten.dto.CenterCompareRequest
import com.sotti.kindergarten.entity.Center
import com.sotti.kindergarten.exception.CenterNotFoundException
import com.sotti.kindergarten.exception.InvalidCompareRequestException
import com.sotti.kindergarten.repository.CenterRepository
import com.sotti.kindergarten.repository.CenterSearchFilter
import com.sotti.kindergarten.repository.CompareProjection
import com.sotti.kindergarten.repository.RegionRepository
import com.sotti.kindergarten.repository.SearchListProjection
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.util.UUID

class CenterServiceTest :
    BehaviorSpec({
        val centerRepository = mockk<CenterRepository>(relaxed = true)
        val regionRepository = mockk<RegionRepository>(relaxed = true)
        val regionCacheService = mockk<RegionCacheService>(relaxed = true)
        val centerService = CenterService(centerRepository, regionRepository, regionCacheService)

        Given("유치원 목록 조회") {
            val proj1 =
                SearchListProjection(
                    id = UUID.randomUUID(),
                    name = "테스트유치원1",
                    establishType = null,
                    address = null,
                    phone = null,
                    lat = null,
                    lng = null,
                    distanceKm = 1.0,
                    capacity = null,
                    currentEnrollment = null,
                    totalClassCount = null,
                    mealProvided = false,
                    busAvailable = false,
                    extendedCare = false,
                    totalCount = 2,
                )
            val proj2 = proj1.copy(id = UUID.randomUUID(), name = "테스트유치원2")

            val center1 = mockk<Center>(relaxed = true)
            every { center1.id } returns UUID.randomUUID()
            every { center1.name } returns "테스트유치원1"
            every { center1.location } returns null

            When("반경 검색으로 조회") {
                val pageable = PageRequest.of(0, 20, Sort.by("updatedAt").descending())
                val filter = CenterSearchFilter()
                every {
                    centerRepository.findNearby(37.5, 127.0, 2000.0, filter, pageable)
                } returns PageImpl(listOf(proj1, proj2), pageable, 2)

                val result = centerService.listCenters(37.5, 127.0, 2.0, null, null, null, 0, 20)

                Then("페이징된 결과를 반환한다") {
                    result.content.size shouldBe 2
                    result.page shouldBe 0
                    result.size shouldBe 20
                    result.totalElements shouldBe 2
                }
            }

            When("필터링 없이 조회") {
                val pageable = PageRequest.of(0, 20, Sort.by("updatedAt").descending())
                val filter = CenterSearchFilter()
                val proj3 = proj1.copy(id = UUID.randomUUID(), name = "필터테스트")
                every {
                    centerRepository.findAllWithFilters(filter, pageable)
                } returns PageImpl(listOf(proj3), pageable, 1)

                val result = centerService.listCenters(null, null, null, null, null, null, 0, 20)

                Then("전체 목록을 반환한다") {
                    result.content.size shouldBe 1
                }
            }
        }

        Given("유치원 상세 조회") {
            val centerId = UUID.randomUUID()
            val center = mockk<Center>(relaxed = true)

            When("존재하는 유치원 조회") {
                every { center.id } returns centerId
                every { center.name } returns "테스트유치원"
                every { center.safetyEducations } returns mutableSetOf()
                every { center.insurances } returns mutableSetOf()
                every { centerRepository.findByIdWithDetails(centerId) } returns center

                val result = centerService.getCenterDetail(centerId)

                Then("상세 정보를 반환한다") {
                    result.id shouldBe centerId
                    result.name shouldBe "테스트유치원"
                }
            }

            When("존재하지 않는 유치원 조회") {
                val invalidId = UUID.randomUUID()
                every { centerRepository.findByIdWithDetails(invalidId) } returns null

                Then("CenterNotFoundException이 발생한다") {
                    shouldThrow<CenterNotFoundException> {
                        centerService.getCenterDetail(invalidId)
                    }
                }
            }
        }

        Given("유치원 비교") {
            val center1Id = UUID.randomUUID()
            val center2Id = UUID.randomUUID()

            val projection1 =
                CompareProjection(
                    id = center1Id,
                    name = "유치원1",
                    establishType = "공립",
                    address = "서울시 강남구",
                    distanceKm = null,
                    capacity = 100,
                    currentEnrollment = 75,
                    teacherCount = 10,
                    classCount = 5,
                    mealProvided = true,
                    busAvailable = true,
                    extendedCare = true,
                    buildingArea = 500.0,
                    classroomArea = 300.0,
                    cctvInstalled = true,
                    cctvTotal = 10,
                    isActive = true,
                )
            val projection2 =
                CompareProjection(
                    id = center2Id,
                    name = "유치원2",
                    establishType = "사립",
                    address = "서울시 서초구",
                    distanceKm = null,
                    capacity = 80,
                    currentEnrollment = 60,
                    teacherCount = 8,
                    classCount = 4,
                    mealProvided = true,
                    busAvailable = false,
                    extendedCare = false,
                    buildingArea = 400.0,
                    classroomArea = 250.0,
                    cctvInstalled = true,
                    cctvTotal = 8,
                    isActive = true,
                )

            When("2개 유치원 비교") {
                val request = CenterCompareRequest(centerIds = listOf(center1Id, center2Id), lat = null, lng = null)
                every { centerRepository.findCompareData(request.centerIds, null, null) } returns listOf(projection1, projection2)

                val result = centerService.compareCenters(request)

                Then("비교 결과를 반환한다") {
                    result.centers.size shouldBe 2
                    result.centers[0].name shouldBe "유치원1"
                    result.centers[1].name shouldBe "유치원2"
                    result.centers[0].currentEnrollment shouldBe 75
                    result.centers[1].currentEnrollment shouldBe 60
                }
            }

            When("1개만 요청") {
                val request = CenterCompareRequest(centerIds = listOf(center1Id), lat = null, lng = null)

                Then("InvalidCompareRequestException이 발생한다") {
                    shouldThrow<InvalidCompareRequestException> {
                        centerService.compareCenters(request)
                    }
                }
            }

            When("5개 요청") {
                val request =
                    CenterCompareRequest(
                        centerIds = listOf(center1Id, center2Id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                        lat = null,
                        lng = null,
                    )

                Then("InvalidCompareRequestException이 발생한다") {
                    shouldThrow<InvalidCompareRequestException> {
                        centerService.compareCenters(request)
                    }
                }
            }

            When("존재하지 않는 유치원 포함") {
                val invalidId = UUID.randomUUID()
                val request = CenterCompareRequest(centerIds = listOf(center1Id, invalidId), lat = null, lng = null)
                every { centerRepository.findCompareData(request.centerIds, null, null) } returns listOf(projection1)

                Then("CenterNotFoundException이 발생한다") {
                    shouldThrow<CenterNotFoundException> {
                        centerService.compareCenters(request)
                    }
                }
            }
        }
    })
