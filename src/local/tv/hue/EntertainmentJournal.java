package local.tv.hue;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Durable marker for a possibly claimed area; contains no Bridge credentials. */
final class EntertainmentJournal implements EntertainmentLease.Journal {
 private final SharedPreferences prefs;
 EntertainmentJournal(Context context){prefs=context.getApplicationContext().getSharedPreferences("root_entertainment_recovery",Context.MODE_PRIVATE);}
 @Override public synchronized boolean pending(){return prefs.contains("lease");}
 @Override public synchronized void record(String bridgeId,String areaId)throws Exception {
  if(pending())throw new IllegalStateException("Recovery already exists");
  if(bridgeId==null||!bridgeId.matches("(?i)[0-9a-f]{12}|[0-9a-f]{16}")||areaId==null||!areaId.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid identity");
  String record=new JSONObject().put("version",2).put("confirmed",false).put("bridge",bridgeId.toLowerCase(java.util.Locale.ROOT)).put("area",areaId.toLowerCase(java.util.Locale.ROOT)).toString();
  if(!prefs.edit().putString("lease",record).commit())throw new IllegalStateException("Lease record write failed");
 }
 @Override public synchronized boolean matches(String bridgeId,String areaId)throws Exception {
  String raw=prefs.getString("lease","");if(raw.isEmpty())return false;
  JSONObject record=read();return record.getString("bridge").equalsIgnoreCase(bridgeId)&&record.getString("area").equalsIgnoreCase(areaId);
 }
 private JSONObject read()throws Exception{
  String raw=prefs.getString("lease","");if(raw.isEmpty())throw new IllegalStateException("Missing Entertainment recovery");
  JSONObject record=new JSONObject(raw);int version=record.getInt("version");String bridge=record.getString("bridge"),area=record.getString("area");
  if((version!=1&&version!=2)||!bridge.matches("(?i)[0-9a-f]{12}|[0-9a-f]{16}")||!area.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalStateException("Invalid Entertainment recovery");
  return record;
 }
 synchronized String bridge()throws Exception{return read().getString("bridge");}
 synchronized String area()throws Exception{return read().getString("area");}
 @Override public synchronized void confirm()throws Exception{
  JSONObject record=read();record.put("version",2).put("confirmed",true);
  if(!prefs.edit().putString("lease",record.toString()).commit())throw new IllegalStateException("Lease confirmation write failed");
 }
 @Override public synchronized boolean confirmed()throws Exception{JSONObject record=read();return record.getInt("version")==2&&record.optBoolean("confirmed",false);}
 @Override public synchronized void clear(){if(!prefs.edit().remove("lease").commit())throw new IllegalStateException("Lease clear failed");}
}
