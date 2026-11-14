package local.tv.hue;

/** Conservative, opt-out approximation: edge data alone cannot prove that black bars exist. */
final class DirectBorderCompensator {
 static final class Frame {
  final int[][] left,top,right,bottom;
  Frame(int[][] l,int[][] t,int[][] r,int[][] b){left=l;top=t;right=r;bottom=b;}
 }
 private static final long HOLD_NS=3_000_000_000L;
 private final Gain horizontal=new Gain(1.5),left=new Gain(1.35),right=new Gain(1.35);

 Frame apply(int[][] l,int[][] t,int[][] r,int[][] b,long now){
  if(l.length<2||r.length<2||t.length<3||b.length<3){horizontal.update(1,now);left.update(1,now);right.update(1,now);return new Frame(l,t,r,b);}
  int[] lt=l[l.length-1],lb=l[0],rt=r[0],rb=r[r.length-1];
  double[] h={ratio(lt,t[1]),ratio(rt,t[t.length-2]),ratio(lb,b[b.length-2]),ratio(rb,b[1])};
  boolean horizontalCandidate=true;double ratioSum=0;
  for(double value:h){if(value<.12||value>.84)horizontalCandidate=false;ratioSum+=value;}
  double hGain=horizontal.update(horizontalCandidate?4/ratioSum:1,now);
  // For vertical bars, use inner upper/lower edge samples as references for each side.
  double leftRatio=verticalRatio(l[l.length/2],t[1],b[b.length-2]);
  double rightRatio=verticalRatio(r[r.length/2],t[t.length-2],b[1]);
  double lGain=left.update(!horizontalCandidate&&leftRatio>=.12&&leftRatio<.86?1/leftRatio:1,now);
  double rGain=right.update(!horizontalCandidate&&rightRatio>=.12&&rightRatio<.86?1/rightRatio:1,now);
  int[][] outL=copy(l),outT=copy(t),outR=copy(r),outB=copy(b);
  scaleAll(outT,hGain);scaleAll(outB,hGain);scaleAll(outL,lGain);scaleAll(outR,rGain);
  scale(outT[0],lGain);scale(outB[outB.length-1],lGain);
  scale(outT[outT.length-1],rGain);scale(outB[0],rGain);
  return new Frame(outL,outT,outR,outB);
 }
 private static double verticalRatio(int[] side,int[] top,int[] bottom){
  double a=ratio(top,side),b=ratio(bottom,side);
  if(a<0||b<0||Math.abs(a-b)>.15)return -1;
  return (a+b)/2;
 }
 // A ratio is trusted only when both colors have compatible chromaticity.
 private static double ratio(int[] reference,int[] sample){
  int r=Math.max(reference[0],Math.max(reference[1],reference[2]));
  int s=Math.max(sample[0],Math.max(sample[1],sample[2]));
  if(r<30||s<5)return -1;
  double difference=0;for(int i=0;i<3;i++)difference+=Math.abs((double)reference[i]/r-(double)sample[i]/s);
  return difference/3>.12?-1:(double)s/r;
 }
 private static int[][] copy(int[][] in){int[][] out=new int[in.length][];for(int i=0;i<in.length;i++)out[i]=in[i].clone();return out;}
 private static void scaleAll(int[][] values,double gain){for(int[] rgb:values)scale(rgb,gain);}
 private static void scale(int[] rgb,double gain){for(int i=0;i<3;i++)rgb[i]=(int)Math.min(255,Math.round(rgb[i]*gain));}
 private static final class Gain {
  private final double maximum;private long since=-1,last=-1;private double value=1;
  Gain(double maximum){this.maximum=maximum;}
  double update(double requested,long now){
   if(requested<=1.03){since=-1;last=now;value=1;return 1;}else{if(since<0)since=now;if(now-since<HOLD_NS)requested=1;}
   requested=Math.max(1,Math.min(maximum,requested));
   double dt=last<0?0:Math.max(0,Math.min(.5,(now-last)/1e9));last=now;
   value+=(requested-value)*(1-Math.exp(-dt/.8));return value;
  }
 }
}
