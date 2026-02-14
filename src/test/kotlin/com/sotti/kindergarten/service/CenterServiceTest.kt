package com.sotti.kindergarten.service

import com.sotti.kindergarten.dto.CenterCompareRequest
import com.sotti.kindergarten.entity.Center
import com.sotti.kindergarten.exception.CenterNotFoundException
import com.sotti.kindergarten.exception.InvalidCompareRequestException
import com.sotti.kindergarten.repository.CenterRepository
import com.sotti.kindergarten.repository.CenterSearchFilter
import com.sotti.kindergarten.repository.RegionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.util.Optional
import java.util.UUID

class CenterServiceTest :
    BehaviorSpec({
        val centerRepository = mockk<CenterRepository>(relaxed = true)
        val regionRepository = mockk<RegionRepository>(relaxed = true)
        val centerService = CenterService(centerRepository, regionRepository)

        Given("유치원 목록 조회") {
            val center1 = mockk<Center>(relaxed = true)
            val center2 = mockk<Center>(relaxed = true)

            every { center1.id } returns UUID.randomUUID()
            every { center1.name } returns "테스트유치원1"
            every { center1.location } returns null
            every { center2.id } returns UUID.randomUUID()
            every { center2.name } returns "테스트유치원2"
            every { center2.location } returns null

            When("반경 검색으로 조회") {
                val pageable = PageRequest.of(0, 20, Sort.by("updatedAt").descending())
                val filter = CenterSearchFilter()
                every {
                    centerRepository.findNearby(37.5, 127.0, 2000.0, filter, pageable)
                } returns PageImpl(listOf(center1, center2), pageable, 2)

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
                every {
                    centerRepository.findAllWithFilters(filter, pageable)
                } returns PageImpl(listOf(center1), pageable, 1)

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
                every { center.safetyEducations } returns mutableListOf()
                every { center.insurances } returns mutableListOf()
                every { centerRepository.findById(centerId) } returns Optional.of(center)

                val result = centerService.getCenterDetail(centerId)

                Then("상세 정보를 반환한다") {
                    result.id shouldBe centerId
                    result.name shouldBe "테스트유치원"
                }
            }

            When("존재하지 않는 유치원 조회") {
                val invalidId = UUID.randomUUID()
                every { centerRepository.findById(invalidId) } returns Optional.empty()

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
            val center1 = mockk<Center>(relaxed = true)
            val center2 = mockk<Center>(relaxed = true)

            every { center1.id } returns center1Id
            every { center1.name } returns "유치원1"
            every { center1.enrollment3 } returns 20
            every { center1.enrollment4 } returns 25
            every { center1.enrollment5 } returns 30
            every { center2.id } returns center2Id
            every { center2.name } returns "유치원2"
            every { center2.enrollment3 } returns 15
            every { center2.enrollment4 } returns 20
            every { center2.enrollment5 } returns 25

            When("2개 유치원 비교") {
                val request = CenterCompareRequest(centerIds = listOf(center1Id, center2Id), lat = null, lng = null)
                every { centerRepository.findAllById(request.centerIds) } returns listOf(center1, center2)

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
                every { centerRepository.findAllById(request.centerIds) } returns listOf(center1)

                Then("CenterNotFoundException이 발생한다") {
                    shouldThrow<CenterNotFoundException> {
                        centerService.compareCenters(request)
                    }
                }
            }
        }
    })
