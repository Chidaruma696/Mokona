package com.mokona.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The parts of Pixiv's app API that Mokona reads. Unknown fields are ignored.

@Serializable
data class ImageUrls(
	@SerialName("square_medium") val squareMedium: String = "",
	val medium: String = "",
	val large: String = "",
	val original: String? = null,
)

@Serializable
data class MetaSinglePage(@SerialName("original_image_url") val originalImageUrl: String? = null)

@Serializable
data class MetaPage(@SerialName("image_urls") val imageUrls: ImageUrls = ImageUrls())

@Serializable
data class Tag(val name: String = "", @SerialName("translated_name") val translatedName: String? = null)

@Serializable
data class ProfileImageUrls(val medium: String = "")

@Serializable
data class PixivUser(
	val id: Long = 0,
	val name: String = "",
	val account: String = "",
	@SerialName("profile_image_urls") val profileImageUrls: ProfileImageUrls = ProfileImageUrls(),
	@SerialName("is_followed") val isFollowed: Boolean = false,
)

@Serializable
data class Illust(
	val id: Long = 0,
	val title: String = "",
	val type: String = "illust",
	@SerialName("image_urls") val imageUrls: ImageUrls = ImageUrls(),
	val caption: String = "",
	val user: PixivUser = PixivUser(),
	val tags: List<Tag> = emptyList(),
	@SerialName("create_date") val createDate: String = "",
	@SerialName("page_count") val pageCount: Int = 1,
	val width: Int = 1,
	val height: Int = 1,
	@SerialName("sanity_level") val sanityLevel: Int = 0,
	/** 0 = all ages, 1 = R-18, 2 = R-18G. */
	@SerialName("x_restrict") val xRestrict: Int = 0,
	@SerialName("meta_single_page") val metaSinglePage: MetaSinglePage = MetaSinglePage(),
	@SerialName("meta_pages") val metaPages: List<MetaPage> = emptyList(),
	@SerialName("total_view") val totalView: Long = 0,
	@SerialName("total_bookmarks") val totalBookmarks: Long = 0,
	@SerialName("is_bookmarked") val isBookmarked: Boolean = false,
	@SerialName("is_muted") val isMuted: Boolean = false,
) {
	val isAdult: Boolean get() = xRestrict > 0
	val isAnimated: Boolean get() = type == "ugoira"
	/** Keeps the real proportion of the picture in grids; Pixiv caps the ratio so nothing becomes a sliver. */
	val aspectRatio: Float get() = (width.toFloat() / height.coerceAtLeast(1)).coerceIn(0.4f, 2.5f)
	/** Original files of every page, single-page works first. */
	val originalUrls: List<String>
		get() = if (metaPages.isNotEmpty()) metaPages.mapNotNull { it.imageUrls.original } else listOfNotNull(metaSinglePage.originalImageUrl)
	/** Large previews of every page, for the detail pager. */
	val largeUrls: List<String>
		get() = if (metaPages.isNotEmpty()) metaPages.map { it.imageUrls.large } else listOf(imageUrls.large)
}

@Serializable
data class IllustsPage(val illusts: List<Illust> = emptyList(), @SerialName("next_url") val nextUrl: String? = null)

@Serializable
data class IllustDetail(val illust: Illust)

@Serializable
data class TrendTag(val tag: String = "", @SerialName("translated_name") val translatedName: String? = null, val illust: Illust? = null)

@Serializable
data class TrendTags(@SerialName("trend_tags") val trendTags: List<TrendTag> = emptyList())

@Serializable
data class UserDetail(val user: PixivUser = PixivUser())

/** A ranking mode of Pixiv's app API. R-18 modes exist only while adult content is on. */
data class RankingMode(val id: String, val adult: Boolean) {
	companion object {
		val all = listOf(
			RankingMode("day", false), RankingMode("week", false), RankingMode("month", false),
			RankingMode("day_male", false), RankingMode("day_female", false),
			RankingMode("week_original", false), RankingMode("week_rookie", false),
			RankingMode("day_r18", true), RankingMode("week_r18", true), RankingMode("day_male_r18", true), RankingMode("day_female_r18", true),
		)
	}
}
