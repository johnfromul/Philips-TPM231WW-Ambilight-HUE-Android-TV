package local.tv.hue;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit assembly of the root source, Bridge session and durable recovery record. */
final class RootSyncEngine {
 private final RootSyncCore core;private final List<RootSyncCore.Lamp> lamps;
 RootSyncEngine(Context activity,String host,String appKey,String clientKey,String bridgeId,String areaId,
                List<RootSyncCore.Lamp> selection,EntertainmentMapping.Entry[] mapping,RootSyncCore.Source source){
  if(activity==null||selection==null)throw new IllegalArgumentException("Missing session configuration");
  lamps=Collections.unmodifiableList(new ArrayList<>(selection));
  RootSyncPreferences store=new RootSyncPreferences(activity,bridgeId);
  HueLightRestorer restorer=new HueLightRestorer(new HueEntertainmentApi(activity,host,appKey));
  HueEntertainmentSession session=new HueEntertainmentSession(activity,host,appKey,clientKey,bridgeId,areaId);
  core=new RootSyncCore(store,new RootSyncLights(restorer,session,mapping),source);
 }
 // All operations belong on one background worker. A failed recovery must be retried.
 void start()throws Exception{RootSessionGate.claim(this);try{core.start(lamps);}catch(Exception e){RootSessionGate.release(this);throw e;}}
 void tick()throws Exception{try{core.tick();}catch(Exception e){RootSessionGate.release(this);throw e;}}
 void stop(boolean normal)throws Exception{try{if(normal)core.stopNormally();else core.stop();}finally{RootSessionGate.release(this);}}
 void recover()throws Exception{RootSessionGate.claim(this);try{core.recover();}finally{RootSessionGate.release(this);}}
}
