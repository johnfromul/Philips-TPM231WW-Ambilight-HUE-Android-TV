package local.tv.hue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Lets an authorized ADB shell close the pairing receiver without stopping the app. */
public final class AirSetupStopReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  if("local.tv.hue.STOP_AIR_SETUP".equals(intent.getAction()))AirSetupActivity.stopCurrent();
 }
}
