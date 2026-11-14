package local.tv.hue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Merge legacy per-light updates into the latest Entertainment area frame. */
final class VirtualHueColors {
 private static final class Light {
  final int channel;
  Double x, y;
  Integer brightness;
  boolean on = true;
  double fromX, fromY, fromBrightness;
  long startNs, durationNs;

  Light(int channel) { this.channel = channel; }
 }

 private final Map<Integer, Light> lights = new LinkedHashMap<>();
 private final LongSupplier clock;

 VirtualHueColors(Map<Integer, Integer> lampToChannel) {
  this(lampToChannel, System::nanoTime);
 }
 VirtualHueColors(Map<Integer, Integer> lampToChannel, LongSupplier clock) {
  if(clock==null) throw new IllegalArgumentException("Clock required");
  this.clock=clock;
  if (lampToChannel.isEmpty() || lampToChannel.size() > HueStreamV2Frame.MAX_CHANNELS)
   throw new IllegalArgumentException("Invalid Entertainment mapping");
  boolean[] used = new boolean[256];
  lampToChannel.forEach((lamp, channel) -> {
   if (lamp < 1 || channel < 0 || channel > 255 || used[channel])
    throw new IllegalArgumentException("Invalid Entertainment channel");
   used[channel] = true;
   lights.put(lamp, new Light(channel));
  });
 }

 synchronized void update(int lamp, Double x, Double y, Integer brightness, Boolean on) {
  update(lamp,x,y,brightness,on,0);
 }
 synchronized void update(int lamp, Double x, Double y, Integer brightness, Boolean on, int transitionDs) {
  Light light = lights.get(lamp);
  validate(light,x,y,brightness,transitionDs);
  long now=clock.getAsLong();
  apply(light,x,y,brightness,on,transitionDs,now);
 }
 synchronized void updateGroup(java.util.List<Integer> ids,Double x,Double y,Integer brightness,Boolean on,int transitionDs){
  if(ids==null||ids.isEmpty()||new java.util.HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Invalid group");
  for(Integer id:ids)validate(lights.get(id),x,y,brightness,transitionDs);
  long now=clock.getAsLong();for(Integer id:ids)apply(lights.get(id),x,y,brightness,on,transitionDs,now);
 }
 private static void validate(Light light,Double x,Double y,Integer brightness,int transitionDs){
  if (light == null || (x == null) != (y == null)
    || (x != null && (!unit(x) || !unit(y) || x + y > 1.000001))
    || (brightness != null && (brightness < 0 || brightness > 254)) || transitionDs<0 || transitionDs>65535) {
   throw new IllegalArgumentException("Invalid legacy light update");
  }
 }
 private static void apply(Light light,Double x,Double y,Integer brightness,Boolean on,int transitionDs,long now){
  double[] current=sample(light,now);
  if(current!=null){light.fromX=current[0];light.fromY=current[1];light.fromBrightness=current[2];}
  if (x != null) {
   light.x = x;
   light.y = y;
  }
  if (brightness != null) light.brightness = brightness;
  if (on != null) light.on = on;
  light.startNs=now;light.durationNs=current==null?0:transitionDs*100000000L;
 }

 /** Caller seeds all channels with their initial state before sending the first frame. */
 synchronized HueStreamV2Frame.Channel[] snapshot() {
  HueStreamV2Frame.Channel[] frame = new HueStreamV2Frame.Channel[lights.size()];
  int index = 0;
  for (Light light : lights.values()) {
   if (light.x == null || light.brightness == null) return null;
   double[] value=sample(light,clock.getAsLong());
   frame[index++] = new HueStreamV2Frame.Channel(light.channel, scale(value[0]), scale(value[1]), scale(value[2]));
  }
  return frame;
 }

 private static double[] sample(Light light,long now){
  if(light.x==null||light.brightness==null)return null;
  double target=light.on?light.brightness/254.0:0;
  if(light.durationNs==0)return new double[]{light.x,light.y,target};
  double t=Math.max(0,Math.min(1,(double)(now-light.startNs)/light.durationNs));
  return new double[]{light.fromX+(light.x-light.fromX)*t,light.fromY+(light.y-light.fromY)*t,
   light.fromBrightness+(target-light.fromBrightness)*t};
 }

 private static boolean unit(double value) { return value >= 0 && value <= 1; }

 private static int scale(double value) { return (int) Math.round(value * 65535); }
}
