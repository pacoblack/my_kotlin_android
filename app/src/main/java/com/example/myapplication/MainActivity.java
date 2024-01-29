package com.example.myapplication;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.torrent.activities.TorrentActivity;
import com.google.android.exoplayer2.offline.DownloadService;
//import com.test.gang.cmake.Hello;
//import com.test.gang.cmake.NativeDemo;

public class MainActivity extends AppCompatActivity {
    static {
        System.loadLibrary("native-lib");
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WaterFallLayout.init(this);
        findViewById(R.id.jni_btn)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
//                        Toast.makeText(MainActivity.this, NativeDemo.helloFromJNI(), Toast.LENGTH_LONG).show();
//                        Hello hello=new Hello();
//                        hello.test();
                    }
                });
        findViewById((R.id.video_btn))
                .setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                List<MediaItem> mediaItems = new ArrayList<>(2);
//                MediaItem item = new MediaItem.Builder().setUri("http://www.nenu.edu.cn/_upload/article/videos/03/5f/7c999eed42e3aadc413d7f851f0e/0f50b3eb-9285-41d2-ac4d-6cc363651aad_B.mp4").build();
//                mediaItems.add(item);
//                Intent intent = new Intent(MainActivity.this, ExoPlayerActivity.class);
//                intent.putExtra(
//                        IntentUtil.PREFER_EXTENSION_DECODERS_EXTRA,
//                        true);
//                IntentUtil.addToIntent(mediaItems, intent);
//                startActivity(intent);
//                IPlayerActivity.Companion.startActivity(MainActivity.this, "http://www.nenu.edu.cn/_upload/article/videos/03/5f/7c999eed42e3aadc413d7f851f0e/0f50b3eb-9285-41d2-ac4d-6cc363651aad_B.mp4");
                TorrentActivity.Companion.startActivity(MainActivity.this);
            }
        });
//        startDownloadService();
    }
}
