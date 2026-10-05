# kotlinx.serialization: the app only decodes JsonElement, whose serializers ship their own
# keep rules. Navigation routes are @Serializable objects/classes and are covered by the
# library's consumer rules.

# Retrofit service interfaces are kept by Retrofit's consumer rules; suspend functions need
# the continuation's generic signature.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
