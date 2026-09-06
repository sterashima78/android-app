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
