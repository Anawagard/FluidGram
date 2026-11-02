package org.questfreedom.core;
import java.util.Locale;
/** View filtering only. Does not uninstall or disable any packages. */
public final class PackagePolicy {
 private PackagePolicy() {}
 public static boolean isMetaPackage(String name) {
  if (name == null) return false;
  String p = name.toLowerCase(Locale.ROOT);
  return p.equals("com.meta") || p.startsWith("com.meta.")
    || p.equals("com.oculus") || p.startsWith("com.oculus.")
    || p.equals("com.facebook") || p.startsWith("com.facebook.");
 }
 public static boolean shouldShow(String p, boolean hideMeta) {
  return p != null && !p.isBlank() && (!hideMeta || !isMetaPackage(p));
 }
}
