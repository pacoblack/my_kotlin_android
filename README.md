# my_kotlin_app
# WMRouter 源码结构  
--- adapter-kotlin-coroutines kotlin协程适配器  
|  
--- adapter-rxjava3 rxjava的适配器，返回一个Observable  
|  
--- compiler 注解处理器  
    |--- PageAnnotationProcessor 处理注解 RouterPage ，将对应的 activity等 注册到handler  
    |--- RegexAnnotationProcessor 处理注解 RouterRegex， 将对应的 activity等 注册到handler  
    |--- ServiceAnnotationProcessor  处理注解 RouterService， 将该service 实现的接口实现class  
    |--- UriAnnotationProcessor 处理注解 RouterUri， 将对应的 activity等 注册到handler  
|  
--- demoapp 示例app  
|  
--- demokotlin kotlin实现的demo  
|  
--- demolib1 service和uri实现示例  
|  
--- demolib2 具体的实现示例  
|  
--- docs 文档  
|  
--- interfaces 定义router相关的逐级  
|  
--- router router的具体实现  
|  
--- router-result-adapter 通过给当前页面注册一个空fragment接受回调  
|   
--- WmPlugin 插件，通过Plugin的Transform 将之前生成的class，注入到相应的位置  
  
# CMakeList 用法  
- 首先肯定是添加ndk  
- 接下来创建并编写 CMakeLists.txt，并把该文件配置到gradle中，同时注意so文件的输出目录  
- 引用的时候在gradle中添加sourceSets指定so所在目录  
- 编写native方法的时候注意包名要匹配，方法名要匹配  