package local.tv.hue;

/** Time-based low-pass filter; independent of the frame rate and reset for every session. */
final class DirectColorSmoother {
 private final double timeConstantSeconds;
 DirectColorSmoother(){this(0.65);}
 DirectColorSmoother(double timeConstantSeconds){this.timeConstantSeconds=timeConstantSeconds;}
 private double[][] previous;
 private long lastNanos;
 int[][] apply(int[][] input,long nowNanos){
  if(previous==null||previous.length!=input.length){previous=new double[input.length][3];for(int i=0;i<input.length;i++)for(int c=0;c<3;c++)previous[i][c]=input[i][c];lastNanos=nowNanos;}
  double dt=Math.max(0,Math.min(2,(nowNanos-lastNanos)/1_000_000_000.0));
  double alpha=1-Math.exp(-dt/timeConstantSeconds);lastNanos=nowNanos;
  int[][] output=new int[input.length][3];
  for(int i=0;i<input.length;i++)for(int c=0;c<3;c++){previous[i][c]+=alpha*(input[i][c]-previous[i][c]);output[i][c]=(int)Math.round(previous[i][c]);}
  return output;
 }
}
