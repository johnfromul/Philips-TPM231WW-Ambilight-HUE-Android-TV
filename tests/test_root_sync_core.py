import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

HARNESS = r'''
package local.tv.hue;
import java.util.*;
public class RootSyncHarness {
 static class Store implements RootSyncCore.Store {
  Map<Integer,String> data; String sourceState; boolean failSave;
  public boolean pending(){return data!=null;}
  public void save(Map<Integer,String> s,String source) throws Exception {if(failSave)throw new Exception("disk");data=new LinkedHashMap<>(s);sourceState=source;}
  public Map<Integer,String> load(){return new LinkedHashMap<>(data);}
  public String loadSource(){return sourceState;}
  public void clear(){data=null;}
 }
 static class Lights implements RootSyncCore.Lights {
  final List<String> calls=new ArrayList<>(); int badRestore=-1, badApply=-1; boolean badStop;
  public String capture(int id){calls.add("capture"+id);return "original"+id;}
  public void restore(int id,String value) throws Exception {calls.add("restore"+id+":"+value);if(id==badRestore)throw new Exception("offline");}
  public void stop() throws Exception {calls.add("stop");if(badStop)throw new Exception("stream active");}
  public void applyFrame(List<RootSyncCore.Lamp> lamps,int[][] rgb) throws Exception {calls.add("applyFrame");if(badApply>=0)throw new Exception("broken link");}
 }
 static class Source implements RootSyncCore.Source {
  boolean started,stopped; int[][] values={{100,200,300},{400,500,600}};
  public String capture(){return "original-hal-state";}
  public void start(List<RootSyncCore.Lamp> l){started=true;}
  public int[][] read(){return values;}
  public void restore(String state){if(!"original-hal-state".equals(state))throw new AssertionError("wrong source snapshot");stopped=true;}
 }
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static List<RootSyncCore.Lamp> lamps(){return Arrays.asList(new RootSyncCore.Lamp(1,2,1,6),new RootSyncCore.Lamp(2,6,1,6));}
 public static void main(String[] a) throws Exception {
  Store s=new Store();Lights l=new Lights();Source p=new Source();RootSyncCore c=new RootSyncCore(s,l,p);
  c.start(lamps());check(s.pending()&&p.started,"record before start");c.tick();c.stop();
  check(!s.pending()&&p.stopped,"clean shutdown");check(l.calls.contains("restore1:original1")&&l.calls.contains("restore2:original2"),"both states restored");
  s=new Store();l=new Lights();p=new Source();c=new RootSyncCore(s,l,p);s.failSave=true;
  try{c.start(lamps());throw new AssertionError("save failure accepted");}catch(Exception expected){}
  check(!p.started&&!l.calls.contains("applyFrame"),"no output on save failure");
  s=new Store();l=new Lights();p=new Source();c=new RootSyncCore(s,l,p);c.start(lamps());l.badApply=2;l.badRestore=1;
  try{c.tick();throw new AssertionError("apply failure accepted");}catch(Exception expected){}
  check(p.stopped&&s.pending()&&l.calls.contains("restore2:original2"),"restore attempts all and keeps recovery");
  l.badRestore=-1;c.recover();check(!s.pending(),"recovery retried");
  s=new Store();l=new Lights();p=new Source();c=new RootSyncCore(s,l,p);c.start(lamps());p.values=new int[][]{{1,2,3},{70000,2,3}};
  try{c.tick();throw new AssertionError("invalid color accepted");}catch(Exception expected){}
  check(!l.calls.contains("applyFrame")&&!s.pending(),"validate whole frame before writes");
  s=new Store();l=new Lights();p=new Source();c=new RootSyncCore(s,l,p);c.start(lamps());l.badStop=true;
  try{c.stop();throw new AssertionError("stop failure accepted");}catch(Exception expected){}
  check(s.pending()&&p.stopped&&!l.calls.contains("restore1:original1"),"do not restore lights while stream may still run");
  l.badStop=false;c.recover();check(!s.pending()&&l.calls.contains("restore1:original1"),"retry after stream stops");
  try{new RootSyncCore.Lamp(1,10,1,6);throw new AssertionError("invalid angle accepted");}catch(IllegalArgumentException expected){}
  System.out.println("ok");
 }
}
'''

class RootSyncCoreTests(unittest.TestCase):
    def test_recovery_and_failure_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'RootSyncHarness.java'
            path.write_text(HARNESS)
            subprocess.run(['javac', '-d', tmp, str(ROOT/'src/local/tv/hue/RootSyncCore.java'), str(path)], check=True)
            result = subprocess.run(['java', '-cp', tmp, 'local.tv.hue.RootSyncHarness'], text=True, capture_output=True, check=True)
            self.assertEqual(result.stdout.strip(), 'ok')
