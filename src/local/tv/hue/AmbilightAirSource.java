package local.tv.hue;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Receives Philips ExtPixels from the local TV before the Hue stream is opened. */
final class AmbilightAirSource implements RootSyncCore.Source {
 static final String MARKER="ambilight_air_v1";
 private static final long MAX_FRAME_AGE_MS=1500;
 static final class Unavailable extends IOException {}
 private final Context context;
 private final BooleanSupplier activateAir;
 private final BooleanSupplier cancelled;
 private final CountDownLatch firstFrame=new CountDownLatch(1);
 private final String diagnosticId=Diagnostics.id();
 private MulticastSocket socket;
 private WifiManager.MulticastLock lock;
 private List<RootSyncCore.Lamp> lamps;
 private volatile Frame latest;
 private volatile IOException receiveError;
 private DirectColorSmoother smoother;
 private boolean configurationRecorded;
 private static final class Frame {
  final int[][] zones;
  final long time=SystemClock.elapsedRealtime();
  Frame(int[][] zones){this.zones=zones;}
 }
 AmbilightAirSource(Context context){this(context,()->false,()->false);}
 AmbilightAirSource(Context context,BooleanSupplier activateAir,BooleanSupplier cancelled){
  this.context=context.getApplicationContext();
  this.activateAir=activateAir;
  this.cancelled=cancelled;
 }
 public String capture(){return MARKER;}
 public void start(List<RootSyncCore.Lamp> selection)throws Exception{
  lamps=selection;
  smoother=new DirectColorSmoother(DirectSmoothing.seconds(context));
  WifiManager wifi=context.getSystemService(WifiManager.class);
  if(wifi!=null){lock=wifi.createMulticastLock("AmbilightHueAir");lock.acquire();}
  socket=new MulticastSocket(null);
  socket.setReuseAddress(true);
  socket.bind(new InetSocketAddress(2920));
  InetSocketAddress group=new InetSocketAddress("224.8.0.8",2920);
  Set<InetAddress> local=new HashSet<>();
  int joined=0;
  for(NetworkInterface network:Collections.list(NetworkInterface.getNetworkInterfaces())){
   if(!network.isUp()||network.isLoopback()||!network.supportsMulticast())continue;
   try{
    socket.joinGroup(group,network);
    local.addAll(Collections.list(network.getInetAddresses()));
    joined++;
   }catch(IOException error){Diagnostics.error(diagnosticId,"direct.air.join.error",error);}
  }
  if(joined==0)throw new Unavailable();
  MulticastSocket receiver=socket;
  Thread reader=new Thread(()->receive(receiver,local),"ambilight-air-receiver");
  reader.setDaemon(true);
  reader.start();
  boolean ready=awaitFrame(750);
  if(ready){
   try{awaitContinuousFrames(650);}catch(Unavailable gap){ready=false;}
  }
  if(!ready){
   boolean repairing=activateAir.getAsBoolean();
   if(!awaitFrame(repairing?6000:2250))throw new Unavailable();
   awaitContinuousFrames(repairing?6000:2250);
  }
  read();
  Diagnostics.event(diagnosticId,"direct.air.first_frame","zones=10");
 }
 private boolean awaitFrame(long timeoutMs)throws InterruptedException,Unavailable{
  long deadline=SystemClock.elapsedRealtime()+timeoutMs;
  while(true){
   if(cancelled.getAsBoolean())throw new InterruptedException("Direct start cancelled");
   long remaining=deadline-SystemClock.elapsedRealtime();
   if(remaining<=0)return false;
   if(firstFrame.await(Math.min(remaining,200),TimeUnit.MILLISECONDS)){
    if(receiveError!=null||latest==null)throw new Unavailable();
    if(cancelled.getAsBoolean())throw new InterruptedException("Direct start cancelled");
    return true;
   }
  }
 }
 private void awaitContinuousFrames(long timeout) throws InterruptedException,Unavailable{
  Frame first=latest;
  long deadline=SystemClock.elapsedRealtime()+timeout;
  while(SystemClock.elapsedRealtime()<deadline){
   if(cancelled.getAsBoolean())throw new InterruptedException("Direct start cancelled");
   Frame current=latest;
   if(receiveError!=null)break;
   if(current!=null&&current.time-first.time>=350&&SystemClock.elapsedRealtime()-current.time<250)return;
   Thread.sleep(40);
  }
  throw new Unavailable();
 }
 private void receive(MulticastSocket receiver,Set<InetAddress> local){
  byte[] data=new byte[512];
  DatagramPacket packet=new DatagramPacket(data,data.length);
  long window=SystemClock.elapsedRealtime();
  int frames=0;
  try{
   while(!receiver.isClosed()){
    packet.setLength(data.length);
    receiver.receive(packet);
    if(!local.contains(packet.getAddress()))continue;
    int[][] zones=AirFrame.decode(data,packet.getLength());
    if(zones==null)continue;
    if(!configurationRecorded){
     context.getSharedPreferences("air_setup_state",0).edit().putBoolean("configured",true).apply();
     configurationRecorded=true;
    }
    latest=new Frame(zones);
    firstFrame.countDown();
    frames++;
    long now=SystemClock.elapsedRealtime();
    if(now-window>=5000){
     Diagnostics.event(diagnosticId,"direct.air.window","frames="+frames+" window_ms="+(now-window)+" zones=10");
     frames=0;window=now;
    }
   }
  }catch(IOException error){
   if(!receiver.isClosed()){
    receiveError=error;
    Diagnostics.error(diagnosticId,"direct.air.receive.error",error);
   }
  }finally{firstFrame.countDown();}
 }
 boolean receivingColors(){
  Frame frame=latest;
  if(!fresh(frame))return false;
  for(int[] zone:frame.zones)
   if(zone[0]!=0||zone[1]!=0||zone[2]!=0)return true;
  return false;
 }
 private boolean fresh(Frame frame){
  return receiveError==null&&frame!=null&&SystemClock.elapsedRealtime()-frame.time<=MAX_FRAME_AGE_MS;
 }
 public int[][] read()throws Exception{
  Frame frame=latest;
  if(!fresh(frame))
   throw new Unavailable();
  int[][] rgb=new int[lamps.size()][];
  for(int i=0;i<rgb.length;i++)rgb[i]=frame.zones[lamps.get(i).angle];
  return smoother.apply(rgb,System.nanoTime());
 }
 public void restore(String marker)throws IOException{
  if(!MARKER.equals(marker))throw new IOException("Unknown Air recovery record");
  if(socket!=null)socket.close();
  if(lock!=null&&lock.isHeld())lock.release();
 }
}
