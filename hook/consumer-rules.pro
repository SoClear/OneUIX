-keep class io.github.soclear.oneuix.hook.Main {
    <init>();
}

# Preserve the component name across APK updates.
-keepnames class io.github.soclear.oneuix.RebootActivity

-keep class androidx.datastore.DataStoreFile {
    public static java.io.File dataStoreFile(android.content.Context, java.lang.String);
}

-keep class androidx.datastore.core.DeviceProtectedDataStoreFile {
    public static java.io.File deviceProtectedDataStoreFile(android.content.Context, java.lang.String);
}
