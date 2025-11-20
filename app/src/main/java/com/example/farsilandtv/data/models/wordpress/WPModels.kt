package com.example.farsilandtv.data.models.wordpress

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * WordPress API Response Models
 * These models match the JSON structure from Farsiland.com WordPress REST API
 * Base URL: https://farsiland.com/wp-json/wp/v2/
 */

@JsonClass(generateAdapter = true)
data class WPTitle(
    @Json(name = "rendered")
    val rendered: String // HTML-decoded title
)

@JsonClass(generateAdapter = true)
data class WPContent(
    @Json(name = "rendered")
    val rendered: String // HTML content (description)
)

@JsonClass(generateAdapter = true)
data class WPMovie(
    @Json(name = "id")
    val id: Int,

    @Json(name = "title")
    val title: WPTitle,

    @Json(name = "content")
    val content: WPContent? = null,

    @Json(name = "link")
    val link: String, // URL to movie page for scraping

    @Json(name = "featured_media")
    val featuredMedia: Int, // Poster image ID

    @Json(name = "genres")
    val genres: List<Int> = emptyList(), // Genre IDs

    @Json(name = "date")
    val date: String,

    @Json(name = "modified")
    val modified: String?,

    @Json(name = "modified_gmt")
    val modifiedGmt: String?,

    @Json(name = "acf")
    val acf: Map<String, Any>? = null, // Custom fields (may contain year, rating, etc.)

    // AUDIT FIX: Embedded media data when using _embed=true parameter
    // Eliminates N+1 network queries by including media in main response
    @Json(name = "_embedded")
    val embedded: WPEmbedded? = null
)

@JsonClass(generateAdapter = true)
data class WPTvShow(
    @Json(name = "id")
    val id: Int,

    @Json(name = "title")
    val title: WPTitle,

    @Json(name = "content")
    val content: WPContent? = null,

    @Json(name = "link")
    val link: String, // URL to series page for scraping episodes

    @Json(name = "featured_media")
    val featuredMedia: Int, // Poster image ID

    @Json(name = "genres")
    val genres: List<Int> = emptyList(),

    @Json(name = "date")
    val date: String,

    @Json(name = "modified")
    val modified: String?,

    @Json(name = "modified_gmt")
    val modifiedGmt: String?,

    @Json(name = "acf")
    val acf: Map<String, Any>? = null,

    // AUDIT FIX: Embedded media data when using _embed=true parameter
    @Json(name = "_embedded")
    val embedded: WPEmbedded? = null
)

@JsonClass(generateAdapter = true)
data class WPEpisode(
    @Json(name = "id")
    val id: Int,

    @Json(name = "title")
    val title: WPTitle,

    @Json(name = "content")
    val content: WPContent? = null,

    @Json(name = "link")
    val link: String, // URL to episode page for scraping video URLs

    @Json(name = "featured_media")
    val featuredMedia: Int, // Episode thumbnail

    @Json(name = "date")
    val date: String,

    @Json(name = "modified")
    val modified: String?,

    @Json(name = "modified_gmt")
    val modifiedGmt: String?,

    @Json(name = "acf")
    val acf: Map<String, Any>? = null, // May contain season/episode numbers

    // AUDIT FIX: Embedded media data when using _embed=true parameter
    @Json(name = "_embedded")
    val embedded: WPEmbedded? = null
)

@JsonClass(generateAdapter = true)
data class WPMedia(
    @Json(name = "id")
    val id: Int,

    @Json(name = "source_url")
    val sourceUrl: String, // Direct image URL

    @Json(name = "media_details")
    val mediaDetails: WPMediaDetails? = null
)

@JsonClass(generateAdapter = true)
data class WPMediaDetails(
    @Json(name = "width")
    val width: Int,

    @Json(name = "height")
    val height: Int,

    @Json(name = "file")
    val file: String,

    @Json(name = "sizes")
    val sizes: Map<String, WPMediaSize>? = null
)

@JsonClass(generateAdapter = true)
data class WPMediaSize(
    @Json(name = "source_url")
    val sourceUrl: String,

    @Json(name = "width")
    val width: Int,

    @Json(name = "height")
    val height: Int
)

@JsonClass(generateAdapter = true)
data class WPGenre(
    @Json(name = "id")
    val id: Int,

    @Json(name = "name")
    val name: String,

    @Json(name = "slug")
    val slug: String
)

/**
 * AUDIT FIX: Embedded data container for WordPress _embed parameter
 * When using ?_embed=true, WordPress includes related resources inline
 * This eliminates the need for separate N+1 network requests
 */
@JsonClass(generateAdapter = true)
data class WPEmbedded(
    @Json(name = "wp:featuredmedia")
    val featuredMedia: List<WPMedia>? = null
)
