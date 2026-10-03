package cn.tgbug.nekotodo.data

import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface Api {

    @POST("auth/login")
    suspend fun login(@Body body: LoginBody): TokenResponse

    @GET("auth/me")
    suspend fun me(): UserInfo

    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordBody): OkResponse

    @POST("auth/revoke-all")
    suspend fun revokeAll(): OkResponse

    @POST("auth/delete-account")
    suspend fun deleteAccount(@Body body: PasswordBody): OkResponse

    @GET("me/preferences")
    suspend fun preferences(): Preferences

    @PATCH("me/preferences")
    suspend fun patchPreferences(@Body body: JsonObject): Preferences

    @GET("tasks")
    suspend fun listTasks(
        @Query("category") category: String? = null,
        @Query("source_item_id") sourceItemId: Long? = null,
        @Query("completed") completed: Boolean? = null,
    ): List<Task>

    @POST("tasks")
    suspend fun createTask(@Body body: JsonObject): Task

    @GET("tasks/{id}")
    suspend fun getTask(@Path("id") id: Long): Task

    @PATCH("tasks/{id}")
    suspend fun patchTask(@Path("id") id: Long, @Body body: JsonObject): Task

    @DELETE("tasks/{id}")
    suspend fun deleteTask(@Path("id") id: Long): OkResponse

    /** 只在任务自己的截止日组内移动;to_position 是该组内的 1-based 位次。 */
    @POST("tasks/{id}/move")
    suspend fun moveTask(@Path("id") id: Long, @Body body: MoveBody): Task

    @GET("source-infos")
    suspend fun listSourceInfos(): List<SourceInfo>

    @GET("source-infos/{id}")
    suspend fun getSourceInfo(@Path("id") id: Long): SourceInfoDetail

    @Multipart
    @POST("source-infos")
    suspend fun createSourceInfo(
        @Part content: MultipartBody.Part?,
        @Part files: List<MultipartBody.Part>,
    ): DecompositionAccepted

    @PATCH("source-infos/{id}")
    suspend fun patchSourceInfo(@Path("id") id: Long, @Body body: JsonObject): DecompositionAccepted

    @DELETE("source-infos/{id}")
    suspend fun deleteSourceInfo(@Path("id") id: Long): OkResponse

    @GET("runs")
    suspend fun listRuns(): List<Run>

    @GET("runs/{id}")
    suspend fun getRun(@Path("id") id: String): Run
}
