package com.sotti.kindergarten.service

import com.sotti.kindergarten.dto.FavoriteRequest
import com.sotti.kindergarten.dto.FavoriteResponse
import com.sotti.kindergarten.dto.PageResponse
import com.sotti.kindergarten.entity.Favorite
import com.sotti.kindergarten.exception.CenterNotFoundException
import com.sotti.kindergarten.exception.DuplicateFavoriteException
import com.sotti.kindergarten.exception.FavoriteNotFoundException
import com.sotti.kindergarten.repository.CenterRepository
import com.sotti.kindergarten.repository.FavoriteRepository
import com.sotti.kindergarten.util.toPageResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class FavoriteService(
    private val favoriteRepository: FavoriteRepository,
    private val centerRepository: CenterRepository,
) {
    fun getFavorites(
        deviceId: String,
        page: Int,
        size: Int,
    ): PageResponse<FavoriteResponse> {
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val favoritesPage = favoriteRepository.findAllByDeviceId(deviceId, pageable)

        return favoritesPage.toPageResponse { favorite ->
            FavoriteResponse(
                id = favorite.id!!,
                centerId = favorite.center.id!!,
                centerName = favorite.center.name,
                createdAt = favorite.createdAt!!,
            )
        }
    }

    @Transactional
    fun addFavorite(request: FavoriteRequest): FavoriteResponse {
        // Check if already exists
        if (favoriteRepository.existsByDeviceIdAndCenterId(request.deviceId, request.centerId)) {
            throw DuplicateFavoriteException()
        }

        // Check if center exists
        val center =
            centerRepository
                .findById(request.centerId)
                .orElseThrow { CenterNotFoundException(request.centerId) }

        // Create favorite
        val favorite =
            Favorite(
                deviceId = request.deviceId,
                center = center,
            )

        val saved = favoriteRepository.save(favorite)

        return FavoriteResponse(
            id = saved.id!!,
            centerId = center.id!!,
            centerName = center.name,
            createdAt = saved.createdAt!!,
        )
    }

    @Transactional
    fun removeFavorite(
        id: UUID,
        deviceId: String,
    ) {
        val deletedCount = favoriteRepository.deleteByIdAndDeviceId(id, deviceId)
        if (deletedCount == 0) {
            throw FavoriteNotFoundException()
        }
    }
}
