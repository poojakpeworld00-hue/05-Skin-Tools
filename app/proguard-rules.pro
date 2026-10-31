# R8 rules for the release build. AGP adds proguard-android-optimize.txt, and every library (AdMob, Firebase, Play,
# Room, Glide, Material, Fossify) and our :ads / :launcher modules bring their own consumer rules, so only what is
# specific to this app goes here.

# Readable Crashlytics stack traces (the Crashlytics plugin uploads the mapping file).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep public class * extends java.lang.Exception

# Gson (ads: house-ad cards): generic types and field annotations survive.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Enums read back from saved names / intents (valueOf) keep their constants.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable / Serializable passed between screens and kept in saved state.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# FCM: the silent rc_sync push service is found through the manifest; keep it whole.
-keep class * extends com.google.firebase.messaging.FirebaseMessagingService

# Custom views inflated from XML by class name.
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-dontwarn org.jetbrains.annotations.**

# Fossify Commons' fingerprint library (reprint) still points at FingerprintManager, which SDK 37 removed. The app
# never opens Fossify's app-lock screen, so the reference is never reached.
-dontwarn android.hardware.fingerprint.FingerprintManager
-dontwarn android.hardware.fingerprint.FingerprintManager$**
