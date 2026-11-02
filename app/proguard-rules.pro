# 保留行号，方便定位线上崩溃
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WebRTC 相关类（后续接入时使用）
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
