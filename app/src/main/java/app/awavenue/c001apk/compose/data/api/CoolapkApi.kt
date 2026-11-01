package app.awavenue.c001apk.compose.data.api

import app.awavenue.c001apk.compose.data.model.ApiListResponse
import app.awavenue.c001apk.compose.data.model.ApiObjectResponse
import app.awavenue.c001apk.compose.data.model.ApiReplyListResponse
import retrofit2.http.GET
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

    /**
     * Hot searches (Flutter: GET /v6/search?type=hotSearch&refresh=1&
     * returnType=all). Not wired to UI yet; kept so search work starts
     * from a verified endpoint.
     */
    @GET("v6/search")
    suspend fun getHotSearch(
        @Query("type") type: String = "hotSearch",
        @Query("refresh") refresh: Int = 1,
        @Query("returnType") returnType: String = "all",
    ): ApiListResponse

    /** Search suggestions (Flutter: GET /v6/search/suggestSearchWordsNew). */
    @GET("v6/search/suggestSearchWordsNew")
    suspend fun getSearchSuggest(
        @Query("searchValue") searchValue: String,
    ): ApiListResponse
}
