package com.sotti.kindergarten.util

import com.sotti.kindergarten.exception.BusinessException
import com.sotti.kindergarten.exception.ErrorCode
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ProfanityFilterTest :
    BehaviorSpec({
        val filter = ProfanityFilter()

        Given("비속어 탐지") {
            When("비속어가 포함된 텍스트") {
                Then("true를 반환한다") {
                    filter.containsProfanity("씨발") shouldBe true
                    filter.containsProfanity("병신같은") shouldBe true
                    filter.containsProfanity("지랄하네") shouldBe true
                    filter.containsProfanity("새끼야") shouldBe true
                    filter.containsProfanity("존나좋다") shouldBe true
                }
            }

            When("공백으로 우회 시도") {
                Then("탐지된다") {
                    filter.containsProfanity("씨 발") shouldBe true
                    filter.containsProfanity("병 신") shouldBe true
                    filter.containsProfanity("지 랄") shouldBe true
                }
            }

            When("특수문자로 우회 시도") {
                Then("탐지된다") {
                    filter.containsProfanity("씨!발") shouldBe true
                    filter.containsProfanity("병@신") shouldBe true
                    filter.containsProfanity("새~끼") shouldBe true
                }
            }

            When("정상 텍스트") {
                Then("false를 반환한다") {
                    filter.containsProfanity("좋은 유치원이에요") shouldBe false
                    filter.containsProfanity("아이가 좋아해요") shouldBe false
                    filter.containsProfanity("선생님이 친절해요") shouldBe false
                }
            }
        }

        Given("validate 메서드") {
            When("비속어가 포함된 텍스트") {
                Then("BusinessException이 발생한다") {
                    val ex =
                        shouldThrow<BusinessException> {
                            filter.validate("씨발")
                        }
                    ex.errorCode shouldBe ErrorCode.PROFANITY_DETECTED
                }
            }

            When("정상 텍스트") {
                Then("예외가 발생하지 않는다") {
                    shouldNotThrow<BusinessException> {
                        filter.validate("좋은 유치원이에요")
                    }
                }
            }
        }
    })
