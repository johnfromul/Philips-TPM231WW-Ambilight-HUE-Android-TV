from pathlib import Path
import tempfile,shutil,subprocess,unittest
ROOT=Path(__file__).resolve().parents[1]
@unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
class BridgeTlsTests(unittest.TestCase):
 def test_pins_before_credentials_and_legacy_model_gate(self):
  harness='''package local.tv.hue;public class TlsHarness {
 static void deny(String expected,String actual,boolean pub)throws Exception {try{BridgeTlsPolicy.verify(expected,actual,pub);throw new AssertionError("unsafe trust accepted");}catch(java.security.cert.CertificateException correct){}}
 public static void main(String[]args)throws Exception{
 String a="a".repeat(64),b="b".repeat(64);
 BridgeTlsPolicy.verify("",a,true);BridgeTlsPolicy.verify(a,a,false);
 deny("",a,false);deny(a,b,false);deny(a,b,true);deny("","bad",true);
 if(!BridgeTlsPolicy.legacyModel("BSB002"))throw new AssertionError();
 for(String model:new String[]{"BSB001","BSB003","unknown",""})if(BridgeTlsPolicy.legacyModel(model))throw new AssertionError("HTTP accepted for unknown model");
 }}'''
  with tempfile.TemporaryDirectory() as tmp:
   p=Path(tmp);shutil.copy(ROOT/'src/local/tv/hue/BridgeTlsPolicy.java',p/'BridgeTlsPolicy.java');(p/'TlsHarness.java').write_text(harness)
   subprocess.run(['javac','-d',str(p/'classes'),*map(str,p.glob('*.java'))],check=True,capture_output=True)
   subprocess.run(['java','-cp',str(p/'classes'),'local.tv.hue.TlsHarness'],check=True,capture_output=True)
