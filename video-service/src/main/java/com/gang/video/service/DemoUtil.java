package com.gang.video.service;

import android.content.Context;
import androidx.annotation.NonNull;
import com.gang.video.http.HttpEventListener;
import com.gang.video.http.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.offline.DefaultDownloadIndex;
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.ui.DownloadNotificationHelper;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DemoUtil {
    public static final String DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel";
    private static final boolean USE_CRONET_FOR_NETWORKING = true;
    private static final String DOWNLOAD_CONTENT_DIRECTORY = "download_videos";

    private static DataSource.@MonotonicNonNull Factory httpDataSourceFactory;
    private static DataSource.@MonotonicNonNull Factory dataSourceFactory;
    private static @MonotonicNonNull File downloadDirectory;
    private static @MonotonicNonNull Cache downloadCache;
    private static @MonotonicNonNull DatabaseProvider databaseProvider;
    private static @MonotonicNonNull DownloadManager downloadManager;
    private static @MonotonicNonNull DownloadTracker downloadTracker;
    private static @MonotonicNonNull DownloadNotificationHelper downloadNotificationHelper;

    public static RenderersFactory buildRenderersFactory(
            Context context, boolean preferExtensionRenderer) {
        @DefaultRenderersFactory.ExtensionRendererMode
        int extensionRendererMode =
                useExtensionRenderers()
                        ? (preferExtensionRenderer
                        ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                        : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                        : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
        return new DefaultRenderersFactory(context.getApplicationContext())
                .setExtensionRendererMode(extensionRendererMode);
    }

    public static boolean useExtensionRenderers() {
        return true;
    }

    public static synchronized DataSource.Factory getHttpDataSourceFactory(Context context) {
        if (httpDataSourceFactory == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.eventListenerFactory(HttpEventListener.FACTORY);
            builder.connectionPool(new ConnectionPool(5, 2, TimeUnit.MINUTES));
            OkHttpClient client = builder.build();
            httpDataSourceFactory = new OkHttpDataSourceFactory((Call.Factory)client, "DefaultDownloadAgent");
        }
        return httpDataSourceFactory;
    }

    /** Returns a {@link DataSource.Factory}. */
    public static synchronized DataSource.Factory getDataSourceFactory(Context context) {
        if (dataSourceFactory == null) {
            context = context.getApplicationContext();
            DefaultDataSource.Factory upstreamFactory =
                    new DefaultDataSource.Factory(context, getHttpDataSourceFactory(context));
            dataSourceFactory = buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache(context));
        }
        return dataSourceFactory;
    }

    private static CacheDataSource.Factory buildReadOnlyCacheDataSource(
            DataSource.Factory upstreamFactory, Cache cache) {
        return new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheWriteDataSinkFactory(null)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    //本地缓目录
    public static synchronized Cache getDownloadCache(Context context) {
        if (downloadCache == null) {
            File downloadContentDirectory =
                    context.getExternalFilesDirs(DOWNLOAD_CONTENT_DIRECTORY)[0];
            downloadCache =
                    new SimpleCache(
                            downloadContentDirectory, new NoOpCacheEvictor(), getDatabaseProvider(context));
        }
        return downloadCache;
    }

    private static synchronized File getDownloadDirectory(Context context) {
        if (downloadDirectory == null) {
            downloadDirectory = context.getExternalFilesDir(/* type= */ null);
            if (downloadDirectory == null) {
                downloadDirectory = context.getFilesDir();
            }
        }
        return downloadDirectory;
    }

    private static synchronized DatabaseProvider getDatabaseProvider(Context context) {
        if (databaseProvider == null) {
            databaseProvider = new StandaloneDatabaseProvider(context);
        }
        return databaseProvider;
    }

    public static synchronized DownloadTracker getDownloadTracker(Context context) {
        ensureDownloadManagerInitialized(context);
        return downloadTracker;
    }

    public static synchronized DownloadNotificationHelper getDownloadNotificationHelper(
            Context context) {
        if (downloadNotificationHelper == null) {
            downloadNotificationHelper =
                    new DownloadNotificationHelper(context, DOWNLOAD_NOTIFICATION_CHANNEL_ID);
        }
        return downloadNotificationHelper;
    }

    public static synchronized DownloadManager getDownloadManager(Context context) {
        ensureDownloadManagerInitialized(context);
        return downloadManager;
    }

    private static synchronized void ensureDownloadManagerInitialized(Context context) {
        if (downloadManager == null) {
            downloadManager =
                    new DownloadManager(
                            context,
                            getDatabaseProvider(context),
                            getDownloadCache(context),
                            getHttpDataSourceFactory(context),
                            Executors.newFixedThreadPool(/* nThreads= */ 6));
            downloadTracker =
                    new DownloadTracker(context, getHttpDataSourceFactory(context), downloadManager);
        }
    }

    private static synchronized void ensureDownloadManagerInitialized(Context context, int index) {
        if (downloadManager == null) {
            downloadManager = new DownloadManager(
                    context,
                    new DefaultDownloadIndex(getDatabaseProvider(context)),
                    new DefaultDownloaderFactory(
                            new CacheDataSource.Factory()
                                    .setCache(getDownloadCache(context))
                                    .setUpstreamDataSourceFactory(getHttpDataSourceFactory(context)),
                            Executors.newFixedThreadPool(/* nThreads= */ 6))
            );
            downloadTracker =
                    new DownloadTracker(context, getHttpDataSourceFactory(context), downloadManager);
        }
    }

    private DemoUtil() {}

    public static synchronized void setHttpDataSource(@NonNull DataSource.Factory dataSourceFactory) {
        httpDataSourceFactory = dataSourceFactory;
    }
}
