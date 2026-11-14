package local.tv.hue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Conservative Hue CLIP v2 restoration commands, without network side effects. */
final class HueV2RestorePlan {
 static final class Snapshot {
  final boolean on; final double brightness, x, y; final int mirek; final String mode;
  Snapshot(boolean reachable, boolean on, double brightness, String mode, double x, double y, int mirek,
           String effect, String dynamics, boolean gradient) {
   if(!reachable || gradient || !"no_effect".equals(effect) || !"none".equals(dynamics))
    throw new IllegalArgumentException("Unsupported or unreachable light state");
   if(!Double.isFinite(brightness) || brightness<=0 || brightness>100)throw new IllegalArgumentException("Unrestorable brightness");
   if("xy".equals(mode)) {
    if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||y<0||x>1||y>1||x+y>1.000001)throw new IllegalArgumentException("Invalid xy");
   } else if("ct".equals(mode)) {
    if(mirek<1||mirek>1000)throw new IllegalArgumentException("Invalid temperature");
   } else throw new IllegalArgumentException("Unknown active color mode");
   this.on=on;this.brightness=brightness;this.mode=mode;this.x=x;this.y=y;this.mirek=mirek;
  }
 }
 private static Map<String,Object> obj(String key,Object value){
  Map<String,Object> m=new LinkedHashMap<>();m.put(key,value);return Collections.unmodifiableMap(m);
 }
 static List<Map<String,Object>> commands(Snapshot s) {
  if(s==null)throw new IllegalArgumentException("Missing snapshot");
  List<Map<String,Object>> result=new ArrayList<>();
  if(!s.on)result.add(obj("on",obj("on",false)));
  Map<String,Object> values=new LinkedHashMap<>();
  values.put("dimming",obj("brightness",s.brightness));
  if("xy".equals(s.mode)){
   Map<String,Object> xy=new LinkedHashMap<>();xy.put("x",s.x);xy.put("y",s.y);
   values.put("color",obj("xy",Collections.unmodifiableMap(xy)));
  } else values.put("color_temperature",obj("mirek",s.mirek));
  if(s.on)values.put("on",obj("on",true));
  result.add(Collections.unmodifiableMap(values));
  return Collections.unmodifiableList(result);
 }
}
