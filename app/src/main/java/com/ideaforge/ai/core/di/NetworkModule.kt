package com.ideaforge.ai.core.di

import android.util.Log
import com.ideaforge.ai.core.constants.AppConstants
import com.ideaforge.ai.core.network.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TAG = "NetworkModule"

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = false
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor { message ->
            val sanitized = message
                .replace(Regex("(?i)(authorization[\"\\s:=]+)[^\\s,]+"), "$1[REDACTED]")
                .replace(Regex("(?i)(token[\"\\s:=]+)[^\\s,]+"), "$1[REDACTED]")
                .replace(Regex("(?i)(api[_-]?key[\"\\s:=]+)[^\\s,]+"), "$1[REDACTED]")
                .replace(Regex("(?i)(Bearer\\s+)[A-Za-z0-9._-]+"), "$1[REDACTED]")
            Log.d("OkHttp", sanitized)
        }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        return OkHttpClient.Builder()
            .connectTimeout(AppConstants.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(AppConstants.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(AppConstants.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor(networkErrorInterceptor())
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun networkErrorInterceptor(): Interceptor {
        return Interceptor { chain ->
            try {
                val request = chain.request()
                val response = chain.proceed(request)
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} for ${request.method} ${request.url}")
                }
                response
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "DNS resolution failed: ${e.message}")
                throw java.io.IOException(
                    "Cannot reach server. Check your internet connection.\n" +
                    "DNS resolution failed for host.\n" +
                    "Possible causes: No internet, Private DNS blocking, Firewall, VPN intercepting",
                    e
                )
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Connection timed out: ${e.message}")
                throw java.io.IOException(
                    "Connection timed out. Server may be slow or unreachable.\n" +
                    "URL: ${chain.request().url}\n" +
                    "Try again or check your network.",
                    e
                )
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                Log.e(TAG, "SSL/TLS handshake failed: ${e.message}")
                throw java.io.IOException(
                    "SSL/TLS error. Cannot establish secure connection.\n" +
                    "Possible causes: Device date/time incorrect, VPN/proxy intercepting TLS, Outdated system certificates",
                    e
                )
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Connection refused: ${e.message}")
                throw java.io.IOException(
                    "Connection refused by server.\n" +
                    "URL: ${chain.request().url}\n" +
                    "Server may be down or blocking your IP.",
                    e
                )
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Network I/O error: ${e.javaClass.simpleName}: ${e.message}")
                throw e
            }
        }
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/v1beta/openai/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}
