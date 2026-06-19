# Add project specific ProGuard rules here.

# Keep all public classes
-keep public class * {
    public protected *;
}

# Keep data models
-keep class com.stock.app.model.** { *; }

# Keep custom views
-keep class com.stock.app.view.** { *; }