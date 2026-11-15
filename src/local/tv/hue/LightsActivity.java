package local.tv.hue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class LightsActivity extends Activity {
 @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(AppLanguage.wrap(base));}
 private static final Uri LAMPS=Uri.parse("content://org.droidtv.ambihue.provider/HueLampsInfo");
 private static final Uri BRIDGE=Uri.parse("content://org.droidtv.ambihue.provider/HueBridgeInfo");
 private static final String[] COLUMNS={"LampID","Angle","Placement","Brightness","ConfigureStatus"};
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final LinkedHashMap<Integer,CheckBox> choices=new LinkedHashMap<>();
 private final Map<Integer,Integer> positions=new LinkedHashMap<>();
 private final Map<Integer,Spinner> brightness=new LinkedHashMap<>();
 private Map<Integer,ContentValues> nativeRowsAtLoad;
 private String nativeMacAtLoad;
 private final ArrayList<Button> controls=new ArrayList<>();
 private Map<Integer,ContentValues> initial;
 private SharedPreferences prefs;
 private final ThreadLocal<String> diagnosticId=ThreadLocal.withInitial(Diagnostics::id);
 private String discoveryId="discovery";
 private LinearLayout panel;
 private EditText hostInput;
 private TextView status;
 private TextView pairSummary;private Button bridgePairButton,appPairButton;
 private Button findBridge;
 private Spinner bridgePicker;private ArrayAdapter<String> bridgeAdapter;
 private final List<String> discoveredHosts=new ArrayList<>(),discoveredIds=new ArrayList<>();
 private final ArrayDeque<NsdServiceInfo> resolveQueue=new ArrayDeque<>();
 private String bridgeName="";
 private BridgeTransport bridgeTransport;
 private String host,token,bridgeMac,clientKey;
 private boolean showingLights;
 private Runnable afterTvPairing;
 private static final int TV_PAIRING=141;
 private int connectionFocus;
 private static final long BRIDGE_WAIT_MS=120000;
 private int countdownGeneration;
 private int discoveryGeneration;
 private void stopCountdown(){countdownGeneration++;}
 private void countdown(String key,long deadline,Runnable expired){
  final int generation=++countdownGeneration;
  Runnable tick=new Runnable(){public void run(){if(closing||generation!=countdownGeneration)return;long remaining=Math.max(0,(deadline-android.os.SystemClock.elapsedRealtime()+999)/1000);status.setText(String.format(getResources().getConfiguration().locale,SetupStrings.get(LightsActivity.this,key),remaining));if(remaining==0){stopCountdown();expired.run();}else panel.postDelayed(this,Math.min(1000,Math.max(1,deadline-android.os.SystemClock.elapsedRealtime())));}};
  tick.run();
 }
 private boolean busy,closing,resolving;
 private NsdManager nsd;
 private NsdManager.DiscoveryListener discovery;
 private interface Task {void run() throws Exception;}
 private static class HueError extends IOException {final int type;HueError(int type,String text){super(text);this.type=type;}}
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);connectionFocus=state==null?0:state.getInt("connection_focus",0);Diagnostics.init(this);Diagnostics.event("lifecycle","lights.create",""); prefs=getSharedPreferences("hue_bridge",MODE_PRIVATE);bridgeTransport=new BridgeTransport(this);
  connectionScreen();
 }
 private void base(String title) {
  stopCountdown();
  choices.clear(); positions.clear();brightness.clear();controls.clear();
  panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(72,32,72,40); panel.setBackgroundColor(Color.rgb(25,38,65));
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(panel);setContentView(scroll);
  text(title,27);
  status=text("",17);
 }
 private TextView text(String value,int size) {
  TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);
  t.setPadding(0,8,0,12);panel.addView(t);return t;
 }
 private Button button(String label,Runnable action) {
  Button b=new Button(this);b.setFocusableInTouchMode(true);b.setText(label);b.setTextSize(20);b.setAllCaps(false);
  b.setPadding(24,0,24,0);b.setGravity(Gravity.CENTER);b.setMinHeight(128);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels*.65f),128);lp.gravity=Gravity.CENTER_HORIZONTAL;lp.setMargins(0,8,0,8);
  final int index=controls.size();final boolean lightsPage=showingLights;
  MenuStyle.apply(b);b.setOnFocusChangeListener((v,focused)->{if(focused&&!lightsPage&&!busy)connectionFocus=index;});
  panel.addView(b,lp);controls.add(b);b.setOnClickListener(v -> {if(!busy){if(!lightsPage)connectionFocus=index;action.run();}});return b;
 }
 private void connectionScreen() {
  persistDraft();stopDiscovery();showingLights=false;
  base(SetupStrings.get(this,"setup"));
  text(SetupStrings.get(this,"help"),18);
  if(SettingsBackup.hasInterruptedRestore(this)){
   status.setText(SetupStrings.get(this,"backup_rollback"));
   button(Strings.get(this,"back"),this::finish);
   return;
  }
  if(getSharedPreferences("native_setup_recovery",0).contains("snapshot")){
   status.setText(SetupStrings.get(this,"recovery"));button(SetupStrings.get(this,"recover"),this::recover);button(Strings.get(this,"back"),this::finish);return;
  }
  hostInput=new EditText(this);hostInput.setSingleLine(true);hostInput.setTextColor(Color.WHITE);
  hostInput.setTextSize(20);hostInput.setHint(Strings.get(this,"ip_hint"));hostInput.setHintTextColor(Color.LTGRAY);
  hostInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
  hostInput.setText(prefs.getString("discovery_selected_host",prefs.getString("host","")));panel.addView(hostInput,new LinearLayout.LayoutParams(-1,110));
  hostInput.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){refreshPairStatus();}public void afterTextChanged(android.text.Editable s){}});
  discoveredHosts.clear();discoveredIds.clear();bridgePicker=new Spinner(this);MenuStyle.apply(bridgePicker);bridgeAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new ArrayList<String>());bridgePicker.setAdapter(bridgeAdapter);panel.addView(bridgePicker,new LinearLayout.LayoutParams(-1,96));
  restoreDiscoveredBridges();
  bridgePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,android.view.View v,int position,long id){if(position>=0&&position<discoveredHosts.size()&&hostInput!=null){hostInput.setText(discoveredHosts.get(position));prefs.edit().putString("discovery_selected_host",discoveredHosts.get(position)).apply();}}public void onNothingSelected(AdapterView<?> p){}});
  TextView emptyBridgeHint=text("",16);String locale=getResources().getConfiguration().locale.getLanguage();emptyBridgeHint.setText("cs".equals(locale)?"Po dokončení vyhledávání zde vyberte svůj Bridge, pokud je k dispozici více než jeden.":"de".equals(locale)?"Wählen Sie hier nach der Suche Ihre Bridge aus, wenn mehr als eine verfügbar ist.":"fr".equals(locale)?"Une fois la recherche terminée, sélectionnez ici votre pont si plusieurs sont disponibles.":"es".equals(locale)?"Cuando termine la búsqueda, seleccione aquí su puente si hay más de uno disponible.":"pl".equals(locale)?"Po zakończeniu wyszukiwania wybierz tutaj mostek, jeśli znaleziono więcej niż jeden.":"After the search is complete, select your Bridge here if more than one is available.");emptyBridgeHint.setVisibility(discoveredHosts.isEmpty()?android.view.View.VISIBLE:android.view.View.GONE);bridgeAdapter.registerDataSetObserver(new android.database.DataSetObserver(){public void onChanged(){emptyBridgeHint.setVisibility(discoveredHosts.isEmpty()?android.view.View.VISIBLE:android.view.View.GONE);}});
  text(FeatureStrings.get(this,"discovery_hint"),16);
  pairSummary=text("",17);
  findBridge=button(prefs.getBoolean("discovery_searched",false)?FeatureStrings.get(this,"discover_again"):Strings.get(this,"discover"),this::discover);
  hostInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
  hostInput.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE||(event!=null&&event.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER)){android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(v.getWindowToken(),0);v.clearFocus();findBridge.requestFocus();return true;}return false;});
  bridgePairButton=button(SetupStrings.get(this,"pair"),this::chooseBridgePairTransport);
  appPairButton=button(SetupStrings.get(this,"tv_qr_pair"),this::pairApp);
  button(Strings.get(this,"load"),() -> load(false));
  button(Strings.get(this,"back"),this::back);
  status.setText(Strings.get(this,"network_required"));
  refreshPairStatus();restoreConnectionFocus();
 }
 @Override protected void onActivityResult(int request,int result,android.content.Intent data){
  super.onActivityResult(request,result,data);
  if(request!=TV_PAIRING)return;
  busy=false;
  for(Button control:controls)control.setEnabled(true);
  if(hostInput!=null)hostInput.setEnabled(true);
  Runnable continuation=afterTvPairing;
  afterTvPairing=null;
  String message=data==null?null:data.getStringExtra("message");
  status.setText(SetupStrings.get(this,message==null?"tv_qr_cancelled":message));
  refreshPairStatus();
  if(result==RESULT_OK&&continuation!=null)continuation.run();
 }
 private void restoreConnectionFocus(){if(showingLights)return;panel.post(()->{if(!showingLights&&!busy&&connectionFocus>=0&&connectionFocus<controls.size())controls.get(connectionFocus).requestFocus();});}
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("connection_focus",connectionFocus);}
 private boolean storedBridgeForHost(String host){
  if(host.isEmpty())return false;
  if(host.equals(prefs.getString("host",""))&&!prefs.getString("token","").isEmpty()&&!prefs.getString("clientkey","").isEmpty())return true;
  int index=discoveredHosts.indexOf(host);if(index<0||index>=discoveredIds.size())return false;
  String mac=normalize(discoveredIds.get(index));if(mac.length()!=12)return false;
  try{JSONObject saved=new JSONObject(getSharedPreferences("bridge_profiles",0).getString(mac,"{}"));return !saved.optString("token").isEmpty()&&!saved.optString("clientkey").isEmpty();}catch(JSONException e){return false;}
 }
 private void refreshPairStatus(){
  if(showingLights||pairSummary==null||bridgePairButton==null||appPairButton==null)return;
  boolean bridge=hostInput!=null&&storedBridgeForHost(hostInput.getText().toString().trim());
  boolean app=UiStatus.paired(this);
  bridgePairButton.setText(UiColors.marks((bridge?"✓ ":"✕ ")+SetupStrings.get(this,"pair")));
  appPairButton.setText(UiColors.marks((app?"✓ ":"✕ ")+SetupStrings.get(this,"tv_qr_pair")));
  pairSummary.setText(UiColors.summary(this,FeatureStrings.get(this,"bridge")+": "+(hostInput==null?"":hostInput.getText().toString())
      +"\n"+SetupStrings.get(this,"pair")+": "+(bridge?"✓ "+FeatureStrings.get(this,"stored"):"✕ "+FeatureStrings.get(this,"not_ready"))
      +"\n"+FeatureStrings.get(this,"tv_pairing")+": "+(app?"✓ "+FeatureStrings.get(this,"stored"):"✕ "+FeatureStrings.get(this,"not_ready"))));
 }
 private void chooseBridgePairTransport(){
  if(busy)return;
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle(SetupStrings.get(this,"pair"))
      .setMessage(DirectText.get(this,"pair_https_hint"))
      .setNegativeButton(Strings.get(this,"cancel"),null)
      .setNeutralButton(DirectText.get(this,"legacy_pair"),(d,w)->{bridgeTransport=new BridgeTransport(this);load(true);})
      .setPositiveButton("HTTPS",(d,w)->{bridgeTransport=new BridgeTransport(this,true);load(true);})
      .create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus());
  dialog.show();
 }
 private void pairApp(){
  if(UiStatus.paired(this)){
   new AlertDialog.Builder(this).setMessage(SetupStrings.get(this,"tv_qr_again"))
       .setNegativeButton(Strings.get(this,"cancel"),null)
       .setPositiveButton(android.R.string.ok,(d,w)->startTvPairing(null)).show();
  }else startTvPairing(null);
 }
 private void startTvPairing(Runnable continuation){
  if(busy)return;
  stopDiscovery();
  busy=true;
  afterTvPairing=continuation;
  for(Button control:controls)control.setEnabled(false);
  if(hostInput!=null)hostInput.setEnabled(false);
  status.setText(SetupStrings.get(this,"tv_qr_wait"));
  startActivityForResult(new android.content.Intent(this,QrPairingActivity.class),TV_PAIRING);
 }
 private static String normalize(String s) {return s==null?"":s.replaceAll("[^a-fA-F0-9]", "").toLowerCase(Locale.ROOT);}
 private String tvMac() throws Exception {
  try(Cursor c=getContentResolver().query(BRIDGE,new String[]{"MacAddr"},null,null,null)) {
   if(c!=null && c.moveToFirst()) {String mac=normalize(c.getString(0));if(mac.length()==12)return mac;}
  }
  return "";
 }
 private Map<Integer,ContentValues> readSelection() throws Exception {
  Map<Integer,ContentValues> rows=new LinkedHashMap<>();
  try(Cursor c=getContentResolver().query(LAMPS,COLUMNS,null,null,"LampID ASC")) {
   if(c==null)throw new IOException("Databáze světel není dostupná.");
   while(c.moveToNext()) {ContentValues v=new ContentValues();for(int i=0;i<COLUMNS.length;i++)v.put(COLUMNS[i],c.getInt(i)); rows.put(c.getInt(0),v);}
  }
  return rows;
 }
 private Object request(String host,String path,String body) throws Exception {
  if(!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new IOException("Zadejte IPv4 adresu můstku, například 192.168.2.11.");
  HttpURLConnection c=bridgeTransport.open(host,path);
  String id=diagnosticId.get();long began=android.os.SystemClock.elapsedRealtime();
  String kind=path.equals("/api/config")?"config":path.endsWith("/lights")?"lights":"pair";
  Diagnostics.event(id,"bridge.request.begin","kind="+kind+" method="+(body==null?"GET":"POST"));
  c.setConnectTimeout(6000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);
  try {
   if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.getBytes("UTF-8"));}}
   int http=c.getResponseCode();Diagnostics.event(id,"bridge.response","http="+http+" duration_ms="+(android.os.SystemClock.elapsedRealtime()-began));
   if(http!=200)throw new IOException("Můstek odpověděl HTTP "+http+".");
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>2097152)throw new IOException("Příliš velká odpověď můstku.");}}
   Object data=new JSONTokener(out.toString("UTF-8")).nextValue();
   if(path.equals("/api/config")&&data instanceof JSONObject)bridgeTransport.publicConfig(host,(JSONObject)data);
   if(data instanceof JSONArray){JSONObject first=((JSONArray)data).optJSONObject(0);JSONObject error=first==null?null:first.optJSONObject("error");if(error!=null){int type=error.optInt("type");Diagnostics.event(id,"bridge.api.error","type="+type);if(type==101)throw new HueError(type,"Stiskněte tlačítko na Hue můstku a zkuste Spárovat znovu.");if(type==1)throw new HueError(type,"Přístup vypršel. Stiskněte tlačítko můstku a použijte Spárovat.");throw new HueError(type,"Můstek odmítl požadavek (chyba "+type+").");}}
   return data;
  }catch(Exception e){Diagnostics.error(id,"bridge.request.error",e);throw e;}finally {c.disconnect();Diagnostics.event(id,"bridge.request.end","duration_ms="+(android.os.SystemClock.elapsedRealtime()-began));}
 }
 private String checkBridge(String candidate,String expected) throws Exception {
  Object obj=request(candidate,"/api/config",null);
  if(!(obj instanceof JSONObject))throw new IOException("Adresa neodpovídá Hue můstku.");
  String actual=normalize(((JSONObject)obj).optString("mac"));
  if(actual.length()!=12)throw new IOException("Invalid Bridge identity");
  if(expected.isEmpty())return actual;
  String bridgeid=normalize(((JSONObject)obj).optString("bridgeid"));
  String expectedId=expected.substring(0,6)+"fffe"+expected.substring(6);
  if(!expected.equals(actual)&&!expectedId.equals(bridgeid))throw new IOException("Tento můstek není napárovaný s TV. Vyberte správnou IP adresu.");
  return actual;
 }
 private void task(String message,Task work) {
  if(busy)return;busy=true;for(Button b:controls)b.setEnabled(false);if(hostInput!=null)hostInput.setEnabled(false);status.setText(message);
  worker.execute(() -> {
   String id=Diagnostics.id();diagnosticId.set(id);Diagnostics.event(id,"lights.task.begin","");Diagnostics.network(this,id);
   try {work.run();Diagnostics.event(id,"lights.task.ok","");}catch(Exception e){Diagnostics.error(id,"lights.task.error",e);ui(() -> status.setText(bridgeTransport.requiresHttps()&&(e instanceof ConnectException||e instanceof NoRouteToHostException||e instanceof javax.net.ssl.SSLException)?DirectText.get(this,"https_unavailable")+" ("+id+")":Strings.get(this,"operation_failed",id)));}
   finally {Diagnostics.event(id,"lights.task.end","");diagnosticId.remove();ui(() -> {stopCountdown();for(Button b:controls)b.setEnabled(true);if(hostInput!=null)hostInput.setEnabled(true);busy=false;});}
  });
 }
 private void ui(Runnable r){runOnUiThread(() -> {if(!closing&&!isFinishing())r.run();});}
 private void load(boolean pair) {
  String candidate=hostInput.getText().toString().trim();
  prefs.edit().putString("discovery_selected_host",candidate).apply();
  stopDiscovery();
  status.setTextColor(pair?Color.rgb(255,217,92):Color.WHITE);status.setTypeface(null,pair?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
  task(Strings.get(this,pair?"pair_press":"connecting"),() -> {
   String mac=checkBridge(candidate,"");
   bridgeName=((JSONObject)request(candidate,"/api/config",null)).optString("name",mac);
   SharedPreferences profiles=getSharedPreferences("bridge_profiles",0);
   JSONObject profile=new JSONObject(profiles.getString(mac,"{}"));
   String key=profile.optString("token",prefs.getString("mac","").equals(mac)?prefs.getString("token",""):"");
   String streamKey=profile.optString("clientkey",prefs.getString("mac","").equals(mac)?prefs.getString("clientkey",""):"");
   if(!pair&&streamKey.isEmpty()&&mac.equals(tvMac())){try(NativeLink link=new NativeLink(this)){String existingUser=link.call(4),existingKey=link.call(7);if(existingUser!=null&&existingKey!=null&&!existingKey.isEmpty()){key=existingUser;streamKey=existingKey;}}}
   boolean mustPair=pair;
   if(pair&&!key.isEmpty()&&!streamKey.isEmpty())try{Object existing=request(candidate,"/api/"+URLEncoder.encode(key,"UTF-8")+"/lights",null);if(!(existing instanceof JSONObject))throw new IOException("Unexpected lights response");mustPair=false;Diagnostics.event(diagnosticId.get(),"bridge.authorization.reused","");}catch(HueError e){if(e.type!=1)throw e;Diagnostics.event(diagnosticId.get(),"bridge.authorization.expired","type=1");mustPair=true;}
   if(mustPair){
    bridgeTransport.open(candidate,"/api").disconnect();
    CompletableFuture<Boolean> acknowledged=new CompletableFuture<>();ui(()->{String l=getResources().getConfiguration().locale.getLanguage();String prompt="cs".equals(l)?"Stiskněte tlačítko na Hue Bridge a poté potvrďte OK. Párování bude čekat nejvýše 120 sekund.":"de".equals(l)?"Drücken Sie die Taste an der Hue Bridge und bestätigen Sie mit OK. Die Kopplung wartet höchstens 120 Sekunden.":"fr".equals(l)?"Appuyez sur le bouton du pont Hue, puis confirmez avec OK. L’association attendra jusqu’à 120 secondes.":"es".equals(l)?"Pulse el botón del puente Hue y confirme con OK. La vinculación esperará hasta 120 segundos.":"pl".equals(l)?"Naciśnij przycisk mostka Hue, a następnie potwierdź OK. Parowanie potrwa maksymalnie 120 sekund.":"Press the button on your Hue Bridge, then select OK. Pairing will wait up to 120 seconds.";new AlertDialog.Builder(this).setTitle(SetupStrings.get(this,"pair")).setMessage(prompt).setPositiveButton("OK",(d,w)->acknowledged.complete(true)).setNegativeButton(Strings.get(this,"cancel"),(d,w)->acknowledged.complete(false)).setOnCancelListener(d->acknowledged.complete(false)).show();});
    if(!acknowledged.get(120,TimeUnit.SECONDS))throw new IOException("Pairing cancelled");
    final long deadline=android.os.SystemClock.elapsedRealtime()+BRIDGE_WAIT_MS;
    ui(()->{status.setTextColor(Color.rgb(255,217,92));status.setTypeface(null,android.graphics.Typeface.BOLD);countdown("pair_wait",deadline,()->{});});
    JSONArray result=null;
    while(result==null){
     if(closing)throw new IOException("Párování zrušeno.");
     if(android.os.SystemClock.elapsedRealtime()>=deadline)throw new SocketTimeoutException("Bridge pairing deadline");
     try{result=(JSONArray)request(candidate,"/api",new JSONObject().put("devicetype","ambilight_hue#tv_setup").put("generateclientkey",true).toString());}
     catch(HueError error){if(error.type!=101||android.os.SystemClock.elapsedRealtime()>=deadline)throw error;Thread.sleep(Math.min(1200,Math.max(1,deadline-android.os.SystemClock.elapsedRealtime())));}
    }
    key=result.getJSONObject(0).getJSONObject("success").getString("username");
    streamKey=result.getJSONObject(0).getJSONObject("success").getString("clientkey");
   }
   if(!key.isEmpty()&&!streamKey.isEmpty()){profile.put("token",key).put("clientkey",streamKey).put("host",candidate).put("name",bridgeName);if(!profiles.edit().putString(mac,profile.toString()).commit())throw new IOException("Profile save failed");}
   if(key.isEmpty())throw new IOException("Stiskněte fyzické tlačítko můstku a zvolte Spárovat.");
   Object response=request(candidate,"/api/"+URLEncoder.encode(key,"UTF-8")+"/lights",null);
   if(!(response instanceof JSONObject))throw new IOException("Můstek nevrátil seznam světel.");
   JSONObject lamps=(JSONObject)response;
   ArrayList<Integer> ids=new ArrayList<>();for(Iterator<String> it=lamps.keys();it.hasNext();){String id=it.next();JSONObject lamp=lamps.getJSONObject(id);String type=lamp.optString("type");if("Color light".equals(type)||"Extended color light".equals(type)){int n=Integer.parseInt(id);if(n>0)ids.add(n);}}
   Collections.sort(ids);nativeRowsAtLoad=readSelection();nativeMacAtLoad=tvMac();Map<Integer,ContentValues> selected=mac.equals(nativeMacAtLoad)?nativeRowsAtLoad:new LinkedHashMap<>();
   prefs.edit().putString("host",candidate).putString("mac",mac).putString("name",bridgeName).putString("token",key).putString("clientkey",streamKey).apply();
   host=candidate;token=key;bridgeMac=mac;clientKey=streamKey;
   if(pair){ui(()->{status.setTextColor(Color.WHITE);status.setTypeface(null,android.graphics.Typeface.NORMAL);new AlertDialog.Builder(this).setTitle(SetupStrings.get(this,"pair_success")).setMessage(SetupStrings.get(this,"pair_next")).setCancelable(false).setPositiveButton("OK",(d,w)->connectionScreen()).show();});return;}
   if(!mac.equals(nativeMacAtLoad)&&profile.has("lamps")){JSONArray saved=profile.getJSONArray("lamps");for(int i=0;i<saved.length();i++){JSONObject item=saved.getJSONObject(i);ContentValues value=new ContentValues();for(String column:COLUMNS)value.put(column,item.getInt(column));selected.put(value.getAsInteger("LampID"),value);}}
   selected=restoreDraft(mac,selected);
   final Map<Integer,ContentValues> displayed=selected;
   ui(() -> showLights(lamps,ids,displayed));
  });
 }
 private String[] brightnessLabels(){String l=getResources().getConfiguration().locale.getLanguage();String dark="cs".equals(l)?"tmavé":"de".equals(l)?"dunkel":"fr".equals(l)?"sombre":"es".equals(l)?"oscuro":"pl".equals(l)?"ciemno":"dark";String bright="cs".equals(l)?"jasné":"de".equals(l)?"hell":"fr".equals(l)?"lumineux":"es".equals(l)?"brillante":"pl".equals(l)?"jasno":"bright";return new String[]{"1 – "+dark,"2","3","4","5","6","7","8","9","10 – "+bright};}
 private void showLights(JSONObject lamps,List<Integer> ids,Map<Integer,ContentValues> selected) {
  stopDiscovery();hostInput=null;initial=selected;
  showingLights=true;base(Strings.get(this,"select_title"));
  text(SetupStrings.get(this,"select"),18);
  for(int id:ids) {
   JSONObject lamp=lamps.optJSONObject(String.valueOf(id));
   LinearLayout lampRow=new LinearLayout(this);lampRow.setOrientation(LinearLayout.HORIZONTAL);lampRow.setGravity(Gravity.CENTER_VERTICAL);panel.addView(lampRow,new LinearLayout.LayoutParams(-1,128));
   CheckBox box=new CheckBox(this);box.setText(lamp.optString("name",Strings.get(this,"light",id))+"  (ID "+id+")");
   box.setTextColor(Color.WHITE);box.setTextSize(21);box.setPadding(20,8,20,8);box.setMinHeight(128);box.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(90,220,240)));
   box.setChecked(selected.containsKey(id)&&(!selected.get(id).containsKey("DraftSelected")||selected.get(id).getAsInteger("DraftSelected")==1));box.setFocusable(true);lampRow.addView(box,new LinearLayout.LayoutParams(0,128,4));choices.put(id,box);box.setOnCheckedChangeListener((b,checked)->persistDraft());
   ContentValues row=selected.get(id);
   int angle=row==null?5:Math.max(1,Math.min(10,row.getAsInteger("Angle")));positions.put(id,angle);Button position=button(SetupStrings.get(this,"position")+": "+SetupStrings.positions(this)[angle-1],()->{});position.setOnClickListener(v->{if(!busy)PositionPicker.show(this,lamp.optString("name",Strings.get(this,"light",id)),positions.get(id),value->{positions.put(id,value);position.setText(SetupStrings.get(this,"position")+": "+SetupStrings.positions(this)[value-1]);persistDraft();});});
   panel.removeView(position);LinearLayout.LayoutParams positionParams=new LinearLayout.LayoutParams(0,110,4);positionParams.setMargins(0,0,(int)(5*getResources().getDisplayMetrics().density),0);lampRow.addView(position,positionParams);Spinner bright=new Spinner(this);MenuStyle.apply(bright);bright.setContentDescription(SetupStrings.get(this,"brightness"));bright.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,brightnessLabels()));bright.setSelection(row==null?5:Math.max(0,Math.min(9,row.getAsInteger("Brightness")-1)));lampRow.addView(bright,new LinearLayout.LayoutParams(0,100,2));brightness.put(id,bright);bright.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,android.view.View v,int pos,long item){persistDraft();}public void onNothingSelected(AdapterView<?> p){}});
  }
  int hidden=0;for(int id:selected.keySet())if(!choices.containsKey(id))hidden++;
  status.setText(Strings.get(this,"compatible",ids.size())+(hidden>0?Strings.get(this,"hidden",hidden):""));
  if(ids.isEmpty())text(Strings.get(this,"no_lights"),18);
  Button save=button(SetupStrings.get(this,"save"),this::save);save.setEnabled(!ids.isEmpty());
  button(Strings.get(this,"back"),this::back);
  if(!choices.isEmpty())choices.values().iterator().next().requestFocus();
 }
 private void save() {
  persistDraft();
  Set<Integer> desired=new LinkedHashSet<>();for(Map.Entry<Integer,ContentValues> e:initial.entrySet())if(!e.getValue().containsKey("DraftSelected")||e.getValue().getAsInteger("DraftSelected")==1)desired.add(e.getKey());
  for(Map.Entry<Integer,CheckBox> entry:choices.entrySet()){if(entry.getValue().isChecked())desired.add(entry.getKey());else desired.remove(entry.getKey());}
  if(desired.size()>10){status.setText(Strings.get(this,"too_many"));return;}
  if(desired.isEmpty()){status.setText(SetupStrings.get(this,"choose"));return;}
  if(clientKey==null||clientKey.isEmpty()){status.setText(SetupStrings.get(this,"pair_needed"));return;}
  if(nativeMacAtLoad!=null&&!nativeMacAtLoad.isEmpty()&&!nativeMacAtLoad.equals(bridgeMac)){String id=Diagnostics.id();Diagnostics.event(id,"setup.switch.blocked","reason=native_cache_unverified");String message=FeatureStrings.get(this,"switch_blocked");status.setText(message);new AlertDialog.Builder(this).setMessage(message+"\n"+Strings.get(this,"operation_failed",id)).setPositiveButton("OK",null).show();return;}
  Map<Integer,ContentValues> rows=new LinkedHashMap<>();for(int id:desired){ContentValues row=initial.containsKey(id)?new ContentValues(initial.get(id)):new ContentValues();row.remove("DraftSelected");row.put("LampID",id);if(positions.containsKey(id)){row.put("Angle",positions.get(id));row.put("Brightness",brightness.get(id).getSelectedItemPosition()+1);}if(!row.containsKey("Placement"))row.put("Placement",1);row.put("ConfigureStatus",1);rows.put(id,row);}
  if(!UiStatus.paired(this)){
   startTvPairing(()->writeConfiguration(rows,true));
   return;
  }
  writeConfiguration(rows,false);
 }
 private void recover(){task(Strings.get(this,"saving"),()->{SharedPreferences recovery=getSharedPreferences("native_setup_recovery",0);JSONObject snapshot=new JSONObject(recovery.getString("snapshot",""));if(snapshot.has("reset_tv_api")){JSONObject api=snapshot.getJSONObject("reset_tv_api");SharedPreferences.Editor restored=getSharedPreferences(TvApiClient.PREFS,0).edit();for(String field:new String[]{"user","password","certificate","host"})restored.putString(field,api.getString(field));if(!restored.commit())throw new IOException("TV API restoration failed");}try(NativeLink link=new NativeLink(this)){new TvApiClient(this).setPower(false,()->NativeLink.notifyService(this,false));link.awaitFree();link.restore(snapshot);boolean on="On".equals(snapshot.getString("power"));new TvApiClient(this).setPower(on,()->NativeLink.notifyService(this,on));if(!recovery.edit().clear().commit())throw new IOException("Recovery marker");}ui(()->{connectionScreen();status.setText(SetupStrings.get(this,"restored"));});});}
 private void writeConfiguration(Map<Integer,ContentValues> rows,boolean newlyPaired){
  for(CheckBox box:choices.values())box.setEnabled(false);
  task(Strings.get(this,"saving"),()->{
   SharedPreferences recovery=getSharedPreferences("native_setup_recovery",0);JSONObject snapshot=null;
   try(NativeLink link=new NativeLink(this)){
    if(recovery.contains("snapshot"))throw new IOException("Recovery required");
    checkBridge(host,bridgeMac);boolean initialMatch=tvMac().equals(nativeMacAtLoad)&&readSelection().equals(nativeRowsAtLoad);Diagnostics.event(diagnosticId.get(),"setup.baseline.checked","matches="+initialMatch+" switching="+!bridgeMac.equals(nativeMacAtLoad));if(!initialMatch)throw new IOException("Native configuration changed");
    TvApiClient api=new TvApiClient(this);String power=api.getPower();if(!"On".equals(power)&&!"Off".equals(power))throw new IOException("Unknown power state");
    snapshot=link.snapshot(power);
    if(!nativeMacAtLoad.isEmpty()&&!snapshot.isNull("user")&&!snapshot.isNull("key")){
     SharedPreferences profiles=getSharedPreferences("bridge_profiles",0);JSONObject oldProfile=new JSONObject(profiles.getString(nativeMacAtLoad,"{}"));JSONArray savedRows=new JSONArray();for(ContentValues oldRow:nativeRowsAtLoad.values()){JSONObject entry=new JSONObject();for(String column:COLUMNS)entry.put(column,oldRow.getAsInteger(column));savedRows.put(entry);}oldProfile.put("token",snapshot.getString("user")).put("clientkey",snapshot.getString("key")).put("host",snapshot.optString("ip","")).put("lamps",savedRows);if(!profiles.edit().putString(nativeMacAtLoad,oldProfile.toString()).commit())throw new IOException("Previous profile backup failed");
    }
    if(!recovery.edit().putString("snapshot",snapshot.toString()).commit())throw new IOException("Backup failed");
    try{
     Diagnostics.event(diagnosticId.get(),"setup.native.stop.begin","");api.setPower(false,()->NativeLink.notifyService(this,false));
     link.awaitFree();Diagnostics.event(diagnosticId.get(),"setup.native.stop.free","");
     link.clear("HueLampsInfo");link.clear("HueGroupInfo");link.clear("HueBridgeInfo");
     link.insert("HueBridgeInfo",new JSONObject().put("MacAddr",bridgeMac));link.credentials(bridgeMac,token,clientKey,host);Diagnostics.event(diagnosticId.get(),"setup.credentials.readback.verified","address_match=true credentials_match=true");
     for(ContentValues row:rows.values()){JSONObject value=new JSONObject();for(String key:row.keySet())value.put(key,row.getAsInteger(key));link.insert("HueLampsInfo",value);}
     if(!readSelection().equals(rows))throw new IOException("Configuration verification failed");
     api.setPower(true,()->NativeLink.notifyService(this,true));
     final String checkId=diagnosticId.get();Diagnostics.event(checkId,"setup.native.identity.watch.begin","");
     BridgeSwitchGuard.verify(new BridgeSwitchGuard.Probe(){public String address()throws Exception{return link.call(10);}public String identity()throws Exception{return tvMac();}public void pause(long millis)throws Exception{Thread.sleep(millis);}public void event(int attempt,boolean addressMatch,boolean identityMatch){Diagnostics.event(checkId,"setup.native.identity.watch","attempt="+attempt+" address_match="+addressMatch+" identity_match="+identityMatch);}},host,bridgeMac);
     JSONObject profile=new JSONObject(getSharedPreferences("bridge_profiles",0).getString(bridgeMac,"{}"));JSONArray savedRows=new JSONArray();for(ContentValues row:rows.values()){JSONObject entry=new JSONObject();for(String column:COLUMNS)entry.put(column,row.getAsInteger(column));savedRows.put(entry);}profile.put("lamps",savedRows);if(!getSharedPreferences("bridge_profiles",0).edit().putString(bridgeMac,profile.toString()).commit())throw new IOException("Profile save failed");
     if(!recovery.edit().clear().commit())throw new IOException("Commit marker failed");
     initial=readSelection();nativeRowsAtLoad=initial;nativeMacAtLoad=bridgeMac;
     prefs.edit().putString("configured_name",bridgeName).putString("configured_mac",bridgeMac).putString("configured_host",host).commit();
     getSharedPreferences("light_drafts",0).edit().remove(bridgeMac).commit();
     Diagnostics.event(diagnosticId.get(),"setup.commit.ok","lamps="+rows.size());ui(()->{String message=(newlyPaired?SetupStrings.get(this,"tv_qr_success")+"\n":"")+SetupStrings.get(this,"saved");status.setText(message);new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK",(d,w)->connectionScreen()).show();});
    }catch(Exception e){
     Diagnostics.error(diagnosticId.get(),"setup.commit.failed",e);
     try{Diagnostics.event(diagnosticId.get(),"setup.rollback.begin","identity_mismatch="+(e instanceof BridgeSwitchGuard.Mismatch));api.setPower(false,()->NativeLink.notifyService(this,false));link.awaitFree();link.restore(snapshot);boolean on="On".equals(snapshot.getString("power"));api.setPower(on,()->NativeLink.notifyService(this,on));if(!recovery.edit().clear().commit())throw new IOException("Rollback marker");Diagnostics.event(diagnosticId.get(),"setup.rollback.complete","");String id=diagnosticId.get();boolean mismatch=e instanceof BridgeSwitchGuard.Mismatch;ui(()->{String message=mismatch?FeatureStrings.get(this,"switch_mismatch"):SetupStrings.get(this,"failed_restored")+id;status.setText(message);if(mismatch)new AlertDialog.Builder(this).setMessage(message+"\n"+Strings.get(this,"operation_failed",id)).setPositiveButton("OK",null).show();});}
     catch(Exception rollback){Diagnostics.error(diagnosticId.get(),"setup.rollback.failed",rollback);String id=diagnosticId.get();ui(()->{connectionScreen();status.setText(SetupStrings.get(this,"recovery")+id);});}
    }
   }finally{ui(()->{for(CheckBox box:choices.values())box.setEnabled(true);});}
  });
 }
 private void discover() {
  if(discovery!=null){status.setText(Strings.get(this,"search_running"));return;}
  prefs.edit().putBoolean("discovery_searched",true).apply();findBridge.setText(FeatureStrings.get(this,"discover_again"));
  discoveredHosts.clear();discoveredIds.clear();bridgeAdapter.clear();saveDiscoveredBridges();
  discoveryId=Diagnostics.id();Diagnostics.event(discoveryId,"bridge.discovery.begin","");
  final int searchGeneration=++discoveryGeneration;
  status.setText(Strings.get(this,"searching"));nsd=(NsdManager)getSystemService(NSD_SERVICE);
  discovery=new NsdManager.DiscoveryListener(){
   public void onDiscoveryStarted(String s){Diagnostics.event(discoveryId,"bridge.discovery.started","");}
   public void onDiscoveryStopped(String s){}
   public void onServiceLost(NsdServiceInfo s){}
   public void onStartDiscoveryFailed(String s,int e){Diagnostics.event(discoveryId,"bridge.discovery.start.error","code="+e);ui(()->{stopDiscovery();status.setText(Strings.get(LightsActivity.this,"search_unavailable"));});}
   public void onStopDiscoveryFailed(String s,int e){Diagnostics.event(discoveryId,"bridge.discovery.stop.error","code="+e);}
   public void onServiceFound(NsdServiceInfo s){
    Diagnostics.event(discoveryId,"bridge.discovery.found","");
    ui(()->{if(searchGeneration==discoveryGeneration){resolveQueue.add(s);resolveNext(searchGeneration);}});
   }
  };
  try{nsd.discoverServices("_hue._tcp.",NsdManager.PROTOCOL_DNS_SD,discovery);countdown("discovery_wait",android.os.SystemClock.elapsedRealtime()+BRIDGE_WAIT_MS,()->{if(discovery!=null){stopDiscovery();if(!busy)status.setText(Strings.get(this,"search_done"));}});}catch(RuntimeException e){Diagnostics.error(discoveryId,"bridge.discovery.error",e);stopDiscovery();status.setText(Strings.get(this,"search_unavailable"));}
 }
 private void resolveNext(int generation){if(resolving||resolveQueue.isEmpty()||generation!=discoveryGeneration||discovery==null)return;resolving=true;NsdServiceInfo service=resolveQueue.remove();try{nsd.resolveService(service,new NsdManager.ResolveListener(){
  public void onResolveFailed(NsdServiceInfo s,int code){Diagnostics.event(discoveryId,"bridge.discovery.resolve.failed","code="+code);ui(()->{if(generation!=discoveryGeneration)return;resolving=false;resolveNext(generation);});}
  public void onServiceResolved(NsdServiceInfo s){Diagnostics.event(discoveryId,"bridge.discovery.resolved","has_address="+(s.getHost()!=null)+" port="+s.getPort());ui(()->{if(generation!=discoveryGeneration)return;resolving=false;resolveNext(generation);if(s.getHost()==null)return;String ip=s.getHost().getHostAddress();String advertisedId=s.getServiceName();byte[] bridgeId=s.getAttributes().get("bridgeid");if(bridgeId!=null)advertisedId=new String(bridgeId,java.nio.charset.StandardCharsets.UTF_8);addDiscoveredBridge(generation,s.getServiceName(),ip,advertisedId);worker.execute(()->{try{JSONObject info=(JSONObject)request(ip,"/api/config",null);String mac=normalize(info.optString("mac"));if(mac.length()!=12)return;String name=info.optString("name",s.getServiceName());String model=info.optString("modelid","");String configBridgeId=normalize(info.optString("bridgeid"));String detail=name+(model.isEmpty()?"":" · "+model)+(configBridgeId.isEmpty()?"":" · Bridge ID: "+configBridgeId)+" · MAC: "+mac;ui(()->addDiscoveredBridge(generation,detail,ip,mac));}catch(Exception e){Diagnostics.error(discoveryId,"bridge.discovery.candidate.error",e);}});});}
 });}catch(RuntimeException e){resolving=false;Diagnostics.error(discoveryId,"bridge.discovery.resolve.error",e);resolveNext(generation);}}
 private void addDiscoveredBridge(int generation,String name,String ip,String id){if(generation!=discoveryGeneration||bridgeAdapter==null||hostInput==null)return;int index=discoveredIds.indexOf(id);if(index<0)index=discoveredHosts.indexOf(ip);String label=name+" · "+ip;if(index<0){discoveredIds.add(id);discoveredHosts.add(ip);bridgeAdapter.add(label);Diagnostics.event(discoveryId,"bridge.discovery.candidate.added","count="+discoveredHosts.size());}else{discoveredIds.set(index,id);discoveredHosts.set(index,ip);String old=bridgeAdapter.getItem(index);if(!label.equals(old)){bridgeAdapter.remove(old);bridgeAdapter.insert(label,index);}}saveDiscoveredBridges();refreshPairStatus();}
 private void saveDiscoveredBridges(){
  JSONArray rows=new JSONArray();
  try{for(int i=0;i<discoveredHosts.size();i++)rows.put(new JSONObject().put("host",discoveredHosts.get(i)).put("id",discoveredIds.get(i)).put("label",bridgeAdapter.getItem(i)));prefs.edit().putString("discovery_results",rows.toString()).apply();}
  catch(JSONException e){Diagnostics.error(discoveryId,"bridge.discovery.cache.save.failed",e);}
 }
 private void restoreDiscoveredBridges(){
  try{JSONArray rows=new JSONArray(prefs.getString("discovery_results","[]"));String selected=hostInput.getText().toString().trim();int selection=0;
   for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String ip=row.getString("host");if(!ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")||discoveredHosts.contains(ip))continue;discoveredHosts.add(ip);discoveredIds.add(row.getString("id"));bridgeAdapter.add(row.getString("label"));if(ip.equals(selected))selection=discoveredHosts.size()-1;}
   bridgePicker.setSelection(selection);
  }catch(JSONException e){Diagnostics.error("discovery","bridge.discovery.cache.load.failed",e);discoveredHosts.clear();discoveredIds.clear();bridgeAdapter.clear();}
 }
 private void stopDiscovery(){discoveryGeneration++;resolveQueue.clear();resolving=false;stopCountdown();if(discovery!=null){try{nsd.stopServiceDiscovery(discovery);}catch(RuntimeException ignored){Diagnostics.error(discoveryId,"bridge.discovery.stop.error",ignored);}discovery=null;}}
 private String nativeSignature(){return nativeMacAtLoad+":"+String.valueOf(nativeRowsAtLoad);}
 private void persistDraft(){
  if(!showingLights||bridgeMac==null||initial==null)return;
  try{JSONObject draft=new JSONObject().put("native",nativeSignature());JSONArray rows=new JSONArray();for(Map.Entry<Integer,CheckBox> e:choices.entrySet()){int id=e.getKey();if(!positions.containsKey(id)||!brightness.containsKey(id))continue;ContentValues original=initial.get(id);JSONObject row=new JSONObject().put("LampID",id).put("Angle",positions.get(id)).put("Brightness",brightness.get(id).getSelectedItemPosition()+1).put("Placement",original==null?1:original.getAsInteger("Placement")).put("ConfigureStatus",1).put("DraftSelected",e.getValue().isChecked()?1:0);rows.put(row);}draft.put("rows",rows);getSharedPreferences("light_drafts",0).edit().putString(bridgeMac,draft.toString()).apply();}catch(Exception e){Diagnostics.error("draft","draft.save.failed",e);}
 }
 private Map<Integer,ContentValues> restoreDraft(String mac,Map<Integer,ContentValues> saved){
  try{String value=getSharedPreferences("light_drafts",0).getString(mac,"");if(value.isEmpty())return saved;JSONObject draft=new JSONObject(value);if(!nativeSignature().equals(draft.getString("native")))return saved;Map<Integer,ContentValues> restored=new LinkedHashMap<>(saved);JSONArray rows=draft.getJSONArray("rows");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);ContentValues v=new ContentValues();for(String key:new String[]{"LampID","Angle","Brightness","Placement","ConfigureStatus","DraftSelected"})v.put(key,row.getInt(key));restored.put(row.getInt("LampID"),v);}return restored;}catch(Exception e){Diagnostics.error("draft","draft.load.failed",e);return saved;}
 }
 @Override protected void onPause(){persistDraft();if(!showingLights&&hostInput!=null)prefs.edit().putString("discovery_selected_host",hostInput.getText().toString().trim()).apply();super.onPause();}
 @Override protected void onResume(){super.onResume();refreshPairStatus();restoreConnectionFocus();}
 private void back(){persistDraft();if(showingLights)connectionScreen();else finish();}
 @Override public void onBackPressed(){if(busy){status.setText(Strings.get(this,"wait"));return;}back();}
 @Override protected void onDestroy(){closing=true;stopDiscovery();worker.shutdown();super.onDestroy();}
}
