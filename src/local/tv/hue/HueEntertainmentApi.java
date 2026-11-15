package local.tv.hue;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Authenticated CLIP v2 calls. No credentials are sent over HTTP or written to diagnostics. */
final class HueEntertainmentApi implements EntertainmentLease.Bridge {
 private static final String BASE="/clip/v2/resource/entertainment_configuration";
 private final String host,applicationKey;private final BridgeTransport transport;
 private String applicationId;
 HueEntertainmentApi(Context activity,String host,String applicationKey){
  if(host==null||!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")||applicationKey==null||!applicationKey.matches("[A-Za-z0-9_-]{8,128}"))throw new IllegalArgumentException("Invalid Bridge credentials");
  this.host=host;this.applicationKey=applicationKey;transport=new BridgeTransport(activity,true);
 }
 String applicationId() throws Exception {
  if(applicationId!=null)return applicationId;
  HttpURLConnection c=open("/auth/v1");
  try {
   c.setRequestMethod("GET");int code=c.getResponseCode();if(code!=200)throw new IOException("Bridge authentication failed (HTTP "+code+")");
   String value=c.getHeaderField("hue-application-id");readLimited(c.getInputStream());
   if(value==null||!value.matches("(?i)[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IOException("Bridge did not provide a valid application identity");
   applicationId=value.toLowerCase(Locale.ROOT);return applicationId;
  } finally {c.disconnect();}
 }
 JSONArray areas() throws Exception {return request("GET",BASE,null).getJSONArray("data");}
 void verifyBridge(String bridgeId)throws Exception{
  String expected=bridgeId==null?"":bridgeId.replace(":","").toLowerCase(Locale.ROOT);
  if(!expected.matches("[0-9a-f]{12}|[0-9a-f]{16}"))throw new IOException("Invalid stored Bridge identity");
  JSONObject config=request("GET","/api/config",null);
  String mac=config.optString("mac").replace(":","").toLowerCase(Locale.ROOT);
  String id=config.optString("bridgeid").replace(":","").toLowerCase(Locale.ROOT);
  if(!expected.equals(mac)&&!expected.equals(id))throw new IOException("Recovery belongs to a different Bridge");
 }
 void verifyMapping(String areaId,List<RootSyncCore.Lamp> lamps,EntertainmentMapping.Entry[] mapping)throws Exception {
  JSONArray areaData=request("GET",BASE+"/"+areaId(areaId),null).getJSONArray("data");
  if(areaData.length()!=1)throw new IOException("Entertainment area unavailable");
  JSONArray channels=areaData.getJSONObject(0).getJSONArray("channels");
  verifyResources(channels,lightResources(),request("GET","/clip/v2/resource/entertainment",null).getJSONArray("data"),lamps,mapping);
 }
 static void verifyResources(JSONArray channels,JSONArray lights,JSONArray services,List<RootSyncCore.Lamp> lamps,EntertainmentMapping.Entry[] mapping)throws Exception {
  if(channels.length()!=mapping.length||channels.length()!=lamps.size())throw new IOException("Each Entertainment channel must map to one selected light");
  Map<Integer,Integer> channelToLamp=channelLights(channels,lights,services);
  HashSet<Integer> matched=new HashSet<>();
  for(EntertainmentMapping.Entry entry:mapping){
   if(entry==null||entry.pixelIndex<1||entry.pixelIndex>lamps.size())throw new IOException("Invalid pixel mapping");
   int lamp=lamps.get(entry.pixelIndex-1).id;Integer actual=channelToLamp.get(entry.channelId);
   if(actual==null||actual!=lamp||!matched.add(lamp))throw new IOException("Entertainment channels do not match selected lights");
  }
 }
 private static Map<Integer,Integer> channelLights(JSONArray channels,JSONArray lights,JSONArray services)throws Exception {
  Map<String,Integer> ownerToLamp=new HashMap<>();
  for(int i=0;i<lights.length();i++){
   JSONObject light=lights.getJSONObject(i),owner=light.optJSONObject("owner");String v1=light.optString("id_v1");
   if(owner==null||!v1.matches("/lights/[1-9][0-9]*"))continue;
   int id=Integer.parseInt(v1.substring(8));String device=owner.optString("rid");
   if(device.isEmpty()||ownerToLamp.put(device,id)!=null)throw new IOException("Ambiguous device-to-light mapping");
  }
  Map<String,Integer> serviceToLamp=new HashMap<>();
  for(int i=0;i<services.length();i++){
   JSONObject service=services.getJSONObject(i),owner=service.optJSONObject("owner");if(owner==null)continue;
   Integer lamp=ownerToLamp.get(owner.optString("rid"));if(lamp!=null)serviceToLamp.put(service.getString("id"),lamp);
  }
  Map<Integer,Integer> channelToLamp=new HashMap<>();
  for(int i=0;i<channels.length();i++){
   JSONObject channel=channels.getJSONObject(i);int channelId=channel.getInt("channel_id");JSONArray members=channel.getJSONArray("members");
   if(members.length()!=1||members.getJSONObject(0).getInt("index")!=0)throw new IOException("Multi-segment Entertainment lights are unsupported");
   JSONObject service=members.getJSONObject(0).getJSONObject("service");
   if(!"entertainment".equals(service.optString("rtype")))throw new IOException("Unexpected Entertainment member");
   Integer lamp=serviceToLamp.get(service.optString("rid"));if(lamp==null||channelToLamp.put(channelId,lamp)!=null)throw new IOException("Entertainment channel identity unavailable");
  }
  return channelToLamp;
 }
 JSONObject areaDetails(String id)throws Exception {
  JSONArray data=request("GET",BASE+"/"+areaId(id),null).getJSONArray("data");
  if(data.length()!=1)throw new IOException("Entertainment area unavailable");
  return data.getJSONObject(0);
 }
 HashSet<Integer> areaLightIds(String id)throws Exception {
  Map<Integer,Integer> channels=channelLights(areaDetails(id).getJSONArray("channels"),lightResources(),
      request("GET","/clip/v2/resource/entertainment",null).getJSONArray("data"));
  HashSet<Integer> ids=new HashSet<>(channels.values());
  if(ids.isEmpty()||ids.size()>10||ids.size()!=channels.size())throw new IOException("Unsupported Entertainment area");
  return ids;
 }
 JSONArray lightResources() throws Exception {return request("GET","/clip/v2/resource/light",null).getJSONArray("data");}
 JSONObject legacyLights()throws Exception {
  try{return request("GET","/api/"+applicationKey+"/lights",null);}
  catch(Exception e){throw new IOException("Light list unavailable");}
 }
 EntertainmentMapping.Entry[] mapping(String areaId,List<RootSyncCore.Lamp> lamps)throws Exception {
  JSONArray areas=request("GET",BASE+"/"+areaId(areaId),null).getJSONArray("data");
  if(areas.length()!=1)throw new IOException("Entertainment area unavailable");
  JSONArray channels=areas.getJSONObject(0).getJSONArray("channels");
  JSONArray lights=lightResources();
  JSONArray services=request("GET","/clip/v2/resource/entertainment",null).getJSONArray("data");
  return resolveMapping(channels,lights,services,lamps);
 }
 static EntertainmentMapping.Entry[] resolveMapping(JSONArray channels,JSONArray lights,JSONArray services,List<RootSyncCore.Lamp> lamps)throws Exception {
  Map<Integer,Integer> channelToLamp=channelLights(channels,lights,services);
  EntertainmentMapping.Entry[] result=new EntertainmentMapping.Entry[channels.length()];
  for(int i=0;i<channels.length();i++){
   JSONObject channel=channels.getJSONObject(i);
   Integer lamp=channelToLamp.get(channel.getInt("channel_id"));int pixel=-1;
   for(int j=0;j<lamps.size();j++)if(lamp!=null&&lamps.get(j).id==lamp)pixel=j;
   if(pixel<0)throw new IOException("Channel has no selected lamp");
   result[i]=new EntertainmentMapping.Entry(channel.getInt("channel_id"),pixel+1,lamps.get(pixel).brightness);
  }
  verifyResources(channels,lights,services,lamps,result);
  return result;
 }
 JSONObject lightResource(String id) throws Exception {
  JSONArray data=request("GET","/clip/v2/resource/light/"+areaId(id),null).getJSONArray("data");
  if(data.length()!=1)throw new IOException("Light resource unavailable");return data.getJSONObject(0);
 }
 void updateLight(String id,JSONObject payload)throws Exception {
  JSONArray data=request("PUT","/clip/v2/resource/light/"+areaId(id),payload.toString()).getJSONArray("data");
  if(data.length()==0)throw new IOException("Light update not acknowledged");
 }
 JSONObject legacyLight(int id)throws Exception {
  if(id<=0)throw new IllegalArgumentException("Invalid light ID");
  // The key is part of the V1 URL; never propagate an exception containing that URL.
  try {return request("GET","/api/"+applicationKey+"/lights/"+id,null);}
  catch(Exception e){throw new IOException("Legacy light state unavailable");}
 }
 @Override public EntertainmentLease.Owner owner(String areaId) throws Exception {
  String id=areaId(areaId);JSONArray data=request("GET",BASE+"/"+id,null).getJSONArray("data");
  if(data.length()!=1)return EntertainmentLease.Owner.UNKNOWN;
  JSONObject area=data.getJSONObject(0);if(!id.equalsIgnoreCase(area.optString("id")))return EntertainmentLease.Owner.UNKNOWN;
  String state=area.optString("status");if("inactive".equals(state))return EntertainmentLease.Owner.FREE;
  if(!"active".equals(state))return EntertainmentLease.Owner.UNKNOWN;
  JSONObject owner=area.optJSONObject("active_streamer");if(owner==null||!"auth_v1".equals(owner.optString("rtype")))return EntertainmentLease.Owner.UNKNOWN;
  String ownerId=owner.optString("rid");if(ownerId.isEmpty())return EntertainmentLease.Owner.UNKNOWN;
  return ownerId.equalsIgnoreCase(applicationId())?EntertainmentLease.Owner.OURS:EntertainmentLease.Owner.OTHER;
 }
 @Override public void start(String areaId)throws Exception {action(areaId,"start");}
 @Override public void stop(String areaId)throws Exception {action(areaId,"stop");}
 private void action(String areaId,String action)throws Exception {
  JSONObject response=request("PUT",BASE+"/"+areaId(areaId),new JSONObject().put("action",action).toString());
  if(response.optJSONArray("data")==null||response.getJSONArray("data").length()==0)throw new IOException("Bridge did not acknowledge Entertainment action");
 }
 private HttpURLConnection open(String path)throws Exception {
  HttpURLConnection c=transport.open(host,path);
  if(!(c instanceof HttpsURLConnection)){c.disconnect();throw new IOException("Entertainment requires HTTPS and trusted Bridge certificate");}
  c.setConnectTimeout(6000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
  if(!"/api/config".equals(path))c.setRequestProperty("hue-application-key",applicationKey);
  c.setRequestProperty("Accept","application/json");return c;
 }
 private JSONObject request(String method,String path,String body)throws Exception {
  HttpURLConnection c=open(path);
  try {
   c.setRequestMethod(method);
   if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
   int status=c.getResponseCode();if(status!=200)throw new IOException("Bridge request failed (HTTP "+status+")");
   JSONObject response=new JSONObject(new String(readLimited(c.getInputStream()),StandardCharsets.UTF_8));
   JSONArray errors=response.optJSONArray("errors");if(path.startsWith("/clip/")&&(errors==null||errors.length()!=0))throw new IOException("Bridge request rejected");return response;
  } finally {c.disconnect();}
 }
 private static String areaId(String id){if(id==null||!id.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid area ID");return id.toLowerCase(Locale.ROOT);}
 private static byte[] readLimited(InputStream in)throws IOException {try(InputStream stream=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=stream.read(b))!=-1){out.write(b,0,n);if(out.size()>262144)throw new IOException("Bridge response too large");}return out.toByteArray();}}
}
