package jp.axel.carddownloader

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming


interface ApiService {
    @GET("{id}.jpg")
    @Streaming
    fun downloadCard(@Path("id") id: String): Call<ResponseBody?>?
}