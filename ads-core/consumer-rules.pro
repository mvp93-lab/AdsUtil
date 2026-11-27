# Preserve generic signatures and inner-class metadata for library consumers
-keepattributes Signature,InnerClasses,EnclosingMethod

# Keep ConsentManager listener interface for Java/reflection callers
-keep interface com.mvp93.adsutil.ConsentManager$OnConsentCheckListener { *; }
