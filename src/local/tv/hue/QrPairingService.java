package local.tv.hue;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public final class QrPairingService extends Service {
 static final int READY=1,SUCCESS=2,FAILED=3;
 static final String CONSENT="projection_consent",RESULT="pairing_result";
 private final Handler main=new Handler(Looper.getMainLooper());
 private HandlerThread readerThread;
 private ImageReader reader;
 private VirtualDisplay display;
 private MediaProjection projection;
 private ResultReceiver result;
 private boolean finished;
 private volatile boolean decoded;
 private long lastFrame;

 @Override public IBinder onBind(Intent intent){return null;}

 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent==null){stopSelf();return START_NOT_STICKY;}
  if(result!=null)return START_NOT_STICKY;
  result=intent.getParcelableExtra(RESULT);
  if(result==null){stopSelf();return START_NOT_STICKY;}
  String label=SetupStrings.get(AppLanguage.wrap(this),"tv_qr_wait");
  NotificationManager notifications=getSystemService(NotificationManager.class);
  notifications.createNotificationChannel(new NotificationChannel("tv_qr",label,NotificationManager.IMPORTANCE_LOW));
  startForeground(4202,new Notification.Builder(this,"tv_qr").setSmallIcon(android.R.drawable.ic_menu_camera)
      .setContentTitle("Ambilight + Hue").setContentText(label).build());
  try{
   projection=getSystemService(MediaProjectionManager.class).getMediaProjection(
       Activity.RESULT_OK,intent.getParcelableExtra(CONSENT));
   if(projection==null)throw new IllegalStateException("Capture permission unavailable");
   projection.registerCallback(new MediaProjection.Callback(){
    @Override public void onStop(){if(!decoded)complete(FAILED);}
   },main);
   DisplayMetrics metrics=new DisplayMetrics();
   getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(metrics);
   int scale=Math.max(1,metrics.widthPixels/1500);
   int width=metrics.widthPixels/scale,height=metrics.heightPixels/scale;
   readerThread=new HandlerThread("hue-qr-reader");
   readerThread.start();
   reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
   reader.setOnImageAvailableListener(this::onFrame,new Handler(readerThread.getLooper()));
   display=projection.createVirtualDisplay("Hue pairing QR",width,height,metrics.densityDpi/scale,
       DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,null);
   if(display==null)throw new IllegalStateException("Capture display unavailable");
   result.send(READY,null);
   main.postDelayed(()->complete(FAILED),12000);
  }catch(RuntimeException error){
   Diagnostics.error("pairing","pair.qr.capture.error",error);
   complete(FAILED);
  }
  return START_NOT_STICKY;
 }

 private void onFrame(ImageReader source){
  String pin=null;
  try(Image image=source.acquireLatestImage()){
   if(image==null||decoded||SystemClock.elapsedRealtime()-lastFrame<250)return;
   lastFrame=SystemClock.elapsedRealtime();
   pin=PhilipsPairingQr.read(image);
  }catch(RuntimeException error){
   if(!decoded)main.post(()->{
    if(finished)return;
    Diagnostics.error("pairing","pair.qr.frame.error",error);
    complete(FAILED);
   });
  }
  if(pin!=null){
   decoded=true;
   String code=pin;
   main.post(()->authorize(code));
  }
 }

 private void authorize(String pin){
  if(finished)return;
  main.removeCallbacksAndMessages(null);
  stopCapture();
  Diagnostics.event("pairing","pair.qr.decoded","capture_stopped=true");
  new Thread(()->{
   try{
    AppPairing.grant(this,pin);
    Diagnostics.event("pairing","pair.qr.verified","stored=true");
    main.post(()->complete(SUCCESS));
   }catch(Exception error){
    Diagnostics.error("pairing","pair.qr.grant.error",error);
    main.post(()->complete(FAILED));
   }
  },"hue-qr-authorize").start();
 }

 private void complete(int code){
  if(finished)return;
  finished=true;
  stopCapture();
  if(result!=null)result.send(code,null);
  stopSelf();
 }

 private void stopCapture(){
  if(display!=null){display.release();display=null;}
  if(reader!=null){
   ImageReader owned=reader;
   owned.setOnImageAvailableListener(null,null);
   reader=null;
   new Handler(readerThread.getLooper()).post(owned::close);
  }
  if(projection!=null){projection.stop();projection=null;}
  if(readerThread!=null){readerThread.quitSafely();readerThread=null;}
 }

 @Override public void onDestroy(){
  finished=true;
  main.removeCallbacksAndMessages(null);
  stopCapture();
  super.onDestroy();
 }
}
