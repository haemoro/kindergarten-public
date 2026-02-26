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
        lat: Double?,
        lng: Double?,
    ): Page<SearchListProjection> {
        val conditions = buildNativeConditions(filter)
        val hasLocation = lat != null && lng != null
        val isDistanceSort = pageable.sort.firstOrNull()?.property == "distance"
        val orderBySql =
            if (hasLocation && isDistanceSort) {
                "ST_Distance(c.location, ST_MakePoint(:lng, :lat)::geography)"
            } else {
                getNativeOrderByForFilter(pageable)
            }
        val distanceExpr =
            if (hasLocation) {
                "ST_Distance(c.location, ST_MakePoint(:lng, :lat)::geography) / 1000.0"
            } else {
                "NULL::double precision"
            }

        val sql =
            """
            SELECT c.id, c.name, c.establish_type, c.address, c.phone,
                   ST_Y(c.location::geometry) as lat,
                   ST_X(c.location::geometry) as lng,
                   $distanceExpr as distance_km,
                   c.total_capacity,
                   (COALESCE(c.enrollment3, 0) + COALESCE(c.enrollment4, 0) + COALESCE(c.enrollment5, 0)
                       + COALESCE(c.mixed_enrollment, 0) + COALESCE(c.special_enrollment, 0)) as current_enrollment,
                   (COALESCE(c.class_count3, 0) + COALESCE(c.class_count4, 0) + COALESCE(c.class_count5, 0)
                       + COALESCE(c.mixed_class_count, 0) + COALESCE(c.special_class_count, 0)) as total_class_count,
                   (m.meal_operation_type IS NOT NULL) as meal_provided,
                   (UPPER(b.bus_operating) = 'Y') as bus_available,
                   (a.id IS NOT NULL) as extended_care,
                   COUNT(*) OVER() as total_count
            FROM center c
            LEFT JOIN center_meal m ON m.center_id = c.id
            LEFT JOIN center_bus b ON b.center_id = c.id
            LEFT JOIN center_after_school a ON a.center_id = c.id
            WHERE 1=1
            $conditions
            ORDER BY $orderBySql
            """.trimIndent()

        val query =
            entityManager
                .createNativeQuery(sql)
                .bindFilterParams(filter)
                .apply {
                    if (hasLocation) {
                        setParameter("lat", lat)
                        setParameter("lng", lng)
                    }
                    firstResult = pageable.offset.toInt()
                    maxResults = pageable.pageSize
                }

        return mapSearchResults(query, pageable)
    }

    override fun findNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        filter: CenterSearchFilter,
        pageable: Pageable,
        sortType: String?,
    ): Page<SearchListProjection> {
        val conditions = buildNativeConditions(filter)
        val orderBySql = getNativeOrderBy(sortType)

        val sql =
            """
            SELECT c.id, c.name, c.establish_type, c.address, c.phone,
                   ST_Y(c.location::geometry) as lat,
                   ST_X(c.location::geometry) as lng,
                   ST_Distance(c.location, ST_MakePoint(:lng, :lat)::geography) / 1000.0 as distance_km,
                   c.total_capacity,
                   (COALESCE(c.enrollment3, 0) + COALESCE(c.enrollment4, 0) + COALESCE(c.enrollment5, 0)
                       + COALESCE(c.mixed_enrollment, 0) + COALESCE(c.special_enrollment, 0)) as current_enrollment,
                   (COALESCE(c.class_count3, 0) + COALESCE(c.class_count4, 0) + COALESCE(c.class_count5, 0)
                       + COALESCE(c.mixed_class_count, 0) + COALESCE(c.special_class_count, 0)) as total_class_count,
                   (m.meal_operation_type IS NOT NULL) as meal_provided,
                   (UPPER(b.bus_operating) = 'Y') as bus_available,
                   (a.id IS NOT NULL) as extended_care,
                   COUNT(*) OVER() as total_count
            FROM center c
            LEFT JOIN center_meal m ON m.center_id = c.id
            LEFT JOIN center_bus b ON b.center_id = c.id
            LEFT JOIN center_after_school a ON a.center_id = c.id
            WHERE ST_DWithin(c.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
            $conditions
            ORDER BY $orderBySql
            """.trimIndent()

        val query =
            entityManager
                .createNativeQuery(sql)
                .bindGeoParams(lat, lng, radiusMeters)
                .bindFilterParams(filter)
                .apply {
                    firstResult = pageable.offset.toInt()
                    maxResults = pageable.pageSize
                }

        return mapSearchResults(query, pageable)
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

        return executeAdminPagedQuery(builder, pageable)
    }

    override fun findMapMarkers(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        filter: CenterSearchFilter,
        limit: Int?,
    ): List<MapMarkerProjection> {
        val conditions = buildNativeConditions(filter)
        val limitClause = if (limit != null) "LIMIT $limit" else ""

        val sql =
            """
            SELECT c.id, c.name, c.establish_type, c.address, c.phone,
                   ST_Y(c.location::geometry) as lat,
                   ST_X(c.location::geometry) as lng
            FROM center c
            WHERE ST_DWithin(c.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
            $conditions
            ORDER BY c.location::geometry <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geometry
            $limitClause
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

    override fun findCompareData(
        ids: List<UUID>,
        lat: Double?,
        lng: Double?,
    ): List<CompareProjection> {
        val hasLocation = lat != null && lng != null

        val distanceSelect =
            if (hasLocation) {
                "ST_Distance(c.location, ST_MakePoint(:lng, :lat)::geography) / 1000.0 as distance_km"
            } else {
                "NULL as distance_km"
            }

        val sql =
            """
            SELECT
                c.id,
                c.name,
                c.establish_type,
                c.address,
                $distanceSelect,
                c.total_capacity,
                (COALESCE(c.enrollment3, 0) + COALESCE(c.enrollment4, 0) + COALESCE(c.enrollment5, 0)
                    + COALESCE(c.mixed_enrollment, 0) + COALESCE(c.special_enrollment, 0)) as current_enrollment,
                (COALESCE(t.director_count, 0) + COALESCE(t.vice_director_count, 0)
                    + COALESCE(t.master_teacher_count, 0) + COALESCE(t.lead_teacher_count, 0)
                    + COALESCE(t.general_teacher_count, 0) + COALESCE(t.special_teacher_count, 0)) as teacher_count,
                (COALESCE(c.class_count3, 0) + COALESCE(c.class_count4, 0) + COALESCE(c.class_count5, 0)
                    + COALESCE(c.mixed_class_count, 0) + COALESCE(c.special_class_count, 0)) as class_count,
                (m.meal_operation_type IS NOT NULL) as meal_provided,
                (UPPER(b2.bus_operating) = 'Y') as bus_available,
                (a.id IS NOT NULL) as extended_care,
                bl.building_area,
                cl.classroom_area,
                (UPPER(sc.cctv_installed) = 'Y') as cctv_installed,
                sc.cctv_total,
                c.is_active
            FROM center c
            LEFT JOIN center_teacher t ON t.center_id = c.id
            LEFT JOIN center_meal m ON m.center_id = c.id
            LEFT JOIN center_bus b2 ON b2.center_id = c.id
            LEFT JOIN center_after_school a ON a.center_id = c.id
            LEFT JOIN center_building bl ON bl.center_id = c.id
            LEFT JOIN center_classroom cl ON cl.center_id = c.id
            LEFT JOIN center_safety_check sc ON sc.center_id = c.id
            WHERE c.id IN (:ids)
            """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("ids", ids)
        if (hasLocation) {
            query.setParameter("lat", lat)
            query.setParameter("lng", lng)
        }

        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as List<Array<Any?>>

        return results.map { row ->
            val enrollmentSum = (row[6] as? Number)?.toInt()
            val hasAnyEnrollment =
                enrollmentSum != null && enrollmentSum > 0

            CompareProjection(
                id = row[0] as UUID,
                name = row[1] as String,
                establishType = row[2] as? String,
                address = row[3] as? String,
                distanceKm = (row[4] as? Number)?.toDouble(),
                capacity = (row[5] as? Number)?.toInt(),
                currentEnrollment = if (hasAnyEnrollment) enrollmentSum else null,
                teacherCount = (row[7] as? Number)?.toInt()?.takeIf { it > 0 },
                classCount = (row[8] as? Number)?.toInt()?.takeIf { it > 0 },
                mealProvided = row[9] as? Boolean ?: false,
                busAvailable = row[10] as? Boolean ?: false,
                extendedCare = row[11] as? Boolean ?: false,
                buildingArea = (row[12] as? Number)?.toDouble(),
                classroomArea = (row[13] as? Number)?.toDouble(),
                cctvInstalled = row[14] as? Boolean ?: false,
                cctvTotal = (row[15] as? Number)?.toInt(),
                isActive = row[16] as? Boolean ?: true,
            )
        }
    }

    // --- Shared result mapper ---

    private fun mapSearchResults(
        query: Query,
        pageable: Pageable,
    ): Page<SearchListProjection> {
        @Suppress("UNCHECKED_CAST")
        val results = query.resultList as List<Array<Any?>>

        if (results.isEmpty()) {
            return PageableExecutionUtils.getPage(emptyList(), pageable) { 0L }
        }

        val totalCount = (results[0][14] as Number).toLong()
        val content =
            results.map { row ->
                val enrollment = (row[9] as? Number)?.toInt()
                val classCount = (row[10] as? Number)?.toInt()
                SearchListProjection(
                    id = row[0] as UUID,
                    name = row[1] as String,
                    establishType = row[2] as? String,
                    address = row[3] as? String,
                    phone = row[4] as? String,
                    lat = (row[5] as? Number)?.toDouble(),
                    lng = (row[6] as? Number)?.toDouble(),
                    distanceKm = (row[7] as? Number)?.toDouble(),
                    capacity = (row[8] as? Number)?.toInt(),
                    currentEnrollment = enrollment?.takeIf { it > 0 },
                    totalClassCount = classCount?.takeIf { it > 0 },
                    mealProvided = row[11] as? Boolean ?: false,
                    busAvailable = row[12] as? Boolean ?: false,
                    extendedCare = row[13] as? Boolean ?: false,
                    totalCount = totalCount,
                )
            }

        return PageableExecutionUtils.getPage(content, pageable) { totalCount }
    }

    // --- Admin Querydsl (remains JPA for admin-specific filters) ---

    private fun executeAdminPagedQuery(
        builder: BooleanBuilder,
        pageable: Pageable,
    ): Page<Center> {
        val orderBy = toOrderSpecifiers(pageable)

        val content =
            jpaQueryFactory
                .selectFrom(qCenter)
                .fetchListRelations()
                .where(builder)
                .orderBy(*orderBy)
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()

        if (content.isEmpty()) {
            return PageableExecutionUtils.getPage(emptyList(), pageable) { 0L }
        }

        val countQuery = {
            jpaQueryFactory
                .select(qCenter.count())
                .from(qCenter)
                .where(builder)
                .fetchOne() ?: 0L
        }

        return PageableExecutionUtils.getPage(content, pageable, countQuery)
    }

    private fun toOrderSpecifiers(pageable: Pageable): Array<OrderSpecifier<*>> {
        val specifiers =
            pageable.sort.mapNotNull { order ->
                when (order.property) {
                    "name" -> if (order.isAscending) qCenter.name.asc() else qCenter.name.desc()
                    "totalCapacity" ->
                        if (order.isAscending) qCenter.totalCapacity.asc() else qCenter.totalCapacity.desc()
                    "updatedAt" ->
                        if (order.isAscending) qCenter.updatedAt.asc() else qCenter.updatedAt.desc()
                    "enrollment" ->
                        enrollmentExpression().let { if (order.isAscending) it.asc() else it.desc() }
                    "occupancyRate" ->
                        occupancyRateExpression().let { if (order.isAscending) it.asc() else it.desc() }
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
            """CASE WHEN COALESCE({0}, 0) > 0 THEN 1.0 * (COALESCE({1}, 0) + COALESCE({2}, 0) + COALESCE({3}, 0) + COALESCE({4}, 0) + COALESCE({5}, 0)) / {0} ELSE 0.0 END""",
            qCenter.totalCapacity,
            qCenter.enrollment3,
            qCenter.enrollment4,
            qCenter.enrollment5,
            qCenter.mixedEnrollment,
            qCenter.specialEnrollment,
        )

    private fun JPAQuery<Center>.fetchListRelations(): JPAQuery<Center> =
        this
            .leftJoin(qCenter.meal)
            .fetchJoin()
            .leftJoin(qCenter.bus)
            .fetchJoin()
            .leftJoin(qCenter.afterSchool)
            .fetchJoin()

    // --- Native SQL helpers ---

    private fun getNativeOrderBy(sortType: String?): String =
        when (sortType) {
            "name" -> "c.name ASC"
            "capacity" -> "COALESCE(c.total_capacity, 0) DESC"
            "enrollment" ->
                """(COALESCE(c.enrollment3, 0) + COALESCE(c.enrollment4, 0) + COALESCE(c.enrollment5, 0)
                    + COALESCE(c.mixed_enrollment, 0) + COALESCE(c.special_enrollment, 0)) DESC"""
            "occupancyRate" ->
                """CASE WHEN COALESCE(c.total_capacity, 0) > 0
                    THEN 1.0 * (COALESCE(c.enrollment3, 0) + COALESCE(c.enrollment4, 0)
                    + COALESCE(c.enrollment5, 0) + COALESCE(c.mixed_enrollment, 0)
                    + COALESCE(c.special_enrollment, 0)) / c.total_capacity
                    ELSE 0 END DESC"""
            else -> "c.location::geometry <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geometry"
        }

    private fun getNativeOrderByForFilter(pageable: Pageable): String {
        val order = pageable.sort.firstOrNull() ?: return "c.updated_at DESC"
        return when (order.property) {
            "name" -> "c.name ${if (order.isAscending) "ASC" else "DESC"}"
            "totalCapacity" -> "COALESCE(c.total_capacity, 0) DESC NULLS LAST"
            "updatedAt" -> "c.updated_at ${if (order.isAscending) "ASC" else "DESC"}"
            "enrollment" ->
                """(COALESCE(c.enrollment3, 0) + COALESCE(c.enrollment4, 0) + COALESCE(c.enrollment5, 0)
                    + COALESCE(c.mixed_enrollment, 0) + COALESCE(c.special_enrollment, 0)) DESC"""
            "occupancyRate" ->
                """CASE WHEN COALESCE(c.total_capacity, 0) > 0
                    THEN 1.0 * (COALESCE(c.enrollment3, 0) + COALESCE(c.enrollment4, 0)
                    + COALESCE(c.enrollment5, 0) + COALESCE(c.mixed_enrollment, 0)
                    + COALESCE(c.special_enrollment, 0)) / c.total_capacity
                    ELSE 0 END DESC"""
            else -> "c.updated_at DESC"
        }
    }

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
