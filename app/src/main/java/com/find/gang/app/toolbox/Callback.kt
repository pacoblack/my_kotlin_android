package com.find.gang.app.toolbox

interface Callback<P,V> {
    @Throws(Exception::class)
    fun call(p: P?): V?
}