package local.tv.hue;

import android.media.Image;
import android.util.Base64;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONException;
import org.json.JSONObject;

final class PhilipsPairingQr {
 private static final String PREFIX="https://apps.nettvservices.com/downloadpta/pta2.html?data=";
 // Philips embeds this format key in the TV and its companion app.
 private static final String FORMAT_KEY="ZmVay1EQVFOaZhwQ4Kv81ypLAZNczV9sG4KkseXWn1NEk6cXmPKO/MCa9sryslvLCFMnNe4Z4CPXzToowvhHvA==";

 private PhilipsPairingQr(){}

 static String read(Image image){
  int width=image.getWidth(),height=image.getHeight();
  Image.Plane plane=image.getPlanes()[0];
  ByteBuffer pixels=plane.getBuffer();
  byte[] luminance=new byte[width*height];
  for(int y=0;y<height;y++){
   int in=y*plane.getRowStride();
   for(int x=0;x<width;x++){
    int r=pixels.get(in)&255,g=pixels.get(in+1)&255,b=pixels.get(in+2)&255;
    luminance[y*width+x]=(byte)((r+2*g+b)/4);
    in+=plane.getPixelStride();
   }
  }
  try{
   PlanarYUVLuminanceSource source=new PlanarYUVLuminanceSource(luminance,width,height,0,0,width,height,false);
   String qr=new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)),
       Collections.singletonMap(DecodeHintType.TRY_HARDER,Boolean.TRUE)).getText();
   if(!qr.startsWith(PREFIX)||qr.length()>2048)return null;
   byte[] payload=Base64.decode(qr.substring(PREFIX.length()),Base64.DEFAULT);
   if(payload.length<32||payload.length%16!=0)return null;
   byte[] key=Base64.decode(FORMAT_KEY,Base64.DEFAULT);
   Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
   cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,0,16,"AES"),new IvParameterSpec(payload,0,16));
   JSONObject challenge=new JSONObject(new String(cipher.doFinal(payload,16,payload.length-16),StandardCharsets.UTF_8));
   String pin=challenge.optString("pin");
   return pin.matches("[0-9]{4}")?pin:null;
  }catch(ReaderException|IllegalArgumentException|GeneralSecurityException|JSONException ignored){return null;}
 }
}
