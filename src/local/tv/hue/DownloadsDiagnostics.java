package local.tv.hue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Publishes the bounded private diagnostic history without blocking callers on MediaStore. */
final class DownloadsDiagnostics {
 private static final String NAME="ambilight-hue-diagnostics.jsonl";
 private static final String URI_KEY="downloads_uri";
 private final Context context;
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
 private final AtomicBoolean scheduled=new AtomicBoolean();
 private boolean reportedFailure;

 DownloadsDiagnostics(Context context){this.context=context.getApplicationContext();}

 void changed(){
  if(Build.VERSION.SDK_INT>=29&&scheduled.compareAndSet(false,true)){
   worker.schedule(this::publish,2,TimeUnit.SECONDS);
  }
 }

 private void publish(){
  // Events arriving after this point schedule the next snapshot.
  scheduled.set(false);
  try{
   byte[] snapshot=Diagnostics.export().getBytes(StandardCharsets.UTF_8);
   if(snapshot.length==0)return;
   ContentResolver resolver=context.getContentResolver();
   SharedPreferences prefs=context.getSharedPreferences("diagnostics_downloads",Context.MODE_PRIVATE);
   String saved=prefs.getString(URI_KEY,"");
   if(!saved.isEmpty()){
    try{
     write(resolver,Uri.parse(saved),snapshot);
     reportedFailure=false;
     return;
    }catch(FileNotFoundException|SecurityException stale){
     prefs.edit().remove(URI_KEY).apply();
    }
   }
   ContentValues values=new ContentValues();
   values.put(MediaStore.MediaColumns.DISPLAY_NAME,NAME);
   values.put(MediaStore.MediaColumns.MIME_TYPE,"application/x-ndjson");
   values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Ambilight Hue/");
   values.put(MediaStore.MediaColumns.IS_PENDING,1);
   Uri file=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
   if(file==null)throw new IOException("Download creation failed");
   boolean published=false;
   try{
    write(resolver,file,snapshot);
    values.clear();
    values.put(MediaStore.MediaColumns.IS_PENDING,0);
    if(resolver.update(file,values,null,null)!=1)throw new IOException("Download publication failed");
    prefs.edit().putString(URI_KEY,file.toString()).apply();
    published=true;
   }finally{
    if(!published)resolver.delete(file,null,null);
   }
   reportedFailure=false;
  }catch(IOException|RuntimeException error){
   if(!reportedFailure)Log.w("HueDiagnostics","diagnostic_download_failed "+error.getClass().getSimpleName());
   reportedFailure=true;
  }
 }

 private static void write(ContentResolver resolver,Uri file,byte[] data)throws IOException{
  try(OutputStream stream=resolver.openOutputStream(file,"rwt")){
   if(stream==null)throw new IOException("Download stream unavailable");
   stream.write(data);
  }
 }
}
