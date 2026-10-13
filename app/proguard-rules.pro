# CampusConnect release rules
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod

# --- Data models (Room entities, Gson-style reflection, Parcelable) ---------
-keep class com.example.campusconnect.models.** { *; }

# --- BuildConfig is read reflectively (AIConfig Gemini key, CryptoUtils
#     TICKET_HMAC_SECRET). R8 must not rename/strip the class or its fields. ---
-keep class com.example.campusconnect.BuildConfig { *; }

# --- Room -------------------------------------------------------------------
# Room generates *_Impl classes and relies on annotations/entity field names.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# --- Kotlin coroutines / metadata -------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- ZXing (QR ticket generation / scanning) --------------------------------
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**

# --- Enums (RBAC roles etc.) are sometimes valueOf'd by name ----------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Firebase / GMS ---------------------------------------------------------
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
