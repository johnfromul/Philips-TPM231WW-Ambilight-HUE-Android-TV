package local.tv.hue;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.UUID;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import org.json.JSONObject;

/** Completes the firmware's QR authorization against the TV on which the app runs. */
final class AppPairing {
 private AppPairing(){}

 static void grant(Context context,String code)throws Exception{
  if(!code.matches("[0-9]{4}"))throw new IOException("Invalid TV QR challenge");
  String id=UUID.randomUUID().toString().replace("-","");
  JSONObject device=new JSONObject().put("device_name","Ambilight Hue Launcher")
      .put("auth_appId","local.tv.hue").put("auth_appName","Ambilight Hue control").put("id",id);
  byte[] body=new JSONObject().put("auth",new JSONObject().put("pin",code))
      .put("device",device).toString().getBytes(StandardCharsets.UTF_8);
  String[] fingerprint={null};
  SSLContext ssl=SSLContext.getInstance("TLS");
  ssl.init(null,new X509TrustManager[]{new X509TrustManager(){
   public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
   public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{
    throw new CertificateException("Client certificates unsupported");
   }
   public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{
    if(chain==null||chain.length==0)throw new CertificateException("Missing TV certificate");
    try{fingerprint[0]=TvApiClient.hex(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()));}
    catch(GeneralSecurityException e){throw new CertificateException(e);}
   }
  }},new SecureRandom());
  HttpsURLConnection connection=(HttpsURLConnection)new URL("https://127.0.0.1:1926/6/pair/grant_new")
      .openConnection(java.net.Proxy.NO_PROXY);
  JSONObject reply;
  try{
   connection.setSSLSocketFactory(ssl.getSocketFactory());
   connection.setHostnameVerifier((host,session)->"127.0.0.1".equals(host));
   connection.setInstanceFollowRedirects(false);
   connection.setConnectTimeout(4000);
   connection.setReadTimeout(6000);
   connection.setRequestMethod("POST");
   connection.setDoOutput(true);
   connection.setRequestProperty("Content-Type","application/json");
   connection.setFixedLengthStreamingMode(body.length);
   try(OutputStream out=connection.getOutputStream()){out.write(body);}
   try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] buffer=new byte[2048];
    for(int n;(n=in.read(buffer))!=-1;){
     out.write(buffer,0,n);
     if(out.size()>65536)throw new IOException("Pairing response too large");
    }
    reply=new JSONObject(out.toString("UTF-8"));
   }
  }finally{connection.disconnect();}
  String password=reply.optString("auth_key");
  if(!"SUCCESS".equals(reply.optString("error_id"))||fingerprint[0]==null||!password.matches("[0-9a-fA-F]{64}"))
   throw new IOException("The TV rejected QR pairing");
  TvApiClient client=new TvApiClient(context,"pairing",id,password,fingerprint[0]);
  client.limitOperationTime(8000);
  String power=client.getPower();
  if(!"On".equals(power)&&!"Off".equals(power))throw new IOException("Could not verify TV authorization");
  SharedPreferences settings=context.getSharedPreferences(TvApiClient.PREFS,0);
  if(!settings.edit().putString("host","127.0.0.1").putString("user",id)
      .putString("password",password).putString("certificate",fingerprint[0]).commit())
   throw new IOException("Could not store TV authorization");
 }
}
