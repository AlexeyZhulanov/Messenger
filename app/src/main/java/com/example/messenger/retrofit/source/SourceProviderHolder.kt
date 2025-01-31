package com.example.messenger.retrofit.source

import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.retrofit.source.base.RetrofitConfig
import com.example.messenger.retrofit.source.base.RetrofitSourcesProvider
import com.example.messenger.retrofit.source.base.SourcesProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val messengerService: MessengerService,
    private val retrofitServiceProvider: Provider<RetrofitService>
) {

    // Global token sync
    private val tokenMutex = Mutex()
    private val lastTokenUpdate = AtomicLong(0)
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
            val token = settings.getCurrentToken()

            if (token != null) {
                newBuilder.addHeader("Authorization", token)
            }

            val initialResponse = chain.proceed(newBuilder.build())

            if (initialResponse.code == 401) { // If the token has expired
                return@Interceptor runBlocking {
                    tokenMutex.withLock {
                        val now = System.currentTimeMillis()

                        // If the update is already running, we are waiting for it to be completed.
                        if (now - lastTokenUpdate.get() < TOKEN_UPDATE_INTERVAL_MS) {
                            return@runBlocking retryWithNewToken(chain, originalRequest)
                        }

                        lastTokenUpdate.set(now) // Updating the time of the last token update

                        val retrofitService = retrofitServiceProvider.get()

                        // Request a new token
                        val newToken = withContext(Dispatchers.Main) { // Main because the func are wrapped in IO
                            val settingsResponse = messengerService.getSettings()
                            val success = retrofitService.login(settingsResponse.name!!, settingsResponse.password!!)
                            if (success) {
                                    settings.getCurrentToken() // Getting a new token
                            } else {
                                null
                            }
                        }

                        if (newToken != null) {
                            return@runBlocking retryWithNewToken(chain, originalRequest)
                        }
                    }

                    initialResponse // We return the original response if the token could not be updated.
                }
            }

            return@Interceptor initialResponse
        }
    }

    /**
     * Repeats the request with a new token only for 401 error.
     */
    private fun retryWithNewToken(chain: Interceptor.Chain, originalRequest: Request): Response {
        val newToken = appSettings.getCurrentToken() ?: return chain.proceed(originalRequest)

        val newRequest = originalRequest.newBuilder()
            .header("Authorization", newToken)
            .build()

        return chain.proceed(newRequest)
    }

    /**
     * Log requests and responses to LogCat.
     */
    private fun createLoggingInterceptor(): Interceptor {
        return HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.BODY)
    }
}