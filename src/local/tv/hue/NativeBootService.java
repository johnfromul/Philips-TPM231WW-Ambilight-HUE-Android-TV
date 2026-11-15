package local.tv.hue;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

/** Restores Philips' own session on startup when the TV kept its Hue switch on. */
public final class NativeBootService extends Service {
 private static final String CHANNEL="native_boot";
 private static final long BOOT_WAIT_MS=90000;
 private static final long RETRY_MS=3000;
 private static final long CONFIRM_WAIT_MS=20000;
 private Thread worker;

 @Override public IBinder onBind(Intent intent){return null;}

 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(worker!=null)return START_NOT_STICKY;
  Diagnostics.init(this);
  String label=FeatureStrings.get(AppLanguage.wrap(this),"native_resume");
  NotificationManager notifications=getSystemService(NotificationManager.class);
  notifications.createNotificationChannel(new NotificationChannel(CHANNEL,label,NotificationManager.IMPORTANCE_LOW));
  startForeground(4202,new Notification.Builder(this,CHANNEL)
      .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Ambilight + Hue")
      .setContentText(label).setOngoing(true).build());
  worker=new Thread(()->{
   try{restoreNative();}
   catch(InterruptedException e){Thread.currentThread().interrupt();}
   finally{stopSelf();}
  },"native-hue-boot");
  worker.start();
  return START_NOT_STICKY;
 }

 private boolean blocked(){
  return RootSyncService.resumeRequested(this)||RootSyncService.sessionActive()
      ||RootSessionGate.busy()||RootRecovery.pending(this)||AirSetupActivity.isRunning()
      ||SettingsBackup.hasInterruptedRestore(this)
      ||getSharedPreferences("native_setup_recovery",0).contains("snapshot");
 }

 private boolean configured(){
  try(Cursor bridges=getContentResolver().query(Uri.withAppendedPath(NativeLink.BASE,"HueBridgeInfo"),
       new String[]{"MacAddr"},null,null,null);
      Cursor lights=getContentResolver().query(Uri.withAppendedPath(NativeLink.BASE,"HueLampsInfo"),
       new String[]{"LampID"},"ConfigureStatus=?",new String[]{"1"},null)){
   return bridges!=null&&bridges.moveToFirst()&&!bridges.isNull(0)&&!bridges.getString(0).isEmpty()
       &&lights!=null&&lights.moveToFirst();
  }
 }

 private void restoreNative()throws InterruptedException{
  String id=Diagnostics.id();
  long deadline=SystemClock.elapsedRealtime()+BOOT_WAIT_MS;
  PowerManager power=getSystemService(PowerManager.class);
  Diagnostics.event(id,"native.resume.boot","");
  while(!Thread.currentThread().isInterrupted()&&SystemClock.elapsedRealtime()<deadline){
   if(blocked()||!UiStatus.paired(this)){
    Diagnostics.event(id,"native.resume.skipped","reason=configuration_or_conflict");
    return;
   }
   if(power.isInteractive()){
    if("true".equals(UiStatus.nativeActiveValue())){
     Diagnostics.event(id,"native.resume.skipped","reason=already_active");
     return;
    }
    try{
     TvApiClient tv=new TvApiClient(this,id);
     tv.limitOperationTime(7000);
     String saved=tv.getPower();
     if("Off".equals(saved)){
      Diagnostics.event(id,"native.resume.skipped","reason=switch_off");
      return;
     }
     if("On".equals(saved)&&configured()){
      tv.limitOperationTime(7000);
      if("On".equals(tv.getAmbilightPower())){
       tv.limitOperationTime(4000);
       if(power.isInteractive()&&!blocked()&&"On".equals(tv.getPower())
           &&!"true".equals(UiStatus.nativeActiveValue())){
        NativeLink.notifyService(this,true);
        Diagnostics.event(id,"native.resume.requested","");
        confirmNative(id);
        return;
       }
      }
     }
    }catch(InterruptedException e){throw e;}
    catch(Exception e){Diagnostics.error(id,"native.resume.retry",e);}
   }
   Thread.sleep(RETRY_MS);
  }
  Diagnostics.event(id,"native.resume.timed_out","");
 }

 private void confirmNative(String id)throws InterruptedException{
  long deadline=SystemClock.elapsedRealtime()+CONFIRM_WAIT_MS;
  while(SystemClock.elapsedRealtime()<deadline){
   if("true".equals(UiStatus.nativeActiveValue())){
    Diagnostics.event(id,"native.resume.active","");
    return;
   }
   Thread.sleep(1000);
  }
  Diagnostics.event(id,"native.resume.unconfirmed","");
 }

 @Override public void onDestroy(){
  if(worker!=null)worker.interrupt();
  super.onDestroy();
 }
}
