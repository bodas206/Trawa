# Keep Android entry points while allowing release shrinking/obfuscation.
-keep public class com.patrix.app.MainActivity { public <init>(); }
-keep public class com.patrix.app.PatrixVpnService { public <init>(); }
