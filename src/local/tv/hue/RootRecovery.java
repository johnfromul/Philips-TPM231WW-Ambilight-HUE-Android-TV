package local.tv.hue;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Recover a durable root session after the original engine/process has gone away. */
final class RootRecovery {
 private final Context activity;
 RootRecovery(Context activity){this.activity=activity;}
 static boolean pending(android.content.Context activity){return RootSyncPreferences.hasRecovery(activity)||new EntertainmentJournal(activity).pending();}
 void run()throws Exception {
  RootSessionGate.claim(this);
  try{recover();}finally{RootSessionGate.release(this);}
 }
 private void recover()throws Exception{
  EntertainmentJournal journal=new EntertainmentJournal(activity);
  if(!RootSyncPreferences.hasRecovery(activity)){
   if(journal.pending())throw new IOException("Entertainment marker exists without a light and HAL snapshot");
   return;
  }
  String bridge=RootSyncPreferences.recoveryBridge(activity);
  if(journal.pending()&&!bridge.equalsIgnoreCase(journal.bridge()))throw new IOException("Recovery records refer to different Bridges");
  RootSyncPreferences store=new RootSyncPreferences(activity,bridge);
  Map<Integer,String> snapshots=store.load();ArrayList<RootSyncCore.Lamp> dummy=new ArrayList<>();
  for(Integer id:snapshots.keySet())dummy.add(new RootSyncCore.Lamp(id,0,0,1));
  String marker=store.loadSource();
  RootSyncCore.Source source;
  if(DirectTvApiSource.MARKER.equals(marker))source=new DirectTvApiSource(activity);
  else if(AmbilightAirSource.MARKER.equals(marker))source=new AmbilightAirSource(activity);
  else throw new IOException("Unknown source recovery record");
  DirectBridgeProfiles.migrate(activity);SharedPreferences prefs=DirectBridgeProfiles.current(activity);
  String host=prefs.getString("host",""),key=prefs.getString("token",""),mac=prefs.getString("mac","").replace(":","").toLowerCase(Locale.ROOT);
  boolean paired=bridge.equals(mac)&&!host.isEmpty()&&!key.isEmpty();
  String pin=activity.getSharedPreferences("bridge_tls",0).getString(host,"");
  boolean pinned=pin.matches("[0-9a-f]{64}");
  HueEntertainmentApi api=paired&&pinned?new HueEntertainmentApi(activity,host,key):null;
  HueLightRestorer restorer=api==null?null:new HueLightRestorer(api);
  RootSyncCore.Lights lights=new RootSyncCore.Lights(){
   public String capture(int id){throw new UnsupportedOperationException();}
   public void applyFrame(List<RootSyncCore.Lamp> lamps,int[][] rgb){throw new UnsupportedOperationException();}
   public void stop()throws Exception{
    if(api==null)throw new IOException("Original Bridge pairing or certificate pin unavailable");
    api.verifyBridge(bridge);
    if(journal.pending()){
     EntertainmentLease.Stream noStream=new EntertainmentLease.Stream(){public void open(){}public void close(){}};
     new EntertainmentLease(bridge,journal.area(),journal,api,noStream).recover();
    }
   }
   public void restore(int id,String snapshot)throws Exception{restorer.restore(id,snapshot);}
  };
  new RootSyncCore(store,lights,source).recover();
 }
}
