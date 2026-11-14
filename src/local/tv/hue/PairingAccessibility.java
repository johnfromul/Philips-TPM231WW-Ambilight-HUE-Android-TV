package local.tv.hue;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;

/** Optional remote-control assistance for the Philips Ambilight Air setup wizard. */
public final class PairingAccessibility extends AccessibilityService {
 private static PairingAccessibility instance;
 private AirWizardRemote airRemote;

 static boolean ready(){return instance!=null;}

 static void disableAfterPairing(){
  PairingAccessibility service=instance;
  if(service==null)return;
  try{service.disableSelf();}
  catch(RuntimeException error){Diagnostics.error("pairing","accessibility.disable.failed",error);}
 }

 @Override protected void onServiceConnected(){
  instance=this;
  AccessibilityServiceInfo info=getServiceInfo();
  if(info!=null){
   info.eventTypes=0;
   setServiceInfo(info);
  }
 }

 static void setAirRemote(boolean active){
  PairingAccessibility service=instance;
  if(service==null)return;
  if(!active){service.closeAirRemote();return;}
  if(service.airRemote==null){
   service.airRemote=new AirWizardRemote(service);
   service.airRemote.start();
  }
 }

 private void closeAirRemote(){
  if(airRemote!=null){airRemote.close();airRemote=null;}
 }

 @Override public void onAccessibilityEvent(AccessibilityEvent event){}
 @Override public void onInterrupt(){}

 @Override public void onDestroy(){
  closeAirRemote();
  if(instance==this)instance=null;
  super.onDestroy();
 }
}
