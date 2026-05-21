-keep class dev.koyufox.fuckpinning.ModuleMain { *; }
-keep class dev.koyufox.fuckpinning.BuildConfig { *; }

-keepattributes InnerClasses,EnclosingMethod,Signature,*Annotation*
-dontwarn io.github.libxposed.api.**
