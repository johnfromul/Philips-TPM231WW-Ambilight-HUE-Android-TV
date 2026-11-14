package local.tv.hue;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

/** Hue Entertainment v2 RGB and xy/brightness payloads. Transport and session ownership are separate. */
final class HueStreamV2Frame {
 private static final byte[] MAGIC="HueStream".getBytes(StandardCharsets.US_ASCII);
 static final int MAX_CHANNELS=20;
 static final class Channel {
  final int id,red,green,blue;
  Channel(int id,int red,int green,int blue){
   if(id<0||id>255||red<0||red>65535||green<0||green>65535||blue<0||blue>65535)throw new IllegalArgumentException("Invalid channel values");
   this.id=id;this.red=red;this.green=green;this.blue=blue;
  }
 }
 static byte[] encode(String areaId,int sequence,Channel[] channels){
  return encode(areaId,sequence,channels,0);
 }
 static byte[] encodeXy(String areaId,int sequence,Channel[] channels){
  return encode(areaId,sequence,channels,1);
 }
 private static byte[] encode(String areaId,int sequence,Channel[] channels,int colorSpace){
  if(areaId==null||!areaId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw new IllegalArgumentException("Invalid entertainment area ID");
  String normalized=UUID.fromString(areaId).toString().toLowerCase(Locale.ROOT);
  if(sequence<0||sequence>255||channels==null||channels.length<1||channels.length>MAX_CHANNELS)throw new IllegalArgumentException("Invalid frame");
  byte[] data=new byte[16+36+channels.length*7];System.arraycopy(MAGIC,0,data,0,MAGIC.length);
  data[9]=2;data[10]=0;data[11]=(byte)sequence; data[14]=(byte)colorSpace;
  System.arraycopy(normalized.getBytes(StandardCharsets.US_ASCII),0,data,16,36);
  HashSet<Integer> seen=new HashSet<>();int offset=52;
  for(Channel c:channels){if(c==null||!seen.add(c.id))throw new IllegalArgumentException("Duplicate or null channel");data[offset++]=(byte)c.id;offset=put16(data,offset,c.red);offset=put16(data,offset,c.green);offset=put16(data,offset,c.blue);}
  return data;
 }
 private static int put16(byte[] out,int offset,int value){out[offset++]=(byte)(value>>>8);out[offset++]=(byte)value;return offset;}
}
