package local.tv.hue;

/** Experimental, conservative fit to one measured television and two lights. */
final class DirectColorCorrection {
 private static final double[][] M={{.413,-.006,-.206},{-.265,.750,-.205},{-.355,-.216,.970}};
 private static final double[][] EXCLUDED={{1,0,.5},{1,1,0},{0,1,.5}};
 static int[][] apply(int[][] input){
  return apply(input,.5);
 }
 static int[][] apply(int[][] input,double strength){
  if(!Double.isFinite(strength)||strength<0||strength>1)throw new IllegalArgumentException("Invalid correction strength");
  int[][] out=new int[input.length][3];
  for(int i=0;i<input.length;i++){
   int[] p=input[i];double max=Math.max(p[0],Math.max(p[1],p[2]));if(max<=0)continue;
   double[] u={p[0]/max,p[1]/max,p[2]/max};double weight=strength;
   for(double[] v:EXCLUDED){double d=0;for(int c=0;c<3;c++)d+=(u[c]-v[c])*(u[c]-v[c]);weight*=1-Math.exp(-d/(2*.18*.18));}
   double[] q=new double[3];double qmax=0;
   for(int c=0;c<3;c++){double x=0;for(int k=0;k<3;k++)x+=M[c][k]*u[k];q[c]=Math.max(0,x);qmax=Math.max(qmax,q[c]);}
   if(qmax<1e-9){System.arraycopy(p,0,out[i],0,3);continue;}
   for(int c=0;c<3;c++)out[i][c]=(int)Math.round(Math.max(0,Math.min(65535,p[c]*(1-weight)+max*q[c]/qmax*weight)));
  }return out;
 }
}
