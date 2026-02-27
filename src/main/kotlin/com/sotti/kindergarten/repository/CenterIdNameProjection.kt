package com.sotti.kindergarten.repository

import java.util.UUID

interface CenterIdNameProjection {
    val id: UUID
    val name: String
    val address: String?
}
