package com.find.gang.myfindapplication.ui.home


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.find.gang.myfindapplication.base.BaseViewModel
import com.find.gang.myfindapplication.network.ApiService
import com.find.gang.myfindapplication.network.RetrofitClient
import com.find.gang.myfindapplication.network.RxSchedulers
import io.reactivex.rxjava3.disposables.Disposable

class HomeViewModel : BaseViewModel() {

    private val apiService: ApiService = RetrofitClient.create(ApiService::class.java)

    private val _articleList = MutableLiveData<List<ArticleItem>>()
    val articleList: LiveData<List<ArticleItem>> = _articleList

    fun loadHomeData() {
        setLoading(true)
        val disposable: Disposable = apiService.getArticleList(1, 10)
            .compose(RxSchedulers.ioToMain())
            .subscribe({ response ->
                setLoading(false)
                if (response.code == 0) {
                    val articleList: List<ArticleItem> = response.data as List<ArticleItem>
                    _articleList.value = response.data.map { article ->
                        ArticleItem(
                            title = article.title,
                            type = determineType(article),
                            url = article.url,
                            isLocal = false
                        )
                    }
                } else {
                    setError(response.msg)
                }
            }, { error ->
                setLoading(false)
                setError(error.message ?: "网络请求失败")
            })
        addDisposable(disposable)
    }

    private fun determineType(article: ArticleItem): String {
        // 根据文章类型判断跳转类型
        return when {
            article.url.contains("video") -> "video"
            else -> "webview"
        }
    }
}

