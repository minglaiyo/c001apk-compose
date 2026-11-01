package app.awavenue.c001apk.compose.data.api

import app.awavenue.c001apk.compose.data.model.ApiListResponse
import app.awavenue.c001apk.compose.data.model.ApiObjectResponse
import app.awavenue.c001apk.compose.data.model.ApiReplyListResponse
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Coolapk API surface ported from the Flutter app's api.dart /
 * network_repo.dart. Base URL is https://api.coolapk.com (the Flutter
 * URL_API_SERVICE); endpoints on the api2 host (api2.coolapk.com) use
 * absolute URLs, as the reply list does.
 *
 * Pagination follows the Flutter list controllers: pages start at 1 and
 * each follow-up page may pass the previous page's last item id as
 * `lastItem` (firstItem is used for pull-to-refresh-newer flows).
 */
interface CoolapkApi {

    /**
     * Generic page list. The Flutter home tabs call this with the tab's
     * page URL; the skeleton's default tab is the hot ranking tab.
     * Example: url = "/page?url=V9_HOME_TAB_RANKING".
     */
    @GET("v6/page/dataList")
    suspend fun getDataList(
        @Query("url") url: String,
        @Query("title") title: String = "",
        @Query("subTitle") subTitle: String = "",
        @Query("page") page: Int,
        @Query("firstItem") firstItem: String? = null,
        @Query("lastItem") lastItem: String? = null,
    ): ApiListResponse

    /** Single feed detail (Flutter: GET /v6/feed/detail?id=). */
    @GET("v6/feed/detail")
    suspend fun getFeedDetail(
        @Query("id") id: String,
    ): ApiObjectResponse

    /**
     * Reply list of one feed (Flutter: GET api2 /v6/feed/replyList).
     * Absolute URL: this endpoint lives on api2, which overrides the
     * client's api base URL. Defaults mirror the Flutter feed
     * controller (listType lastupdate_desc, discussMode 1, feedType
     * feed, blockStatus 0, fromFeedAuthor 0).
     */
    @GET("https://api2.coolapk.com/v6/feed/replyList")
    suspend fun getFeedReply(
        @Query("id") id: String,
        @Query("listType") listType: String = "lastupdate_desc",
        @Query("page") page: Int,
        @Query("firstItem") firstItem: String? = null,
        @Query("lastItem") lastItem: String? = null,
        @Query("discussMode") discussMode: Int = 1,
        @Query("feedType") feedType: String = "feed",
        @Query("blockStatus") blockStatus: Int = 0,
        @Query("fromFeedAuthor") fromFeedAuthor: Int = 0,
    ): ApiReplyListResponse

    /** Product detail (Flutter: GET /v6/product/detail?id=). */
    @GET("v6/product/detail")
    suspend fun getProductDetail(
        @Query("id") id: String,
    ): ApiObjectResponse

    /** Topic detail (Flutter: GET /v6/topic/newTagDetail?tag=). */
    @GET("v6/topic/newTagDetail")
    suspend fun getTopicDetail(
        @Query("tag") tag: String,
    ): ApiObjectResponse

    /**
     * Hot searches (Flutter: GET /v6/search?type=hotSearch&refresh=1&
     * returnType=all). Returns the raw envelope: the hot payload is
     * nested card containers (hotSearch / searchHotListCard with
     * recursive `entities`), not feed items, so the screen flattens
     * it itself instead of parsing it as a feed list.
     */
    @GET("v6/search")
    suspend fun getHotSearch(
        @Query("type") type: String = "hotSearch",
        @Query("refresh") refresh: Int = 1,
        @Query("returnType") returnType: String = "all",
    ): JsonObject

    /** Search suggestions (Flutter: GET /v6/search/suggestSearchWordsNew). */
    @GET("v6/search/suggestSearchWordsNew")
    suspend fun getSearchSuggest(
        @Query("searchValue") searchValue: String,
    ): ApiListResponse

    /**
     * Search results (Flutter: NetworkRepo.getSearch, GET /v6/search).
     * The feed tab (type=feed) returns full feed items, so results
     * reuse FeedItem + FeedCard; the other tabs (apk/game/feedTopic/
     * product/user) render the same rows as AppCard. pageType /
     * pageParam carry scoped search (topic pages pass tag, product
     * pages pass product_phone); null means global search.
     */
    @GET("v6/search")
    suspend fun getSearch(
        @Query("type") type: String,
        @Query("feedType") feedType: String = "all",
        @Query("sort") sort: String = "default",
        @Query("searchValue") searchValue: String,
        @Query("isStrict") isStrict: Int = 0,
        @Query("page") page: Int,
        @Query("firstItem") firstItem: String? = null,
        @Query("lastItem") lastItem: String? = null,
        @Query("showAnonymous") showAnonymous: Int = -1,
        @Query("pageType") pageType: String? = null,
        @Query("pageParam") pageParam: String? = null,
    ): ApiListResponse

    // ---- Write operations (stage 4, batch 1: text only) ----

    /**
     * Creates a feed (Flutter: POST /v6/feed/createFeed). type is
     * "feed", or "comment" for app comments; targetType/targetId
     * carry topic (tag), product (product_phone) or app (apk)
     * association; status is always 1 and publish_status selects
     * visibility (1 = only self, 0 = public). pic is the
     * comma-joined uploaded image URL string (batch 2).
     */
    @FormUrlEncoded
    @POST("v6/feed/createFeed")
    suspend fun createFeed(
        @Field("type") type: String,
        @Field("targetType") targetType: String?,
        @Field("targetId") targetId: String?,
        @Field("message") message: String,
        @Field("status") status: Int = 1,
        @Field("publish_status") publishStatus: Int,
        @Field("pic") pic: String?,
    ): JsonObject

    /**
     * Replies to a feed or a reply (Flutter: POST /v6/feed/reply,
     * query id + type). type is "feed" (reply to a feed) or "reply"
     * (reply to a comment); replyAndForward is 1 for 回复并转发.
     */
    @FormUrlEncoded
    @POST("v6/feed/reply")
    suspend fun reply(
        @Query("id") id: String,
        @Query("type") type: String,
        @Field("message") message: String,
        @Field("replyAndForward") replyAndForward: Int,
        @Field("pic") pic: String?,
    ): JsonObject

    /**
     * Like/unlike, all four sharing the Flutter helper's shape:
     * query id + detail=0, body trace="". Feed likes return the new
     * count inside data.count; reply likes return the count as data.
     */
    @FormUrlEncoded
    @POST("v6/feed/like")
    suspend fun likeFeed(
        @Query("id") id: String,
        @Query("detail") detail: Int = 0,
        @Field("trace") trace: String = "",
    ): JsonObject

    @FormUrlEncoded
    @POST("v6/feed/unlike")
    suspend fun unlikeFeed(
        @Query("id") id: String,
        @Query("detail") detail: Int = 0,
        @Field("trace") trace: String = "",
    ): JsonObject

    @FormUrlEncoded
    @POST("v6/feed/likeReply")
    suspend fun likeReply(
        @Query("id") id: String,
        @Query("detail") detail: Int = 0,
        @Field("trace") trace: String = "",
    ): JsonObject

    @FormUrlEncoded
    @POST("v6/feed/unLikeReply")
    suspend fun unLikeReply(
        @Query("id") id: String,
        @Query("detail") detail: Int = 0,
        @Field("trace") trace: String = "",
    ): JsonObject

    /** Follow/unfollow a user (query uid, same trace body). */
    @FormUrlEncoded
    @POST("v6/user/follow")
    suspend fun followUser(
        @Query("uid") uid: String,
        @Field("trace") trace: String = "",
    ): JsonObject

    @FormUrlEncoded
    @POST("v6/user/unfollow")
    suspend fun unfollowUser(
        @Query("uid") uid: String,
        @Field("trace") trace: String = "",
    ): JsonObject

    /** Follow/unfollow a topic (GET, query tag = topic name). */
    @GET("v6/feed/followTag")
    suspend fun followTag(
        @Query("tag") tag: String,
    ): JsonObject

    @GET("v6/feed/unFollowTag")
    suspend fun unFollowTag(
        @Query("tag") tag: String,
    ): JsonObject

    /**
     * Follow/unfollow a product. Note the inverted semantics:
     * currently followed -> status 0 cancels, otherwise status 1.
     */
    @FormUrlEncoded
    @POST("v6/product/changeFollowStatus")
    suspend fun changeProductFollow(
        @Field("id") id: String,
        @Field("status") status: Int,
    ): JsonObject

    /**
     * Write-operation image captcha (v1, NOT the Netease v2 detail
     * flow): image bytes, then POST /v6/account/requestValidate
     * with type err_request_captcha; success is data == "验证通过".
     */
    @GET("v6/account/captchaImage")
    suspend fun captchaImage(
        @Query("_") timestamp: Long,
        @Query("w") width: Int = 270,
        @Query("h") height: Int = 113,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("v6/account/requestValidate")
    suspend fun requestValidate(
        @Field("type") type: String,
        @Field("code") code: String,
    ): JsonObject
}
