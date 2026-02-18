package com.sotti.kindergarten.repository

import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sotti.kindergarten.entity.Center
import com.sotti.kindergarten.entity.QCenter
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils
import java.util.UUID

class CenterRepositoryImpl(
    private val jpaQueryFactory: JPAQueryFactory,
    private val entityManager: EntityManager,
) : CenterRepositoryCustom {
    private val qCenter = QCenter.center

    private fun parseTypes(establishType: String?): List<String>? =
        establishType
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }

    override fun findAllWithFilters(
        filter: CenterSearchFilter,
        pageable: Pageable,
    ): Page<Center> {
        val builder = BooleanBuilder().applyFilter(filter)
        return executePagedQuery(builder, pageable)
    }

    override fun findNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        filter: CenterSearchFilter,
        pageable: Pageable,
        sortType: String?,
    ): Page<Center> {
        val conditions = buildNativeConditions(filter)
        val orderBySql = getNativeOrderBy(sortType)

        val idSql =
            """
            SELECT c.id FROM center c
            WHERE ST_DWithin(c.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
            $conditions
            ORDER BY $orderBySql
            """.trimIndent()

        val idQuery =
            entityManager
                .createNativeQuery(idSql)
                .bindGeoParams(lat, lng, radiusMeters)
                .bindFilterParams(filter)
                .apply {
                    firstResult = pageable.offset.toInt()
                    maxResults = pageable.pageSize
                }

        @Suppress("UNCHECKED_CAST")
        val ids = idQuery.resultList as List<UUID>

        if (ids.isEmpty()) {
            return PageableExecutionUtils.getPage(emptyList(), pageable) { 0L }
        }

        val idOrder = ids.withIndex().associate { (index, id) -> id to index }
        val content =
            jpaQueryFactory
                .selectFrom(qCenter)
                .fetchAllOneToOne()
                .where(qCenter.id.`in`(ids))
                .fetch()
                .sortedBy { idOrder[it.id] }

        val countSql =
            """
            SELECT COUNT(*) FROM center c
            WHERE ST_DWithin(c.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
            $conditions
            """.trimIndent()

        val countQuery = {
            val cq =
                entityManager
                    .createNativeQuery(countSql)
                    .bindGeoParams(lat, lng, radiusMeters)
                    .bindFilterParams(filter)
            (cq.singleResult as Number).toLong()
        }

        return PageableExecutionUtils.getPage(content, pageable, countQuery)
    }

    override fun findAllWithAdminFilters(
        keyword: String?,
        establishType: String?,
        isVerified: Boolean?,
        isActive: Boolean?,
        pageable: Pageable,
    ): Page<Center> {
        val builder = BooleanBuilder()

        keyword?.let {
            builder.and(
                qCenter.name
                    .contains(it)
                    .or(qCenter.address.contains(it))
                    .or(qCenter.kinderCode.contains(it)),
            )
        }
        parseTypes(establishType)?.let {
            builder.and(qCenter.establishType.`in`(it))
        }
        isVerified?.let {
            builder.and(qCenter.isVerified.eq(it))
        }
        isActive?.let {
            builder.and(qCenter.isActive.eq(it))
        }

        return executePagedQuery(builder, pageable)
    }

    override fun findMapMarkers(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        filter: CenterSearchFilter,
    ): List<MapMarkerProjection> {
        val conditions = buildNativeConditions(filter)

        val sql =
            """
            SELECT c.id, c.name, c.establish_type, c.address, c.phone,
                   ST_Y(c.location::geometry) as lat,
                   ST_X(c.location::geometry) as lng
            FROM center c
            WHERE ST_DWithin(c.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
            $conditions
            ORDER BY ST_Distance(c.location, ST_MakePoint(:lng, :lat)::geography) ASC
            """.trimIndent()

        val query =
            entityManager
                .createNativeQuery(sql)
                .bindGeoParams(lat, lng, radiusMeters)
                .bindFilterParams(filter)

        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as List<Array<Any>>

        return results.map { row ->
            MapMarkerProjection(
                id = row[0] as UUID,
                name = row[1] as String,
                establishType = row[2] as? String,
                address = row[3] as? String,
                phone = row[4] as? String,
                lat = (row[5] as Number).toDouble(),
                lng = (row[6] as Number).toDouble(),
            )
        }
    }

    // --- Querydsl helpers ---

    private fun BooleanBuilder.applyFilter(filter: CenterSearchFilter): BooleanBuilder {
        if (filter.activeOnly) and(qCenter.isActive.isTrue)
        filter.establishTypes?.let { and(qCenter.establishType.`in`(it)) }
        filter.name?.let { and(qCenter.name.contains(it).or(qCenter.address.contains(it))) }
        filter.sidoName?.let { and(qCenter.address.containsIgnoreCase(it)) }
        filter.sggName?.let { and(qCenter.address.containsIgnoreCase(it)) }
        return this
    }

    private fun toOrderSpecifiers(pageable: Pageable): Array<OrderSpecifier<*>> {
        val specifiers =
            pageable.sort.mapNotNull { order ->
                when (order.property) {
                    "name" -> if (order.isAscending) qCenter.name.asc() else qCenter.name.desc()
                    "totalCapacity" -> if (order.isAscending) qCenter.totalCapacity.asc() else qCenter.totalCapacity.desc()
                    "updatedAt" -> if (order.isAscending) qCenter.updatedAt.asc() else qCenter.updatedAt.desc()
                    "enrollment" -> enrollmentExpression().let { if (order.isAscending) it.asc() else it.desc() }
                    "occupancyRate" -> occupancyRateExpression().let { if (order.isAscending) it.asc() else it.desc() }
                    else -> null
                }
            }
        return specifiers.ifEmpty { listOf(qCenter.updatedAt.desc()) }.toTypedArray()
    }

    private fun enrollmentExpression(): NumberExpression<Int> =
        Expressions.numberTemplate(
            Int::class.java,
            "COALESCE({0}, 0) + COALESCE({1}, 0) + COALESCE({2}, 0) + COALESCE({3}, 0) + COALESCE({4}, 0)",
            qCenter.enrollment3,
            qCenter.enrollment4,
            qCenter.enrollment5,
            qCenter.mixedEnrollment,
            qCenter.specialEnrollment,
        )

    private fun occupancyRateExpression(): NumberExpression<Double> =
        Expressions.numberTemplate(
            Double::class.java,
            "CASE WHEN COALESCE({0}, 0) > 0 THEN 1.0 * (COALESCE({1}, 0) + COALESCE({2}, 0) + COALESCE({3}, 0) + COALESCE({4}, 0) + COALESCE({5}, 0)) / {0} ELSE 0.0 END",
            qCenter.totalCapacity,
            qCenter.enrollment3,
            qCenter.enrollment4,
            qCenter.enrollment5,
            qCenter.mixedEnrollment,
            qCenter.specialEnrollment,
        )

    private fun getNativeOrderBy(sortType: String?): String =
        when (sortType) {
            "name" -> "c.name ASC"
            "capacity" -> "COALESCE(c.total_capacity, 0) DESC"
            "enrollment" ->
                "(COALESCE(c.enrollment_3, 0) + COALESCE(c.enrollment_4, 0) + COALESCE(c.enrollment_5, 0) + COALESCE(c.mixed_enrollment, 0) + COALESCE(c.special_enrollment, 0)) DESC"
            "occupancyRate" ->
                "CASE WHEN COALESCE(c.total_capacity, 0) > 0 THEN 1.0 * (COALESCE(c.enrollment_3, 0) + COALESCE(c.enrollment_4, 0) + COALESCE(c.enrollment_5, 0) + COALESCE(c.mixed_enrollment, 0) + COALESCE(c.special_enrollment, 0)) / c.total_capacity ELSE 0 END DESC"
            else -> "ST_Distance(c.location, ST_MakePoint(:lng, :lat)::geography) ASC"
        }

    private fun executePagedQuery(
        builder: BooleanBuilder,
        pageable: Pageable,
    ): Page<Center> {
        val orderBy = toOrderSpecifiers(pageable)

        val ids =
            jpaQueryFactory
                .select(qCenter.id)
                .from(qCenter)
                .where(builder)
                .orderBy(*orderBy)
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()

        if (ids.isEmpty()) {
            return PageableExecutionUtils.getPage(emptyList(), pageable) { 0L }
        }

        val content =
            jpaQueryFactory
                .selectFrom(qCenter)
                .fetchAllOneToOne()
                .where(qCenter.id.`in`(ids))
                .orderBy(*orderBy)
                .fetch()

        val countQuery = {
            jpaQueryFactory
                .select(qCenter.count())
                .from(qCenter)
                .where(builder)
                .fetchOne() ?: 0L
        }

        return PageableExecutionUtils.getPage(content, pageable, countQuery)
    }

    private fun JPAQuery<Center>.fetchAllOneToOne(): JPAQuery<Center> =
        this
            .leftJoin(qCenter.building)
            .fetchJoin()
            .leftJoin(qCenter.classroom)
            .fetchJoin()
            .leftJoin(qCenter.teacher)
            .fetchJoin()
            .leftJoin(qCenter.lessonDay)
            .fetchJoin()
            .leftJoin(qCenter.meal)
            .fetchJoin()
            .leftJoin(qCenter.bus)
            .fetchJoin()
            .leftJoin(qCenter.yearOfWork)
            .fetchJoin()
            .leftJoin(qCenter.environment)
            .fetchJoin()
            .leftJoin(qCenter.safetyCheck)
            .fetchJoin()
            .leftJoin(qCenter.mutualAid)
            .fetchJoin()
            .leftJoin(qCenter.afterSchool)
            .fetchJoin()

    // --- Native SQL helpers ---

    private fun buildNativeConditions(filter: CenterSearchFilter): String =
        buildString {
            if (filter.activeOnly) append(" AND c.is_active = true")
            filter.establishTypes?.let { append(" AND c.establish_type IN (:establishTypes)") }
            filter.name?.let { append(" AND (c.name LIKE :name OR c.address LIKE :name)") }
            filter.sidoName?.let { append(" AND c.address ILIKE '%' || :sidoName || '%'") }
            filter.sggName?.let { append(" AND c.address ILIKE '%' || :sggName || '%'") }
        }

    private fun Query.bindGeoParams(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
    ): Query =
        apply {
            setParameter("lat", lat)
            setParameter("lng", lng)
            setParameter("radiusMeters", radiusMeters)
        }

    private fun Query.bindFilterParams(filter: CenterSearchFilter): Query =
        apply {
            filter.establishTypes?.let { setParameter("establishTypes", it) }
            filter.name?.let { setParameter("name", "%$it%") }
            filter.sidoName?.let { setParameter("sidoName", it) }
            filter.sggName?.let { setParameter("sggName", it) }
        }
}
