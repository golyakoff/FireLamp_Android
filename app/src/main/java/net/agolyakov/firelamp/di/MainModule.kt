package net.agolyakov.firelamp.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.agolyakov.firelamp.BuildConfig
import net.agolyakov.firelamp.data.local.EffectCatalogRepository
import net.agolyakov.firelamp.data.local.FireLampPreferences
import net.agolyakov.firelamp.data.local.KnownLamps
import net.agolyakov.firelamp.data.local.LanguagePreferences
import net.agolyakov.firelamp.domain.repository.PreferencesRepository
import net.agolyakov.firelamp.domain.usecase.LoadDeviceWithNameUseCase
import net.agolyakov.firelamp.service.bluetooth.BluetoothAdapterProvider
import net.agolyakov.firelamp.service.bluetooth.BluetoothService
import net.agolyakov.firelamp.data.remote.api.GithubApiService
import net.agolyakov.firelamp.data.remote.interceptors.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MainModule {
    // Region: Context Providers
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    // Region: Bluetooth Dependencies
    @Provides
    @Singleton
    fun provideBluetoothAdapterProvider(
        @ApplicationContext context: Context
    ): BluetoothAdapterProvider = BluetoothAdapterProvider(context)

    @Provides
    @Singleton
    fun provideBluetoothService(
        bluetoothAdapterProvider: BluetoothAdapterProvider,
        effectCatalog: EffectCatalogRepository,
        knownLamps: KnownLamps
    ): BluetoothService = BluetoothService(bluetoothAdapterProvider, effectCatalog, knownLamps)

    // Region: Preferences
    @Provides
    @Singleton
    fun provideFireLampPreferences(
        @ApplicationContext context: Context
    ): FireLampPreferences = FireLampPreferences(context)

    @Provides
    @Singleton
    fun providePreferencesRepository(
        preferences: FireLampPreferences
    ): PreferencesRepository = preferences

    @Provides
    @Singleton
    fun provideLanguagePreferences(
        @ApplicationContext context: Context
    ): LanguagePreferences = LanguagePreferences(context)

    // Region: Network
    @Provides
    @Singleton
    fun provideGitHubToken(): String {
        // Optional: empty unless github.token is set in local.properties
        return BuildConfig.GITHUB_TOKEN
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(token: String): AuthInterceptor {
        return AuthInterceptor(token)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideGitHubApiService(retrofit: Retrofit): GithubApiService =
        retrofit.create(GithubApiService::class.java)

    // Region: Use Cases
    @Provides
    @Singleton
    fun provideLoadDeviceWithNameUseCase(
        preferencesRepository: PreferencesRepository
    ): LoadDeviceWithNameUseCase = LoadDeviceWithNameUseCase(preferencesRepository)
}