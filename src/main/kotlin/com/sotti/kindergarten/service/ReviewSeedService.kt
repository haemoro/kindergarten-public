package com.sotti.kindergarten.service

import com.sotti.kindergarten.dto.admin.BatchSeedResponse
import com.sotti.kindergarten.dto.admin.ReviewSeedSaveRequest
import com.sotti.kindergarten.dto.admin.ReviewSeedSaveResponse
import com.sotti.kindergarten.dto.admin.ReviewSeedStatusResponse
import com.sotti.kindergarten.dto.admin.ReviewSeedTargetResponse
import com.sotti.kindergarten.dto.admin.ReviewSeedTargetsResponse
import com.sotti.kindergarten.dto.admin.RichTargetResponse
import com.sotti.kindergarten.dto.admin.RichTargetsResponse
import com.sotti.kindergarten.entity.ReviewSeedState
import com.sotti.kindergarten.entity.UserReview
import com.sotti.kindergarten.exception.BusinessException
import com.sotti.kindergarten.exception.ErrorCode
import com.sotti.kindergarten.repository.CenterRepository
import com.sotti.kindergarten.repository.CenterReviewRepository
import com.sotti.kindergarten.repository.RegionRepository
import com.sotti.kindergarten.repository.ReviewSeedStateRepository
import com.sotti.kindergarten.repository.UserReviewRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

@Service
@Transactional(readOnly = true)
class ReviewSeedService(
    private val centerRepository: CenterRepository,
    private val centerReviewRepository: CenterReviewRepository,
    private val userReviewRepository: UserReviewRepository,
    private val regionRepository: RegionRepository,
    private val reviewSeedStateRepository: ReviewSeedStateRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val STATE_KEY = "review_seed_round_robin"

        private val NICKNAMES_PREFIX =
            listOf(
                "행복한",
                "사랑스런",
                "귀여운",
                "씩씩한",
                "건강한",
                "밝은",
                "똑똑한",
                "즐거운",
                "활발한",
                "다정한",
                "따뜻한",
                "웃는",
            )
        private val NICKNAMES_SUFFIX =
            listOf(
                "엄마",
                "아빠",
                "맘",
                "대디",
                "학부모",
                "파파",
                "마미",
                "부모",
            )
    }

    // ========== 외부리뷰 기반 리치 타겟 ==========

    fun getRichTargets(count: Int): RichTargetsResponse {
        val rows = centerReviewRepository.findRichTargetsRaw(minCount = 3L)

        val grouped = linkedMapOf<UUID, MutableList<Array<Any?>>>()
        for (row in rows) {
            val centerId = row[0] as UUID
            grouped.getOrPut(centerId) { mutableListOf() }.add(row)
        }

        val allTargets =
            grouped.entries.map { (centerId, rowGroup) ->
                val first = rowGroup[0]
                RichTargetResponse(
                    centerId = centerId,
                    name = first[1] as String,
                    address = first[2] as? String,
                    establishType = first[3] as? String,
                    externalReviewCount = (first[4] as Number).toInt(),
                    snippets = rowGroup.map { it[5] as String },
                )
            }

        return RichTargetsResponse(
            targets = allTargets.take(count),
            totalFound = allTargets.size,
        )
    }

    // ========== 일괄 시딩 ==========

    @Transactional
    fun batchSeed(count: Int): BatchSeedResponse {
        val targets = fetchAllTargets(count)
        if (targets.isEmpty()) {
            log.info("시딩 대상 유치원이 없습니다")
            return BatchSeedResponse(seededCount = 0, samples = emptyList())
        }

        log.info("시딩 시작: ${targets.size}건")
        val now = LocalDateTime.now()
        val results = mutableListOf<ReviewSeedSaveResponse>()
        val centerMap =
            centerRepository
                .findAllById(targets.map { it.centerId })
                .associateBy { it.id }

        for (target in targets) {
            val content = generateReviewContent(target)
            val nickname = generateNickname()
            val deviceId = "seed-${UUID.randomUUID()}"
            val pastDate = randomPastDate(now)

            val center = centerMap[target.centerId] ?: continue

            val review =
                UserReview(
                    deviceId = deviceId,
                    center = center,
                    nickname = nickname,
                    content = content,
                )
            val saved = userReviewRepository.save(review)
            userReviewRepository.flush()

            userReviewRepository.updateTimestamps(
                id = saved.id!!,
                createdAt = pastDate,
                updatedAt = pastDate,
            )

            results.add(
                ReviewSeedSaveResponse(
                    reviewId = saved.id!!,
                    centerId = center.id!!,
                    nickname = nickname,
                    content = content,
                ),
            )
        }

        log.info("시딩 완료: ${results.size}건")
        return BatchSeedResponse(
            seededCount = results.size,
            samples = results.take(5),
        )
    }

    private fun fetchAllTargets(count: Int): List<ReviewSeedTargetResponse> {
        // "%" matches all addresses (all sido regions)
        val rows =
            centerRepository.findCentersWithoutReviewBySido(
                sidoName = "%",
                offset = 0,
                limit = count,
            )
        return rows.map { row ->
            ReviewSeedTargetResponse(
                centerId = row[0] as UUID,
                name = row[1] as String,
                establishType = row[2] as? String,
                address = row[3] as? String,
                mealType = row[4] as? String,
                busOperating = row[5] as? String,
                teacherCount = (row[6] as? Number)?.toInt(),
                cctvTotal = (row[7] as? Number)?.toInt(),
            )
        }
    }

    private fun generateNickname(): String {
        val prefix = NICKNAMES_PREFIX.random()
        val suffix = NICKNAMES_SUFFIX.random()
        val num = Random.nextInt(1, 100)
        return "$prefix$suffix$num"
    }

    private fun randomPastDate(now: LocalDateTime): LocalDateTime {
        val daysAgo = Random.nextLong(7, 90)
        val hour = Random.nextInt(7, 23)
        val minute = Random.nextInt(0, 60)
        return now
            .minusDays(daysAgo)
            .withHour(hour)
            .withMinute(minute)
            .withSecond(Random.nextInt(0, 60))
    }

    // ========== 리뷰 내용 생성 (유치원 정보 기반 개인화) ==========

    private fun generateReviewContent(target: ReviewSeedTargetResponse): String {
        val candidates = mutableListOf<String>()
        val district = extractDistrict(target.address)

        // 급식 직영
        if (target.mealType == "직영") {
            candidates.addAll(
                listOf(
                    "급식 직영이라 재료 신선하고 아이가 밥을 잘 먹어요",
                    "직영 급식 퀄리티가 좋아서 집에서보다 더 잘 먹고 와요",
                    "급식을 직접 운영해서 먹는 거 걱정 없는 게 제일 좋아요",
                    "직영 급식이라 식단이 알차고 아이가 편식도 줄었어요",
                    "매일 급식 사진 올라오는데 직영이라 그런지 정말 알차요",
                    "급식 직영이라 위생 관리도 잘 되고 안심이에요",
                    "직영 급식이 맛있나봐요 매일 오늘 뭐 먹었는지 이야기해요",
                ),
            )
        }
        if (target.mealType == "위탁") {
            candidates.addAll(
                listOf(
                    "위탁 급식인데 메뉴 구성이 다양해서 아이가 좋아해요",
                    "위탁급식인데 맛있나봐요 아이가 밥 이야기를 많이 해요",
                ),
            )
        }

        // 통학버스
        if (target.busOperating == "Y") {
            candidates.addAll(
                listOf(
                    "통학버스가 있어서 맞벌이한테 정말 편한 곳이에요",
                    "버스 운행해줘서 등하원 걱정 없는 게 큰 장점이에요",
                    "통학버스 선생님이 친절하셔서 아이가 버스 타는 걸 좋아해요",
                    "등하원 버스가 집 앞까지 와서 너무 편해요",
                ),
            )
        }

        // 교사 수 (8명 이상)
        val tc = target.teacherCount
        if (tc != null && tc >= 8) {
            candidates.addAll(
                listOf(
                    "선생님이 ${tc}명이서 아이들 한 명 한 명 세심하게 봐주세요",
                    "교사 수가 충분해서 소그룹으로 잘 봐주시는 느낌이에요",
                    "선생님이 많아서 아이 개개인에게 관심을 많이 가져주세요",
                ),
            )
        }

        // CCTV (10대 이상)
        val cctv = target.cctvTotal
        if (cctv != null && cctv >= 10) {
            candidates.addAll(
                listOf(
                    "CCTV가 ${cctv}대나 있어서 안전 면에서 믿음이 가요",
                    "곳곳에 CCTV가 있어서 안심하고 보내고 있어요",
                    "보안 시설이 잘 갖춰져 있어서 안전하다고 느껴요",
                ),
            )
        }

        // 설립유형
        if (target.establishType?.contains("공립") == true) {
            candidates.addAll(
                listOf(
                    "공립이라 학비 부담이 적고 커리큘럼이 탄탄해요",
                    "공립 유치원이라 교육 과정이 체계적이에요",
                    "공립이라 가격 대비 만족도가 정말 높아요",
                    "공립이라 교사 전문성이 좋고 안정적이에요",
                ),
            )
        }
        if (target.establishType?.contains("사립") == true) {
            candidates.addAll(
                listOf(
                    "다양한 프로그램 덕분에 아이가 지루해하지 않아요",
                    "특별활동이 많아서 아이가 재밌게 다녀요",
                    "교육 프로그램이 다양해서 아이가 많이 성장했어요",
                    "프로그램이 알차서 아이가 매일 뭐 배웠는지 이야기해요",
                ),
            )
        }

        // 지역 기반
        if (district != null) {
            candidates.addAll(
                listOf(
                    "${district}에서 입소문 나 있는 곳이에요 보내길 잘했어요",
                    "${district}에서 여기만한 유치원 없는 것 같아요",
                    "$district 맘카페에서도 평판이 좋은 곳이에요",
                ),
            )
        }

        // 복합 조건
        if (target.mealType == "직영" && target.busOperating == "Y") {
            candidates.add("급식 직영에 통학버스도 있어서 정말 편한 곳이에요")
        }
        if (cctv != null && cctv >= 10 && tc != null && tc >= 8) {
            candidates.add("선생님도 많고 CCTV도 곳곳에 있어서 안심이에요")
        }
        if (target.mealType == "직영" && target.establishType?.contains("공립") == true) {
            candidates.add("공립에 직영 급식까지 가성비가 정말 좋아요")
        }

        // 일반 긍정 (항상 후보에 포함)
        candidates.addAll(GENERAL_REVIEWS)

        return candidates.random()
    }

    private fun extractDistrict(address: String?): String? {
        if (address == null) return null
        val parts = address.split(" ")
        return parts.getOrNull(1)?.takeIf { it.endsWith("구") || it.endsWith("군") || it.endsWith("시") }
    }

    // ========== 기존 API (라운드로빈 기반) ==========

    fun getNextTargets(count: Int): ReviewSeedTargetsResponse {
        val sidoList = getDistinctSidoNames()
        if (sidoList.isEmpty()) {
            return ReviewSeedTargetsResponse(targets = emptyList(), currentSido = "", nextSido = "")
        }

        val state = getOrCreateState()
        val sidoIndex = state.currentSidoIndex % sidoList.size
        val currentSido = sidoList[sidoIndex]
        val offset = state.currentOffset

        val targets = findTargetsInSido(currentSido, offset, count)

        val nextSidoIndex = if (targets.size < count) (sidoIndex + 1) % sidoList.size else sidoIndex
        return ReviewSeedTargetsResponse(
            targets = targets,
            currentSido = currentSido,
            nextSido = sidoList[nextSidoIndex],
        )
    }

    @Transactional
    fun saveSeedReview(request: ReviewSeedSaveRequest): ReviewSeedSaveResponse {
        val center =
            centerRepository
                .findById(request.centerId)
                .orElseThrow { BusinessException(ErrorCode.KINDERGARTEN_NOT_FOUND) }

        val review =
            UserReview(
                deviceId = "seed-${UUID.randomUUID()}",
                center = center,
                nickname = request.nickname,
                content = request.content,
            )
        val saved = userReviewRepository.save(review)
        advanceState()

        return ReviewSeedSaveResponse(
            reviewId = saved.id!!,
            centerId = center.id!!,
            nickname = saved.nickname,
            content = saved.content,
        )
    }

    fun getStatus(): ReviewSeedStatusResponse {
        val sidoList = getDistinctSidoNames()
        val state = getOrCreateState()
        val sidoIndex = state.currentSidoIndex % sidoList.size.coerceAtLeast(1)

        return ReviewSeedStatusResponse(
            currentSidoIndex = sidoIndex,
            currentSidoName = sidoList.getOrElse(sidoIndex) { "" },
            currentOffset = state.currentOffset,
            totalSidoCount = sidoList.size,
            sidoList = sidoList,
        )
    }

    // ========== Private helpers ==========

    private fun getDistinctSidoNames(): List<String> =
        regionRepository
            .findAllByOrderBySidoCodeAscSggCodeAsc()
            .map { it.sidoName }
            .distinct()

    private fun getOrCreateState(): ReviewSeedState =
        reviewSeedStateRepository.findByKey(STATE_KEY)
            ?: reviewSeedStateRepository.save(ReviewSeedState(key = STATE_KEY))

    private fun advanceState() {
        val sidoList = getDistinctSidoNames()
        if (sidoList.isEmpty()) return

        val state =
            reviewSeedStateRepository.findByKey(STATE_KEY)
                ?: reviewSeedStateRepository.save(ReviewSeedState(key = STATE_KEY))

        val currentSido = sidoList[state.currentSidoIndex % sidoList.size]
        val nextOffset = state.currentOffset + 1
        val remaining = countRemainingInSido(currentSido, nextOffset)

        if (remaining > 0) {
            state.currentOffset = nextOffset
        } else {
            state.currentSidoIndex = (state.currentSidoIndex + 1) % sidoList.size
            state.currentOffset = 0
        }
        reviewSeedStateRepository.save(state)
    }

    private fun findTargetsInSido(
        sidoName: String,
        offset: Int,
        count: Int,
    ): List<ReviewSeedTargetResponse> =
        centerRepository
            .findCentersWithoutReviewBySido(
                sidoName = "%$sidoName%",
                offset = offset,
                limit = count,
            ).map { row ->
                ReviewSeedTargetResponse(
                    centerId = row[0] as UUID,
                    name = row[1] as String,
                    establishType = row[2] as? String,
                    address = row[3] as? String,
                    mealType = row[4] as? String,
                    busOperating = row[5] as? String,
                    teacherCount = (row[6] as? Number)?.toInt(),
                    cctvTotal = (row[7] as? Number)?.toInt(),
                )
            }

    private fun countRemainingInSido(
        sidoName: String,
        offset: Int,
    ): Long {
        val total = centerRepository.countCentersWithoutReviewBySido(sidoName = "%$sidoName%")
        return (total - offset).coerceAtLeast(0)
    }
}

private val GENERAL_REVIEWS =
    listOf(
        "아이가 매일 유치원 가고 싶어해서 보람 있어요",
        "선생님들이 정말 따뜻하고 아이를 잘 대해주세요",
        "원장님이 교육에 진심이셔서 믿고 보내고 있어요",
        "아이가 여기 다니면서 많이 밝아졌어요",
        "친구들과 잘 어울리고 매일 재밌다고 해서 만족해요",
        "처음엔 적응 걱정했는데 금방 좋아하더라고요",
        "하원할 때 아이가 더 놀고 싶어하는 걸 보면 좋은 곳이에요",
        "가정통신문도 꼼꼼하고 부모 소통이 잘 되는 곳이에요",
        "행사나 체험활동이 자주 있어서 아이가 좋아해요",
        "교실도 깨끗하고 시설 관리가 잘 되어 있어요",
        "주변에도 추천했어요 선생님들이 정말 좋아요",
        "아이 성격이 밝아지고 사회성이 많이 좋아졌어요",
        "매일 유치원에서 있었던 이야기를 신나게 해줘요",
        "둘째도 여기 보낼 예정이에요 그만큼 만족합니다",
        "교육 환경이 좋고 놀이 공간도 넓어서 좋아요",
        "아이가 한글이랑 숫자를 여기서 다 배워왔어요",
        "놀이 중심 교육이라 아이가 스트레스 없이 다녀요",
        "아이 정서 발달에 신경 많이 써주셔서 감사해요",
        "적응 기간에 선생님이 사진도 보내주시고 세심했어요",
        "매일 데리러 가면 웃으면서 나와서 안심돼요",
        "형제 자매 다 여기 보냈는데 후회 없어요",
        "선생님이 아이 이름 불러주시면서 반겨줘서 좋아요",
        "놀이터도 있고 텃밭 활동도 해서 아이가 좋아해요",
        "학부모 상담도 정기적으로 해줘서 소통이 잘 돼요",
    )
