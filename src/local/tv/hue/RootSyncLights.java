package local.tv.hue;

import java.util.List;

/** Bridges the recovery coordinator to our own Hue light and stream implementations. */
final class RootSyncLights implements RootSyncCore.Lights {
 interface Restorer {String capture(int id)throws Exception;void restore(int id,String snapshot)throws Exception;}
 interface Stream {void validate(List<RootSyncCore.Lamp> lamps,EntertainmentMapping.Entry[] mapping)throws Exception;void start()throws Exception;void stop()throws Exception;void send(EntertainmentMapping.Entry[] mapping,int[][] pixels)throws Exception;}
 private final Restorer restorer;private final Stream stream;private final EntertainmentMapping.Entry[] mapping;
 private long lastMeasure;
 RootSyncLights(HueLightRestorer lights,HueEntertainmentSession session,EntertainmentMapping.Entry[] map){
  this(new Restorer(){public String capture(int id)throws Exception{return lights.capture(id);}public void restore(int id,String s)throws Exception{lights.restore(id,s);}},
   new Stream(){public void validate(List<RootSyncCore.Lamp> lamps,EntertainmentMapping.Entry[] m)throws Exception{session.verifyMapping(lamps,m);}public void start()throws Exception{session.start();}public void stop()throws Exception{session.close();}public void send(EntertainmentMapping.Entry[] m,int[][] p)throws Exception{session.send(m,p);}},map);
 }
 RootSyncLights(Restorer lights,Stream stream,EntertainmentMapping.Entry[] map){
  if(lights==null||stream==null||map==null||map.length==0)throw new IllegalArgumentException("Missing light adapter");
  this.restorer=lights;this.stream=stream;this.mapping=map.clone();
  EntertainmentMapping.channels(mapping,new int[10][3]); // Validate before any hardware changes.
 }
 public String capture(int id)throws Exception{return restorer.capture(id);}
 public void restore(int id,String snapshot)throws Exception{restorer.restore(id,snapshot);}
 public void validate(List<RootSyncCore.Lamp> lamps)throws Exception{
  for(EntertainmentMapping.Entry e:mapping)if(e.pixelIndex>lamps.size())throw new IllegalArgumentException("Mapping refers to unconfigured pixel");
  stream.validate(lamps,mapping);
 }
 public void start(List<RootSyncCore.Lamp> lamps)throws Exception{stream.start();}
 public void stop()throws Exception{stream.stop();}
 public void applyFrame(List<RootSyncCore.Lamp> lamps,int[][] rgb)throws Exception{
  if(rgb==null||rgb.length!=lamps.size()||rgb.length>10)throw new IllegalArgumentException("Invalid pixel frame");
  int[][] pixels=new int[10][3];
  for(int i=0;i<rgb.length;i++){
   if(rgb[i]==null||rgb[i].length!=3)throw new IllegalArgumentException("Invalid pixel");
   System.arraycopy(rgb[i],0,pixels[i],0,3);
  }
  stream.send(mapping,pixels);
  long now=android.os.SystemClock.elapsedRealtime();if(now-lastMeasure>=1000){lastMeasure=now;HueStreamV2Frame.Channel[] actual=EntertainmentMapping.channels(mapping,pixels);StringBuilder text=new StringBuilder("wall_ms=").append(System.currentTimeMillis()).append(" elapsed_ms=").append(now);for(int i=0;i<mapping.length;i++){EntertainmentMapping.Entry e=mapping[i];HueStreamV2Frame.Channel c=actual[i];text.append(" light=").append(lamps.get(e.pixelIndex-1).id).append(" channel=").append(c.id).append(" rgb=").append(c.red).append(',').append(c.green).append(',').append(c.blue);}android.util.Log.i("SpectrumDirect",text.toString());}
 }
}
