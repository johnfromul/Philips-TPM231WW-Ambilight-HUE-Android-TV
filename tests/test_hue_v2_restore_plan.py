import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HARNESS = r'''
package local.tv.hue;
import java.util.*;
public class V2RestoreHarness {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static HueV2RestorePlan.Snapshot s(boolean on,String mode,double bri,boolean reachable,String effect,boolean gradient){
  return new HueV2RestorePlan.Snapshot(reachable,on,bri,mode,.1544,.0937,346,effect,"none",gradient);
 }
 public static void main(String[] args){
  List<Map<String,Object>> off=HueV2RestorePlan.commands(s(false,"xy",35.57,true,"no_effect",false));
  check(off.size()==2 && Boolean.FALSE.equals(((Map)off.get(0).get("on")).get("on")));
  check(off.get(1).containsKey("color") && !off.get(1).containsKey("on"));
  List<Map<String,Object>> on=HueV2RestorePlan.commands(s(true,"ct",50,true,"no_effect",false));
  check(on.size()==1 && on.get(0).containsKey("color_temperature") && Boolean.TRUE.equals(((Map)on.get(0).get("on")).get("on")));
  for(int i=0;i<4;i++)try {
   if(i==0)s(false,"xy",0,true,"no_effect",false);
   if(i==1)s(false,"xy",35,false,"no_effect",false);
   if(i==2)s(false,"xy",35,true,"candle",false);
   if(i==3)s(false,"xy",35,true,"no_effect",true);
   throw new AssertionError("unsupported state accepted");
  } catch(IllegalArgumentException expected){}
  System.out.println("ok");
 }
}
'''

class V2RestoreTests(unittest.TestCase):
 def test_command_order_and_unsupported_states(self):
  with tempfile.TemporaryDirectory() as tmp:
   path=Path(tmp)/'V2RestoreHarness.java';path.write_text(HARNESS)
   subprocess.run(['javac','-d',tmp,str(ROOT/'src/local/tv/hue/HueV2RestorePlan.java'),str(path)],check=True)
   p=subprocess.run(['java','-cp',tmp,'local.tv.hue.V2RestoreHarness'],check=True,text=True,capture_output=True)
   self.assertEqual(p.stdout.strip(),'ok')
