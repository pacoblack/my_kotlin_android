package com.find.gang.app.base

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable

abstract class BaseViewModel : ViewModel() {

    private val compositeDisposable = CompositeDisposable()

    // 通用Loading状态
    private val _isLoading = MutableLiveData<Boolean>()
    open val isLoading: LiveData<Boolean> = _isLoading

    // 通用错误信息
    private val _error = MutableLiveData<String>()
    open val error: LiveData<String?> = _error

    protected fun addDisposable(disposable: Disposable) {
        compositeDisposable.add(disposable)
    }

    protected fun setLoading(loading: Boolean) {
        _isLoading.postValue(loading)
    }

    protected fun setError(errorMessage: String) {
        _error.postValue(errorMessage)
    }

    override fun onCleared() {
        compositeDisposable.clear()
        super.onCleared()
    }
}