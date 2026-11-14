package local.tv.hue;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns a single synchronization session independently of the setup screen. */
public final class RootSyncService extends Service {
 static final String START="local.tv.hue.root.START", STOP="local.tv.hue.root.STOP";
 private static volatile String state="off";
 private static volatile String failureKey="sync_failed";
 private final AtomicBoolean stopRequested=new AtomicBoolean();
 private Thread worker;

 static String state(){return state;}
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
  worker=new Thread(()->runSession(id),"root-sync");
  worker.start();
  return START_NOT_STICKY;
 }
 private final class TvStatusMonitor implements Runnable {
  private static final long MAX_UNVERIFIED_MS=15000;
  private final String id;
  private volatile long lastVerified=android.os.SystemClock.elapsedRealtime();
  private volatile String confirmedStop;
  TvStatusMonitor(String id){this.id=id;}
  String stopReason(){
   if(confirmedStop!=null)return confirmedStop;
   return android.os.SystemClock.elapsedRealtime()-lastVerified>=MAX_UNVERIFIED_MS
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
     if(retry)Diagnostics.event(id,"direct.tv_status.recovered","verified=true");
     retry=false;delay=5000;
    }catch(InterruptedException e){
     Thread.currentThread().interrupt();return;
    }catch(Exception e){
     if(Thread.currentThread().isInterrupted()||stopRequested.get())return;
     Diagnostics.error(id,"direct.tv_status."+check+".retry",e);
     retry=true;delay=500;
    }
   }
  }
 }

 private void runSession(int id){
  RootSyncEngine engine=null;
  Thread monitorThread=null;
  String diagnosticId=Diagnostics.id();String stopReason="requested";
  String stage="recovery_check";
  try{
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
   RootSyncCore.Source source=air?new AmbilightAirSource(this):new DirectTvApiSource(this);
   Diagnostics.event(diagnosticId,"direct.source.selected","source="+(air?"ambilight_air":"tv_api_measured"));
   engine=new RootSyncEngine(this,host,key,client,bridge,area,lamps,mapping,source);
   if(!stopRequested.get()){
    stage="engine_start";
    engine.start();
    Diagnostics.event(diagnosticId,"direct.session.started","lights="+lamps.size());
    state="running";
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
   Diagnostics.event(diagnosticId,"direct.session.ended","reason="+stopReason+" state="+state+" recovery_pending="+RootRecovery.pending(this));
   android.util.Log.i("DirectSyncTest","final_state="+state+" recovery_pending="+RootRecovery.pending(this));
   new android.os.Handler(getMainLooper()).post(()->{worker=null;stopSelf();});
  }
 }
 @Override public void onDestroy(){stopRequested.set(true);super.onDestroy();}
}
