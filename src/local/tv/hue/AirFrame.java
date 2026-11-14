package local.tv.hue;

/** Decodes Philips Air's ten ExtPixel records without retaining the Air key. */
final class AirFrame {
 private static final byte[] MAGIC={'A','m','b','i','l','i','g','h','t','A','i','r'};
 static int[][] decode(byte[] packet,int length){
  if(length!=120||packet.length<length||packet[12]!=1)return null;
  for(int i=0;i<MAGIC.length;i++)if(packet[i]!=MAGIC[i])return null;
  int[][] zones=new int[10][];
  for(int offset=30;offset<120;offset+=9){
   int id=((packet[offset]&255)<<16)|((packet[offset+1]&255)<<8)|(packet[offset+2]&255);
   if(id<1||id>10||zones[id-1]!=null)return null;
   zones[id-1]=new int[]{u16(packet,offset+3),u16(packet,offset+5),u16(packet,offset+7)};
  }
  return zones;
 }
 private static int u16(byte[] data,int offset){return ((data[offset]&255)<<8)|(data[offset+1]&255);}
}
