# Keep the JavaScript bridge methods reachable from injected JS in release builds.
-keepclassmembers class com.stever.scribdownloader.engine.AutopilotEngine$JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.stever.scribdownloader.engine.AutopilotEngine$JsBridge { *; }

# Parcelable/Data classes referenced from WebView bridge JSON parsing keep their names.
-keepnames class com.stever.scribdownloader.data.HistoryEntry
