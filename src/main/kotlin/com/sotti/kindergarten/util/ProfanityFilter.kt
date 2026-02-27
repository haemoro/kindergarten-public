package com.sotti.kindergarten.util

import com.sotti.kindergarten.exception.BusinessException
import com.sotti.kindergarten.exception.ErrorCode
import org.springframework.stereotype.Component

@Component
class ProfanityFilter {
    private val profanityList =
        listOf(
            "씨발",
            "시발",
            "씨팔",
            "시팔",
            "병신",
            "ㅂㅅ",
            "지랄",
            "ㅈㄹ",
            "새끼",
            "ㅅㄲ",
            "존나",
            "졸라",
            "ㅈㄴ",
            "느금마",
            "니엄마",
            "느그마",
            "미친놈",
            "미친년",
            "개새끼",
            "개새",
            "꺼져",
            "닥쳐",
            "ㅆㅂ",
            "ㅅㅂ",
        )

    fun containsProfanity(text: String): Boolean {
        val normalized = text.replace(Regex("[\\s~!@#$%^&*()_+\\-=\\[\\]{};':\",./<>?`]"), "")
        return profanityList.any { normalized.contains(it) }
    }

    fun validate(text: String) {
        if (containsProfanity(text)) {
            throw BusinessException(ErrorCode.PROFANITY_DETECTED)
        }
    }
}
