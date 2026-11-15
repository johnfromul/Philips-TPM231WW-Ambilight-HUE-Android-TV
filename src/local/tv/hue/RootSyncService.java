package local.tv.hue;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.os.SystemClock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns a single synchronization session independently of the setup screen. */
public final class RootSyncService extends Service {
 static final String START="local.tv.hue.root.START", STOP="local.tv.hue.root.STOP";
 static final String AIR_ACTIVATION="local.tv.hue.root.AIR_ACTIVATION";
 private static volatile String state="off";
 private static volatile boolean usingAir;
 private static volatile AirStatusMonitor airStatus;
 private static volatile String failureKey="sync_failed";
 private final AtomicBoolean stopRequested=new AtomicBoolean();
 private Thread worker;

 static String state(){return state;}
 static boolean airEnabledInferred(){
  AirStatusMonitor monitor=airStatus;
  return "running".equals(state)&&monitor!=null&&monitor.confirmed();
 }
 static String failureKey(){return failureKey;}
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public int onStartCommand(Intent intent,int flags,int id){
  String action=intent==null?"":intent.getAction();
  if(STOP.equals(action)){
   stopRequested.set(true);
   if(worker==null)stopSelf();
   return START_NOT_STICKY;
  }
  if(!START.equals(action)){stopSelf();return START_NOT_STICKY;}
  if(worker!=null)return START_NOT_STICKY;
  NotificationManager notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  String channel="root_sync";
  android.content.Context localized=AppLanguage.wrap(this);
  notifications.createNotificationChannel(new NotificationChannel(channel,DirectText.get(localized,"notification_channel"),NotificationManager.IMPORTANCE_LOW));
  Intent open=new Intent(this,MainActivity.class);
  PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification notification=new Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_media_play)
   .setContentTitle("Ambilight + Hue").setContentText(DirectText.get(localized,"option")).setContentIntent(pending).setOngoing(true).build();
  startForeground(4201,notification);
  stopRequested.set(false);failureKey="sync_failed";state="starting";
  ResultReceiver activation=intent.getParcelableExtra(AIR_ACTIVATION);
  worker=new Thread(()->runSession(activation),"root-sync");
  worker.start();
  return START_NOT_STICKY;
 }
 private final class AirStatusMonitor implements Runnable {
  private final HueEntertainmentApi api;
  private final String area;
  private final AmbilightAirSource source;
  private volatile long confirmedAt=-1;

  AirStatusMonitor(HueEntertainmentApi api,String area,AmbilightAirSource source){
   this.api=api;this.area=area;this.source=source;
  }

  boolean confirmed(){
   long time=confirmedAt;
   return !stopRequested.get()&&time>=0&&SystemClock.elapsedRealtime()-time<=10000
       &&source.receivingColors();
  }

  @Override public void run(){
   try{
    while(!stopRequested.get()&&!Thread.currentThread().isInterrupted()){
     try{
      confirmedAt=api.owner(area)==EntertainmentLease.Owner.OURS?SystemClock.elapsedRealtime():-1;
     }catch(Exception error){
      confirmedAt=-1;
     }
     Thread.sleep(5000);
    }
   }catch(InterruptedException error){
    Thread.currentThread().interrupt();
   }
  }
 }

 private final class TvStatusMonitor implements Runnable {
  private static final long MAX_UNVERIFIED_MS=15000;
  private static final long AIR_API_RECOVERY_MS=45000;
  private final String id;
  private volatile long lastVerified=android.os.SystemClock.elapsedRealtime();
  private volatile long unavailableSince=-1;
  private volatile String confirmedStop;
  TvStatusMonitor(String id){this.id=id;}
  String stopReason(){
   if(confirmedStop!=null)return confirmedStop;
   long since=usingAir?unavailableSince:lastVerified;
   long limit=usingAir?AIR_API_RECOVERY_MS:MAX_UNVERIFIED_MS;
   return since>=0&&android.os.SystemClock.elapsedRealtime()-since>=limit
       ?"tv_status_unverified":null;
  }
  @Override public void run(){
   long delay=5000;
   boolean retry=false;
   while(!stopRequested.get()&&!Thread.currentThread().isInterrupted()){
    String check="native_sync";
    try{
     Thread.sleep(delay);
     TvApiClient tv=new TvApiClient(RootSyncService.this,id);
     tv.limitOperationTime(2000);
     String nativePower=tv.getPower();
     if("On".equals(nativePower)){confirmedStop="native_sync_enabled";return;}
     check="ambilight";
     tv.limitOperationTime(2000);
     String ambilight=tv.getAmbilightPower();
     if("Off".equals(ambilight)){confirmedStop="ambilight_off";return;}
     if(!"Off".equals(nativePower)||!"On".equals(ambilight))
      throw new java.io.IOException("TV status unavailable");
     lastVerified=android.os.SystemClock.elapsedRealtime();
     if(retry)Diagnostics.event(id,"direct.tv_status.recovered","outage_ms="+(lastVerified-unavailableSince));
     unavailableSince=-1;
     retry=false;delay=5000;
    }catch(InterruptedException e){
     Thread.currentThread().interrupt();return;
    }catch(Exception e){
     if(Thread.currentThread().isInterrupted()||stopRequested.get())return;
     if(!retry){
      unavailableSince=android.os.SystemClock.elapsedRealtime();
      Diagnostics.error(id,"direct.tv_status."+check+".retry",e);
     }
     retry=true;delay=500;
    }
   }
  }
 }

 private boolean activateAir(ResultReceiver activation,String diagnosticId){
  if(activation==null||stopRequested.get())return false;
  CountDownLatch reply=new CountDownLatch(1);
  AtomicBoolean accepted=new AtomicBoolean();
  Bundle data=new Bundle();
  data.putParcelable(AirWizardActivation.REPLY,new ResultReceiver(null){
   @Override protected void onReceiveResult(int code,Bundle ignored){
    accepted.set(code==1);
    reply.countDown();
   }
  });
  activation.send(0,data);
  try{
   boolean started=reply.await(800,TimeUnit.MILLISECONDS)&&accepted.get();
   Diagnostics.event(diagnosticId,"air.activation.requested","wizard_started="+started);
   return started;
  }catch(InterruptedException e){
   Thread.currentThread().interrupt();
   return false;
  }
 }

 private void runSession(ResultReceiver activation){
  RootSyncEngine engine=null;
  Thread monitorThread=null;
  Thread airStatusThread=null;
  String diagnosticId=Diagnostics.id();String stopReason="requested";
  String stage="recovery_check";
  try{
   if(SettingsBackup.hasInterruptedRestore(this))throw new IllegalStateException("Restore rollback required");
   if(AirSetupActivity.isRunning()){stage="air_setup";throw new IllegalStateException("Finish Ambilight Air setup first");}
   if(RootRecovery.pending(this))throw new IllegalStateException("Recovery required");
   DirectBridgeProfiles.migrate(this);SharedPreferences prefs=DirectBridgeProfiles.current(this);
   String host=prefs.getString("host",""),key=prefs.getString("token",""),client=prefs.getString("clientkey",""),bridge=prefs.getString("mac","");
   stage="native_sync";if(!"Off".equals(new TvApiClient(this).getPower()))throw new IllegalStateException("Native synchronization is active or unavailable");
   stage="tv_ambilight";if(!"On".equals(new TvApiClient(this).getAmbilightPower()))throw new IllegalStateException("TV Ambilight is off or unavailable");
   RootSyncPreferences saved=new RootSyncPreferences(this,bridge);
   stage="lights_configuration";
   List<RootSyncCore.Lamp> lamps=saved.loadConfiguration();
   stage="area_configuration";
   String area=saved.loadArea();
   stage="bridge_identity";
   HueEntertainmentApi api=new HueEntertainmentApi(this,host,key);
   api.verifyBridge(bridge);
   stage="area_mapping";
   EntertainmentMapping.Entry[] mapping=api.mapping(area,lamps);
   org.json.JSONObject metadata=api.areaDetails(area).optJSONObject("metadata");
   String areaName=metadata==null?"":metadata.optString("name");
   java.util.List<Integer> lightIds=new java.util.ArrayList<>();
   for(RootSyncCore.Lamp lamp:lamps)lightIds.add(lamp.id);
   Diagnostics.event(diagnosticId,"direct.area.selected","area="+area+" name="+areaName+" light_ids="+lightIds);
   boolean air=getSharedPreferences("direct_tuning",0).getBoolean("use_air",true);
   usingAir=air;
   RootSyncCore.Source source=air?new AmbilightAirSource(this,()->activateAir(activation,diagnosticId),stopRequested::get):new DirectTvApiSource(this);
   Diagnostics.event(diagnosticId,"direct.source.selected","source="+(air?"ambilight_air":"tv_api_measured"));
   engine=new RootSyncEngine(this,host,key,client,bridge,area,lamps,mapping,source);
   if(!stopRequested.get()){
    stage="engine_start";
    engine.start();
    Diagnostics.event(diagnosticId,"direct.session.started","lights="+lamps.size());
    state="running";
    if(source instanceof AmbilightAirSource){
     AirStatusMonitor statusMonitor=new AirStatusMonitor(api,area,(AmbilightAirSource)source);
     airStatus=statusMonitor;
     airStatusThread=new Thread(statusMonitor,"direct-air-status");
     airStatusThread.start();
    }
    stage="frame_stream";
    long window=System.nanoTime(),total=0,max=0;int frames=0;long next=System.nanoTime();
    TvStatusMonitor monitor=new TvStatusMonitor(diagnosticId);
    monitorThread=new Thread(monitor,"direct-tv-status");
    monitorThread.start();
    while(!stopRequested.get()){
     if(!((android.os.PowerManager)getSystemService(POWER_SERVICE)).isInteractive()){stopReason="screen_not_interactive";break;}
     String statusStop=monitor.stopReason();
     if(statusStop!=null){stopReason=statusStop;break;}
     long begin=System.nanoTime();
     engine.tick();
     long elapsed=System.nanoTime()-begin;
     total+=elapsed;max=Math.max(max,elapsed);frames++;
     long now=System.nanoTime();
     if(now-window>=5000000000L){
      android.util.Log.i("RootSyncPerf","frames="+frames+" window_ms="+((now-window)/1000000L)+" avg_tick_ms="+(total/frames/1000000L)+" max_tick_ms="+(max/1000000L));
      Diagnostics.event(diagnosticId,"direct.stream.window","successful_ticks="+frames+" window_ms="+((now-window)/1000000L)+" avg_tick_ms="+(total/frames/1000000L)+" max_tick_ms="+(max/1000000L));
      window=now;total=0;max=0;frames=0;
     }
     next+=40000000L;long delay=next-System.nanoTime();if(delay>0)java.util.concurrent.TimeUnit.NANOSECONDS.sleep(delay);else next=System.nanoTime();
    }
   }
  }catch(InterruptedException e){
   if(!stopRequested.get()){
    Diagnostics.error(diagnosticId,"direct.interrupted",e);
    state="error";stopReason="interrupted";
   }
  }catch(Exception e){
   stopReason="error_at_"+stage;
   android.util.Log.w("RootSync","Failed at "+stage+" ("+e.getClass().getSimpleName()+")");
   Diagnostics.error("root","root."+stage+".error",e);
   if(e instanceof AmbilightAirSource.Unavailable)failureKey="air_unavailable";
   else if("air_setup".equals(stage))failureKey="finish_air_setup";
   else if("area_configuration".equals(stage))failureKey="area";
   else if("native_sync".equals(stage))failureKey="native_off";
   else if("tv_ambilight".equals(stage))failureKey="ambilight_on";
   else failureKey="sync_failed";
   state="error";
  }
  finally{
   airStatus=null;
   if(airStatusThread!=null)airStatusThread.interrupt();
   if(monitorThread!=null)monitorThread.interrupt();
   if(engine!=null)try{
    engine.stop(!"error".equals(state));
    Diagnostics.event(diagnosticId,"direct.cleanup.completed","recovery_pending="+RootRecovery.pending(this));
   }catch(Exception e){
    Diagnostics.error(diagnosticId,"direct.cleanup.error",e);
    failureKey="recover_failed";state="error";
   }
   if(RootRecovery.pending(this)){state="recovery";failureKey="recovery";}
   else if(!"error".equals(state))state="off";
   usingAir=false;
   Diagnostics.event(diagnosticId,"direct.session.ended","reason="+stopReason+" state="+state+" recovery_pending="+RootRecovery.pending(this));
   android.util.Log.i("DirectSyncTest","final_state="+state+" recovery_pending="+RootRecovery.pending(this));
   new android.os.Handler(getMainLooper()).post(()->{worker=null;stopSelf();});
  }
 }
 @Override public void onDestroy(){stopRequested.set(true);super.onDestroy();}
}
