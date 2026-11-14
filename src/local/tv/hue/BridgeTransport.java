package local.tv.hue;
import android.app.*;import android.content.*;import android.os.*;import java.net.*;import java.security.*;import java.security.cert.*;import java.util.*;import java.util.concurrent.*;import javax.net.ssl.*;import org.json.JSONObject;

/** Per-session HTTPS preference; explicit first-use certificate trust before credentials. */
final class BridgeTransport {
 private final Context context;private final Activity activity;private final SharedPreferences trust;
 private final Map<String,String> schemes=new HashMap<>(),observed=new HashMap<>();private final Set<String> legacyVerified=new HashSet<>();
 BridgeTransport(Context c){context=c.getApplicationContext();activity=c instanceof Activity?(Activity)c:null;trust=c.getSharedPreferences("bridge_tls",0);}
 private String scheme(String host)throws Exception{
  if(!trust.getString(host,"").isEmpty())return "https";
  if(schemes.containsKey(host))return schemes.get(host);
  try(Socket socket=new Socket()){try{socket.connect(new InetSocketAddress(host,443),2000);}catch(ConnectException|SocketTimeoutException e){schemes.put(host,"http");return "http";}}
  schemes.put(host,"https");return "https";
 }
 private static String digest(X509Certificate cert)throws Exception{return TvApiClient.hex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));}
 HttpURLConnection open(String host,String path)throws Exception{
  if(!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new java.io.IOException("Invalid IPv4 address");
  String scheme=scheme(host);boolean publicConfig="/api/config".equals(path);
  if("http".equals(scheme)&&!publicConfig&&!legacyVerified.contains(host))throw new java.io.IOException("Legacy HTTP not verified as Bridge V2");
  if("https".equals(scheme)&&!publicConfig)confirm(host);
  HttpURLConnection raw=(HttpURLConnection)new URL(scheme+"://"+host+path).openConnection();raw.setConnectTimeout(6000);raw.setReadTimeout(10000);raw.setInstanceFollowRedirects(false);
  if(raw instanceof HttpsURLConnection){
   final String expected=trust.getString(host,observed.getOrDefault(host,""));SSLContext ssl=SSLContext.getInstance("TLS");
   ssl.init(null,new TrustManager[]{new X509TrustManager(){
    public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
    public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException("Client certificate unsupported");}
    public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{try{if(c==null||c.length==0)throw new CertificateException("No Bridge certificate");c[0].checkValidity();String fp=digest(c[0]);BridgeTlsPolicy.verify(expected,fp,publicConfig);observed.put(host,fp);}catch(Exception e){throw new CertificateException(e);}}
   }},new SecureRandom());HttpsURLConnection tls=(HttpsURLConnection)raw;tls.setSSLSocketFactory(ssl.getSocketFactory());tls.setHostnameVerifier((h,s)->{try{return host.equals(h)&&observed.getOrDefault(host,"").equals(digest((X509Certificate)s.getPeerCertificates()[0]));}catch(Exception e){return false;}});
  }
  Diagnostics.event("bridge","transport.selected","scheme="+scheme);return raw;
 }
 void publicConfig(String host,JSONObject info)throws Exception{if("http".equals(schemes.get(host))){if(!BridgeTlsPolicy.legacyModel(info.optString("modelid")))throw new java.io.IOException("HTTP fallback requires verified Bridge V2 model");legacyVerified.add(host);}}
 void confirm(String host)throws Exception{
  if(!trust.getString(host,"").isEmpty())return;String fp=observed.get(host);if(fp==null||fp.isEmpty())throw new java.io.IOException("Read Bridge identity before authentication");
  if(activity==null)throw new java.io.IOException("Bridge certificate approval requires setup");
  CompletableFuture<Boolean> answer=new CompletableFuture<>();new Handler(Looper.getMainLooper()).post(()->{if(activity.isFinishing()||activity.isDestroyed()){answer.complete(false);return;}new AlertDialog.Builder(activity).setTitle(TlsStrings.get(activity,"title")).setMessage(TlsStrings.get(activity,"help")+"\n\n"+host+"\nSHA-256:\n"+fp).setPositiveButton(TlsStrings.get(activity,"trust"),(d,w)->answer.complete(true)).setNegativeButton(Strings.get(activity,"cancel"),(d,w)->answer.complete(false)).setOnCancelListener(d->answer.complete(false)).show();});
  if(!answer.get(120,TimeUnit.SECONDS))throw new java.io.IOException("Bridge trust declined");if(!trust.edit().putString(host,fp).commit())throw new java.io.IOException("Cannot save certificate trust");
 }
}
