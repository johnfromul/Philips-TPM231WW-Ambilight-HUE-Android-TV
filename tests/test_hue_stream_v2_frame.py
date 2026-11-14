import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
HARNESS=r'''
package local.tv.hue;
import java.nio.charset.StandardCharsets;
public class HueStreamV2Harness {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  String area="00112233-4455-6677-8899-aabbccddeeff";
  HueStreamV2Frame.Channel[] c={new HueStreamV2Frame.Channel(1,0x1234,0x5678,0x9abc),new HueStreamV2Frame.Channel(255,65535,0,1)};
  byte[] f=HueStreamV2Frame.encode(area,255,c);
  check(f.length==66);check("HueStream".equals(new String(f,0,9,StandardCharsets.US_ASCII)));
  check(f[9]==2&&f[10]==0&&(f[11]&255)==255&&f[12]==0&&f[13]==0&&f[14]==0&&f[15]==0);
  check(area.equals(new String(f,16,36,StandardCharsets.US_ASCII)));
  int[] expected={1,0x12,0x34,0x56,0x78,0x9a,0xbc,255,255,255,0,0,0,1};
  for(int i=0;i<expected.length;i++)check((f[52+i]&255)==expected[i]);
  try {HueStreamV2Frame.encode("bad",0,c);throw new AssertionError();}catch(IllegalArgumentException ok){}
  try {HueStreamV2Frame.encode(area,0,new HueStreamV2Frame.Channel[]{c[0],c[0]});throw new AssertionError();}catch(IllegalArgumentException ok){}
  try {new HueStreamV2Frame.Channel(2,-1,0,0);throw new AssertionError();}catch(IllegalArgumentException ok){}
  try {HueStreamV2Frame.encode(area,256,c);throw new AssertionError();}catch(IllegalArgumentException ok){}
  System.out.println("ok");
 }
}
'''
class HueStreamV2FrameTests(unittest.TestCase):
 def test_wire_layout_and_rejection(self):
  with tempfile.TemporaryDirectory() as tmp:
   path=Path(tmp)/'HueStreamV2Harness.java';path.write_text(HARNESS)
   subprocess.run(['javac','-d',tmp,str(ROOT/'src/local/tv/hue/HueStreamV2Frame.java'),str(path)],check=True)
   p=subprocess.run(['java','-cp',tmp,'local.tv.hue.HueStreamV2Harness'],text=True,capture_output=True,check=True)
   self.assertEqual(p.stdout.strip(),'ok')
