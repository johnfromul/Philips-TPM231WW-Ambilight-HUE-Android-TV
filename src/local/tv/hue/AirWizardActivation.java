package local.tv.hue;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** The Philips wizard re-enables an existing Air configuration when its activity stops. */
final class AirWizardActivation {
 static final String REPLY="air_activation_reply";
 private static final long RETURN_DELAY_MS=2800;
 private static final long RETURN_TIMEOUT_MS=6500;
 private static final String PACKAGE="org.droidtv.ambiair";
 private static final String WIZARD=PACKAGE+".ambiairwizard.AmbiAirWizardActivity";
 private final Activity activity;
 private final Runnable finished;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private ActivityManager.AppTask task;
 private boolean returning;
 private boolean leftActivity;
 private volatile boolean complete;
 private final Runnable returnTask=this::returnToApp;

 private AirWizardActivation(Activity activity,Runnable finished){
  this.activity=activity;
  this.finished=finished;
 }

 static AirWizardActivation start(Activity activity,Runnable finished){
  if(activity.isFinishing()||activity.isDestroyed()||!activity.hasWindowFocus())return null;
  AirWizardActivation activation=new AirWizardActivation(activity,finished);
  return activation.launch()?activation:null;
 }

 private boolean launch(){
  ActivityManager manager=activity.getSystemService(ActivityManager.class);
  if(manager==null)return false;
  try{
   for(ActivityManager.AppTask ownedTask:manager.getAppTasks()){
    if(ownedTask.getTaskInfo().taskId==activity.getTaskId()){
     task=ownedTask;
     break;
    }
   }
   if(task==null)return false;
   Intent wizard=new Intent("org.droidtv.intent.action.ambiAir.LAUNCHWIZARD").setClassName(PACKAGE,WIZARD);
   wizard.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION);
   new Thread(() -> {
    try {
     // Rediscovery stores the real IPv4 address for the linked receiver.
     AirLocalDiscovery.advertise(activity,false,()->complete,()->{});
    } catch (java.io.IOException | InterruptedException error) {
     Diagnostics.event("air","air.activation.discovery.error",error.getClass().getSimpleName());
    }
   },"air-linked-discovery").start();
   activity.startActivity(wizard);
  }catch(RuntimeException error){
   complete=true;
   Diagnostics.error("air","air.activation.wizard.start_error",error);
   return false;
  }
  Diagnostics.event("air","air.activation.wizard.started","source=philips_wizard");
  handler.postDelayed(this::close,RETURN_TIMEOUT_MS);
  return true;
 }

 void onPause(){
  if(complete||leftActivity)return;
  leftActivity=true;
  handler.postDelayed(returnTask,RETURN_DELAY_MS);
 }

 void onWindowFocus(){
  if(leftActivity||returning)close();
 }

 private void returnToApp(){
  if(complete||activity.isFinishing()||activity.isDestroyed())return;
  if(activity.hasWindowFocus()){close();return;}
  returning=true;
  // Only return to our own task. Philips closes its wizard onStop and reuses its saved setup.
  try{task.moveToFront();}
  catch(RuntimeException error){Diagnostics.error("air","air.activation.wizard.return_error",error);close();}
 }

 void close(){
  if(complete)return;
  complete=true;
  handler.removeCallbacksAndMessages(null);
  finished.run();
 }
}
