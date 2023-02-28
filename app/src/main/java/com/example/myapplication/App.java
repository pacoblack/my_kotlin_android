package com.example.myapplication;

import android.app.Application;

import com.sankuai.waimai.router.Router;
import com.sankuai.waimai.router.common.DefaultRootUriHandler;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 创建RootHandler
        DefaultRootUriHandler rootHandler = new DefaultRootUriHandler(this);

        // 初始化
        Router.init(rootHandler);
    }
}
