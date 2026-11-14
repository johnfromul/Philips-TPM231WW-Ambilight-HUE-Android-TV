from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
class BridgeSwitchGuardTests(unittest.TestCase):
 def test_stable_and_stale_identity(self):
  harness = '''package local.tv.hue;
import java.util.*;
public class GuardHarness {
 static class Fake implements BridgeSwitchGuard.Probe {
  int index;String[] addresses,identities;int samples,waits;
  Fake(String[] a,String[] i){addresses=a;identities=i;}
  public String address(){return addresses[Math.min(index,addresses.length-1)];}
  public String identity(){return identities[Math.min(index,identities.length-1)];}
  public void pause(long ms){waits++;index++;}
  public void event(int attempt,boolean a,boolean i){samples++;}
 }
 static void expectMismatch(Fake p)throws Exception{try{BridgeSwitchGuard.verify(p,"new","B");throw new AssertionError("stale bridge accepted");}catch(BridgeSwitchGuard.Mismatch expected){}}
 public static void main(String[] args)throws Exception{
  Fake stable=new Fake(new String[]{"new"},new String[]{"B"});BridgeSwitchGuard.verify(stable,"new","B");if(stable.samples!=5||stable.waits!=4)throw new AssertionError("not monitored");
  expectMismatch(new Fake(new String[]{"new","old"},new String[]{"B"}));
  expectMismatch(new Fake(new String[]{"new"},new String[]{"B","A"}));
  expectMismatch(new Fake(new String[]{null},new String[]{"B"}));
 }
}'''
  with tempfile.TemporaryDirectory() as tmp:
   root=Path(tmp);src=root/'local/tv/hue';src.mkdir(parents=True)
   shutil.copy(ROOT/'src/local/tv/hue/BridgeSwitchGuard.java',src/'BridgeSwitchGuard.java')
   (src/'GuardHarness.java').write_text(harness)
   subprocess.run(['javac','-d',str(root/'classes'),*map(str,src.glob('*.java'))],check=True,capture_output=True)
   subprocess.run(['java','-cp',str(root/'classes'),'local.tv.hue.GuardHarness'],check=True,capture_output=True)

if __name__ == '__main__': unittest.main()
