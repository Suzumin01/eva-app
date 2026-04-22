package com.eva.app.di

import android.content.Context
import com.eva.app.BuildConfig
import com.eva.app.data.api.RefreshRequest
import com.eva.app.data.local.room.AppDatabase
import com.eva.app.data.local.room.DoctorCacheDao
import dagger.hilt.android.qualifiers.ApplicationContext
import com.eva.app.data.api.EvaApi
import com.eva.app.data.local.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): Interceptor = Interceptor { chain ->
        val token = tokenManager.cachedToken
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    @Provides
    @Singleton
    fun provideAuthenticator(tokenManager: TokenManager): okhttp3.Authenticator =
        okhttp3.Authenticator { _, response ->
            // Не трогаем запросы к /auth/refresh, чтобы не зациклиться
            if (response.request.url.encodedPath.contains("auth/refresh")) return@Authenticator null

            val refreshToken = tokenManager.cachedRefreshToken ?: run {
                runBlocking { tokenManager.clearAuth() }
                tokenManager.emitUnauthorized()
                return@Authenticator null
            }

            val body = """{"refreshToken":"$refreshToken"}"""
                .toRequestBody("application/json".toMediaType())
            val refreshReq = Request.Builder()
                .url("${BuildConfig.BASE_URL}auth/refresh")
                .post(body)
                .build()

            val refreshResp = runCatching {
                OkHttpClient().newCall(refreshReq).execute()
            }.getOrNull()

            if (refreshResp?.isSuccessful == true) {
                val json         = JSONObject(refreshResp.body?.string() ?: return@Authenticator null)
                val newToken     = json.optString("token").takeIf { it.isNotBlank() }
                    ?: return@Authenticator null
                val newRefresh   = json.optString("refreshToken").takeIf { it.isNotBlank() }
                    ?: return@Authenticator null
                runBlocking { tokenManager.saveTokens(newToken, newRefresh) }
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            } else {
                runBlocking { tokenManager.clearAuth() }
                tokenManager.emitUnauthorized()
                null
            }
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: Interceptor,
        authenticator: okhttp3.Authenticator
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(authenticator)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideEvaApi(retrofit: Retrofit): EvaApi =
        retrofit.create(EvaApi::class.java)

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideDoctorCacheDao(db: AppDatabase): DoctorCacheDao = db.doctorCacheDao()
}