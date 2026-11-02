package org.questfreedom.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class PackagePolicyTest {
 @Test public void filterCorrectPackages() {
  assertTrue(PackagePolicy.isMetaPackage("com.oculus.system"));
  assertTrue(PackagePolicy.isMetaPackage("com.meta.shell"));
  assertTrue(PackagePolicy.isMetaPackage("com.facebook.katana"));
  assertFalse(PackagePolicy.isMetaPackage("org.fdroid.fdroid"));
 }
 @Test public void toggleIsViewOnly() {
  assertTrue(PackagePolicy.shouldShow("org.fdroid.fdroid",true));
  assertFalse(PackagePolicy.shouldShow("com.oculus.system",true));
  assertTrue(PackagePolicy.shouldShow("com.oculus.system",false));
 }
}
