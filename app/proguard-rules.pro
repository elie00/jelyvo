### Rules for NewPipeExtractor
# allowoptimization lets R8 inline/merge Rhino internals while still keeping every class and
# member name (NewPipe drives Rhino in interpreted mode). Revert to a plain -keep if YouTube
# stream resolution (the throttling-parameter JS path) ever breaks.
-keep,allowoptimization class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.*
-dontwarn javax.script.*
-dontwarn jdk.dynalink.**
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

### Cast
# CastOptionsProvider is named in an android:value STRING in the manifest, never in an
# android:name attribute, so AGP's manifest-aware keep rules do not see it. Without this, Cast
# works in assembleStandaloneDebug and dies only in the minified release build.
-keep class ca.ilianokokoro.umihi.music.core.cast.CastOptionsProvider { <init>(); }
