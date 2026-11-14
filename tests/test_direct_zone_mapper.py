import pathlib
import subprocess
import tempfile
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
HARNESS=r'''package local.tv.hue;
import java.util.Arrays;
public class ZoneHarness {
 static void eq(int[] actual,int r,int g,int b){if(!Arrays.equals(actual,new int[]{r,g,b}))throw new AssertionError(Arrays.toString(actual));}
 public static void main(String[] args){
  int[][] left={{0,0,0},{90,30,0},{180,60,0}};
  int[][] top={{0,0,0},{0,100,0},{0,200,0}};
  int[][] right={{0,0,30},{0,0,90},{0,0,150},{0,0,210}};
  int[][] bottom={{100,0,0},{120,0,0}};
  eq(DirectZoneMapper.map(0,left,top,right,bottom,false),0,0,0);
  eq(DirectZoneMapper.map(1,left,top,right,bottom,false),60*257,20*257,0);
  eq(DirectZoneMapper.map(3,left,top,right,bottom,false),180*257,60*257,0);
  eq(DirectZoneMapper.map(4,left,top,right,bottom,false),0,100*257,0);
  eq(DirectZoneMapper.map(5,left,top,right,bottom,false),0,0,30*257);
  eq(DirectZoneMapper.map(8,left,top,right,bottom,false),0,0,210*257);
  eq(DirectZoneMapper.map(9,left,top,right,bottom),110*257,0,0);
  // With different side and upper colors, upper corner lights use a broad upper contribution.
  int[][] red={{200,0,0},{200,0,0},{200,0,0}},blue={{0,0,200},{0,0,200}};
  int[] l=DirectZoneMapper.map(3,blue,red,blue,bottom),r=DirectZoneMapper.map(5,blue,red,blue,bottom);
  eq(l,43690,0,7710);eq(r,43690,0,7710);
  // Upper lamps on both sides stay symmetric, and legacy and no-top fallbacks remain available.
  eq(DirectZoneMapper.map(3,blue,new int[0][],blue,bottom),0,0,51400);
  eq(DirectZoneMapper.map(5,blue,red,blue,bottom,false),0,0,51400);
  // Uniform colors are unchanged for all ten positions.
  int[][] uniform={{33,42,51},{33,42,51}};
  for(int i=0;i<10;i++)eq(DirectZoneMapper.map(i,uniform,uniform,uniform,uniform),33*257,42*257,51*257);
  // Top center uses the whole upper edge, not just the middle pixel.
  eq(DirectZoneMapper.map(4,uniform,new int[][]{{0,0,0},{0,0,0},{0,0,240}},uniform,uniform),0,0,80*257);
  // An asymmetric upper edge keeps each upper lamp associated with its own corner.
  int[][] split={{200,0,0},{200,0,0},{200,0,0},{200,0,0},{0,0,200},{0,0,200},{0,0,200},{0,0,200}};
  int[] upperLeft=DirectZoneMapper.map(3,red,split,blue,bottom);
  int[] upperRight=DirectZoneMapper.map(5,red,split,blue,bottom);
  if(upperLeft[0]<upperLeft[2]*3||upperRight[2]<upperRight[0]*3)throw new AssertionError("upper corners must remain distinct");
  try{DirectZoneMapper.map(9,left,top,right,new int[0][]);throw new AssertionError("missing side");}catch(IllegalArgumentException expected){}
  try{DirectZoneMapper.map(0,new int[][]{{256,0,0}},top,right,bottom);throw new AssertionError("bad data");}catch(IllegalArgumentException expected){}
  System.out.println("ok");
 }
}'''

class MappingTests(unittest.TestCase):
 def test_positions_interpolation_and_validation(self):
  with tempfile.TemporaryDirectory() as temp:
   path=pathlib.Path(temp)/'ZoneHarness.java';path.write_text(HARNESS)
   subprocess.run(['javac','-d',temp,str(ROOT/'src/local/tv/hue/DirectZoneMapper.java'),str(path)],check=True)
   out=subprocess.run(['java','-cp',temp,'local.tv.hue.ZoneHarness'],check=True,capture_output=True,text=True)
   self.assertEqual(out.stdout.strip(),'ok')
