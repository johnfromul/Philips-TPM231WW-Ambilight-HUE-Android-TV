package local.tv.hue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.widget.TextView;

public final class QrPairingActivity extends Activity {
 private static final int CAPTURE_CONSENT=1;
 private static final int PHILIPS_QR=2;
 private static final ComponentName QR=new ComponentName("org.droidtv.xtv","org.droidtv.xtv.QrCodeActivity");
 private boolean finished,serviceStarted;

 @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppLanguage.wrap(base));}

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  TextView message=new TextView(this);
  message.setTextSize(22);message.setPadding(48,32,48,32);
  message.setText(SetupStrings.get(this,"tv_qr_wait"));
  setContentView(message);
  if(state!=null){
   finish();
   return;
  }
  if(new Intent().setComponent(QR).resolveActivity(getPackageManager())==null){
   complete(false,"tv_qr_unavailable");
   return;
  }
  AlertDialog dialog=new AlertDialog.Builder(this).setMessage(SetupStrings.get(this,"tv_qr_consent"))
      .setPositiveButton(android.R.string.ok,(d,w)->requestConsent())
      .setNegativeButton(Strings.get(this,"cancel"),(d,w)->finish())
      .setOnCancelListener(d->finish()).show();
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
 }

 private void requestConsent(){
  try{
   MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
   startActivityForResult(manager.createScreenCaptureIntent(),CAPTURE_CONSENT);
  }catch(RuntimeException error){fail(error);}
 }

 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=CAPTURE_CONSENT)return;
  if(result!=RESULT_OK||data==null){complete(false,"tv_qr_cancelled");return;}
  ResultReceiver receiver=new ResultReceiver(new Handler(getMainLooper())){
   @Override protected void onReceiveResult(int code,Bundle ignored){
    if(isFinishing()||isDestroyed())return;
    if(code==QrPairingService.READY){
     try{startActivityForResult(new Intent("org.droidtv.action.PTA_QRCODE").setComponent(QR),PHILIPS_QR);}
     catch(RuntimeException error){fail(error);}
    }else complete(code==QrPairingService.SUCCESS,
        code==QrPairingService.SUCCESS?"tv_qr_success":"tv_qr_failed");
   }
  };
  try{
   startForegroundService(new Intent(this,QrPairingService.class)
       .putExtra(QrPairingService.CONSENT,data).putExtra(QrPairingService.RESULT,receiver));
   serviceStarted=true;
  }catch(RuntimeException error){fail(error);}
 }

 private void fail(RuntimeException error){
  Diagnostics.error("pairing","pair.qr.activity.error",error);
  complete(false,"tv_qr_unavailable");
 }

 private void complete(boolean success,String message){
  if(finished)return;
  finished=true;
  if(success)PairingAccessibility.disableAfterPairing();
  finishActivity(PHILIPS_QR);
  setResult(success?RESULT_OK:RESULT_CANCELED,new Intent().putExtra("message",message));
  finish();
 }

 @Override protected void onDestroy(){
  if(serviceStarted)stopService(new Intent(this,QrPairingService.class));
  super.onDestroy();
 }
}
