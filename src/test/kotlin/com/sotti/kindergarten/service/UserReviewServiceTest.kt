package com.sotti.kindergarten.service

import com.sotti.kindergarten.dto.app.UserReviewCreateRequest
import com.sotti.kindergarten.dto.app.UserReviewUpdateRequest
import com.sotti.kindergarten.entity.Center
import com.sotti.kindergarten.entity.UserReview
import com.sotti.kindergarten.exception.BusinessException
import com.sotti.kindergarten.exception.ErrorCode
import com.sotti.kindergarten.repository.CenterRepository
import com.sotti.kindergarten.repository.UserReviewRepository
import com.sotti.kindergarten.util.ProfanityFilter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.Optional
import java.util.UUID

class UserReviewServiceTest :
    BehaviorSpec({
        val userReviewRepository = mockk<UserReviewRepository>(relaxed = true)
        val centerRepository = mockk<CenterRepository>(relaxed = true)
        val profanityFilter = ProfanityFilter()
        val service = UserReviewService(userReviewRepository, centerRepository, profanityFilter)

        val centerId = UUID.randomUUID()
        val deviceId = "test-device-id"
        val center = mockk<Center>(relaxed = true)

        beforeSpec {
            every { center.id } returns centerId
            every { center.name } returns "테스트유치원"
        }

        Given("한줄평 목록 조회") {
            val review = mockk<UserReview>(relaxed = true)
            every { review.id } returns UUID.randomUUID()
            every { review.center } returns center
            every { review.deviceId } returns deviceId
            every { review.nickname } returns "테스터"
            every { review.content } returns "좋은 유치원이에요"

            val pageable = PageRequest.of(0, 20)
            every {
                userReviewRepository.findAllByCenterIdOrderByCreatedAtDesc(centerId, pageable)
            } returns PageImpl(listOf(review), pageable, 1)

            When("본인 deviceId로 조회") {
                val result = service.getReviews(centerId, deviceId, 0, 20)

                Then("isMine이 true로 반환된다") {
                    result.content.size shouldBe 1
                    result.content[0].isMine shouldBe true
                    result.content[0].nickname shouldBe "테스터"
                }
            }

            When("다른 deviceId로 조회") {
                val result = service.getReviews(centerId, "other-device", 0, 20)

                Then("isMine이 false로 반환된다") {
                    result.content[0].isMine shouldBe false
                }
            }

            When("deviceId 없이 조회") {
                val result = service.getReviews(centerId, null, 0, 20)

                Then("isMine이 false로 반환된다") {
                    result.content[0].isMine shouldBe false
                }
            }
        }

        Given("한줄평 작성") {
            val request =
                UserReviewCreateRequest(
                    deviceId = deviceId,
                    centerId = centerId,
                    nickname = "테스터",
                    content = "좋은 유치원이에요",
                )

            When("정상 작성") {
                every { userReviewRepository.existsByDeviceIdAndCenterId(deviceId, centerId) } returns false
                every { centerRepository.findById(centerId) } returns Optional.of(center)

                val savedReview = mockk<UserReview>(relaxed = true)
                every { savedReview.id } returns UUID.randomUUID()
                every { savedReview.center } returns center
                every { savedReview.nickname } returns "테스터"
                every { savedReview.content } returns "좋은 유치원이에요"
                every { userReviewRepository.save(any()) } returns savedReview

                val result = service.createReview(request)

                Then("리뷰가 생성된다") {
                    result.nickname shouldBe "테스터"
                    result.content shouldBe "좋은 유치원이에요"
                    result.isMine shouldBe true
                }
            }

            When("이미 리뷰를 작성한 경우") {
                every { userReviewRepository.existsByDeviceIdAndCenterId(deviceId, centerId) } returns true

                Then("DUPLICATE_USER_REVIEW 에러가 발생한다") {
                    val ex = shouldThrow<BusinessException> { service.createReview(request) }
                    ex.errorCode shouldBe ErrorCode.DUPLICATE_USER_REVIEW
                }
            }

            When("존재하지 않는 유치원에 작성") {
                every { userReviewRepository.existsByDeviceIdAndCenterId(deviceId, centerId) } returns false
                every { centerRepository.findById(centerId) } returns Optional.empty()

                Then("KINDERGARTEN_NOT_FOUND 에러가 발생한다") {
                    val ex = shouldThrow<BusinessException> { service.createReview(request) }
                    ex.errorCode shouldBe ErrorCode.KINDERGARTEN_NOT_FOUND
                }
            }

            When("비속어가 포함된 닉네임으로 작성") {
                every { userReviewRepository.existsByDeviceIdAndCenterId(deviceId, centerId) } returns false
                every { centerRepository.findById(centerId) } returns Optional.of(center)

                val badRequest = request.copy(nickname = "씨발놈")

                Then("PROFANITY_DETECTED 에러가 발생한다") {
                    val ex = shouldThrow<BusinessException> { service.createReview(badRequest) }
                    ex.errorCode shouldBe ErrorCode.PROFANITY_DETECTED
                }
            }

            When("비속어가 포함된 내용으로 작성") {
                every { userReviewRepository.existsByDeviceIdAndCenterId(deviceId, centerId) } returns false
                every { centerRepository.findById(centerId) } returns Optional.of(center)

                val badRequest = request.copy(content = "병신같은 유치원")

                Then("PROFANITY_DETECTED 에러가 발생한다") {
                    val ex = shouldThrow<BusinessException> { service.createReview(badRequest) }
                    ex.errorCode shouldBe ErrorCode.PROFANITY_DETECTED
                }
            }
        }

        Given("한줄평 수정") {
            val reviewId = UUID.randomUUID()
            val updateRequest =
                UserReviewUpdateRequest(
                    deviceId = deviceId,
                    nickname = "수정닉네임",
                    content = "수정된 리뷰",
                )

            When("본인 리뷰 수정") {
                val review = mockk<UserReview>(relaxed = true)
                every { review.id } returns reviewId
                every { review.center } returns center
                every { review.nickname } returns "수정닉네임"
                every { review.content } returns "수정된 리뷰"
                every { userReviewRepository.findByIdAndDeviceId(reviewId, deviceId) } returns review

                val result = service.updateReview(reviewId, updateRequest)

                Then("수정된 리뷰가 반환된다") {
                    result.nickname shouldBe "수정닉네임"
                    result.content shouldBe "수정된 리뷰"
                    result.isMine shouldBe true
                }
            }

            When("타인 리뷰 수정 시도") {
                every { userReviewRepository.findByIdAndDeviceId(reviewId, deviceId) } returns null

                Then("USER_REVIEW_NOT_FOUND 에러가 발생한다") {
                    val ex =
                        shouldThrow<BusinessException> {
                            service.updateReview(reviewId, updateRequest)
                        }
                    ex.errorCode shouldBe ErrorCode.USER_REVIEW_NOT_FOUND
                }
            }
        }

        Given("한줄평 삭제") {
            val reviewId = UUID.randomUUID()

            When("본인 리뷰 삭제") {
                every { userReviewRepository.deleteByIdAndDeviceId(reviewId, deviceId) } returns 1

                service.deleteReview(reviewId, deviceId)

                Then("삭제가 수행된다") {
                    verify { userReviewRepository.deleteByIdAndDeviceId(reviewId, deviceId) }
                }
            }

            When("타인 리뷰 삭제 시도") {
                every { userReviewRepository.deleteByIdAndDeviceId(reviewId, "other-device") } returns 0

                Then("USER_REVIEW_NOT_FOUND 에러가 발생한다") {
                    val ex =
                        shouldThrow<BusinessException> {
                            service.deleteReview(reviewId, "other-device")
                        }
                    ex.errorCode shouldBe ErrorCode.USER_REVIEW_NOT_FOUND
                }
            }
        }
    })
