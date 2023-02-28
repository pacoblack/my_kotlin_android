package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.offline.DownloadService;
import com.test.gang.cmake.Hello;
import com.test.gang.cmake.NativeDemo;
import com.test.gang.video.DemoDownloadService;
import com.test.gang.video.DemoUtil;
import com.test.gang.video.DownloadTracker;
import com.test.gang.video.IntentUtil;
import com.test.gang.video.PlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DownloadTracker.Listener{
    static {
        System.loadLibrary("native-lib");
    }

    private DownloadTracker downloadTracker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        downloadTracker = DemoUtil.getDownloadTracker(/* context= */ this);
        WaterFallLayout.init(this);
        findViewById(R.id.jni_btn)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Toast.makeText(MainActivity.this, NativeDemo.helloFromJNI(), Toast.LENGTH_LONG).show();
                        Hello hello=new Hello();
                        hello.test();
                    }
                });
        findViewById((R.id.video_btn))
                .setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<MediaItem> mediaItems = new ArrayList<>(2);
                MediaItem item = new MediaItem.Builder().setUri("http://www.nenu.edu.cn/_upload/article/videos/03/5f/7c999eed42e3aadc413d7f851f0e/0f50b3eb-9285-41d2-ac4d-6cc363651aad_B.mp4").build();
                mediaItems.add(item);
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra(
                        IntentUtil.PREFER_EXTENSION_DECODERS_EXTRA,
                        true);
                IntentUtil.addToIntent(mediaItems, intent);
                startActivity(intent);
            }
        });
//        startDownloadService();
    }

    /** Start the download service if it should be running but it's not currently. */
    private void startDownloadService() {
        // Starting the service in the foreground causes notification flicker if there is no scheduled
        // action. Starting it in the background throws an exception if the app is in the background too
        // (e.g. if device screen is locked).
        try {
            DownloadService.start(this, DemoDownloadService.class);
        } catch (IllegalStateException e) {
            DownloadService.startForeground(this, DemoDownloadService.class);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        downloadTracker.addListener(this);
    }

    @Override
    public void onStop() {
        downloadTracker.removeListener(this);
        super.onStop();
    }

    @Override
    public void onDownloadsChanged() {

    }
}
