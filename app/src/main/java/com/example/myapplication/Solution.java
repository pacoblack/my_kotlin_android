package com.example.myapplication;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

class Solution {
    public int solution(int[] A, int X, int Y, int Z) {
        if (A == null || A.length == 0) {
            return 0;
        }
        if (A.length == 1) {
            if (A[0] <=X || A[0] <= Y || A[0] <= Z){
                return 0;
            } else {
                return -1;
            }
        }
        if (X < Y) {
            int temp = X;
            X = Y;
            Y = temp;
        }
        if (X < Z) {
            int temp = X;
            X = Z;
            Z = temp;
        }

        if (Y < Z) {
            int temp = Y;
            Y = Z;
            Z = temp;
        }

        if (A.length == 2) {
            if (A[0] < A[1]) {
                int temp = A[0];
                A[0] = A[1];
                A[1] = temp;
            }

            if (X >= A[0] && Y >= A[1]){
                return 0;
            } else {
                return -1;
            }
        }

        return enqueue(A, 0, X, Y, Z, X, Y, Z, -1, -1, -1, false ,-1);
    }

    private int enqueue(int[] A, int start, int X, int Y, int Z, int x, int y, int z, int indexX, int indexY, int indexZ, boolean blocked ,int choose) {
        if (start == A.length) {
            return findMax(x-X - (indexX >= 0 ? A[indexX] : 0), y -Y - (indexY >= 0 ? A[indexY] : 0), z-Z- (indexZ >= 0 ? A[indexZ] : 0));
        }

        int coastX= -1;
        int coastY= -1;
        int coastZ= -1;
        if (!isBlocked()) {
            if (A[start] <= X) {
                coastX = enqueue(A, start + 1, X - A[start], Y, Z, x, y, z, start, indexY, indexZ, blocked, choose);
            } else if (A[start] <= Y) {
                coastY = enqueue(A, start + 1, X, Y - A[start], Z, x, y, z, indexX, start, indexZ, blocked, choose);
            } else if (A[start] <= Z) {
                coastZ = enqueue(A, start + 1, X, Y, Z - A[start], x, y, z, indexX, indexY, start, blocked, choose);
            } else {
                return -1;
            }
        } else {

        }

        return findMin(coastX,coastY,coastZ);
    }

    private boolean isBlocked(){
        return false;
    }

    private int findMax(int x, int y, int z) {
        int a = x > y ? x : y;
        int b = y > z ? y : z;
        return a > b ? a : b;
    }

    private int findMin(int x, int y, int z) {
        if (x == -1) {
            return findMin(y, z);
        }
        if (y == -1) {
            return  findMin(x, z);
        }
        if (z == -1) {
            return findMin(x, y);
        }
        return -1;
    }

    private int findMin(int x, int y) {
        if (x != -1 && y != -1) {
            return x < y ? x : y;
        } else {
            if (x > 0) return x;
            if (y > 0) return y;
            return -1;
        }
    }


    public static void main(String[] args) {
        System.out.println(new Solution().solution(new int[]{2,8,4,3,2}, 7, 11, 3));
        Observable.just("a")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
            @Override
            public void accept(String s) throws Exception {
                System.out.println(s);
            }
        });
    }

    OkHttpClient client = new OkHttpClient();

    String run(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
}