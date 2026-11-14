package local.tv.hue;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.concurrent.CompletableFuture;

/** Assists the user during a requested Philips TV PIN or Ambilight Air setup. */
public final class PairingAccessibility extends AccessibilityService {
 private static volatile PairingAccessibility instance;
 private static CompletableFuture<String> pending;
 private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
 static boolean ready(){return instance!=null;}
 private static volatile int observedAirSwitch=-1;
 static int airSwitchState(){return instance==null?-1:observedAirSwitch;}
 private static final String SETTINGS="com.android.tv.settings";
 private static final String[] PIN_PACKAGES={
  "org.droidtv.tvsystemui","org.droidtv.xtv","org.droidtv.tv.tptvservice","android"
 };
 static synchronized CompletableFuture<String> arm(){
  if(pending!=null)pending.cancel(false);
  pending=new CompletableFuture<>();
  monitorAirSwitch();
  return pending;
 }
 static synchronized void disarm(CompletableFuture<String> request){
  if(pending==request){
   pending=null;
   request.cancel(false);
   monitorAirSwitch();
  }
 }
 @Override protected void onServiceConnected(){instance=this;monitorAirSwitch();}
 static void monitorAirSwitch(){
  PairingAccessibility service=instance;
  if(service==null)return;
  if(android.os.Looper.myLooper()==service.handler.getLooper())service.updateEventPackages();
  else service.handler.post(service::updateEventPackages);
 }
 private void updateEventPackages(){
  if(instance!=this)return;
  android.accessibilityservice.AccessibilityServiceInfo info=getServiceInfo();
  if(info==null)return;
  boolean pairing;
  synchronized(PairingAccessibility.class){pairing=pending!=null&&!pending.isDone();}
  if(pairing){
   info.packageNames=new String[PIN_PACKAGES.length+1];
   info.packageNames[0]=SETTINGS;
   System.arraycopy(PIN_PACKAGES,0,info.packageNames,1,PIN_PACKAGES.length);
  }else{
   info.packageNames=new String[]{SETTINGS};
  }
  info.eventTypes=pairing?AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED|AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:0;
  setServiceInfo(info);
 }
 private AirWizardRemote airRemote;
 private Runnable airEnablePoll;
 static void setAirRemote(boolean active){
  PairingAccessibility service=instance;
  if(service==null)return;
  if(active){
   if(service.airRemote==null){
    service.airRemote=new AirWizardRemote(service);
    service.airRemote.start();
   }
  }else service.closeAirRemote();
 }
 /** Opens the native Air settings and enables its switch when it is off. */
 static boolean enableAir(android.content.Context context){
  PairingAccessibility service=instance;
  if(service==null)return false;
  try{context.startActivity(new android.content.Intent("android.settings.AMBILIGHT_AIR").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));}
  catch(RuntimeException e){return false;}
  service.requestAirEnable();
  return true;
 }
 private void requestAirEnable(){
  if(airEnablePoll!=null)handler.removeCallbacks(airEnablePoll);
  final long deadline=android.os.SystemClock.uptimeMillis()+8000L;
  airEnablePoll=new Runnable(){public void run(){
   if(instance!=PairingAccessibility.this||android.os.SystemClock.uptimeMillis()>deadline){airEnablePoll=null;return;}
   AccessibilityNodeInfo root=getRootInActiveWindow();boolean done=false;
   if(root!=null){try{
    if("com.android.tv.settings".equals(String.valueOf(root.getPackageName()))){
     java.util.List<AccessibilityNodeInfo> switches=root.findAccessibilityNodeInfosByViewId("android:id/switch_widget");
     if(!switches.isEmpty()){
      AccessibilityNodeInfo sw=switches.get(0);
      if(sw.isChecked()){recordAirSwitch(true);done=true;performGlobalAction(GLOBAL_ACTION_BACK);}
      else {AccessibilityNodeInfo parent=sw.getParent();
       if(parent!=null&&parent.isClickable())done=parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
       if(!done&&sw.isClickable())done=sw.performAction(AccessibilityNodeInfo.ACTION_CLICK);
       if(done){recordAirSwitch(true);Diagnostics.event("air","air.switch.auto_enabled","source=accessibility");performGlobalAction(GLOBAL_ACTION_BACK);}
      }
     }
    }
   }finally{root.recycle();}}
   if(!done)handler.postDelayed(this,250L);else airEnablePoll=null;
  }};
  handler.post(airEnablePoll);
 }
 private void closeAirRemote(){
  if(airRemote!=null){airRemote.close();airRemote=null;}
 }
 @Override public void onAccessibilityEvent(AccessibilityEvent event){
  CompletableFuture<String> request; synchronized(PairingAccessibility.class){request=pending;}
  if(request==null||request.isDone()||!isPinPackage(String.valueOf(event.getPackageName())))return;
  Diagnostics.event("pairing","pin.window","package="+event.getPackageName()+" event="+event.getEventType());
  StringBuilder eventText=new StringBuilder();for(CharSequence value:event.getText())eventText.append(value).append('\n');capture(request,eventText);
  scan(request);handler.postDelayed(()->scan(request),200);
 }
 private void recordAirSwitch(boolean enabled){
  observedAirSwitch=enabled?1:0;
  getSharedPreferences("air_setup_state",0).edit()
   .putInt("observed_switch",observedAirSwitch).putLong("observed_at",System.currentTimeMillis()).apply();
 }
 private void scan(CompletableFuture<String> request){
  synchronized(PairingAccessibility.class){if(pending!=request||request.isDone())return;}
  AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return;
  try{String pkg=String.valueOf(root.getPackageName());if(!isPinPackage(pkg))return;StringBuilder text=new StringBuilder();collect(root,text,0);
   capture(request,text);
  }finally{root.recycle();}
 }
 private static boolean isPinPackage(String name){
  for(String pkg:PIN_PACKAGES)if(pkg.equals(name))return true;
  return false;
 }
 private void capture(CompletableFuture<String> request,CharSequence text){
  String pin=PairingPin.extract(text);
  if(pin!=null&&request.complete(pin))monitorAirSwitch();
 }
 private void collect(AccessibilityNodeInfo node,StringBuilder text,int depth){if(depth>25||text.length()>8192)return;if(node.getText()!=null)text.append(node.getText()).append('\n');for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null){try{collect(child,text,depth+1);}finally{child.recycle();}}}}
 @Override public void onInterrupt(){}
 @Override public void onDestroy(){closeAirRemote();instance=null;observedAirSwitch=-1;airEnablePoll=null;handler.removeCallbacksAndMessages(null);synchronized(PairingAccessibility.class){if(pending!=null){pending.cancel(false);pending=null;}}super.onDestroy();}
}
