package local.tv.hue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class DirectBootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
  boolean direct=RootSyncService.resumeRequested(context);
  if(!direct&&!UiStatus.paired(context))return;
  Intent start=direct
      ?new Intent(context,RootSyncService.class).setAction(RootSyncService.RESUME)
      :new Intent(context,NativeBootService.class);
  try{
   context.startForegroundService(start);
  }catch(RuntimeException e){
   Diagnostics.error("boot",direct?"direct.resume.boot_error":"native.resume.boot_error",e);
  }
 }
}
