# Godot Android native code resolves Java/Kotlin methods by their original names via JNI.
# Keep the Godot Android library intact when the application release build runs R8.
-keep class org.godotengine.godot.** { *; }
