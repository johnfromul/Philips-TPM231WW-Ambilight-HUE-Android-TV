package local.tv.hue;

import android.content.Context;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Own CLIP + DTLS session; caller owns snapshot/restore and runs methods off the UI thread. */
final class HueEntertainmentSession implements Closeable {
 private static final long MIN_FRAME_INTERVAL_NS=40000000L;
 private final String host,area;private final HueEntertainmentApi api;private final HueDtlsClient dtls;
 private final EntertainmentLease lease;private byte[] key;private boolean started,closed;private int sequence;private long lastFrameNs;

 HueEntertainmentSession(Context activity,String host,String appKey,String clientKeyHex,String bridgeId,String areaId){
  if(clientKeyHex==null||!clientKeyHex.matches("(?i)[0-9a-f]{32}"))throw new IllegalArgumentException("Invalid Entertainment key");
  this.host=host;this.area=areaId;api=new HueEntertainmentApi(activity,host,appKey);dtls=new HueDtlsClient();
  key=new byte[16];for(int i=0;i<key.length;i++)key[i]=(byte)Integer.parseInt(clientKeyHex.substring(i*2,i*2+2),16);
  EntertainmentLease.Stream transport=new EntertainmentLease.Stream(){
   @Override public void open()throws Exception{byte[] id=api.applicationId().getBytes(StandardCharsets.US_ASCII);try{dtls.connect(InetAddress.getByName(HueEntertainmentSession.this.host),2100,id,key);}finally{Arrays.fill(id,(byte)0);}}
   @Override public void close()throws Exception{dtls.close();}
  };
  lease=new EntertainmentLease(bridgeId,areaId,new EntertainmentJournal(activity),api,transport);
 }
 synchronized void verifyMapping(List<RootSyncCore.Lamp> lamps,EntertainmentMapping.Entry[] mapping)throws Exception{api.verifyMapping(area,lamps,mapping);}
 synchronized void start()throws Exception{
  if(closed||started||key==null)throw new IllegalStateException("Invalid session state");
  try {
   api.applicationId(); // Confirm credentials before recording/claiming an area.
   lease.start();started=true;lastFrameNs=0;sequence=0;
  } catch(Exception failure){try{close();}catch(Exception recovery){failure.addSuppressed(recovery);}throw failure;}
 }
 /** Return false for a deliberately dropped frame; never queue stale frames. */
 synchronized boolean send(EntertainmentMapping.Entry[] mapping,int[][] pixels)throws Exception{
  return sendFrame(() -> HueStreamV2Frame.encode(area,sequence,EntertainmentMapping.channels(mapping,pixels)));
 }
 synchronized boolean sendXy(VirtualHueColors colors)throws Exception{
  HueStreamV2Frame.Channel[] channels=colors.snapshot();
  if(channels==null)return false;
  return sendFrame(() -> HueStreamV2Frame.encodeXy(area,sequence,channels));
 }
 private interface Frame { byte[] encode(); }
 private boolean sendFrame(Frame frame)throws Exception{
  if(!started||closed)throw new IllegalStateException("Session is not running");
  long now=System.nanoTime();if(lastFrameNs!=0&&now-lastFrameNs<MIN_FRAME_INTERVAL_NS)return false;
  try{dtls.send(frame.encode());lastFrameNs=now;sequence=(sequence+1)&255;return true;}
  catch(Exception failure){try{close();}catch(Exception recovery){failure.addSuppressed(recovery);}throw failure;}
 }
 @Override public synchronized void close()throws IOException{
  if(closed)return;started=false;
  try{lease.stop();closed=true;}catch(Exception e){throw new IOException("Entertainment cleanup incomplete; recovery required",e);}
  finally{if(key!=null){Arrays.fill(key,(byte)0);key=null;}}
 }
}
