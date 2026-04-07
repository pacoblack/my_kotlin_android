package com.find.gang.app.toolbox

interface Callback<P,V> {
    @Throws(Exception::class)
    fun call(p: P?): V?
}

interface Callback2<P,V,T> {
    @Throws(Exception::class)
    fun onSuccess(p: P?): V?

    fun onError(t: T?):V?
}