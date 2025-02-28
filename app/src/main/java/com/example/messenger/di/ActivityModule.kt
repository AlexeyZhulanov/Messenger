package com.example.messenger.di

import com.example.messenger.MainActivity
import com.example.messenger.retrofit.source.Navigator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
abstract class ActivityModule {
    @Binds
    abstract fun bindNavigator(mainActivity: MainActivity): Navigator
}