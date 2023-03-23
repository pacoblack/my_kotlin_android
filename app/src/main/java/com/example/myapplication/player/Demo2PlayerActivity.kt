package com.example.myapplication.player

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media2.exoplayer.external.ExoPlayerFactory
import androidx.media2.exoplayer.external.source.ExtractorMediaSource
import androidx.media2.exoplayer.external.upstream.DefaultDataSourceFactory
import com.example.myapplication.R
import com.google.android.exoplayer2.source.ClippingMediaSource
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector

class Demo2PlayerActivity:AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player_demo_)
        val player = ExoPlayerFactory.newSimpleInstance(this, DefaultTrackSelector())
        val defaultDataSourceFactory = DefaultDataSourceFactory(this, "audio/mpeg") //  userAgent -> audio/mpeg  不能为空
        val concatenatingMediaSource = ConcatenatingMediaSource() //创建一个媒体连接源
        val mediaSource1 = ExtractorMediaSource.Factory(defaultDataSourceFactory)
            .createMediaSource(Uri.parse("http://xiaxiayige.u.qiniudn.com/Big%20Big%20World.mp3")) //创建一个播放数据源

        val mediaSource2 = ExtractorMediaSource.Factory(defaultDataSourceFactory)
            .createMediaSource(Uri.parse("http://xiaxiayige.u.qiniudn.com/Let%20It%20Go%20%281%29.mp3")) //创建一个播放数据源

        concatenatingMediaSource.addMediaSource(mediaSource1)

        val clippingMediaSource= ClippingMediaSource(concatenatingMediaSource,60*1000*1000*3,10*1000*1000)

        player.playWhenReady = true
        player.prepare(clippingMediaSource)


    }
}