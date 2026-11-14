package local.tv.hue;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Own-Bridge light snapshots and checked restoration. Run on a worker thread. */
final class HueLightRestorer {
 interface Api {
  JSONArray lights() throws Exception;
  JSONObject light(String uuid) throws Exception;
  JSONObject legacy(int id) throws Exception;
  void update(String uuid,JSONObject command) throws Exception;
 }
 private final Api api;
 HueLightRestorer(HueEntertainmentApi bridge){this(new Api(){
  public JSONArray lights()throws Exception{return bridge.lightResources();}
  public JSONObject light(String id)throws Exception{return bridge.lightResource(id);}
  public JSONObject legacy(int id)throws Exception{return bridge.legacyLight(id);}
  public void update(String id,JSONObject c)throws Exception{bridge.updateLight(id,c);}
 });}
 HueLightRestorer(Api api){if(api==null)throw new IllegalArgumentException("Missing API");this.api=api;}

 String capture(int id)throws Exception {
  if(id<=0)throw new IllegalArgumentException("Invalid light ID");
  JSONArray resources=api.lights();JSONObject found=null;
  for(int i=0;i<resources.length();i++){
   JSONObject v=resources.getJSONObject(i);
   if(("/lights/"+id).equals(v.optString("id_v1"))){if(found!=null)throw new IOException("Ambiguous light mapping");found=v;}
  }
  if(found==null)throw new IOException("Light mapping missing");
  JSONObject legacy=api.legacy(id).getJSONObject("state");
  if(!legacy.optBoolean("reachable",false))throw new IOException("Light is unreachable");
  if(!"none".equals(legacy.optString("effect","")))throw new IOException("Unsupported legacy effect");
  boolean on=legacy.getBoolean("on");
  if(on!=found.getJSONObject("on").getBoolean("on"))throw new IOException("Light state changed while capturing");
  String mode=legacy.optString("colormode");
  JSONObject effects=found.optJSONObject("effects"),dynamics=found.optJSONObject("dynamics");
  String effect=effects==null?"no_effect":effects.optString("status","");
  String dynamic=dynamics==null?"none":dynamics.optString("status","");
  JSONObject xy=found.optJSONObject("color");xy=xy==null?null:xy.optJSONObject("xy");
  double x=xy==null?0:xy.getDouble("x"), y=xy==null?0:xy.getDouble("y");
  JSONObject temperature=found.optJSONObject("color_temperature");
  int mirek=temperature==null?0:temperature.optInt("mirek",0);
  if("xy".equals(mode)&&xy==null || "ct".equals(mode)&&(temperature==null||!temperature.optBoolean("mirek_valid",false)))throw new IOException("Active color state unavailable");
  double brightness=found.getJSONObject("dimming").getDouble("brightness");
  if(!legacy.has("bri")||Math.abs(legacy.getInt("bri")*100.0/254.0-brightness)>.8)throw new IOException("Light brightness changed while capturing");
  if("xy".equals(mode)){
   JSONArray old=legacy.getJSONArray("xy");
   if(old.length()!=2||Math.abs(old.getDouble(0)-x)>.003||Math.abs(old.getDouble(1)-y)>.003)throw new IOException("Light color changed while capturing");
  } else if("ct".equals(mode)&&legacy.getInt("ct")!=mirek)throw new IOException("Light temperature changed while capturing");
  HueV2RestorePlan.Snapshot checked=new HueV2RestorePlan.Snapshot(true,on,brightness,mode,x,y,mirek,effect,dynamic,found.has("gradient"));
  String uuid=uuid(found.getString("id"));
  return new JSONObject().put("version",1).put("id",id).put("uuid",uuid).put("on",checked.on)
   .put("brightness",checked.brightness).put("mode",checked.mode).put("x",checked.x).put("y",checked.y).put("mirek",checked.mirek).toString();
 }
 void restore(int id,String raw)throws Exception {
  JSONObject saved=new JSONObject(raw);
  if(saved.getInt("version")!=1||saved.getInt("id")!=id)throw new IOException("Snapshot does not match light");
  String uuid=uuid(saved.getString("uuid"));
  HueV2RestorePlan.Snapshot state=new HueV2RestorePlan.Snapshot(true,saved.getBoolean("on"),saved.getDouble("brightness"),saved.getString("mode"),saved.getDouble("x"),saved.getDouble("y"),saved.getInt("mirek"),"no_effect","none",false);
  JSONObject before=api.light(uuid);
  if(!("/lights/"+id).equals(before.optString("id_v1")))throw new IOException("Light identity changed");
  for(Map<String,Object> command:HueV2RestorePlan.commands(state))api.update(uuid,new JSONObject(command));
  long until=System.nanoTime()+6000000000L;
  while(true){
   JSONObject light=api.light(uuid),legacy=api.legacy(id).getJSONObject("state");
   if(matches(light,legacy,state))return;
   if(System.nanoTime()>=until)throw new IOException("Light restoration not verified");
   Thread.sleep(250);
  }
 }
 private static boolean matches(JSONObject v,JSONObject legacy,HueV2RestorePlan.Snapshot s)throws Exception {
  if(!legacy.optBoolean("reachable",false)||legacy.getBoolean("on")!=s.on||v.getJSONObject("on").getBoolean("on")!=s.on||!s.mode.equals(legacy.optString("colormode"))||!"none".equals(legacy.optString("effect")))return false;
  if(Math.abs(v.getJSONObject("dimming").getDouble("brightness")-s.brightness)>.15)return false;
  if("xy".equals(s.mode)){
   JSONObject color=v.getJSONObject("color").getJSONObject("xy");
   return Math.abs(color.getDouble("x")-s.x)<.002&&Math.abs(color.getDouble("y")-s.y)<.002;
  }
  return v.getJSONObject("color_temperature").optInt("mirek",0)==s.mirek;
 }
 private static String uuid(String id){
  if(id==null||!id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid light identifier");
  return id.toLowerCase(Locale.ROOT);
 }
}
