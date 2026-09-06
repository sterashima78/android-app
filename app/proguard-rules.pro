# LiteRT-LM 0.16.1 JNI looks up these JVM classes and members by exact name.
# Re-review this block when upgrading LiteRT-LM; avoid package-wide keep so R8 can
# remove Kotlin APIs that are not used by this app or the native bridge.
-keep,allowoptimization class com.google.ai.edge.litertlm.LiteRtLmJniException { *; }

-keep,allowoptimization class com.google.ai.edge.litertlm.BenchmarkInfo
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.BenchmarkInfo {
    <init>(double,double,int,int,double,double);
}

-keep,allowoptimization class com.google.ai.edge.litertlm.InputData$Text
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.InputData$Text {
    java.lang.String getText();
}
-keep,allowoptimization class com.google.ai.edge.litertlm.InputData$Audio
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.InputData$Audio {
    byte[] getBytes();
}
-keep,allowoptimization class com.google.ai.edge.litertlm.InputData$Image
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.InputData$Image {
    byte[] getBytes();
}

-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.SamplerConfig {
    int getTopK();
    double getTopP();
    double getTemperature();
    int getSeed();
}
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.ThinkingConfig {
    boolean getEnableThinking();
    int getThinkingTokenBudget();
}
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.RepetitionPenaltyConfig {
    java.lang.Float getRepetitionPenalty();
    java.lang.Float getPresencePenalty();
    java.lang.Float getFrequencyPenalty();
    java.lang.Integer getWindowSize();
}
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.NoRepeatNgramConfig {
    java.lang.Integer getNoRepeatNgramSize();
    java.lang.Integer getWindowSize();
}
-keepclassmembers,allowoptimization class com.google.ai.edge.litertlm.SuppressTokensConfig {
    int[] getSuppressTokensArray();
}

-keep,allowoptimization interface com.google.ai.edge.litertlm.LiteRtLmJni$JniInferenceCallback {
    void onNext(java.lang.String);
    void onDone();
    void onError(int, java.lang.String);
}
-keepclassmembers,allowoptimization class * implements com.google.ai.edge.litertlm.LiteRtLmJni$JniInferenceCallback {
    void onNext(java.lang.String);
    void onDone();
    void onError(int, java.lang.String);
}
-keep,allowoptimization interface com.google.ai.edge.litertlm.LiteRtLmJni$JniMessageCallback {
    void onMessage(java.lang.String);
    void onDone();
    void onError(int, java.lang.String);
}
-keepclassmembers,allowoptimization class * implements com.google.ai.edge.litertlm.LiteRtLmJni$JniMessageCallback {
    void onMessage(java.lang.String);
    void onDone();
    void onError(int, java.lang.String);
}

# androidx.privacysandbox.sdkruntime references optional SDK Sandbox platform APIs.
# The app does not use Privacy Sandbox directly; these guarded compatibility paths
# can remain unresolved while R8 removes unreachable code.
-dontwarn android.app.sdksandbox.SandboxedSdk
-dontwarn android.app.sdksandbox.SdkSandboxController
-dontwarn android.app.sdksandbox.SdkSandboxManager
-dontwarn android.app.sdksandbox.SharedPreferencesSyncManager
-dontwarn android.app.sdksandbox.SharedPreferencesSyncManager$SharedPreferencesKey

# MBassador's optional expression-language filter is not used by SMBJ's handlers.
-dontwarn javax.el.BeanELResolver
-dontwarn javax.el.ELContext
-dontwarn javax.el.ELResolver
-dontwarn javax.el.ExpressionFactory
-dontwarn javax.el.FunctionMapper
-dontwarn javax.el.ValueExpression
-dontwarn javax.el.VariableMapper

# SMBJ's optional GSS/Kerberos authenticator is not used. The app authenticates
# with AuthenticationContext(username, password, domain), which uses the NTLM path.
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
