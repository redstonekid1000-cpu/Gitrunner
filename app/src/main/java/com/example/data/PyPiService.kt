package com.example.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class PyPiResponse(
    val info: PyPiInfo
)

@JsonClass(generateAdapter = true)
data class PyPiInfo(
    val name: String,
    val version: String,
    val summary: String? = null,
    val author: String? = null,
    val description: String? = null,
    val home_page: String? = null
)

interface PyPiService {
    @GET("pypi/{package}/json")
    suspend fun getPackageInfo(@Path("package") packageName: String): PyPiResponse
}

object PyPiClient {
    private const val BASE_URL = "https://pypi.org/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val service: PyPiService by lazy {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PyPiService::class.java)
    }
}
