package com.mokona.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.mokona.app.data.PixivApi
import com.mokona.app.data.PixivAuth
import com.mokona.app.data.Updates
import com.mokona.app.data.WallpaperPrefs
import com.mokona.app.ui.AppPrefs
import okhttp3.OkHttpClient

class MokonaApp : Application(), SingletonImageLoader.Factory {

	override fun onCreate() {
		super.onCreate()
		instance = this
		AppPrefs.init(this)
		PixivAuth.init(this)
		WallpaperPrefs.init(this)
		Updates.init(this)
	}

	/** Pixiv's image CDN refuses requests without a Pixiv referer; every image load goes through this client. */
	override fun newImageLoader(context: PlatformContext): ImageLoader {
		val client = OkHttpClient.Builder()
			.addInterceptor { chain ->
				chain.proceed(chain.request().newBuilder().header("Referer", PixivApi.IMAGE_REFERER).build())
			}
			.build()
		return ImageLoader.Builder(context)
			.components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
			.crossfade(true)
			.build()
	}

	companion object {
		lateinit var instance: MokonaApp
			private set
		val appContext: Context get() = instance
	}
}
