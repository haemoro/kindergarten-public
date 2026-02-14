package com.sotti.kindergarten.repository

import com.querydsl.core.BooleanBuilder
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
    ): Page<Center> {
        val conditions = buildNativeConditions(filter)

        val idSql =
            """
            SELECT c.id FROM center c
            WHERE ST_DWithin(c.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
            $conditions
            ORDER BY ST_Distance(c.location, ST_MakePoint(:lng, :lat)::geography) ASC
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
        establishType?.let {
            builder.and(qCenter.establishType.eq(it))
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
            SELECT c.id, c.name, c.establish_type,
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
                lat = (row[3] as Number).toDouble(),
                lng = (row[4] as Number).toDouble(),
            )
        }
    }

    // --- Querydsl helpers ---

    private fun BooleanBuilder.applyFilter(filter: CenterSearchFilter): BooleanBuilder {
        if (filter.activeOnly) and(qCenter.isActive.isTrue)
        filter.establishType?.let { and(qCenter.establishType.eq(it)) }
        filter.name?.let { and(qCenter.name.contains(it)) }
        filter.sidoName?.let { and(qCenter.address.containsIgnoreCase(it)) }
        filter.sggName?.let { and(qCenter.address.containsIgnoreCase(it)) }
        return this
    }

    private fun executePagedQuery(
        builder: BooleanBuilder,
        pageable: Pageable,
    ): Page<Center> {
        val ids =
            jpaQueryFactory
                .select(qCenter.id)
                .from(qCenter)
                .where(builder)
                .orderBy(qCenter.updatedAt.desc())
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
                .orderBy(qCenter.updatedAt.desc())
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
            filter.establishType?.let { append(" AND c.establish_type = :establishType") }
            filter.name?.let { append(" AND c.name LIKE :name") }
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
            filter.establishType?.let { setParameter("establishType", it) }
            filter.name?.let { setParameter("name", "%$it%") }
            filter.sidoName?.let { setParameter("sidoName", it) }
            filter.sggName?.let { setParameter("sggName", it) }
        }
}
