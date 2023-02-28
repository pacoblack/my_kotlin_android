package com.test.gang.cmake;

import android.util.Log;

import java.util.Date;

//非静态native方法
public class Hello {
    public int property;

    public int function(int foo, Date date, int[] arr) {
        System.out.println("function");
        Log.d("Yxjie", "jni输入数字：" + foo);
        return foo;
    }

    public boolean func(int a, double b, char c) {
        System.out.println("Hello.func " +a + ", " + b + ", "+c);
        return true;
    }

    public native void test();
}
