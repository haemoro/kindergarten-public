package com.sotti.kindergarten.client.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverSearchResponse(
    val lastBuildDate: String? = null,
    val total: Int = 0,
    val start: Int = 1,
    val display: Int = 10,
    val items: List<NaverSearchItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NaverSearchItem(
    val title: String = "",
    val link: String = "",
    val description: String = "",
    val bloggername: String? = null,
    val bloggerlink: String? = null,
    val cafename: String? = null,
    val cafeurl: String? = null,
    val postdate: String? = null,
)
