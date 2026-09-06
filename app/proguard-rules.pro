-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.mediapipe.tasks.genai.** { *; }

# androidx.privacysandbox.sdkruntime references optional SDK Sandbox platform APIs.
# The app does not use Privacy Sandbox directly; these guarded compatibility paths
# can remain unresolved while R8 removes unreachable code.
-dontwarn android.app.sdksandbox.SandboxedSdk
-dontwarn android.app.sdksandbox.SdkSandboxController
-dontwarn android.app.sdksandbox.SdkSandboxManager
-dontwarn android.app.sdksandbox.SharedPreferencesSyncManager
-dontwarn android.app.sdksandbox.SharedPreferencesSyncManager$SharedPreferencesKey
