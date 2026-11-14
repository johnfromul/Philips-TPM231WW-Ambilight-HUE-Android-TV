package local.tv.hue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Transport-independent session coordinator. It never assumes stop or restore succeeded. */
final class RootSyncCore {
 interface Store {
  boolean pending() throws Exception;
  void save(Map<Integer,String> snapshot,String sourceSnapshot) throws Exception;
  Map<Integer,String> load() throws Exception;
  String loadSource() throws Exception;
  void clear() throws Exception;
 }
 interface Lights {
  String capture(int id) throws Exception;
  void restore(int id, String snapshot) throws Exception;
  default void validate(List<Lamp> lamps) throws Exception {}
  default void start(List<Lamp> lamps) throws Exception {}
  /** Stop any active stream before issuing light restoration commands. */
  default void stop() throws Exception {}
  /** One coherent batch; implementations must not send one REST request per light per frame. */
  void applyFrame(List<Lamp> lamps, int[][] rgb) throws Exception;
 }
 interface Source {
  String capture() throws Exception;
  void start(List<Lamp> lamps) throws Exception;
  int[][] read() throws Exception;
  void restore(String snapshot) throws Exception;
 }
 static final class Lamp {
  final int id, angle, distance, brightness;
  Lamp(int id,int angle,int distance,int brightness) {
   if(id<=0||angle<0||angle>9||distance<0||distance>3||brightness<1||brightness>10) throw new IllegalArgumentException("Invalid lamp configuration");
   this.id=id;this.angle=angle;this.distance=distance;this.brightness=brightness;
  }
 }
 private final Store store; private final Lights lights; private final Source source;
 private List<Lamp> active=Collections.emptyList(); private boolean running;
 RootSyncCore(Store store,Lights lights,Source source){this.store=store;this.lights=lights;this.source=source;}

 synchronized void start(List<Lamp> selection) throws Exception {
  if(running || store.pending()) throw new IllegalStateException("Recovery required before starting");
  if(selection==null||selection.isEmpty()||selection.size()>10) throw new IllegalArgumentException("Select 1–10 lights");
  List<Lamp> checked=new ArrayList<>(selection); Map<Integer,String> snapshots=new LinkedHashMap<>();
  for(Lamp lamp:checked){if(lamp==null || snapshots.containsKey(lamp.id))throw new IllegalArgumentException("Duplicate or invalid light");snapshots.put(lamp.id,"");}
  lights.validate(Collections.unmodifiableList(checked));
  for(Lamp lamp:checked){String value=lights.capture(lamp.id);if(value==null||value.isEmpty())throw new IllegalStateException("Light state unavailable");snapshots.put(lamp.id,value);}
  String sourceSnapshot=source.capture();
  if(sourceSnapshot==null||sourceSnapshot.isEmpty())throw new IllegalStateException("Source state unavailable");
  store.save(Collections.unmodifiableMap(snapshots),sourceSnapshot); // Must be durable before any output or source configuration.
  try {source.start(Collections.unmodifiableList(checked));lights.start(Collections.unmodifiableList(checked));active=Collections.unmodifiableList(checked);running=true;}
  catch(Exception e){try{recover();}catch(Exception restore){e.addSuppressed(restore);}throw e;}
 }

 synchronized void tick() throws Exception {
  if(!running)throw new IllegalStateException("Not running");
  try {
   int[][] values=source.read();
   if(values==null||values.length<active.size())throw new IllegalStateException("Incomplete pixel data");
   for(int i=0;i<active.size();i++){
    int[] rgb=values[i];if(rgb==null||rgb.length!=3)throw new IllegalStateException("Invalid pixel data");
    for(int value:rgb)if(value<0||value>65535)throw new IllegalStateException("Invalid colour value");
   }
   int[][] frame=new int[active.size()][3];
   for(int i=0;i<active.size();i++)System.arraycopy(values[i],0,frame[i],0,3);
   lights.applyFrame(active,frame);
  } catch(Exception e) {try{recover();}catch(Exception restore){e.addSuppressed(restore);}throw e;}
 }

 synchronized void stop() throws Exception {if(!running&&!store.pending())return;recover();}
 synchronized void stopNormally() throws Exception {
  if(!running)return;
  running=false;
  active=Collections.emptyList();
  lights.stop();
  source.restore(store.loadSource());
  store.clear();
 }
 synchronized void recover() throws Exception {
  running=false;active=Collections.emptyList();
  if(!store.pending())return;
  String sourceSnapshot=store.loadSource();
  Map<Integer,String> snapshots=store.load();
  Exception error=null;
  boolean streamStopped=false;
  try{lights.stop();streamStopped=true;}catch(Exception e){error=e;}
  try{source.restore(sourceSnapshot);}catch(Exception e){if(error==null)error=e;else error.addSuppressed(e);}
  if(streamStopped)for(Map.Entry<Integer,String> entry:snapshots.entrySet())try{lights.restore(entry.getKey(),entry.getValue());}catch(Exception e){if(error==null)error=e;else error.addSuppressed(e);}
  if(error!=null)throw error; // Leave recovery record intact for retry.
  store.clear();
 }
}
