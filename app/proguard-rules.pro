# R8 规则
# Room 自己带了规则（数据库类、DAO 都会自动保留）。下面是保险起见，
# 把存进数据库的数据类和类型转换器原样保留，免得被改名后读不出旧数据。
-keep class com.shopledger.app.Trip { *; }
-keep class com.shopledger.app.Entry { *; }
-keepclassmembers class com.shopledger.app.** {
    @androidx.room.TypeConverter *;
}

# 崩溃日志里保留行号，方便定位问题
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
