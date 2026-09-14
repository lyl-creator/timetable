# 保留自研 xls 解析器（反射未使用，仅作保守保留）
-keep class com.lyl.timetable.xls.** { *; }

# Kotlin 元数据
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.**
