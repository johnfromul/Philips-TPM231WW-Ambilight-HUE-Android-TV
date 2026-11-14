import pathlib, subprocess, tempfile, unittest

class SmootherTest(unittest.TestCase):
 def test_time_based_smoothing_and_lamp_independence(self):
  root=pathlib.Path(__file__).resolve().parents[1]
  source='''package local.tv.hue;
public class Test {public static void main(String[] a){
 DirectColorSmoother s=new DirectColorSmoother();
 int[][] x=s.apply(new int[][]{{0,0,0},{255,0,0}},0);if(x[0][0]!=0||x[1][0]!=255)throw new AssertionError();
 x=s.apply(new int[][]{{255,0,0},{0,0,0}},40_000_000L);if(x[0][0]<1||x[0][0]>30||x[1][0]<225)throw new AssertionError();
 x=s.apply(new int[][]{{255,0,0},{0,0,0}},650_000_000L);if(x[0][0]<155||x[0][0]>170)throw new AssertionError();
 DirectColorSmoother t=new DirectColorSmoother();t.apply(new int[][]{{0,0,0}},0);int y=t.apply(new int[][]{{255,0,0}},650_000_000L)[0][0];if(Math.abs(y-x[0][0])>1)throw new AssertionError();
 }}'''
  with tempfile.TemporaryDirectory() as tmp:
   p=pathlib.Path(tmp)/'Test.java';p.write_text(source)
   subprocess.run(['javac','-d',tmp,str(root/'src/local/tv/hue/DirectColorSmoother.java'),str(p)],check=True)
   subprocess.run(['java','-cp',tmp,'local.tv.hue.Test'],check=True)
