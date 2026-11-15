package local.tv.hue;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

final class SettingsBackup {
 private static final String[] STORES={"app_language","air_setup_state","hue_bridge",
     "bridge_profiles","bridge_tls","direct_bridge","direct_tuning","root_sync_private",
     "light_drafts",TvApiClient.PREFS};
 private static final String[] NATIVE_TABLES={"bridge","lamps","groups"};
 private static final String[] CREDENTIALS={"user","key","ip"};

 private SettingsBackup(){}

 static void checkIdle(Context context)throws IOException{
  String state=RootSyncService.state();
  if(RootSessionGate.busy()||RootRecovery.pending(context)||RootSyncService.sessionActive()||"recovery".equals(state)||AirSetupActivity.isRunning()
      ||context.getSharedPreferences("native_setup_recovery",0).contains("snapshot"))
   throw new IOException("Synchronization or recovery in progress");
 }

 static byte[] export(Context context)throws Exception{
  checkIdle(context);
  if(hasInterruptedRestore(context))throw new IOException("Restore rollback required");
  try(NativeLink link=new NativeLink(context)){
   return encode(snapshot(context,link));
  }
 }

 static JSONObject read(InputStream stream)throws Exception{
  if(stream==null)throw new IOException("Backup stream unavailable");
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  byte[] buffer=new byte[8192];
  int count;
  while((count=stream.read(buffer))!=-1){
   if(count>BackupCipher.MAX_FILE_BYTES-out.size())throw new IOException("Backup too large");
   out.write(buffer,0,count);
  }
  return decode(out.toByteArray());
 }

 static void restore(Context context,JSONObject incoming)throws Exception{
  checkIdle(context);
  if(hasInterruptedRestore(context))throw new IOException("Restore rollback required");
  if("true".equals(UiStatus.nativeActiveValue())||
      (UiStatus.paired(context)&&!"Off".equals(new TvApiClient(context).getPower())))
   throw new IOException("Native synchronization must be off");
  try(NativeLink link=new NativeLink(context)){
   link.awaitFree();
   AtomicFile rollback=journal(context);
   writeJournal(rollback,encode(snapshot(context,link)));
   try{
    apply(context,link,incoming);
    rollback.delete();
   }catch(Exception error){
    try{apply(context,link,decode(rollback.readFully()));rollback.delete();}
    catch(Exception failed){error.addSuppressed(failed);}
    throw error;
   }
  }
 }

 static boolean hasInterruptedRestore(Context context){return journal(context).getBaseFile().exists();}

 static void recover(Context context)throws Exception{
  checkIdle(context);
  AtomicFile rollback=journal(context);
  if(!rollback.getBaseFile().exists())return;
  JSONObject previous=decode(rollback.readFully());
  try(NativeLink link=new NativeLink(context)){
   link.awaitFree();
   apply(context,link,previous);
   rollback.delete();
  }
 }

 private static JSONObject snapshot(Context context,NativeLink link)throws Exception{
  JSONObject preferences=new JSONObject();
  for(String store:STORES){
   JSONObject values=new JSONObject();
   for(Map.Entry<String,?> entry:context.getSharedPreferences(store,0).getAll().entrySet()){
    Object value=entry.getValue();
    if(value instanceof Float)value=new JSONObject().put("float",value);
    else if(value instanceof Long)value=new JSONObject().put("long",value);
    values.put(entry.getKey(),value);
   }
   preferences.put(store,values);
  }
  JSONObject nativeState=link.snapshot("Off");
  for(String name:CREDENTIALS)if(!nativeState.has(name))nativeState.put(name,JSONObject.NULL);
  return new JSONObject().put("version",1).put("preferences",preferences).put("native",nativeState);
 }

 private static void apply(Context context,NativeLink link,JSONObject backup)throws Exception{
  JSONObject nativeState=backup.getJSONObject("native");
  link.restore(nativeState);
  if(!link.rows("HueBridgeInfo").toString().equals(nativeState.getJSONArray("bridge").toString())||
     !link.rows("HueGroupInfo").toString().equals(nativeState.getJSONArray("groups").toString()))
   throw new IOException("Native settings verification failed");
  JSONObject preferences=backup.getJSONObject("preferences");
  for(String store:STORES){
   JSONObject values=preferences.getJSONObject(store);
   SharedPreferences.Editor editor=context.getSharedPreferences(store,0).edit().clear();
   for(Iterator<String> it=values.keys();it.hasNext();){
    String name=it.next();
    Object value=values.get(name);
    if(value instanceof String)editor.putString(name,(String)value);
    else if(value instanceof Boolean)editor.putBoolean(name,(Boolean)value);
    else if(value instanceof Long)editor.putLong(name,(Long)value);
    else if(value instanceof Integer)editor.putInt(name,(Integer)value);
    else if(((JSONObject)value).has("long"))editor.putLong(name,((JSONObject)value).getLong("long"));
    else editor.putFloat(name,(float)((JSONObject)value).getDouble("float"));
   }
   if(!editor.commit())throw new IOException("Could not save settings");
  }
 }

 private static byte[] encode(JSONObject value)throws Exception{
  return BackupCipher.encrypt(value.toString().getBytes(StandardCharsets.UTF_8));
 }

 private static JSONObject decode(byte[] data)throws Exception{
  JSONObject backup=new JSONObject(new String(BackupCipher.decrypt(data),StandardCharsets.UTF_8));
  if(backup.getInt("version")!=1)throw new IOException("Unsupported backup version");
  JSONObject preferences=backup.getJSONObject("preferences");
  for(String store:STORES){
   JSONObject fields=preferences.getJSONObject(store);
   for(Iterator<String> it=fields.keys();it.hasNext();){
    Object value=fields.get(it.next());
    if(value instanceof String||value instanceof Boolean||value instanceof Integer||value instanceof Long)continue;
    if(value instanceof JSONObject){
     JSONObject number=(JSONObject)value;
     if(number.length()==1&&(number.opt("long") instanceof Number||
         Float.isFinite((float)number.optDouble("float"))))continue;
    }
    throw new IOException("Invalid preference");
   }
  }
  JSONObject state=backup.getJSONObject("native");
  for(String table:NATIVE_TABLES){
   JSONArray rows=state.getJSONArray(table);
   for(int i=0;i<rows.length();i++)rows.getJSONObject(i);
  }
  for(String name:CREDENTIALS){
   if(!state.has(name)||(!state.isNull(name)&&!(state.get(name) instanceof String)))
    throw new IOException("Invalid native credential");
  }
  return backup;
 }

 private static AtomicFile journal(Context context){
  return new AtomicFile(new File(context.getFilesDir(),"settings-restore-rollback"));
 }

 private static void writeJournal(AtomicFile journal,byte[] data)throws IOException{
  FileOutputStream stream=journal.startWrite();
  try{stream.write(data);journal.finishWrite(stream);}
  catch(IOException error){journal.failWrite(stream);throw error;}
 }
}
