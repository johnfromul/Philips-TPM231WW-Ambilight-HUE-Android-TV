package local.tv.hue;

import android.content.Context;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** Rootless source. It reads measured TV colors and never changes TV settings. */
final class DirectTvApiSource implements RootSyncCore.Source {
 static final String MARKER="tv_api_measured_v1";
 private final Context context;
 private TvApiClient client;
 private List<RootSyncCore.Lamp> lamps;
 private int[] sizes;
 private DirectColorSmoother smoother;
 private DirectBorderCompensator borderCompensator;
 private boolean blendZones,compensateBorders;
 private int colorMode;
 private final String diagnosticId=Diagnostics.id();
 private long diagnosticWindow;
 private int samples,zeroFrames,changedFrames,mappedChanges,outputChanges;
 private int[] previousRaw,previousMapped,previousOutput;
 DirectTvApiSource(Context c){if(c==null)throw new IllegalArgumentException("Missing context");context=c.getApplicationContext();}
 public String capture()throws Exception{
  TvApiClient check=new TvApiClient(context);
  if(!"On".equals(check.getAmbilightPower()))throw new IOException("TV Ambilight is off or unavailable");
  if(!"Off".equals(check.getPower()))throw new IOException("Stop native Hue synchronization first");
  return MARKER;
 }
 public void start(List<RootSyncCore.Lamp> selection)throws Exception{
  if(selection==null||selection.isEmpty()||selection.size()>10)throw new IllegalArgumentException("Invalid selection");
  lamps=new ArrayList<>(selection);client=new TvApiClient(context);smoother=new DirectColorSmoother(DirectSmoothing.seconds(context));diagnosticWindow=System.nanoTime();
  android.content.SharedPreferences tuning=context.getSharedPreferences("direct_tuning",0);
  blendZones=tuning.getBoolean("blend_zones",true);compensateBorders=tuning.getBoolean("compensate_borders",true);borderCompensator=new DirectBorderCompensator();colorMode=tuning.getInt("color_mode",tuning.getBoolean("color_correction",true)?1:0);
  if(colorMode<0||colorMode>2)colorMode=1;
  Diagnostics.event(diagnosticId,"direct.tuning","blend_zones="+blendZones+" compensate_borders="+compensateBorders+" color_mode="+colorMode);
  JSONObject topology=client.getAmbilightTopology();
  sizes=new int[]{size(topology,"left"),size(topology,"top"),size(topology,"right"),size(topology,"bottom")};
  for(RootSyncCore.Lamp l:lamps){int side=l.angle<=3?0:l.angle==4?1:l.angle<=8?2:3;if(sizes[side]==0)throw new IOException("Selected TV side has no Ambilight zones");}
  client.openMeasuredStream();
  read(); // Reject malformed data before starting Entertainment.
 }
 private static int size(JSONObject topology,String key)throws Exception{
  int n=topology.getInt(key);if(n<0||n>128)throw new IOException("Invalid Ambilight topology");return n;
 }
 public int[][] read()throws Exception{
  if(client==null||sizes==null||lamps==null)throw new IllegalStateException("TV source not started");
  JSONObject root=client.readMeasuredFrame().getJSONObject("layer1");
  int[][] left=side(root,"left",sizes[0]),top=side(root,"top",sizes[1]),right=side(root,"right",sizes[2]),bottom=side(root,"bottom",sizes[3]);
  int[] raw=flatten(left,top,right,bottom);
  if(compensateBorders){DirectBorderCompensator.Frame adjusted=borderCompensator.apply(left,top,right,bottom,System.nanoTime());left=adjusted.left;top=adjusted.top;right=adjusted.right;bottom=adjusted.bottom;}
  int[][] result=new int[lamps.size()][];
  for(int i=0;i<lamps.size();i++)result[i]=DirectZoneMapper.map(lamps.get(i).angle,left,top,right,bottom,blendZones);
  int[][] smoothed=smoother.apply(result,System.nanoTime());
  int[][] output=colorMode==0?smoothed:DirectColorCorrection.apply(smoothed,colorMode==2?.75:.5);
  int[] mapped=flatten(result),sent=flatten(output);
  boolean allZero=true;for(int value:raw)if(value!=0){allZero=false;break;}
  samples++;if(allZero)zeroFrames++;
  if(previousRaw!=null&&!java.util.Arrays.equals(previousRaw,raw))changedFrames++;
  if(previousMapped!=null&&!java.util.Arrays.equals(previousMapped,mapped))mappedChanges++;
  if(previousOutput!=null&&!java.util.Arrays.equals(previousOutput,sent))outputChanges++;
  previousRaw=raw;previousMapped=mapped;previousOutput=sent;
  long now=System.nanoTime();if(now-diagnosticWindow>=5_000_000_000L){
   Diagnostics.event(diagnosticId,"direct.source.window","window_ms="+((now-diagnosticWindow)/1000000)+" samples="+samples+" zero_frames="+zeroFrames+" raw_changed="+changedFrames+" mapped_changed="+mappedChanges+" output_changed="+outputChanges+" zones="+(raw.length/3)+" lights="+output.length);
   Diagnostics.event(diagnosticId,"direct.color.sample","scale=0-65535 mapped="+formatColors(result)+" smoothed="+formatColors(smoothed)+" corrected="+formatColors(output));
   diagnosticWindow=now;samples=zeroFrames=changedFrames=mappedChanges=outputChanges=0;
  }
  return output;
 }
 private static int[] flatten(int[][]... groups){int n=0;for(int[][] group:groups)n+=group.length*3;int[] out=new int[n];int i=0;for(int[][] group:groups)for(int[] rgb:group)for(int v:rgb)out[i++]=v;return out;}
 private static String formatColors(int[][] colors){StringBuilder out=new StringBuilder();for(int i=0;i<colors.length;i++){if(i>0)out.append(';');out.append(i).append(':').append(colors[i][0]).append(',').append(colors[i][1]).append(',').append(colors[i][2]);}return out.toString();}
 private static int[][] side(JSONObject root,String name,int count)throws Exception{
  int[][] out=new int[count][3];if(count==0)return out;JSONObject side=root.getJSONObject(name);
  if(side.length()!=count)throw new IOException("Ambilight topology changed");
  for(int i=0;i<count;i++){
   JSONObject pixel=side.getJSONObject(Integer.toString(i));
   for(int c=0;c<3;c++){
    Object value=pixel.get(new String[]{"r","g","b"}[c]);
    if(!(value instanceof Number)||((Number)value).doubleValue()!=((Number)value).intValue())throw new IOException("Invalid color data");
    int n=((Number)value).intValue();if(n<0||n>255)throw new IOException("Invalid color data");out[i][c]=n;
   }
  }
  return out;
 }
 public void restore(String marker)throws Exception{
  if(!MARKER.equals(marker))throw new IOException("Unknown source recovery record");
  if(client!=null)client.closeMeasuredStream();client=null;sizes=null;lamps=null;smoother=null;borderCompensator=null;
 }
}
