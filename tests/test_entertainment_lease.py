import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
HARNESS=r'''
package local.tv.hue;
public class LeaseHarness {
 static void check(boolean x){if(!x)throw new AssertionError();}
 static class J implements EntertainmentLease.Journal {
  boolean pending,confirmed,failConfirm;String b,a;
  public boolean pending(){return pending;}
  public void record(String b,String a){pending=true;this.b=b;this.a=a;}
  public boolean matches(String b,String a){return this.b.equals(b)&&this.a.equals(a);}
  public void confirm()throws Exception{if(failConfirm)throw new Exception("disk");confirmed=true;}
  public boolean confirmed(){return confirmed;}
  public void clear(){pending=false;}
 }
 static class B implements EntertainmentLease.Bridge {
  EntertainmentLease.Owner owner=EntertainmentLease.Owner.FREE;boolean timeout,stopFail,timeoutBeforeClaim;int starts,stops;
  public EntertainmentLease.Owner owner(String a){return owner;}
  public void start(String a)throws Exception{starts++;if(timeoutBeforeClaim)throw new Exception("ambiguous timeout");owner=EntertainmentLease.Owner.OURS;if(timeout)throw new Exception("lost acknowledgement");}
  public void stop(String a)throws Exception{stops++;if(stopFail)throw new Exception("offline");owner=EntertainmentLease.Owner.FREE;}
 }
 static class S implements EntertainmentLease.Stream {boolean opened,closed;public void open(){opened=true;}public void close(){closed=true;}}
 public static void main(String[] a)throws Exception {
  J j=new J();B b=new B();S s=new S();EntertainmentLease x=new EntertainmentLease("bridge","area",j,b,s);
  x.start();check(j.pending&&s.opened);x.stop();check(!j.pending&&b.stops==1&&s.closed);
  j=new J();b=new B();s=new S();b.owner=EntertainmentLease.Owner.OTHER;x=new EntertainmentLease("bridge","area",j,b,s);
  try{x.start();throw new AssertionError();}catch(IllegalStateException expected){}check(!j.pending&&b.starts==0&&b.stops==0);
  j=new J();b=new B();s=new S();b.timeout=true;x=new EntertainmentLease("bridge","area",j,b,s);
  try{x.start();throw new AssertionError();}catch(Exception expected){}check(!j.pending&&b.stops==1);
  j=new J();b=new B();s=new S();b.timeoutBeforeClaim=true;x=new EntertainmentLease("bridge","area",j,b,s);
  try{x.start();throw new AssertionError();}catch(Exception expected){}check(j.pending&&b.stops==0);
  j=new J();b=new B();s=new S();x=new EntertainmentLease("bridge","area",j,b,s);x.start();b.owner=EntertainmentLease.Owner.OTHER;
  try{x.stop();throw new AssertionError();}catch(IllegalStateException expected){}check(j.pending&&b.stops==0);
  j=new J();b=new B();s=new S();x=new EntertainmentLease("bridge","area",j,b,s);x.start();b.stopFail=true;
  try{x.stop();throw new AssertionError();}catch(Exception expected){}check(j.pending);b.stopFail=false;x.recover();check(!j.pending);
  j=new J();b=new B();s=new S();x=new EntertainmentLease("bridge","area",j,b,s);x.start();
  b.owner=EntertainmentLease.Owner.FREE;x=new EntertainmentLease("bridge","area",j,b,new S());x.recover();check(!j.pending);
  j=new J();b=new B();s=new S();j.failConfirm=true;x=new EntertainmentLease("bridge","area",j,b,s);
  try{x.start();throw new AssertionError();}catch(Exception expected){}check(!j.pending&&b.stops==1&&!s.opened);
  j=new J();j.record("bridge","area");b=new B();x=new EntertainmentLease("bridge","area",j,b,new S());
  try{x.recover();throw new AssertionError();}catch(IllegalStateException expected){}check(j.pending);
  System.out.println("ok");
 }
}
'''
class EntertainmentLeaseTests(unittest.TestCase):
 def test_ownership_and_interrupted_claim(self):
  with tempfile.TemporaryDirectory() as tmp:
   path=Path(tmp)/'LeaseHarness.java';path.write_text(HARNESS)
   subprocess.run(['javac','-d',tmp,str(ROOT/'src/local/tv/hue/EntertainmentLease.java'),str(path)],check=True)
   p=subprocess.run(['java','-cp',tmp,'local.tv.hue.LeaseHarness'],text=True,capture_output=True,check=True)
   self.assertEqual(p.stdout.strip(),'ok')
