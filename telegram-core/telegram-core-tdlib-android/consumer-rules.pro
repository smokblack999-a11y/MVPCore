# TDLib JSONJava is loaded by JNI and the bridge accesses its static methods reflectively.
-keep class org.drinkless.tdlib.JsonClient { *; }
