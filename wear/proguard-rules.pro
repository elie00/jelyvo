# Jelyvo Wear uses no reflection-based application models.

# NewPipe evaluates YouTube's player JavaScript through Rhino when the lightweight InnerTube
# resolver is challenged. Keep those interpreted symbols intact in release builds.
-keep,allowoptimization class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
