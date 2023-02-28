package com.example.myapplication

import kotlin.concurrent.thread


fun aa () {
    thread(start=true) {
        println("running from thread():${Thread.currentThread()}")
    }

}


//fun methodWithSynchronizedBlock() {
//    println("outside of a synchronized block: ${Thread.currentThread()}")
//    synchronized(object) {
//        println("inside a synchronized block: ${Thread.currentThread()}")
//    }
//}
 class AB {
     @Volatile private var running = false

     fun start() {
         running = true
         thread(start = true) {
             while(running) {
                 println("Still running:${Thread.currentThread()}")
             }
         }
     }

     fun stop() {
         running = false
         println("Stopped:${Thread.currentThread()}")
     }

 }

