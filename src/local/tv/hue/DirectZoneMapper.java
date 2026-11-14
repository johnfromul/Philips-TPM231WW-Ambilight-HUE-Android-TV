package local.tv.hue;

/** Approximate external-lamp colors using the TV's measured perimeter zones. */
final class DirectZoneMapper {
 private static final double UPPER_MIX=0.85;
 private DirectZoneMapper(){}
 static int[] map(int angle,int[][] left,int[][] top,int[][] right,int[][] bottom){
  return map(angle,left,top,right,bottom,true);
 }
 static int[] map(int angle,int[][] left,int[][] top,int[][] right,int[][] bottom,boolean blend){
  if(angle<0||angle>9)throw new IllegalArgumentException("Unknown position");
  if(angle==4)return blend?average(top):sample(top,0.5);
  if(angle==9)return sample(bottom,0.5);
  boolean isLeft=angle<=3;
  int[][] side=isLeft?left:right;
  double at=isLeft?angle/3.0:(angle-5)/3.0;
  int[] local=sample(side,at);
  if(!blend||top==null||top.length==0)return local;
  // Left samples run bottom-to-top; right samples run top-to-bottom.
  double nearTop=isLeft?at:1-at;
  double topWeight=UPPER_MIX*nearTop*nearTop*nearTop;
  if(topWeight==0)return local;
  int[] upper=upperEdge(top,isLeft),result=new int[3];
  for(int c=0;c<3;c++)result[c]=(int)Math.round(local[c]*(1-topWeight)+upper[c]*topWeight);
  return result;
 }
 private static void validate(int[][] side){
  if(side==null||side.length==0||side.length>128)throw new IllegalArgumentException("Selected side unavailable");
  for(int[] rgb:side){if(rgb==null||rgb.length!=3)throw new IllegalArgumentException("Malformed pixel");for(int v:rgb)if(v<0||v>255)throw new IllegalArgumentException("Invalid pixel");}
 }
 private static int[] sample(int[][] side,double at){
  validate(side);
  double pos=at*(side.length-1);int a=(int)pos,b=Math.min(a+1,side.length-1);double f=pos-a;
  int[] result=new int[3];for(int c=0;c<3;c++)result[c]=(int)Math.round((side[a][c]*(1-f)+side[b][c]*f)*257.0);
  return result;
 }
 private static int[] average(int[][] side){
  validate(side);int[] result=new int[3];
  for(int c=0;c<3;c++){long total=0;for(int[] rgb:side)total+=rgb[c];result[c]=(int)Math.round(total*257.0/side.length);}
  return result;
 }
 /** Prefer the upper edge near each corner, while blending smoothly to its neighbours. */
 private static int[] upperEdge(int[][] side,boolean isLeft){
  validate(side);if(side.length==1)return sample(side,0);
  double center=isLeft?.10:.90,weightSum=0;double[] sums=new double[3];
  for(int i=0;i<side.length;i++){
   double position=(double)i/(side.length-1),delta=(position-center)/.08;
   double weight=Math.exp(-.5*delta*delta);weightSum+=weight;
   for(int c=0;c<3;c++)sums[c]+=side[i][c]*weight;
  }
  if(weightSum<1e-12)return sample(side,isLeft?0:1);
  int[] result=new int[3];for(int c=0;c<3;c++)result[c]=(int)Math.round(sums[c]*257.0/weightSum);
  return result;
 }
}
