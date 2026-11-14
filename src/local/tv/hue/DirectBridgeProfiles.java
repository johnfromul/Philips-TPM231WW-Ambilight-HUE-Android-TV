package local.tv.hue;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/** Direct selection is private to this mode and never changes the native bridge. */
final class DirectBridgeProfiles {
 static final String PREFS="direct_bridge";
 static final class Profile {
  final String mac,host,name,token,client;
  Profile(String mac,String host,String name,String token,String client){this.mac=mac;this.host=host;this.name=name;this.token=token;this.client=client;}
  String label(){return name+" · "+host+" · "+mac;}
 }
 static SharedPreferences current(Context c){return c.getSharedPreferences(PREFS,0);}
 static String mac(String value){return value==null?"":value.replace(":","").toLowerCase(Locale.ROOT);}
 static Profile profile(String id,String host,String name,String token,String client){
  id=mac(id);if(!id.matches("[0-9a-f]{12}")||host==null||!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")||token==null||!token.matches("[A-Za-z0-9_-]{8,128}")||client==null||!client.matches("(?i)[0-9a-f]{32}"))return null;
  for(String part:host.split("\\."))if(Integer.parseInt(part)>255)return null;
  if(name==null||name.trim().isEmpty())name=id;return new Profile(id,host,name,token,client);
 }
 static Profile currentProfile(Context c){SharedPreferences p=current(c);return profile(p.getString("mac",""),p.getString("host",""),p.getString("name",""),p.getString("token",""),p.getString("clientkey",""));}
 static void migrate(Context c)throws Exception{
  SharedPreferences p=current(c);if(p.contains("mac"))return;
  SharedPreferences old=c.getSharedPreferences("hue_bridge",0);Profile v=profile(old.getString("mac",""),old.getString("host",""),old.getString("name",""),old.getString("token",""),old.getString("clientkey",""));
  if(v!=null)save(c,v);
 }
 static ArrayList<Profile> all(Context c)throws Exception{
  migrate(c);Map<String,Profile> values=new LinkedHashMap<>();
  Profile current=currentProfile(c);if(current!=null)values.put(current.mac,current);
  SharedPreferences all=c.getSharedPreferences("bridge_profiles",0);
  for(Map.Entry<String,?> e:all.getAll().entrySet())try{
   if(!(e.getValue() instanceof String))continue;JSONObject o=new JSONObject((String)e.getValue());
   Profile v=profile(e.getKey(),o.optString("host"),o.optString("name"),o.optString("token"),o.optString("clientkey"));
   if(v!=null&&!values.containsKey(v.mac))values.put(v.mac,v);
  }catch(Exception ignored){}
  return new ArrayList<>(values.values());
 }
 static void save(Context c,Profile v)throws Exception{
  if(v==null)throw new IllegalArgumentException("Invalid profile");
  if(!current(c).edit().putString("mac",v.mac).putString("host",v.host).putString("name",v.name).putString("token",v.token).putString("clientkey",v.client).commit())throw new IllegalStateException("Cannot save direct bridge");
 }
}
