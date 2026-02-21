package com.sotti.kindergarten.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "naver.search")
data class NaverSearchProperties(
    val clientId: String,
    val clientSecret: String,
)
