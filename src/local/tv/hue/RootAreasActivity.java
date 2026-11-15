package local.tv.hue;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.view.Gravity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** Experimental ROOT setup and bounded session control. */
public final class RootAreasActivity extends Activity {
 @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(AppLanguage.wrap(base));}
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final AtomicBoolean stopRequested=new AtomicBoolean();
 private final List<String> areaIds=new ArrayList<>();
 private Spinner areas;
 private int lightRequest;
 private String loadedLightArea="";
 private Spinner bridgePicker;
 private final List<DirectBridgeProfiles.Profile> bridgeProfiles=new ArrayList<>();
 private boolean switchingBridge;
 private EditText bridgeHost;
 private TextView status;
 private String lastSyncState="off";
 private Button load,start,stop,airSettings,openLights;
 private TextView bridgeStatus,setupStatus;
 private volatile boolean active;
 private LinearLayout lightRows;
 private LinearLayout mainPanel,lightsPanel;
 private ScrollView screen;
 private Button lightsEntry;
 private int focusedAction=2;
 private Button[] actions;
 private AirWizardActivation airActivation;
 private boolean acceptAirActivation;
 private final List<CheckBox> lightChoices=new ArrayList<>();
 private final List<Integer> positions=new ArrayList<>();
 private final List<Spinner> brightness=new ArrayList<>();
 private final List<Integer> lightIds=new ArrayList<>();
 private SeekBar smoothing;
 private NsdManager.DiscoveryListener discovery;
 private final android.os.Handler stateHandler=new android.os.Handler(android.os.Looper.getMainLooper());
 private final Runnable stateRefresh=new Runnable(){public void run(){String s=RootSyncService.state();boolean session="starting".equals(s)||"running".equals(s);boolean recovery=RootRecovery.pending(RootAreasActivity.this);start.setEnabled(!switchingBridge&&(session||!recovery));stop.setEnabled(session||!switchingBridge&&!recovery);refreshBridgePicker();refreshIndicators(s);if("starting".equals(s))status.setText(DirectText.get(RootAreasActivity.this,"starting"));else if("running".equals(s))status.setText("");else if("recovery".equals(s))status.setText(DirectText.get(RootAreasActivity.this,"recovery"));else if("error".equals(s))status.setText(DirectText.get(RootAreasActivity.this,RootSyncService.failureKey()));else if("off".equals(s)&&!"off".equals(lastSyncState))status.setText("");lastSyncState=s;stateHandler.postDelayed(this,1000);}};

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  if(state!=null)focusedAction=state.getInt("focused_action",2);
  getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN|android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  LinearLayout layout=new LinearLayout(this);
  layout.setOrientation(LinearLayout.VERTICAL);
  layout.setPadding(48,32,48,32);
  layout.setBackgroundColor(Color.rgb(25,38,65));
  TextView heading=new TextView(this);
  heading.setText(DirectText.get(this,"title"));
  heading.setTextColor(Color.WHITE);heading.setTextSize(26);layout.addView(heading);
  TextView help=new TextView(this);
  help.setText(DirectText.get(this,"help"));
  help.setTextColor(Color.WHITE);layout.addView(help);
  SharedPreferences tuning=getSharedPreferences("direct_tuning",0);
  CheckBox airSource=new CheckBox(this);
  airSource.setText(DirectText.get(this,"air_source"));
  airSource.setTextColor(Color.WHITE);
  airSource.setChecked(tuning.getBoolean("use_air",true));
  MenuStyle.apply(airSource);
  airSettings=new Button(this);
  airSettings.setText(DirectText.get(this,"air_setup"));
  MenuStyle.apply(airSettings);
  TextView airHint=new TextView(this);
  airHint.setText(DirectText.get(this,"air_hint"));airHint.setTextColor(Color.LTGRAY);airHint.setGravity(Gravity.CENTER_HORIZONTAL);
  airSettings.setOnClickListener(v->{
   if(directBusy()){status.setText(DirectText.get(this,"busy"));return;}
   startActivity(new android.content.Intent(this,AirSetupActivity.class));
  });
  CheckBox blendZones=new CheckBox(this);blendZones.setText(DirectText.get(this,"blend_zones"));blendZones.setTextColor(Color.WHITE);blendZones.setChecked(tuning.getBoolean("blend_zones",true));MenuStyle.apply(blendZones);layout.addView(blendZones);
  CheckBox compensateBorders=new CheckBox(this);compensateBorders.setText(DirectText.get(this,"compensate_borders"));compensateBorders.setTextColor(Color.WHITE);compensateBorders.setChecked(tuning.getBoolean("compensate_borders",true));MenuStyle.apply(compensateBorders);layout.addView(compensateBorders);
  TextView borderHint=new TextView(this);borderHint.setText(DirectText.get(this,"border_hint"));borderHint.setTextColor(Color.LTGRAY);layout.addView(borderHint);
  TextView colorLabel=new TextView(this);colorLabel.setText(DirectText.get(this,"color_correction"));colorLabel.setTextColor(Color.WHITE);layout.addView(colorLabel);
  Spinner colorMode=new Spinner(this);MenuStyle.apply(colorMode);colorMode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{DirectText.get(this,"color_off"),DirectText.get(this,"color_normal"),DirectText.get(this,"color_strong")}));int initialColorMode=tuning.getInt("color_mode",tuning.getBoolean("color_correction",true)?1:0);colorMode.setSelection(initialColorMode>=0&&initialColorMode<=2?initialColorMode:1);layout.addView(colorMode);
  TextView tuningHint=new TextView(this);tuningHint.setText(DirectText.get(this,"tuning_hint"));tuningHint.setTextColor(Color.LTGRAY);layout.addView(tuningHint);
  Runnable showSourceOptions=()->{
   int measuredVisibility=airSource.isChecked()?android.view.View.GONE:android.view.View.VISIBLE;
   for(android.view.View view:new android.view.View[]{blendZones,compensateBorders,borderHint,colorLabel,colorMode,tuningHint})
    view.setVisibility(measuredVisibility);
   int airVisibility=airSource.isChecked()?android.view.View.VISIBLE:android.view.View.GONE;
   airSettings.setVisibility(airVisibility);airHint.setVisibility(airVisibility);
  };
  showSourceOptions.run();
  airSource.setOnCheckedChangeListener((button,checked)->{
   tuning.edit().putBoolean("use_air",checked).apply();
   showSourceOptions.run();
  });
  blendZones.setOnCheckedChangeListener((button,checked)->tuning.edit().putBoolean("blend_zones",checked).apply());
  compensateBorders.setOnCheckedChangeListener((button,checked)->tuning.edit().putBoolean("compensate_borders",checked).apply());
  colorMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int position,long id){tuning.edit().putInt("color_mode",position).apply();}});
  try{DirectBridgeProfiles.migrate(this);bridgeProfiles.addAll(DirectBridgeProfiles.all(this));}catch(Exception e){android.util.Log.w("DirectProfiles","Profile loading failed");}
  TextView bridgeLabel=new TextView(this);bridgeLabel.setText(DirectText.get(this,"bridge_select"));bridgeLabel.setTextColor(Color.WHITE);layout.addView(bridgeLabel);
  TextView bridgeHint=new TextView(this);bridgeHint.setText(DirectText.get(this,"bridge_hint"));bridgeHint.setTextColor(Color.LTGRAY);layout.addView(bridgeHint);
  bridgePicker=new Spinner(this);MenuStyle.apply(bridgePicker);layout.addView(bridgePicker);
  ArrayList<String> profileLabels=new ArrayList<>();int selectedBridge=0;
  String selectedMac=DirectBridgeProfiles.current(this).getString("mac","");
  for(int i=0;i<bridgeProfiles.size();i++){DirectBridgeProfiles.Profile p=bridgeProfiles.get(i);profileLabels.add(p.label());if(p.mac.equals(selectedMac))selectedBridge=i;}
  bridgePicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,profileLabels));
  if(!bridgeProfiles.isEmpty())bridgePicker.setSelection(selectedBridge);
  bridgeHost=new EditText(this);bridgeHost.setTextColor(Color.WHITE);bridgeHost.setHintTextColor(Color.LTGRAY);
  bridgeHost.setHint(Strings.get(this,"ip_hint"));
  bridgeHost.setText(DirectBridgeProfiles.current(this).getString("host",""));bridgeHost.setTextColor(UiColors.YELLOW);bridgeHost.setFocusable(false);bridgeHost.setKeyListener(null);layout.addView(bridgeHost);
  bridgeStatus=new TextView(this);bridgeStatus.setTextColor(UiColors.YELLOW);bridgeStatus.setGravity(Gravity.CENTER_HORIZONTAL);layout.addView(bridgeStatus);
  layout.addView(airSource);layout.addView(airSettings);layout.addView(airHint);
  setupStatus=new TextView(this);setupStatus.setTextColor(Color.WHITE);setupStatus.setGravity(Gravity.CENTER_HORIZONTAL);layout.addView(setupStatus);
  mainPanel=layout;
  lightsPanel=new LinearLayout(this);lightsPanel.setOrientation(LinearLayout.VERTICAL);lightsPanel.setPadding(48,32,48,32);lightsPanel.setBackgroundColor(Color.rgb(25,38,65));
  TextView lightsHeading=new TextView(this);
  lightsHeading.setText(DirectText.get(this,"lights_settings"));
  lightsHeading.setTextColor(Color.WHITE);
  lightsHeading.setTextSize(26);
  lightsPanel.addView(lightsHeading);
  TextView smoothingNotice=new TextView(this);
  smoothingNotice.setText(DirectText.get(this,"smoothing_restart"));
  smoothingNotice.setTextColor(UiColors.YELLOW);
  smoothingNotice.setTextSize(12);
  smoothingNotice.setGravity(Gravity.CENTER);
  smoothingNotice.setVisibility(android.view.View.INVISIBLE);
  load=new Button(this);load.setText(DirectText.get(this,"load_areas"));lightsPanel.addView(load);
  areas=new Spinner(this);lightsPanel.addView(areas,new LinearLayout.LayoutParams(standardWidth(),(int)(52*getResources().getDisplayMetrics().density)));
  areas.setDropDownWidth(standardWidth());
  ((LinearLayout.LayoutParams)areas.getLayoutParams()).gravity=Gravity.CENTER_HORIZONTAL;
  areas.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(android.widget.AdapterView<?> parent){clearLights();}
   public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){loadLights();}
  });
  Button loadLights=new Button(this);loadLights.setText(DirectText.get(this,"load_lights"));lightsPanel.addView(loadLights);
  lightRows=new LinearLayout(this);lightRows.setOrientation(LinearLayout.VERTICAL);lightsPanel.addView(lightRows);
  loadLights.setOnClickListener(v->loadLights());
  TextView smoothingTitle=new TextView(this);smoothingTitle.setText(DirectText.get(this,"smoothing"));smoothingTitle.setTextColor(Color.WHITE);smoothingTitle.setGravity(Gravity.CENTER);smoothingTitle.setTextSize(17);lightsPanel.addView(smoothingTitle);
  TextView smoothingValue=new TextView(this);smoothingValue.setTextColor(Color.WHITE);smoothingValue.setTextSize(17);smoothingValue.setGravity(Gravity.CENTER);lightsPanel.addView(smoothingValue);
  smoothing=new SeekBar(this);smoothing.setMax(DirectSmoothing.MAX-DirectSmoothing.MIN);smoothing.setProgress(DirectSmoothing.load(this)-DirectSmoothing.MIN);
  LinearLayout.LayoutParams smoothingParams=new LinearLayout.LayoutParams(standardWidth(),(int)(44*getResources().getDisplayMetrics().density));smoothingParams.gravity=Gravity.CENTER_HORIZONTAL;lightsPanel.addView(smoothing,smoothingParams);
  smoothing.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){smoothingValue.setText(String.format(getResources().getConfiguration().locale,"%.2f s",(value+DirectSmoothing.MIN)/100.0));
    smoothingNotice.setVisibility(value+DirectSmoothing.MIN!=DirectSmoothing.load(RootAreasActivity.this)
      ?android.view.View.VISIBLE:android.view.View.INVISIBLE);
   }public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
  smoothingValue.setText(String.format(getResources().getConfiguration().locale,"%.2f s",DirectSmoothing.load(this)/100.0));
  android.view.View smoothingScale=new android.view.View(this){
   final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
   @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;float inset=smoothing.getPaddingLeft()+smoothing.getThumbOffset();float left=inset,right=getWidth()-smoothing.getPaddingRight()-smoothing.getThumbOffset();float x=left+(right-left)*(DirectSmoothing.DEFAULT-DirectSmoothing.MIN)/(float)(DirectSmoothing.MAX-DirectSmoothing.MIN);paint.setColor(Color.LTGRAY);paint.setStrokeWidth(2*density);canvas.drawLine(x,0,x,11*density,paint);paint.setTextSize(13*density);paint.setTextAlign(android.graphics.Paint.Align.LEFT);canvas.drawText(DirectText.get(RootAreasActivity.this,"smoothing_fast"),0,30*density,paint);paint.setTextAlign(android.graphics.Paint.Align.CENTER);canvas.drawText(DirectText.get(RootAreasActivity.this,"smoothing_default"),x,30*density,paint);paint.setTextAlign(android.graphics.Paint.Align.RIGHT);canvas.drawText(DirectText.get(RootAreasActivity.this,"smoothing_smooth"),getWidth(),30*density,paint);}
  };
  float density=getResources().getDisplayMetrics().density;
  android.widget.FrameLayout scaleRow=new android.widget.FrameLayout(this);
  LinearLayout.LayoutParams scaleParams=new LinearLayout.LayoutParams(standardWidth(),Math.round(56*density));
  scaleParams.gravity=Gravity.CENTER_HORIZONTAL;
  lightsPanel.addView(scaleRow,scaleParams);
  scaleRow.addView(smoothingScale,new android.widget.FrameLayout.LayoutParams(-1,-1));
  scaleRow.addView(smoothingNotice,new android.widget.FrameLayout.LayoutParams(0,-2));
  scaleRow.addOnLayoutChangeListener((v,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
   int width=right-left;
   if(width==oldRight-oldLeft)return;
   float startInset=smoothing.getPaddingLeft()+smoothing.getThumbOffset();
   float endInset=smoothing.getPaddingRight()+smoothing.getThumbOffset();
   float fraction=(DirectSmoothing.DEFAULT-DirectSmoothing.MIN)/(float)(DirectSmoothing.MAX-DirectSmoothing.MIN);
   float marker=startInset+(width-startInset-endInset)*fraction;
   android.graphics.Paint labelPaint=new android.graphics.Paint();
   labelPaint.setTextSize(13*density);
   int noticeLeft=(int)Math.ceil(marker+labelPaint.measureText(DirectText.get(this,"smoothing_default"))/2+12*density);
   int noticeRight=(int)Math.floor(width-labelPaint.measureText(DirectText.get(this,"smoothing_smooth"))-12*density);
   android.widget.FrameLayout.LayoutParams noticeParams=new android.widget.FrameLayout.LayoutParams(
     Math.max(0,noticeRight-noticeLeft),Math.round(38*density));
   noticeParams.leftMargin=noticeLeft;
   noticeParams.topMargin=Math.round(7*density);
   smoothingNotice.setLayoutParams(noticeParams);
  });
  openLights=new Button(this);openLights.setText(DirectText.get(this,"lights_settings"));layout.addView(openLights);
  openLights.setOnClickListener(v->{if(directBusy()){status.setText(DirectText.get(this,"busy"));return;}lightsEntry=openLights;showLights(true);});
  start=new Button(this);start.setText(DirectText.get(this,"start"));layout.addView(start);
  Button save=new Button(this);save.setText(DirectText.get(this,"save"));lightsPanel.addView(save);
  Button lightsBack=new Button(this);lightsBack.setText(Strings.get(this,"back"));lightsPanel.addView(lightsBack);lightsBack.setOnClickListener(v->showLights(false));
  stop=new Button(this);stop.setText(DirectText.get(this,"stop"));layout.addView(stop);
  status=new TextView(this);status.setTextColor(Color.WHITE);layout.addView(status,new LinearLayout.LayoutParams(-1,-2));
  TextView stopHint=new TextView(this);stopHint.setText(DirectText.get(this,"stop_hint"));stopHint.setTextColor(Color.LTGRAY);stopHint.setGravity(Gravity.CENTER_HORIZONTAL);layout.addView(stopHint);
  Button back=new Button(this);back.setText(Strings.get(this,"back"));layout.addView(back);
  for(Button button:new Button[]{load,loadLights,openLights,start,save,stop,back,lightsBack}){button.setAllCaps(false);button.setFocusableInTouchMode(true);MenuStyle.apply(button);}
  airSettings.setFocusableInTouchMode(true);
  styleActionButtons(layout,airSettings,openLights,start,stop,back);
  actions=new Button[]{airSettings,openLights,start,stop,back};
  for(int i=0;i<actions.length;i++){final int index=i;actions[i].setOnFocusChangeListener((view,focused)->{if(focused)focusedAction=index;});}
  styleActionButtons(lightsPanel,load,loadLights,save,lightsBack);
  MenuStyle.apply(areas);
  screen=new ScrollView(this);screen.setFillViewport(true);screen.setBackgroundColor(Color.rgb(25,38,65));screen.addView(layout);setContentView(screen);
  load.setOnClickListener(v->loadAreas());
  start.setOnClickListener(v->startSession());
  save.setOnClickListener(v->{if(saveSelection()){smoothingNotice.setVisibility(android.view.View.INVISIBLE);showLights(false);status.setText(DirectText.get(this,"saved"));}});
  stop.setOnClickListener(v->{String s=RootSyncService.state();if(!"starting".equals(s)&&!"running".equals(s)){status.setText(DirectText.get(this,"direct_stopped"));return;}acceptAirActivation=false;startService(new android.content.Intent(this,RootSyncService.class).setAction(RootSyncService.STOP));status.setText(DirectText.get(this,"stopping"));});
  back.setOnClickListener(v->onBackPressed());
  bridgePicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(android.widget.AdapterView<?> p){}
   public void onItemSelected(android.widget.AdapterView<?> p,android.view.View view,int position,long id){
    if(position>=0&&position<bridgeProfiles.size())selectBridge(bridgeProfiles.get(position));
   }
  });
  refreshBridgePicker();loadAreas();restoreMainFocus();
 }
 private void styleActionButtons(LinearLayout parent,Button... buttons){
  int width=standardWidth();
  int height=(int)(52*getResources().getDisplayMetrics().density);
  int gap=(int)(5*getResources().getDisplayMetrics().density);
  for(Button button:buttons){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(width,height);params.gravity=android.view.Gravity.CENTER_HORIZONTAL;params.setMargins(0,gap,0,gap);button.setLayoutParams(params);}
 }
 private int standardWidth(){return (int)(getResources().getDisplayMetrics().widthPixels*.65f);}
 private void restoreMainFocus(){if(mainPanel.getParent()!=screen||actions==null)return;int index=focusedAction;mainPanel.post(()->{if(mainPanel.getParent()==screen&&index>=0&&index<actions.length)actions[index].requestFocus();});}
 private void refreshIndicators(String state){
  String mac=DirectBridgeProfiles.current(this).getString("mac","");
  boolean configured=getSharedPreferences("air_setup_state",0).getBoolean("configured",false);
  boolean lights=false;
  try{lights=!mac.isEmpty()&&!new RootSyncPreferences(this,mac).loadConfiguration().isEmpty();}catch(Exception ignored){}
  boolean running="running".equals(state);
  airSettings.setText(UiColors.marks((configured?"✓ ":"")+DirectText.get(this,"air_setup")));
  openLights.setText(UiColors.marks((lights?"✓ ":"")+DirectText.get(this,"lights_settings")));
  start.setText(UiColors.marks((running?"✓ ":"")+DirectText.get(this,"start")));
  bridgeStatus.setText(DirectText.get(this,"bridge_ip")+": "+DirectBridgeProfiles.current(this).getString("host",""));
  boolean receiver=AirSetupActivity.isRunning();
  String airState=RootSyncService.airEnabledInferred()?"✓ "+DirectText.get(this,"air_inferred_enabled")
      :DirectText.get(this,"air_switch_unknown");
  setupStatus.setText(UiColors.marks(DirectText.get(this,"receiver_status")+": "+(receiver?DirectText.get(this,"receiver_running"):DirectText.get(this,"receiver_stopped"))+"\n"+DirectText.get(this,"air_status")+": "+airState+"\n"+DirectText.get(this,"direct_status")+": "+(running?"✓ "+DirectText.get(this,"direct_running"):"✕ "+DirectText.get(this,"direct_stopped"))));
 }
 private void showLights(boolean show){
  if(show){mainPanel.removeView(status);lightsPanel.addView(status);screen.removeView(mainPanel);screen.addView(lightsPanel);lightsPanel.post(()->{if(load!=null)load.requestFocus();});}
  else {lightsPanel.removeView(status);mainPanel.addView(status);screen.removeView(lightsPanel);screen.addView(mainPanel);Button target=lightsEntry;mainPanel.post(()->{if(target!=null)target.requestFocus();else restoreMainFocus();});}
 }
 @Override public void onBackPressed(){acceptAirActivation=false;if(lightsPanel.getParent()==screen)showLights(false);else super.onBackPressed();}
 private boolean directBusy(){String s=RootSyncService.state();return "starting".equals(s)||"running".equals(s)||RootSessionGate.busy()||RootRecovery.pending(this);}
 private void refreshBridgePicker(){bridgePicker.setEnabled(!switchingBridge&&!directBusy());}
 private void restoreBridgeSelection(){
  String mac=DirectBridgeProfiles.current(this).getString("mac","");
  for(int i=0;i<bridgeProfiles.size();i++)if(bridgeProfiles.get(i).mac.equals(mac)){bridgePicker.setSelection(i);return;}
 }
 private void selectBridge(DirectBridgeProfiles.Profile target){
  if(target.mac.equals(DirectBridgeProfiles.current(this).getString("mac","")))return;
  if(switchingBridge||directBusy()){restoreBridgeSelection();status.setText(DirectText.get(this,"busy"));return;}
  switchingBridge=true;bridgePicker.setEnabled(false);start.setEnabled(false);load.setEnabled(false);
  status.setText(DirectText.get(this,"bridge_checking"));
  worker.execute(()->{
   Exception problem=null;
   try{
    DirectProfileGuard.select(this::directBusy,()->{
     String pin=getSharedPreferences("bridge_tls",0).getString(target.host,"");
     if(!pin.matches("[a-f0-9]{64}"))throw new java.io.IOException("Saved bridge certificate unavailable");
     HueEntertainmentApi api=new HueEntertainmentApi(this,target.host,target.token);
     api.verifyBridge(target.mac);api.legacyLights();api.areas();
    },()->DirectBridgeProfiles.save(this,target));
   }catch(Exception e){problem=e;Diagnostics.error("direct_profile","direct_profile.select.failed",e);}
   final Exception result=problem;
   runOnUiThread(()->{
    if(isDestroyed())return;switchingBridge=false;refreshBridgePicker();load.setEnabled(true);
    if(result!=null){restoreBridgeSelection();status.setText(DirectText.get(this,"bridge_failed"));return;}
    bridgeHost.setText(target.host);areaIds.clear();areas.setAdapter(null);clearLights();
    status.setText(DirectText.get(this,"bridge_selected"));loadAreas();
   });
  });
 }
 @Override protected void onResume(){super.onResume();stateHandler.removeCallbacks(stateRefresh);stateHandler.post(stateRefresh);restoreMainFocus();}
 @Override public void onWindowFocusChanged(boolean focused){
  super.onWindowFocusChanged(focused);
  if(focused&&airActivation!=null)airActivation.onWindowFocus();
 }
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("focused_action",focusedAction);}
 @Override protected void onPause(){
  if(airActivation!=null)airActivation.onPause();
  else acceptAirActivation=false;
  stateHandler.removeCallbacks(stateRefresh);
  super.onPause();
 }
 private void discoverBridge(){
  if(discovery!=null||active)return;
  NsdManager manager=(NsdManager)getSystemService(NSD_SERVICE);
  discovery=new NsdManager.DiscoveryListener(){
   public void onDiscoveryStarted(String name){}
   public void onDiscoveryStopped(String name){}
   public void onStartDiscoveryFailed(String name,int error){show(DirectText.get(RootAreasActivity.this,"discovery_failed"));}
   public void onStopDiscoveryFailed(String name,int error){}
   public void onServiceLost(NsdServiceInfo service){}
   public void onServiceFound(NsdServiceInfo service){
    manager.resolveService(service,new NsdManager.ResolveListener(){
     public void onResolveFailed(NsdServiceInfo item,int error){}
     public void onServiceResolved(NsdServiceInfo item){
      if(item.getHost()==null)return;
      String address=item.getHost().getHostAddress();
      if(address==null||!address.matches("[0-9.]+"))return;
      runOnUiThread(()->{if(!isDestroyed())bridgeHost.setText(address);});
     }
    });
   }
  };
  try{
   manager.discoverServices("_hue._tcp.",NsdManager.PROTOCOL_DNS_SD,discovery);
   show(Strings.get(this,"searching"));
   new android.os.Handler(getMainLooper()).postDelayed(this::stopDiscovery,10000);
  }catch(RuntimeException e){discovery=null;show(DirectText.get(RootAreasActivity.this,"discovery_failed"));}
 }
 private void stopDiscovery(){
  NsdManager.DiscoveryListener listener=discovery;discovery=null;
  if(listener!=null)try{((NsdManager)getSystemService(NSD_SERVICE)).stopServiceDiscovery(listener);}catch(RuntimeException ignored){}
 }
 private void clearLights(){
  lightRequest++;loadedLightArea="";
  lightRows.removeAllViews();lightChoices.clear();positions.clear();brightness.clear();lightIds.clear();
 }
 private String[] brightnessLabels(){String l=getResources().getConfiguration().locale.getLanguage();String dark="cs".equals(l)?"tmavé":"de".equals(l)?"dunkel":"fr".equals(l)?"sombre":"es".equals(l)?"oscuro":"pl".equals(l)?"ciemno":"dark";String bright="cs".equals(l)?"jasné":"de".equals(l)?"hell":"fr".equals(l)?"lumineux":"es".equals(l)?"brillante":"pl".equals(l)?"jasno":"bright";return new String[]{"1 – "+dark,"2","3","4","5","6","7","8","9","10 – "+bright};}
 private void loadLights(){
  if(active||switchingBridge)return;
  clearLights();
  int index=areas.getSelectedItemPosition();
  if(index<0||index>=areaIds.size())return;
  String area=areaIds.get(index);
  int request=lightRequest;
  SharedPreferences prefs=DirectBridgeProfiles.current(this);
  String host=prefs.getString("host",""),key=prefs.getString("token","");
  worker.execute(()->{
   try{
    HueEntertainmentApi api=new HueEntertainmentApi(this,host,key);
    java.util.Set<Integer> members=api.areaLightIds(area);
    JSONObject lamps=api.legacyLights();
    for(int id:members)if(lamps.optJSONObject(Integer.toString(id))==null)
     throw new java.io.IOException("Entertainment light details unavailable");
    List<RootSyncCore.Lamp> saved=new RootSyncPreferences(this,prefs.getString("mac","")).loadConfiguration(area);
    if(saved.isEmpty()&&nativeBridgeMatches(prefs.getString("mac",""))){
     try(android.database.Cursor cursor=getContentResolver().query(android.net.Uri.parse("content://org.droidtv.ambihue.provider/HueLampsInfo"),null,null,null,null)){
      if(cursor!=null)while(cursor.moveToNext()){
       int selected=cursor.getInt(cursor.getColumnIndexOrThrow("ConfigureStatus"));
       int id=cursor.getInt(cursor.getColumnIndexOrThrow("LampID"));
       int angle=cursor.getInt(cursor.getColumnIndexOrThrow("Angle"));
       int level=cursor.getInt(cursor.getColumnIndexOrThrow("Brightness"));
       if(selected==1&&angle>=1&&angle<=10&&level>=1&&level<=10)saved.add(new RootSyncCore.Lamp(id,angle-1,1,level));
      }
     }
    }
    ArrayList<Integer> ids=new ArrayList<>(members);
    java.util.Collections.sort(ids);
    runOnUiThread(()->{
     if(isDestroyed()||request!=lightRequest||switchingBridge)return;
     loadedLightArea=area;
     for(int id:ids){
      JSONObject lamp=lamps.optJSONObject(Integer.toString(id));if(lamp==null)continue;
      LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);lightRows.addView(row,new LinearLayout.LayoutParams(-1,128));
      String name=lamp.optString("name",Strings.get(this,"light",id));
      CheckBox choice=new CheckBox(this);choice.setText(name+"  (ID "+id+")");choice.setTextColor(Color.WHITE);choice.setTextSize(21);choice.setPadding(20,8,20,8);choice.setMinHeight(128);choice.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(90,220,240)));
      choice.setChecked(true);row.addView(choice,new LinearLayout.LayoutParams(0,128,4));
      int angle=5,levelValue=6;
      for(RootSyncCore.Lamp previous:saved)if(previous.id==id){angle=previous.angle+1;levelValue=previous.brightness;break;}
      final int positionIndex=positions.size();positions.add(angle);
      Button position=new Button(this);position.setAllCaps(false);position.setText(SetupStrings.get(this,"position")+": "+SetupStrings.positions(this)[angle-1]);MenuStyle.apply(position);
      position.setOnClickListener(v->PositionPicker.show(this,name,positions.get(positionIndex),value->{positions.set(positionIndex,value);position.setText(SetupStrings.get(this,"position")+": "+SetupStrings.positions(this)[value-1]);}));
      LinearLayout.LayoutParams positionParams=new LinearLayout.LayoutParams(0,110,4);positionParams.setMargins(0,0,(int)(5*getResources().getDisplayMetrics().density),0);row.addView(position,positionParams);
      Spinner level=new Spinner(this);MenuStyle.apply(level);level.setContentDescription(SetupStrings.get(this,"brightness"));level.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,brightnessLabels()));level.setSelection(Math.max(0,Math.min(9,levelValue-1)));row.addView(level,new LinearLayout.LayoutParams(0,100,2));
      lightIds.add(id);lightChoices.add(choice);brightness.add(level);
     }
    });
   }catch(Exception e){runOnUiThread(()->{
    if(!isDestroyed()&&request==lightRequest&&!switchingBridge)
     status.setText(DirectText.get(this,"lights"));
   });}
  });
 }
 private boolean nativeBridgeMatches(String mac){
  try(android.database.Cursor cursor=getContentResolver().query(android.net.Uri.parse("content://org.droidtv.ambihue.provider/HueBridgeInfo"),new String[]{"MacAddr"},null,null,null)){
   return cursor!=null&&cursor.moveToFirst()&&DirectBridgeProfiles.mac(mac).equals(DirectBridgeProfiles.mac(cursor.getString(0)));
  }catch(Exception e){return false;}
 }
 private static String response(HttpURLConnection connection)throws Exception{
  int code=connection.getResponseCode();if(code!=200)throw new java.io.IOException("Bridge HTTP "+code);
  try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buf=new byte[2048];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>262144)throw new java.io.IOException("Response too large");}
   return new String(out.toByteArray(),StandardCharsets.UTF_8);
  }finally{connection.disconnect();}
 }
 private void pairBridge(Button pair){
  if(active||RootRecovery.pending(this)||"starting".equals(RootSyncService.state())||"running".equals(RootSyncService.state()))return;
  String host=bridgeHost.getText().toString().trim();
  if(!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")){status.setText(DirectText.get(this,"enter_ipv4"));return;}
  pair.setEnabled(false);
  show(DirectText.get(this,"pair_prompt"));
  worker.execute(()->{
   try{
    BridgeTransport transport=new BridgeTransport(this);
    HttpURLConnection config=transport.open(host,"/api/config");
    if(!(config instanceof HttpsURLConnection)){config.disconnect();throw new java.io.IOException("HTTPS required");}
    JSONObject info=new JSONObject(response(config));
    String mac=info.getString("mac").replace(":","").toLowerCase(java.util.Locale.ROOT);
    if(!mac.matches("[0-9a-f]{12}"))throw new java.io.IOException("Invalid Bridge identity");
    HttpURLConnection request=transport.open(host,"/api");
    if(!(request instanceof HttpsURLConnection)){request.disconnect();throw new java.io.IOException("HTTPS required");}
    request.setRequestMethod("POST");request.setDoOutput(true);request.setRequestProperty("Content-Type","application/json");
    byte[] body=new JSONObject().put("devicetype","ambilight_root#tv").put("generateclientkey",true).toString().getBytes(StandardCharsets.UTF_8);
    try(OutputStream out=request.getOutputStream()){out.write(body);}
    JSONArray answer=new JSONArray(response(request));
    if(answer.length()!=1||!answer.getJSONObject(0).has("success"))throw new java.io.IOException("Pairing rejected; press Bridge button");
    JSONObject success=answer.getJSONObject(0).getJSONObject("success");
    String token=success.getString("username"),client=success.getString("clientkey");
    if(!token.matches("[A-Za-z0-9_-]{8,128}")||!client.matches("(?i)[0-9a-f]{32}"))throw new java.io.IOException("Invalid pairing response");
    if(!getSharedPreferences("hue_bridge",0).edit().putString("host",host).putString("mac",mac).putString("token",token).putString("clientkey",client).commit())throw new java.io.IOException("Cannot save pairing");
    show(DirectText.get(this,"pair_done"));
   }catch(Exception e){show(DirectText.get(this,"pair_failed"));}
   finally{runOnUiThread(()->{if(!isDestroyed())pair.setEnabled(true);});}
  });
 }
 private void show(String text){runOnUiThread(()->{if(!isDestroyed())status.setText(text);});}
 private void loadAreas(){
  if(active||switchingBridge)return;
  load.setEnabled(false);
  clearLights();areaIds.clear();areas.setAdapter(null);
  SharedPreferences prefs=DirectBridgeProfiles.current(this);
  String host=prefs.getString("host",""),key=prefs.getString("token","");
  worker.execute(()->{
   try{
    JSONArray data=new HueEntertainmentApi(this,host,key).areas();
    ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();
    for(int i=0;i<data.length();i++){
     JSONObject area=data.getJSONObject(i);
     ids.add(area.getString("id"));
     JSONObject metadata=area.optJSONObject("metadata");
     String rawStatus=area.optString("status");
     String areaStatus="active".equals(rawStatus)?DirectText.get(this,"area_active"):
         "inactive".equals(rawStatus)?DirectText.get(this,"area_inactive"):FeatureStrings.get(this,"unknown");
     names.add((metadata==null?"?":metadata.optString("name","?"))+" ("+areaStatus+")");
    }
    runOnUiThread(()->{
     if(isDestroyed())return;
     areaIds.clear();areaIds.addAll(ids);
     areas.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
     try{String saved=new RootSyncPreferences(this,prefs.getString("mac","")).loadArea();int index=ids.indexOf(saved);if(index>=0)areas.setSelection(index);}catch(Exception ignored){}
     load.setEnabled(true);
     status.setText(names.isEmpty()?DirectText.get(this,"no_areas"):"");
    });
   }catch(Exception e){Diagnostics.error("direct","direct.areas.load.error",e);show(DirectText.get(this,"loading_failed"));runOnUiThread(()->{if(!isDestroyed())load.setEnabled(true);});}
  });
 }
 private boolean saveSelection(){
  if(active||switchingBridge||!"off".equals(RootSyncService.state())&&!"error".equals(RootSyncService.state())){status.setText(DirectText.get(this,"busy"));return false;}
  if(areas.getSelectedItemPosition()<0||areas.getSelectedItemPosition()>=areaIds.size()){status.setText(DirectText.get(this,"area"));return false;}
  final String area=areaIds.get(areas.getSelectedItemPosition());
  if(!area.equals(loadedLightArea)){status.setText(DirectText.get(this,"lights"));return false;}
  final List<RootSyncCore.Lamp> lamps=new ArrayList<>();
  for(int i=0;i<lightChoices.size();i++)if(lightChoices.get(i).isChecked())
   lamps.add(new RootSyncCore.Lamp(lightIds.get(i),positions.get(i)-1,1,brightness.get(i).getSelectedItemPosition()+1));
  if(lamps.isEmpty()||lamps.size()>10){status.setText(DirectText.get(this,"lights"));return false;}
  SharedPreferences prefs=DirectBridgeProfiles.current(this);
  final String host=prefs.getString("host",""),key=prefs.getString("token",""),client=prefs.getString("clientkey",""),bridge=prefs.getString("mac","");
  if(RootRecovery.pending(this)){startActivity(new android.content.Intent(this,RootRecoveryActivity.class));return false;}
  try{new RootSyncPreferences(this,bridge).saveConfiguration(lamps,area);if(!getSharedPreferences("direct_tuning",0).edit().putInt("smoothing_centiseconds",smoothing.getProgress()+DirectSmoothing.MIN).commit())throw new java.io.IOException("Smoothing setting was not saved");}catch(Exception e){Diagnostics.error("direct","direct.configuration.save.error",e);status.setText(DirectText.get(this,"save_failed"));return false;}
  return true;
 }
 private void showMissingDirectConfiguration(){
  new android.app.AlertDialog.Builder(this)
      .setMessage(DirectText.get(this,"direct_config_required"))
      .setNegativeButton(Strings.get(this,"back"),null)
      .setPositiveButton(DirectText.get(this,"lights_settings"),(dialog,which)->{
       lightsEntry=start;
       showLights(true);
      }).show();
 }
 private void startSession(){
  if(RootRecovery.pending(this)){startActivity(new android.content.Intent(this,RootRecoveryActivity.class));return;}
  if(directBusy()){status.setText(DirectText.get(this,"busy"));return;}
  SharedPreferences selected=DirectBridgeProfiles.current(this);
  String bridge=selected.getString("mac","");
  if(DirectBridgeProfiles.currentProfile(this)==null){status.setText(DirectText.get(this,"bridge_failed"));return;}
  try{
   RootSyncPreferences saved=new RootSyncPreferences(this,bridge);
   String area=saved.loadArea();
   if(saved.loadConfiguration(area).isEmpty()){showMissingDirectConfiguration();return;}
   Diagnostics.event("direct","direct.start.saved_configuration","bridge="+bridge+" area="+area);
  }catch(Exception e){Diagnostics.error("direct","direct.start.configuration.error",e);showMissingDirectConfiguration();return;}
  android.content.Intent startRequest=new android.content.Intent(this,RootSyncService.class).setAction(RootSyncService.START);
  acceptAirActivation=getSharedPreferences("direct_tuning",0).getBoolean("use_air",true)
      &&getSharedPreferences("air_setup_state",0).getBoolean("configured",false);
  if(acceptAirActivation)startRequest.putExtra(RootSyncService.AIR_ACTIVATION,new ResultReceiver(stateHandler){
   @Override protected void onReceiveResult(int code,Bundle data){
    ResultReceiver reply=data.getParcelable(AirWizardActivation.REPLY);
    if(acceptAirActivation&&airActivation==null&&"starting".equals(RootSyncService.state())){
     airActivation=AirWizardActivation.start(RootAreasActivity.this,()->airActivation=null);
    }
    acceptAirActivation=false;
    if(reply!=null)reply.send(airActivation!=null?1:0,null);
   }
  });
  startForegroundService(startRequest);
  bridgePicker.setEnabled(false);
  status.setText(DirectText.get(this,"starting"));
 }
 @Override protected void onDestroy(){acceptAirActivation=false;if(airActivation!=null)airActivation.close();stateHandler.removeCallbacks(stateRefresh);stopDiscovery();worker.shutdown();super.onDestroy();}
}
