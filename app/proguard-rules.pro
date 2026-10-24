# 见 https://developer.android.com/build/shrink-code
# 目前未开启 R8 混淆，保留此文件以便后续接入。

# kotlinx.serialization 生成的序列化器通过静态引用访问，R8 默认能保留；
# 这里只兜底保住 @Serializable 类的伴生对象。
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
