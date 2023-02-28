#include "jni.h"

extern "C"
JNIEXPORT jstring  JNICALL
Java_com_test_gang_cmake_NativeDemo_helloFromJNI(JNIEnv *env, jclass jclz) {
    //静态native方法 需要两个参数JNIEnv，以及jclass
    return env->NewStringUTF("Hello From JNI");
}

extern "C"
JNIEXPORT void Java_com_test_gang_cmake_Hello_test(JNIEnv *env, jobject obj) {
    //非静态方法需要JNIEnv以及jobject（java中Hello，class对象引用）
    jclass hello_clazz = env->GetObjectClass(obj);//获取java端 hello.class
    jfieldID fieldId_prop = env->GetFieldID(hello_clazz, "property", "I");//获取属性
    jint prop_int = env->GetIntField(obj, fieldId_prop);//获取java prop默认值

    jmethodID methodId_function = env->GetMethodID(hello_clazz, "function",
                                                   "(ILjava/util/Date;[I)I");//获取对应方法

    //批量传入参数
    jmethodID methodId_func = env->GetMethodID(hello_clazz, "func", "(IDC)Z");
    jvalue * args = new jvalue[3];
    args[0].i = 10L;
    args[1].d = 3.14;
    args[2].c = L'3';//Java 中字符双字节，C++字符为单字节需要编程器宽字节
    env->CallBooleanMethod(obj, methodId_func, 10L, 3.14, L'3');
    delete[] args;
    //直接传值
    env->SetIntField(obj, fieldId_prop, 100L);//设置java类中props的值
    env->CallIntMethod(obj, methodId_function, 11L, NULL, NULL);//调用方法

}
