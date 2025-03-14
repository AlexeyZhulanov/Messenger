package com.example.messenger.retrofit.source

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.retrofit.source.base.RetrofitConfig
import com.example.messenger.retrofit.source.base.RetrofitSourcesProvider
import com.example.messenger.retrofit.source.base.SourcesProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SourceProviderHolder @Inject constructor(
    private val appSettings: AppSettings,
    private val retrofitServiceProvider: Provider<RetrofitService>,
    private val context: Context
) {

    // Global token sync
    private val tokenMutex = Mutex()
    private val lastTokenUpdate = AtomicLong(0)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val allowedPathsWithoutToken = setOf(
        "refresh",
        "login",
        "register"
    )

    companion object {
        private const val TOKEN_UPDATE_INTERVAL_MS = 10_000 // 10 sec
        private const val BASE_URL = "https://amessenger.ru"
    }

    @Inject
    lateinit var retrofitService: RetrofitService

    val sourcesProvider: SourcesProvider by lazy {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val config = RetrofitConfig(
            retrofit = createRetrofit(moshi),
            moshi = moshi
        )
        RetrofitSourcesProvider(config)
    }

    /**
     * Create an instance of Retrofit client.
     */
    private fun createRetrofit(moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(createOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    /**
     * Create an instance of OkHttpClient with interceptors for authorization
     * and logging (see [createAuthorizationInterceptor] and [createLoggingInterceptor]).
     */
    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(createAuthorizationInterceptor(appSettings))
            .addInterceptor(createLoggingInterceptor())
            .build()
    }

    /**
     * Add Authorization header to each request if JWT-token exists.
     */
    private fun createAuthorizationInterceptor(settings: AppSettings): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            val newBuilder = originalRequest.newBuilder()
            val accessToken = settings.getCurrentAccessToken()

            if (accessToken != null) {
                newBuilder.addHeader("Authorization", accessToken)
            } else {
                val path = originalRequest.url.encodedPath
                if (path !in allowedPathsWithoutToken) {
                    return@Interceptor runBlocking {
                        val deferred = serviceScope.async {
                            handleRequestWithDelay(chain, originalRequest, settings)
                        }
                        deferred.await()
                    }
                }
            }
            val initialResponse = chain.proceed(newBuilder.build())

            if (initialResponse.code == 401) { // If the token has expired
                return@Interceptor runBlocking {
                    tokenMutex.withLock {
                        val now = System.currentTimeMillis()

                        // If the update is already running, we are waiting for it to be completed.
                        if (now - lastTokenUpdate.get() < TOKEN_UPDATE_INTERVAL_MS) {
                            return@runBlocking retryWithNewToken(chain, originalRequest, settings)
                        }

                        if (settings.isTokenRefreshing() || settings.isTokenRecentlyRefreshed()) {
                            return@runBlocking retryWithNewToken(chain, originalRequest, settings)
                        }

                        lastTokenUpdate.set(now) // Updating the time of the last token update

                        settings.setTokenRefreshing(true)

                        val refreshToken = settings.getCurrentRefreshToken()
                        if (refreshToken == null) {
                            // if refresh token does not exist we are going to auth fragment
                            logout()
                            settings.setTokenRefreshing(false)
                            Log.d("testRefreshError", "refresh token is null, assess=null")
                            return@runBlocking initialResponse
                        }

                        // We reset the access Token before sending the request to /refresh
                        settings.setCurrentAccessToken(null)

                        val retrofitService = retrofitServiceProvider.get()

                        // Request a new token
                        val newAccessToken = try {
                            retrofitService.refreshToken(refreshToken)
                        } catch (e: Exception) {
                            logout()
                            settings.setTokenRefreshing(false)
                            Log.d("testRefreshError", "new access token is null")
                            return@runBlocking initialResponse
                        }

                        settings.setCurrentAccessToken(newAccessToken)
                        settings.setTokenRefreshing(false)
                        return@runBlocking retryWithNewToken(chain, originalRequest, settings)
                    }
                }
            }
            return@Interceptor initialResponse
        }
    }

    /**
     * Repeats the request with a new token only for 401 error.
     */
    private fun retryWithNewToken(chain: Interceptor.Chain, originalRequest: Request, settings: AppSettings): Response {
        val newToken = settings.getCurrentAccessToken() ?: return chain.proceed(originalRequest)

        val newRequest = originalRequest.newBuilder()
            .header("Authorization", newToken)
            .header("Cache-Control", "no-cache") // Отключаем кеширование
            .build()

        return chain.proceed(newRequest)
    }

    private suspend fun handleRequestWithDelay(chain: Interceptor.Chain,
                        originalRequest: Request, settings: AppSettings): Response {
        delay(1000) // Задержка в 1 секунду
        return retryWithNewToken(chain, originalRequest, settings)
    }

    /**
     * Log requests and responses to LogCat.
     */
    private fun createLoggingInterceptor(): Interceptor {
        return HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.BODY)
    }

    private fun logout() {
        appSettings.setCurrentAccessToken(null)
        appSettings.setCurrentRefreshToken(null)
        appSettings.setRemember(false)
        context.sendBroadcast(Intent("com.example.messenger.LOGOUT"))
    }
}