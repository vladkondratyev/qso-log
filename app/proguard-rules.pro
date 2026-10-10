# The country file and the Apps Script are read as Java resources (shared module): R8 keeps resources, no rules needed.
# Line numbers in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations Tink (security-crypto) is compiled against; not needed at run time.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
