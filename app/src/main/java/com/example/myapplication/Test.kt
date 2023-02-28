package com.example.myapplication
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ticker
import kotlin.system.measureTimeMillis

open class D {
    open fun foo() {
        println("D.foo in self")
    }
}

class D1 : D() { }

open class C {
    open fun D.foo() {
        println("D.foo in C")
    }

    open fun D1.foo() {
        println("D1.foo in C")
    }

    fun caller(d: D) {
        d.foo()   // 调用扩展函数
    }

    fun caller(d: D1) {
        d.foo()   // 调用扩展函数
    }
}

class C1 : C() {
    override fun D.foo() {
        println("D.foo in C1")
    }

    override fun D1.foo() {
        println("D1.foo in C1")
    }
}

class Runoob<in A, out B>(val b: B) {
    fun foo(a: A): B {
        return b
    }
}

fun result(vararg args: String) =
    html {
        head {
            title {+"XML encoding with Kotlin"}
        }
        body {
            h1 {+"XML encoding with Kotlin"}
            p  {+"this format can be used as an alternative markup to XML"}

            // 一个具有属性和文本内容的元素
            a(href = "http://kotlinlang.org") {+"Kotlin"}

            // 混合的内容
            p {
                +"This is some"
                b {+"mixed"}
                +"text. For more see the"
                a(href = "http://kotlinlang.org") {+"Kotlin"}
                +"project"
            }
            p {+"some text"}

            // 以下代码生成的内容
            p {
                for (arg in args)
                    +arg
            }
        }
    }

//fun main() {
//    C().caller(D())   // 输出 "D.foo in C"
//    C1().caller(D())  // 输出 "D.foo in C1" —— 分发接收者虚拟解析
//    C().caller(D1())  // 输出 "D.foo in C" —— 扩展接收者静态解析
//    for (i in 1 until   4 step 2) print(i) // 输出“13”
//}

//fun main() = runBlocking {
//    repeat(100_000) { // 启动大量的协程
//        launch {
//            delay(1000L)
//            println(". ${System.currentTimeMillis()}")
//        }
//    }
//}
//fun main() = runBlocking {
//    GlobalScope.launch {
//        repeat(1000) { i ->
//            println("I'm sleeping $i ...")
//            delay(500L)
//        }
//    }
//    delay(13000L) // 在延迟后退出
//}

//data class Ball(var hits: Int)
//
//fun main() = runBlocking {
//    val table = Channel<Ball>() // 一个共享的 table（桌子）
//    launch { player("ping", table) }
//    launch { player("pong", table) }
//    table.send(Ball(0)) // 乒乓球
//    delay(10000) // 延迟 1 秒钟
//    coroutineContext.cancelChildren() // 游戏结束，取消它们
//}
//
//suspend fun player(name: String, table: Channel<Ball>) {
//    for (ball in table) { // 在循环中接收球
//        ball.hits++
//        println("$name $ball")
//        delay(300) // 等待一段时间
//        table.send(ball) // 将球发送回去
//    }
//}

//fun main() = runBlocking {
//    val result = withTimeoutOrNull(1300L) {
//        repeat(1000) { i ->
//            println("I'm sleeping $i ...")
//            delay(500L)
//        }
//        "Done" // 在它运行得到结果之前取消它
//    }
//    println("Result is $result")
//}

//fun main() = runBlocking<Unit> {
//    val tickerChannel = ticker(delayMillis = 100, initialDelayMillis = 0) //创建计时器通道
//    var nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
//    println("Initial element is available immediately: $nextElement") // 初始尚未经过的延迟
//
//    nextElement = withTimeoutOrNull(50) { tickerChannel.receive() } // 所有随后到来的元素都经过了 100 毫秒的延迟
//    println("Next element is not ready in 50 ms: $nextElement")
//
//    nextElement = withTimeoutOrNull(60) { tickerChannel.receive() }
//    println("Next element is ready in 100 ms: $nextElement")
//
//    // 模拟大量消费延迟
//    println("Consumer pauses for 150ms")
//    delay(150)
//    // 下一个元素立即可用
//    nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
//    println("Next element is available immediately after large consumer delay: $nextElement")
//    // 请注意，`receive` 调用之间的暂停被考虑在内，下一个元素的到达速度更快
//    nextElement = withTimeoutOrNull(60) { tickerChannel.receive() }
//    println("Next element is ready in 50ms after consumer pause in 150ms: $nextElement")
//
//    tickerChannel.cancel() // 表明不再需要更多的元素
//}

// 注意，在这个示例中我们在 `main` 函数的右边没有加上 `runBlocking`
//fun main() {
//    val time = measureTimeMillis {
//        // 我们可以在协程外面启动异步执行
//        val one = somethingUsefulOneAsync()
//        val two = somethingUsefulTwoAsync()
//        // 但是等待结果必须调用其它的挂起或者阻塞
//        // 当我们等待结果的时候，这里我们使用 `runBlocking { …… }` 来阻塞主线程
//        runBlocking {
//            println("The answer is ${one.await() + two.await()}")
//        }
//    }
//    println("Completed in $time ms")
//}
//
//fun somethingUsefulOneAsync() = GlobalScope.async {
//    doSomethingUsefulOne()
//}
//
//fun somethingUsefulTwoAsync() = GlobalScope.async {
//    doSomethingUsefulTwo()
//}
//
//suspend fun doSomethingUsefulOne(): Int {
//    delay(1000L) // 假设我们在这里做了些有用的事
//    return 13
//}
//
//suspend fun doSomethingUsefulTwo(): Int {
//    delay(1000L) // 假设我们在这里也做了些有用的事
//    return 29
//}

val threadLocal = ThreadLocal<String?>() // 声明线程局部变量

fun main() = runBlocking<Unit> {
    threadLocal.set("main")
    println("Pre-main, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
    val job = launch(Dispatchers.Default + threadLocal.asContextElement(value = "launch") ) {
        println("Launch start, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
        yield()
        println("After yield, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
    }
    job.join()
    println("Post-main, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
}