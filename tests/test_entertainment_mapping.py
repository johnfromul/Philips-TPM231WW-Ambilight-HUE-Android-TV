import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
HARNESS=r'''
package local.tv.hue;
public class MappingHarness {
 static void check(boolean x){if(!x)throw new AssertionError();}
 public static void main(String[] args){
  int[][] pixels=new int[10][3];pixels[0]=new int[]{65535,0,101};pixels[1]=new int[]{0,60000,9};
  EntertainmentMapping.Entry[] m={new EntertainmentMapping.Entry(0,2,10),new EntertainmentMapping.Entry(1,1,5)};
  HueStreamV2Frame.Channel[] c=EntertainmentMapping.channels(m,pixels);
  check(c[0].id==0&&c[0].green==60000&&c[1].id==1&&c[1].red==32768&&c[1].blue==51);
  try{EntertainmentMapping.channels(new EntertainmentMapping.Entry[]{m[0],m[0]},pixels);throw new AssertionError();}catch(IllegalArgumentException ok){}
  try{new EntertainmentMapping.Entry(1,0,5);throw new AssertionError();}catch(IllegalArgumentException ok){}
  try{pixels[1][0]=-1;EntertainmentMapping.channels(m,pixels);throw new AssertionError();}catch(IllegalArgumentException ok){}
  System.out.println("ok");
 }
}
'''
class EntertainmentMappingTests(unittest.TestCase):
 def test_explicit_mapping_scaling_and_validation(self):
  with tempfile.TemporaryDirectory() as tmp:
   path=Path(tmp)/'MappingHarness.java';path.write_text(HARNESS)
   sources=['EntertainmentMapping.java','HueStreamV2Frame.java']
   subprocess.run(['javac','-d',tmp,*[str(ROOT/'src/local/tv/hue'/s) for s in sources],str(path)],check=True)
   p=subprocess.run(['java','-cp',tmp,'local.tv.hue.MappingHarness'],text=True,capture_output=True,check=True)
   self.assertEqual(p.stdout.strip(),'ok')
