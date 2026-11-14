package local.tv.hue;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Separate private configuration and write-ahead recovery record, bound to one Bridge. */
final class RootSyncPreferences implements RootSyncCore.Store {
 private final SharedPreferences prefs;
 private final String bridge;
 static boolean hasRecovery(Context context){return context.getApplicationContext().getSharedPreferences("root_sync_private",Context.MODE_PRIVATE).contains("recovery");}
 static String recoveryBridge(Context context)throws Exception{
  String raw=context.getApplicationContext().getSharedPreferences("root_sync_private",Context.MODE_PRIVATE).getString("recovery","");
  if(raw.isEmpty())throw new IllegalStateException("Missing root recovery");
  JSONObject record=new JSONObject(raw);String id=record.getString("bridge").toLowerCase(Locale.ROOT);
  if(record.getInt("version")!=2||!id.matches("[0-9a-f]{12}|[0-9a-f]{16}"))throw new IllegalStateException("Invalid root recovery identity");
  return id;
 }
 RootSyncPreferences(Context context,String bridgeId) {
  bridge=bridgeId==null?"":bridgeId.replace(":","").toLowerCase(Locale.ROOT);
  if(!bridge.matches("[0-9a-f]{12}|[0-9a-f]{16}"))throw new IllegalArgumentException("Invalid bridge identity");
  prefs=context.getApplicationContext().getSharedPreferences("root_sync_private",Context.MODE_PRIVATE);
 }
 void saveConfiguration(List<RootSyncCore.Lamp> lamps) throws Exception {
  saveConfiguration(lamps, null);
 }
 void saveConfiguration(List<RootSyncCore.Lamp> lamps,String area) throws Exception {
  if(pending())throw new IllegalStateException("Recovery required before configuration changes");
  if(lamps==null||lamps.isEmpty()||lamps.size()>10)throw new IllegalArgumentException("Select 1–10 lights");
  JSONArray array=new JSONArray();java.util.HashSet<Integer> ids=new java.util.HashSet<>();
  for(RootSyncCore.Lamp l:lamps){if(l==null||!ids.add(l.id))throw new IllegalArgumentException("Invalid light");array.put(new JSONObject().put("id",l.id).put("angle",l.angle).put("distance",l.distance).put("brightness",l.brightness));}
  JSONObject value=new JSONObject().put("version",1).put("bridge",bridge).put("lamps",array);
  if(area!=null){
   if(!area.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid area");
   value.put("area",area);
  }
  SharedPreferences.Editor editor=prefs.edit().putString("config:"+bridge,value.toString());
  if(area!=null)editor.putString("config:"+bridge+":"+area,value.toString());
  if(!editor.commit())throw new IllegalStateException("Configuration write failed");
 }
 String loadArea()throws Exception{
  String raw=prefs.getString("config:"+bridge,"");
  if(raw.isEmpty())throw new IllegalStateException("Configure an Entertainment area");
  JSONObject value=new JSONObject(raw);
  if(value.getInt("version")!=1||!bridge.equals(value.getString("bridge")))throw new IllegalStateException("Configuration mismatch");
  String area=value.optString("area","");
  if(!area.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalStateException("Configure an Entertainment area");
  return area;
 }
 List<RootSyncCore.Lamp> loadConfiguration() throws Exception {
  return decodeConfiguration(prefs.getString("config:"+bridge,""));
 }
 List<RootSyncCore.Lamp> loadConfiguration(String area) throws Exception {
  String raw=prefs.getString("config:"+bridge+":"+area,"");
  if(raw.isEmpty()){
   String latest=prefs.getString("config:"+bridge,"");
   if(!latest.isEmpty()&&area.equals(new JSONObject(latest).optString("area")))raw=latest;
  }
  return decodeConfiguration(raw);
 }
 private List<RootSyncCore.Lamp> decodeConfiguration(String raw) throws Exception {
  if(raw.isEmpty())return new ArrayList<>();
  JSONObject value=new JSONObject(raw);if(value.getInt("version")!=1||!bridge.equals(value.getString("bridge")))throw new IllegalStateException("Configuration mismatch");
  JSONArray a=value.getJSONArray("lamps");if(a.length()<1||a.length()>10)throw new IllegalStateException("Invalid configuration");
  ArrayList<RootSyncCore.Lamp> result=new ArrayList<>();java.util.HashSet<Integer> ids=new java.util.HashSet<>();
  for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);RootSyncCore.Lamp l=new RootSyncCore.Lamp(v.getInt("id"),v.getInt("angle"),v.getInt("distance"),v.getInt("brightness"));if(!ids.add(l.id))throw new IllegalStateException("Duplicate light");result.add(l);}
  return result;
 }
 @Override public boolean pending(){return prefs.contains("recovery");}
 @Override public void save(Map<Integer,String> states,String sourceSnapshot) throws Exception {
  if(pending())throw new IllegalStateException("Recovery already exists");
  if(states.isEmpty()||states.size()>10||sourceSnapshot==null||sourceSnapshot.isEmpty()||sourceSnapshot.length()>65536)throw new IllegalArgumentException("Invalid snapshot");
  JSONArray array=new JSONArray();
  for(Map.Entry<Integer,String> e:states.entrySet()){
   if(e.getKey()<=0||e.getValue()==null||e.getValue().isEmpty()||e.getValue().length()>16384)throw new IllegalArgumentException("Invalid light state");
   array.put(new JSONObject().put("id",e.getKey()).put("state",e.getValue()));
  }
  JSONObject record=new JSONObject().put("version",2).put("bridge",bridge).put("lights",array).put("source",sourceSnapshot);
  if(!prefs.edit().putString("recovery",record.toString()).commit())throw new IllegalStateException("Recovery record write failed");
 }
 @Override public Map<Integer,String> load() throws Exception {
  String raw=prefs.getString("recovery","");if(raw.isEmpty())throw new IllegalStateException("Missing recovery");
  JSONObject record=new JSONObject(raw);
  if(record.getInt("version")!=2||!bridge.equals(record.getString("bridge")))throw new IllegalStateException("Recovery belongs to another bridge");
  JSONArray array=record.getJSONArray("lights");if(array.length()<1||array.length()>10)throw new IllegalStateException("Invalid recovery");
  LinkedHashMap<Integer,String> states=new LinkedHashMap<>();
  for(int i=0;i<array.length();i++){JSONObject v=array.getJSONObject(i);int id=v.getInt("id");String state=v.getString("state");if(id<=0||state.isEmpty()||state.length()>16384||states.put(id,state)!=null)throw new IllegalStateException("Invalid recovery light");}
  return states;
 }
 @Override public String loadSource() throws Exception {
  String raw=prefs.getString("recovery","");if(raw.isEmpty())throw new IllegalStateException("Missing recovery");
  JSONObject record=new JSONObject(raw);if(record.getInt("version")!=2||!bridge.equals(record.getString("bridge")))throw new IllegalStateException("Recovery belongs to another bridge");
  String source=record.getString("source");if(source.isEmpty()||source.length()>65536)throw new IllegalStateException("Invalid source state");return source;
 }
 @Override public void clear(){if(!prefs.edit().remove("recovery").commit())throw new IllegalStateException("Recovery clear failed");}
}
