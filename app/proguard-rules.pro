# QuickJS (quickjs-kt) calls back into Kotlin through JNI by class/method name.
-keep class com.dokar.quickjs.** { *; }

# Classes decoded from JSON produced by scripts (kotlinx.serialization keeps generated serializers,
# these keep the polymorphic subclasses' names stable).
-keep,includedescriptorclasses class kr.b1ink.scriptwidget.domain.model.**$$serializer { *; }
-keep,includedescriptorclasses class kr.b1ink.scriptwidget.data.**$$serializer { *; }

# Workers created by WorkManager's default factory via reflection.
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
