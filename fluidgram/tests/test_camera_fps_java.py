"""Java source check for camera FPS selector; not a hardware camera test."""
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from apply_fluidgram import FPS_CLASS

RANGE_STUB = """package android.util;
public final class Range<T extends Comparable<? super T>> {
  private final T low, high;
  public Range(T low, T high) { this.low=low; this.high=high; }
  public T getLower() { return low; }
  public T getUpper() { return high; }
}
"""
JAVA_TEST = """import android.util.Range;
import org.telegram.messenger.camera.FluidCameraFps;
public class CameraFpsCheck {
 @SafeVarargs static Range<Integer>[] ranges(Range<Integer>... values) {return values;}
 static Range<Integer> r(int a,int b){return new Range<Integer>(a,b);}
 static void eq(Range<Integer> actual,int a,int b){
  if(actual==null||actual.getLower()!=a||actual.getUpper()!=b)
   throw new AssertionError("FPS range mismatch");
 }
 public static void main(String[] args){
  eq(FluidCameraFps.choose(ranges(r(15,30),r(30,30),r(30,60))),30,30);
  eq(FluidCameraFps.choose(ranges(r(15,30),r(30,60))),15,30);
  eq(FluidCameraFps.choose(ranges(r(15,24),r(60,60))),15,24);
  eq(FluidCameraFps.choose(ranges(r(24,30))),24,30);
  if(FluidCameraFps.choose(null)!=null)throw new AssertionError("null");
  if(FluidCameraFps.choose(ranges())!=null)throw new AssertionError("empty");
  System.out.println("six assertions passed");
 }
}
"""
class JavaSelectorTest(unittest.TestCase):
 def test_java_fps(self):
  if not shutil.which("javac") or not shutil.which("java"):
   self.skipTest("Java unavailable")
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder)
   files={"android/util/Range.java":RANGE_STUB,
          "org/telegram/messenger/camera/FluidCameraFps.java":FPS_CLASS,
          "CameraFpsCheck.java":JAVA_TEST}
   for filename,content in files.items():
    p=root/filename
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(content,encoding="utf-8")
   compile=subprocess.run(["javac","-d",str(root),*files],cwd=root,capture_output=True,text=True)
   self.assertEqual(compile.returncode,0,compile.stderr)
   run=subprocess.run(["java","-cp",str(root),"CameraFpsCheck"],capture_output=True,text=True)
   self.assertEqual(run.returncode,0,run.stderr)
   self.assertIn("six assertions passed",run.stdout)
