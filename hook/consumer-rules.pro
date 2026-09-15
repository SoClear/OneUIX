-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# Preserve the component name across APK updates.
-keepnames class io.github.soclear.oneuix.RebootActivity
