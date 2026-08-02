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

### In-app updater
# R8 8.x can move the generated serialization/accessor code across class boundaries while leaving
# GithubReleaseResponse.Companion private. The resulting release-only IllegalAccessError prevents
# old installs from even parsing releases/latest. These DTOs are tiny; keep them intact so the
# recovery path cannot be optimized away again.
-keep class ca.ilianokokoro.umihi.music.models.dto.GithubReleaseResponse { *; }
-keep class ca.ilianokokoro.umihi.music.models.dto.GithubReleaseResponse$Companion { *; }
-keep class ca.ilianokokoro.umihi.music.models.dto.AssetsResponse { *; }
