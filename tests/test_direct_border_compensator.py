import pathlib,subprocess,tempfile,unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
HARNESS=r'''package local.tv.hue;
public class BorderHarness {
 static int[][] repeat(int n,int r,int g,int b){int[][] out=new int[n][3];for(int[] v:out){v[0]=r;v[1]=g;v[2]=b;}return out;}
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  int[][] left=repeat(3,152,19,0),right=repeat(3,0,47,152);
  int[][] top={{152,19,0},{152,19,0},{152,19,0},{121,24,30},{0,47,152},{0,47,152},{0,47,152},{0,47,152}};
  int[][] bottom={{0,47,152},{0,47,152},{0,47,152},{0,47,152},{76,33,76},{152,19,0},{152,19,0},{152,19,0},{152,19,0}};
  DirectBorderCompensator b=new DirectBorderCompensator();
  DirectBorderCompensator.Frame f=null;
  for(int i=0;i<60;i++)f=b.apply(left,top,right,bottom,i*100_000_000L);
  check(f.top[1][0]==152&&f.right[1][2]==152); // no correction for full-frame image
  int[][] barTop={{121,15,0},{91,11,0},{91,11,0},{72,14,18},{0,28,91},{0,28,91},{0,28,91},{0,37,121}};
  int[][] barBottom={{0,28,91},{0,28,91},{0,28,91},{0,28,91},{45,19,45},{91,11,0},{91,11,0},{91,11,0},{91,11,0}};
  for(int i=60;i<80;i++)f=b.apply(left,barTop,right,barBottom,i*100_000_000L);
  check(f.top[1][0]==91); // must not react to a brief black-looking scene
  for(int i=80;i<130;i++)f=b.apply(left,barTop,right,barBottom,i*100_000_000L);
  check(f.top[1][0]>125&&f.bottom[1][2]>125&&f.left[1][0]==152);
  check(barTop[1][0]==91); // never mutate input
  f=b.apply(left,top,right,bottom,13_100_000_000L);check(f.top[1][0]==152); // no lingering bright flash
  DirectBorderCompensator v=new DirectBorderCompensator();
  int[][] rightDark=repeat(3,0,35,114);
  int[][] verticalTop={{152,19,0},{152,19,0},{152,19,0},{114,26,38},{0,47,152},{0,47,152},{0,47,152},{0,35,114}};
  int[][] verticalBottom={{0,35,114},{0,47,152},{0,47,152},{0,47,152},{76,33,76},{152,19,0},{152,19,0},{152,19,0},{152,19,0}};
  for(int i=0;i<70;i++)f=v.apply(left,verticalTop,rightDark,verticalBottom,i*100_000_000L);
  check(f.right[1][2]>140&&f.left[1][0]==152&&f.top[7][2]>140);
  int[][] black=repeat(3,0,0,0);DirectBorderCompensator dark=new DirectBorderCompensator();
  for(int i=0;i<70;i++)f=dark.apply(black,top,black,bottom,i*100_000_000L);
  check(f.left[0][0]==0&&f.right[0][2]==0);
 }
}'''

class BorderTests(unittest.TestCase):
 def test_measured_horizontal_vertical_and_release(self):
  with tempfile.TemporaryDirectory() as tmp:
   path=pathlib.Path(tmp)/'BorderHarness.java';path.write_text(HARNESS)
   subprocess.run(['javac','-d',tmp,str(ROOT/'src/local/tv/hue/DirectBorderCompensator.java'),str(path)],check=True)
   subprocess.run(['java','-cp',tmp,'local.tv.hue.BorderHarness'],check=True)
