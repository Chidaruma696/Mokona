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
	/** Bio; only present in v1/user/detail. */
	val comment: String? = null,
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
	@SerialName("total_comments") val totalComments: Int = 0,
	/** Manga chapters belong to a series. */
	val series: Series? = null,
) {
	val isManga: Boolean get() = type == "manga"
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
	val webUrl: String get() = "https://www.pixiv.net/artworks/$id"
}

@Serializable
data class Series(val id: Long = 0, val title: String = "")

@Serializable
data class SeriesDetail(val id: Long = 0, val title: String = "", val caption: String = "", @SerialName("series_work_count") val workCount: Int = 0, val user: PixivUser = PixivUser())

@Serializable
data class SeriesPage(
	@SerialName("illust_series_detail") val detail: SeriesDetail = SeriesDetail(),
	val illusts: List<Illust> = emptyList(),
	@SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
data class IllustsPage(val illusts: List<Illust> = emptyList(), @SerialName("next_url") val nextUrl: String? = null)

@Serializable
data class IllustDetail(val illust: Illust)

@Serializable
data class TrendTag(val tag: String = "", @SerialName("translated_name") val translatedName: String? = null, val illust: Illust? = null)

@Serializable
data class TrendTags(@SerialName("trend_tags") val trendTags: List<TrendTag> = emptyList())

// ---- users

/** A user in a list: the person plus a few of their latest works. */
@Serializable
data class UserPreview(
	val user: PixivUser = PixivUser(),
	val illusts: List<Illust> = emptyList(),
	@SerialName("is_muted") val isMuted: Boolean = false,
)

@Serializable
data class UsersPage(@SerialName("user_previews") val userPreviews: List<UserPreview> = emptyList(), @SerialName("next_url") val nextUrl: String? = null)

@Serializable
data class Profile(
	val webpage: String? = null,
	@SerialName("twitter_account") val twitterAccount: String? = null,
	@SerialName("twitter_url") val twitterUrl: String? = null,
	@SerialName("background_image_url") val backgroundImageUrl: String? = null,
	@SerialName("total_follow_users") val totalFollowUsers: Int = 0,
	@SerialName("total_mypixiv_users") val totalMypixivUsers: Int = 0,
	@SerialName("total_illusts") val totalIllusts: Int = 0,
	@SerialName("total_manga") val totalManga: Int = 0,
	@SerialName("total_illust_bookmarks_public") val totalIllustBookmarksPublic: Int = 0,
	val region: String? = null,
)

@Serializable
data class UserDetail(val user: PixivUser = PixivUser(), val profile: Profile = Profile())

// ---- comments

@Serializable
data class ParentComment(val id: Long = 0, val comment: String? = null, val user: PixivUser? = null)

@Serializable
data class Comment(
	val id: Long = 0,
	val comment: String = "",
	val date: String = "",
	val user: PixivUser = PixivUser(),
	@SerialName("parent_comment") val parentComment: ParentComment? = null,
	@SerialName("has_replies") val hasReplies: Boolean = false,
)

@Serializable
data class CommentsPage(val comments: List<Comment> = emptyList(), @SerialName("next_url") val nextUrl: String? = null, @SerialName("total_comments") val totalComments: Int = 0)

// ---- ugoira (Pixiv's animated works: a zip of frames plus a delay per frame)

@Serializable
data class UgoiraFrame(val file: String = "", val delay: Int = 100)

@Serializable
data class ZipUrls(val medium: String = "")

@Serializable
data class UgoiraMetadata(@SerialName("zip_urls") val zipUrls: ZipUrls = ZipUrls(), val frames: List<UgoiraFrame> = emptyList())

@Serializable
data class UgoiraResponse(@SerialName("ugoira_metadata") val ugoiraMetadata: UgoiraMetadata = UgoiraMetadata())

// ---- bookmarks and search helpers

@Serializable
data class BookmarkTag(val name: String = "", val count: Int = 0)

@Serializable
data class BookmarkTagsPage(@SerialName("bookmark_tags") val bookmarkTags: List<BookmarkTag> = emptyList(), @SerialName("next_url") val nextUrl: String? = null)

@Serializable
data class AutocompleteResponse(val tags: List<Tag> = emptyList())

/** A ranking mode of Pixiv's app API. R-18 modes exist only while adult content is on. */
data class RankingMode(val id: String, val adult: Boolean) {
	companion object {
		val all = listOf(
			RankingMode("day", false), RankingMode("week", false), RankingMode("month", false),
			RankingMode("day_male", false), RankingMode("day_female", false),
			RankingMode("week_original", false), RankingMode("week_rookie", false),
			RankingMode("day_manga", false), RankingMode("week_manga", false), RankingMode("month_manga", false), RankingMode("week_rookie_manga", false),
			RankingMode("day_r18", true), RankingMode("week_r18", true), RankingMode("day_male_r18", true), RankingMode("day_female_r18", true),
			RankingMode("day_r18_manga", true), RankingMode("week_r18_manga", true),
		)
	}
}

/** Order of the "newest" section. Popular order is not a sort here: it is the other section of the search, see PixivApi.searchPopular. */
enum class SearchSort(val id: String) { DATE_DESC("date_desc"), DATE_ASC("date_asc") }
enum class SearchTarget(val id: String) { PARTIAL_TAGS("partial_match_for_tags"), EXACT_TAGS("exact_match_for_tags"), TITLE_CAPTION("title_and_caption") }
enum class SearchDuration(val id: String?) { ALL(null), LAST_DAY("within_last_day"), LAST_WEEK("within_last_week"), LAST_MONTH("within_last_month") }
enum class Restrict(val id: String) { PUBLIC("public"), PRIVATE("private") }
