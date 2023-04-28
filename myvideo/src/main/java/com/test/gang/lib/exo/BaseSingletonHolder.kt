package com.test.gang.lib.exo

open class BaseSingletonHolder<out instanceClass, in paramClass> constructor(val constructor:(paramClass)-> instanceClass) {
    private var mInstance:instanceClass?= null
    fun getInstance(param: paramClass): instanceClass{
        return mInstance ?: synchronized(this){
            mInstance ?: constructor(param).apply { mInstance = this }
        }
    }
}