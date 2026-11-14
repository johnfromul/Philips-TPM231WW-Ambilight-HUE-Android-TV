package local.tv.hue;

import java.util.HashSet;

/** Explicit channel-to-HAL-index mapping; Bridge channel IDs are not Hue v1 light IDs. */
final class EntertainmentMapping {
 static final class Entry {
  final int channelId,pixelIndex,brightness;
  Entry(int channelId,int pixelIndex,int brightness){
   if(channelId<0||channelId>255||pixelIndex<1||pixelIndex>10||brightness<1||brightness>10)throw new IllegalArgumentException("Invalid mapping");
   this.channelId=channelId;this.pixelIndex=pixelIndex;this.brightness=brightness;
  }
 }
 static HueStreamV2Frame.Channel[] channels(Entry[] map,int[][] pixels){
  if(map==null||map.length<1||map.length>HueStreamV2Frame.MAX_CHANNELS||pixels==null||pixels.length!=10)throw new IllegalArgumentException("Invalid mapping or pixel count");
  HashSet<Integer> used=new HashSet<>();HueStreamV2Frame.Channel[] result=new HueStreamV2Frame.Channel[map.length];
  for(int i=0;i<map.length;i++){
   Entry e=map[i];if(e==null||!used.add(e.channelId))throw new IllegalArgumentException("Duplicate channel");
   int[] rgb=pixels[e.pixelIndex-1];if(rgb==null||rgb.length!=3)throw new IllegalArgumentException("Invalid pixel");
   for(int value:rgb)if(value<0||value>65535)throw new IllegalArgumentException("Invalid channel");
   result[i]=new HueStreamV2Frame.Channel(e.channelId,scale(rgb[0],e.brightness),scale(rgb[1],e.brightness),scale(rgb[2],e.brightness));
  }
  return result;
 }
 private static int scale(int value,int brightness){return (value*brightness+5)/10;}
}
