package com.example.myapplication;

public class AAB extends AA{
    static{
        System.out.println("class B");
    }
    {
        System.out.println(" B block");
    }
    AAB() {
        System.out.println(" B constructor");
    }

    public static void main(String[] args) {
        new AAB();
    }
}
