package com.example.messenger.retrofit.source

import com.example.messenger.Singletons
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.retrofit.source.base.RetrofitConfig
import com.example.messenger.retrofit.source.base.RetrofitSourcesProvider
import com.example.messenger.retrofit.source.base.SourcesProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object SourceProviderHolder {

    private const val BASE_URL = "https://amessenger.ru" // ;)
    private val messengerService: MessengerService
        get() = Singletons.messengerRepository as MessengerService
    private val retrofitService: RetrofitService
        get() = Singletons.retrofitRepository as RetrofitService

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
            .addInterceptor(createAuthorizationInterceptor(Singletons.appSettings))
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
            val semaphore = Semaphore(1)
            val response: Response

            runBlocking {
                val initialResponse = chain.proceed(newBuilder.build())
                if (initialResponse.code == 401) { // Unauthorized
                    semaphore.acquire()
                    try {
                        // Выполняем асинхронный запрос для обновления токена
                        val job = async(Dispatchers.IO) {
                            val settingsResponse = messengerService.getSettings()
                            val success = retrofitService.login(settingsResponse.name!!, settingsResponse.password!!)
                            if (success) {
                                settings.getCurrentToken() // Получаем новый токен
                            } else {
                                null
                            }
                        }

                        val newToken = job.await()
                        if (newToken != null) {
                            // Создаем новый запрос с обновленным токеном
                            val newRequest = originalRequest.newBuilder()
                                .header("Authorization", newToken)
                                .build()
                            // Повторно отправляем запрос
                            response = chain.proceed(newRequest)
                        } else {
                            response = initialResponse
                        }
                    } finally {
                        semaphore.release()
                    }
                } else {
                    response = initialResponse
                }
            }

            response
        }
    }
    /**
     * Log requests and responses to LogCat.
     */
    private fun createLoggingInterceptor(): Interceptor {
        return HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.BODY)
    }
}