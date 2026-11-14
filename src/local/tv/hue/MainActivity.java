package local.tv.hue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
 private String initialLanguage;
 @Override protected void attachBaseContext(android.content.Context base){initialLanguage=base.getSharedPreferences("app_language",0).getString("language","");super.attachBaseContext(AppLanguage.wrap(base));}
 private TextView status;
 private TextView stateSummary;private Button onButton,offButton;private long statusGeneration;
 private boolean statusReading;
 private String previousSyncState="";
 private int focusedButton;
 private final android.os.Handler refreshHandler=new android.os.Handler(android.os.Looper.getMainLooper());
 private final Runnable refreshTick=new Runnable(){public void run(){refreshStatus();refreshHandler.postDelayed(this,5000);}};
 private boolean busy;
 private static final int WIZARD_REQUEST=104;
 private String wizardPriorPower="";
 private final java.util.ArrayList<Button> buttons=new java.util.ArrayList<>();
 private final java.util.concurrent.ExecutorService apiWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
 private final java.util.concurrent.ExecutorService statusWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  focusedButton=state==null?(getSharedPreferences("hue_bridge",0).getString("configured_mac","").isEmpty()?2:0):state.getInt("focused_button",0);
  wizardPriorPower=state==null?"":state.getString("wizard_prior_power","");
  Diagnostics.init(this);
  String installedVersion="unknown";
  try{installedVersion=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
  catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
  Diagnostics.event("lifecycle","create","version="+installedVersion+" sdk="+android.os.Build.VERSION.SDK_INT);
  LinearLayout panel=new LinearLayout(this);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setGravity(android.view.Gravity.CENTER);
  panel.setPadding(60,24,60,24);
  panel.setBackgroundColor(Color.rgb(25,38,65));
  TextView title=new TextView(this);
  String appTitle="Ambilight + Hue";
  android.text.SpannableString heading=new android.text.SpannableString(
    appTitle+" ("+FeatureStrings.get(this,"native_mode")+")");
  heading.setSpan(new android.text.style.RelativeSizeSpan(0.65f),appTitle.length()+1,
    heading.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  title.setText(heading);
  title.setTextSize(30);
  title.setTextColor(Color.WHITE);
  panel.addView(title);
  String lang=getResources().getConfiguration().locale.getLanguage();
  String label="cs".equals(lang)?"Jazyk":"de".equals(lang)?"Sprache":"fr".equals(lang)?"Langue":"es".equals(lang)?"Idioma":"pl".equals(lang)?"Język":"Language";
  android.widget.Spinner languagePicker=new android.widget.Spinner(this);
  String[] codes={"","cs","en","de","fr","es","pl"};String system="cs".equals(lang)?"Podle systému":"de".equals(lang)?"Systemsprache":"fr".equals(lang)?"Langue du système":"es".equals(lang)?"Idioma del sistema":"pl".equals(lang)?"Język systemowy":"System language";String[] names={system,"Čeština","English","Deutsch","Français","Español","Polski"};
  languagePicker.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names){@Override public android.view.View getView(int position,android.view.View convert,android.view.ViewGroup parent){TextView view=(TextView)super.getView(position,convert,parent);view.setText(label+": "+names[position]+"  ▾");view.setTextColor(Color.WHITE);view.setGravity(android.view.Gravity.CENTER);return view;}});
  MenuStyle.apply(languagePicker);
  String selected=getSharedPreferences("app_language",0).getString("language","");int choice=0;for(int i=0;i<codes.length;i++)if(codes[i].equals(selected))choice=i;
  languagePicker.setSelection(choice);
  languagePicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int pos,long id){String current=getSharedPreferences("app_language",0).getString("language","");if(!codes[pos].equals(current)){getSharedPreferences("app_language",0).edit().putString("language",codes[pos]).apply();initialLanguage=codes[pos];recreate();}}});
  stateSummary=new TextView(this);stateSummary.setTextColor(Color.WHITE);stateSummary.setTextSize(17);panel.addView(stateSummary);
  TextView description=new TextView(this);
  description.setText(Strings.get(this,"restore"));
  description.setTextColor(Color.LTGRAY); description.setTextSize(18);
  description.setPadding(0,12,0,24); panel.addView(description);
  LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);panel.addView(grid,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout topRow=new LinearLayout(this);LinearLayout bottomRow=new LinearLayout(this);grid.addView(topRow);grid.addView(bottomRow);
  Button on=button(topRow,Strings.get(this,"on"));
  onButton=on;
  on.setOnClickListener(v -> sync(true));
  Button off=button(topRow,Strings.get(this,"off"));
  offButton=off;
  off.setOnClickListener(v -> sync(false));
  Button setup=button(bottomRow,SetupStrings.get(this,"setup"));
  setup.setOnClickListener(v -> {if(directBusy()){status.setText(DirectText.get(this,"busy"));return;}focusedButton=2;startActivity(new Intent(this,LightsActivity.class));});
  button(bottomRow,FeatureStrings.get(this,"advanced")).setOnClickListener(v->{focusedButton=3;advanced();});
  LinearLayout languageRow=new LinearLayout(this);languageRow.setGravity(android.view.Gravity.CENTER);languageRow.setPadding(0,16,0,0);languageRow.addView(languagePicker,new LinearLayout.LayoutParams(720,80));panel.addView(languageRow);
  status=new TextView(this); status.setTextColor(Color.WHITE); status.setTextSize(16);
  status.setPadding(0,20,0,0); panel.addView(status);
  android.widget.ScrollView scroll=new android.widget.ScrollView(this);
  scroll.setFillViewport(true); scroll.addView(panel);
  LinearLayout screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);screen.setBackgroundColor(Color.rgb(25,38,65));
  screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  String version="";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
  TextView credits=new TextView(this);credits.setText("v"+version+"\norfin22 (fromul)\nToengel (https://toengel.net/philipsblog)");
  credits.setTextColor(Color.LTGRAY);credits.setTextSize(12);credits.setGravity(android.view.Gravity.START);credits.setPadding(24,4,24,12);
  LinearLayout footer=new LinearLayout(this);footer.setOrientation(LinearLayout.HORIZONTAL);footer.setGravity(android.view.Gravity.BOTTOM);
  footer.addView(credits,new LinearLayout.LayoutParams(0,-2,1));
  String firmware=readProperty("ro.odm.tpvision.product.swver");
  String model=readProperty("persist.sys.set_type");if(model.isEmpty())model=android.os.Build.MODEL;
  String firmwareLabel="cs".equals(lang)?"Verze firmwaru":"de".equals(lang)?"Firmware-Version":"fr".equals(lang)?"Version du firmware":"es".equals(lang)?"Versión de firmware":"pl".equals(lang)?"Wersja oprogramowania":"Firmware version";
  String modelLabel="cs".equals(lang)?"Model zařízení":"de".equals(lang)?"Gerätemodell":"fr".equals(lang)?"Modèle":"es".equals(lang)?"Modelo":"pl".equals(lang)?"Model urządzenia":"Device model";
  String unavailable="cs".equals(lang)?"nedostupné":"de".equals(lang)?"nicht verfügbar":"fr".equals(lang)?"indisponible":"es".equals(lang)?"no disponible":"pl".equals(lang)?"niedostępne":"unavailable";
  TextView device=new TextView(this);device.setText(firmwareLabel+": "+(firmware.isEmpty()?unavailable:firmware)+"\n"+modelLabel+": "+(model==null||model.isEmpty()?unavailable:model));device.setTextColor(Color.LTGRAY);device.setTextSize(12);device.setGravity(android.view.Gravity.END);device.setPadding(24,4,24,12);
  footer.addView(device,new LinearLayout.LayoutParams(0,-2,1));screen.addView(footer,new LinearLayout.LayoutParams(-1,-2));
  setContentView(screen); restoreMenuFocus();
  command(getIntent());
 }
 private static String readProperty(String key){Process process=null;try{process=new ProcessBuilder("/system/bin/getprop",key).start();if(!process.waitFor(1,java.util.concurrent.TimeUnit.SECONDS))return "";byte[] b=new byte[256];int n=process.getInputStream().read(b);return n<0?"":new String(b,0,n,"UTF-8").trim();}catch(Exception e){return "";}finally{if(process!=null)process.destroy();}}
 private void advanced(){new AlertDialog.Builder(this).setTitle(FeatureStrings.get(this,"advanced")).setItems(new String[]{SetupStrings.get(this,"native_wizard"),AppUpdates.label(this,"check"),SetupStrings.get(this,"backup_menu"),FeatureStrings.get(this,"identity_title"),DirectText.get(this,"option")},(d,item)->{
  if(item==0)openNativeWizard();
  else if(item==1)AppUpdates.check(this);
  else if(item==2)startActivity(new Intent(this,BackupActivity.class));
  else if(item==3)new AlertDialog.Builder(this).setMessage(FeatureStrings.get(this,"identity")).setPositiveButton("OK",null).show();
  else startActivity(new Intent(this,RootAreasActivity.class));
 }).setNegativeButton(Strings.get(this,"back"),null).show();}
 private boolean directBusy(){String s=RootSyncService.state();return "starting".equals(s)||"running".equals(s)||RootSessionGate.busy()||RootRecovery.pending(this);}
 private void openNativeWizard(){
  if(directBusy()){status.setText(DirectText.get(this,"busy"));return;}
  if(busy)return;busy=true;for(Button b:buttons)b.setEnabled(false);
  apiWorker.execute(()->{try{String power=new TvApiClient(this).getPower();if(!"On".equals(power)&&!"Off".equals(power))throw new java.io.IOException("Unknown synchronization state");runOnUiThread(()->{if(isFinishing()||isDestroyed())return;wizardPriorPower=power;busy=false;for(Button b:buttons)b.setEnabled(true);try{startActivityForResult(new Intent().setComponent(new ComponentName("org.droidtv.alhuewizard","org.droidtv.alhuewizard.ALHueWizardActivity")),WIZARD_REQUEST);}catch(RuntimeException e){wizardPriorPower="";status.setText(Strings.get(this,"wizard_missing"));}});}catch(Exception e){String id=Diagnostics.id();Diagnostics.error(id,"wizard.capture.error",e);runOnUiThread(()->{if(isDestroyed())return;busy=false;for(Button b:buttons)b.setEnabled(true);status.setText(Strings.get(this,"request_failed",e.getClass().getSimpleName(),id));});}});
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=WIZARD_REQUEST||wizardPriorPower.isEmpty())return;String prior=wizardPriorPower;wizardPriorPower="";long token=generation.incrementAndGet();busy=true;for(Button b:buttons)b.setEnabled(false);apiWorker.execute(()->{try{restoreWizardPower(prior,token);for(int seconds:new int[]{3,7,12,20})observer.schedule(()->{if(generation.get()!=token)return;try{restoreWizardPower(prior,token);}catch(Exception e){Diagnostics.error("wizard","wizard.recheck.error",e);}},seconds,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception e){String id=Diagnostics.id();Diagnostics.error(id,"wizard.restore.error",e);runOnUiThread(()->status.setText(Strings.get(this,"request_failed",e.getClass().getSimpleName(),id)));}finally{runOnUiThread(()->{if(isDestroyed())return;busy=false;for(Button b:buttons)b.setEnabled(true);refreshStatus();});}});}
 private void restoreWizardPower(String prior,long token)throws Exception{if(generation.get()!=token)return;TvApiClient api=new TvApiClient(this);String current=api.getPower();if(!prior.equals(current)&&generation.get()==token){String id=Diagnostics.id();Diagnostics.event(id,"wizard.restore.begin","desired="+prior+" observed="+current);api.setPower("On".equals(prior),()->notifyNative("On".equals(prior),id));Diagnostics.event(id,"wizard.restore.complete","desired="+prior);}}

 private String directSummary(){String s=RootSyncService.state();if("starting".equals(s))return "\n"+DirectText.get(this,"starting");if("running".equals(s))return "\n"+DirectText.get(this,"running");if("recovery".equals(s))return "\n"+DirectText.get(this,"recovery");return "";}
 private void refreshStatus(){if(busy||statusReading||isDestroyed())return;statusReading=true;long token=++statusGeneration;statusWorker.execute(()->{String power=FeatureStrings.get(this,"unknown");if(UiStatus.paired(this))try{power=new TvApiClient(this).getPower();}catch(Exception e){Diagnostics.error("status","power.read.failed",e);}String ambilight=FeatureStrings.get(this,"unknown"),ambilightObserved="unknown";if(UiStatus.paired(this))try{String al=new TvApiClient(this).getAmbilightPower();ambilightObserved=al;ambilight="On".equals(al)?"✓ "+FeatureStrings.get(this,"state_on"):"Off".equals(al)?"✕ "+FeatureStrings.get(this,"state_off"):ambilight;}catch(Exception e){Diagnostics.error("status","ambilight.read.failed",e);}String value=power;String nativeState=UiStatus.nativeActiveValue();String snapshot=value+"/"+nativeState+"/"+ambilightObserved;if(!snapshot.equals(previousSyncState)){previousSyncState=snapshot;Diagnostics.event("status","sync.state.changed","saved="+value+" native_active="+nativeState+" ambilight="+ambilightObserved);}String summary=FeatureStrings.get(this,"bridge")+": "+UiStatus.configuredBridge(this)+"\n"+FeatureStrings.get(this,"tv_pairing")+": "+(UiStatus.paired(this)?"✓ "+FeatureStrings.get(this,"stored"):"✕ "+FeatureStrings.get(this,"not_ready"))+"\n"+FeatureStrings.get(this,"switch")+": "+("On".equals(value)?FeatureStrings.get(this,"state_on"):"Off".equals(value)?FeatureStrings.get(this,"state_off"):value)+"\n"+FeatureStrings.get(this,"ambilight")+": "+ambilight+"\n"+UiStatus.nativeActiveLabel(this,nativeState)+directSummary();runOnUiThread(()->{statusReading=false;if(isDestroyed()||busy||token!=statusGeneration)return;stateSummary.setText(UiColors.summary(this,summary));onButton.setText(UiColors.marks(("On".equals(value)?"✓ ":"")+Strings.get(this,"on")));offButton.setText(UiColors.marks(("Off".equals(value)?"✓ ":"")+Strings.get(this,"off")));onButton.setTextColor("On".equals(value)?UiColors.GREEN:Color.WHITE);offButton.setTextColor("Off".equals(value)?UiColors.RED:Color.WHITE);});});}
 @Override protected void onResume(){super.onResume();if(!initialLanguage.equals(getSharedPreferences("app_language",0).getString("language",""))){recreate();return;}if(RootRecovery.pending(this)&&!RootSessionGate.busy()){startActivity(new Intent(this,RootRecoveryActivity.class));return;}restoreMenuFocus();refreshStatus();refreshHandler.removeCallbacks(refreshTick);refreshHandler.postDelayed(refreshTick,5000);}
 @Override protected void onPause(){refreshHandler.removeCallbacks(refreshTick);super.onPause();}
 private void restoreMenuFocus(){final int target=focusedButton;getWindow().getDecorView().post(()->{if(!busy&&target>=0&&target<buttons.size()){buttons.get(target).requestFocus();}});}
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("focused_button",focusedButton);state.putString("wizard_prior_power",wizardPriorPower);}
 private Button button(LinearLayout panel,String text) {
  Button b=new Button(this);b.setFocusableInTouchMode(true);b.setText(text);b.setAllCaps(false);b.setTextSize(20);
  b.setSingleLine(true);
  b.setGravity(android.view.Gravity.CENTER);
  b.setPadding(24,0,24,0);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,110,1);lp.setMargins(8,8,8,8);
  final int index=buttons.size();
  MenuStyle.apply(b);b.setOnFocusChangeListener((v,hasFocus)->{if(hasFocus&&!busy)focusedButton=index;});
  panel.addView(b,lp);buttons.add(b);return b;
 }
 private static final java.util.concurrent.ScheduledExecutorService observer=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
 private static final java.util.concurrent.atomic.AtomicLong generation=new java.util.concurrent.atomic.AtomicLong();
 private void observe(String id,boolean desired,long token) {
  final android.content.Context app=getApplicationContext();
  for(int seconds:new int[]{2,10,30,60})observer.schedule(() -> {
   if(generation.get()!=token){Diagnostics.event(id,"observation.skipped","newer_request=true");return;}
   Diagnostics.event(id,"observation.begin","after_seconds="+seconds);
   Diagnostics.network(app,id);
   try {String saved=new TvApiClient(app,id).getPower();Diagnostics.event(id,"observation.result","after_seconds="+seconds+" saved="+saved+" matches_request="+((desired?"On":"Off").equals(saved))+" native_active="+UiStatus.nativeActiveValue()+" ambilight="+new TvApiClient(app,id).getAmbilightPower());}
   catch(Exception e){Diagnostics.error(id,"observation.error",e);}
  },seconds,java.util.concurrent.TimeUnit.SECONDS);
 }
 private void sync(boolean on) {
  if(SettingsBackup.hasInterruptedRestore(this)){startActivity(new Intent(this,BackupActivity.class));return;}
  if(directBusy()){status.setText(DirectText.get(this,"busy"));return;}
  if(on&&getSharedPreferences("native_setup_recovery",0).contains("snapshot")){status.setText(SetupStrings.get(this,"recovery"));startActivity(new Intent(this,LightsActivity.class));return;}
  if(busy){Diagnostics.event("ui","request.ignored","busy=true");return;}
  final String id=Diagnostics.id();final long token=generation.incrementAndGet();
  final long began=android.os.SystemClock.elapsedRealtime();
  Diagnostics.event(id,"request.begin","desired="+(on?"On":"Off"));Diagnostics.network(this,id);
  busy=true;for(Button b:buttons)b.setEnabled(false);
  statusGeneration++;
  status.setText(Strings.get(this,on?"starting":"stopping"));
  apiWorker.execute(() -> {
   String message;
   try {
    new TvApiClient(this,id).setPower(on,() -> notifyNative(on,id));
    observe(id,on,token);
    message=Strings.get(this,on?"on_ok":"off_ok");
   } catch(Exception e) {Diagnostics.error(id,"request.error",e);message=Strings.get(this,"request_failed",e.getClass().getSimpleName(),id);observe(id,on,token);}
   Diagnostics.event(id,"request.end","duration_ms="+(android.os.SystemClock.elapsedRealtime()-began));
   final String result=message;
   runOnUiThread(() -> {if(!isFinishing()&&!isDestroyed()){status.setText(result);for(Button b:buttons)b.setEnabled(true);busy=false;restoreMenuFocus();refreshStatus();}});
  });
 }
 private void notifyNative(boolean on,String id){
  Diagnostics.event(id,"native_service.notify.begin","desired="+(on?"On":"Off"));
  Intent service=new Intent().setComponent(new ComponentName("org.droidtv.alhuewizard","org.droidtv.ambihue.HueService"));
  service.setAction("org.droidtv.ambihue.HueService."+(on?"ALHUEON":"ALHUEOFF"));
  ComponentName component=android.os.Build.VERSION.SDK_INT>=26?startForegroundService(service):startService(service);
  if(component==null)throw new IllegalStateException("Native Hue service unavailable");
  Diagnostics.event(id,"native_service.notify.accepted","actual_light_sync=unverified");
 }
 private void command(Intent i) {
  String value=i.getStringExtra("sync");
  if("on".equals(value)) sync(true);
  if("off".equals(value)) sync(false);
 }
 @Override protected void onNewIntent(Intent i) {super.onNewIntent(i);setIntent(i);command(i);}
 @Override protected void onStart(){super.onStart();Diagnostics.event("lifecycle","start","");}
 @Override protected void onStop(){Diagnostics.event("lifecycle","stop","");super.onStop();}
 @Override protected void onDestroy(){Diagnostics.event("lifecycle","destroy","");refreshHandler.removeCallbacks(refreshTick);statusWorker.shutdown();apiWorker.shutdown();super.onDestroy();}
}
