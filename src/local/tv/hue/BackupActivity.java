package local.tv.hue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BackupActivity extends Activity {
 private static final String PATH=Environment.DIRECTORY_DOWNLOADS+"/Ambilight+HUE/";
 private static final String EXT=".ambihue";
 private static final int OPEN=1,CREATE=2;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final List<Button> buttons=new ArrayList<>();
 private TextView status;
 private boolean busy;

 @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppLanguage.wrap(base));}

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  Diagnostics.init(this);
  showMenu();
 }

 private void showMenu(){
  buttons.clear();
  LinearLayout panel=new LinearLayout(this);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(48,32,48,32);
  panel.setBackgroundColor(Color.rgb(25,38,65));
  TextView heading=new TextView(this);
  heading.setText(label("backup_menu"));
  heading.setTextColor(Color.WHITE);
  heading.setTextSize(26);
  panel.addView(heading);
  status=new TextView(this);
  status.setTextColor(Color.WHITE);
  status.setPadding(0,12,0,16);
  panel.addView(status);
  if(SettingsBackup.hasInterruptedRestore(this)){
   status.setText(label("backup_rollback"));
   add(panel,"backup_recover",()->run(()->{SettingsBackup.recover(this);return textResult("backup_restored");},true));
  }else{
   status.setText(label("backup_hint"));
   add(panel,"backup_export",this::export);
   add(panel,"backup_import",this::choose);
   Button wipe=add(panel,"backup_wipe",this::confirmWipe);
   TextView airHint=new TextView(this);
   airHint.setText(label("backup_air_hint"));
   airHint.setTextColor(Color.LTGRAY);
   airHint.setTextSize(14);
   airHint.setGravity(Gravity.CENTER);
   int gap=Math.round(6*getResources().getDisplayMetrics().density);
   airHint.setPadding(0,0,0,gap);
   LinearLayout.LayoutParams hintParams=new LinearLayout.LayoutParams(
       wipe.getLayoutParams().width,-2);
   hintParams.gravity=Gravity.CENTER_HORIZONTAL;
   panel.addView(airHint,hintParams);
  }
  Button back=new Button(this);
  back.setText(Strings.get(this,"back"));
  style(panel,back);
  back.setOnClickListener(v->finish());
  ScrollView scroll=new ScrollView(this);
  scroll.setFillViewport(true);
  scroll.setBackgroundColor(Color.rgb(25,38,65));
  scroll.addView(panel);
  setContentView(scroll);
  buttons.get(0).requestFocus();
 }

 private void style(LinearLayout panel,Button button){
  button.setAllCaps(false);
  MenuStyle.apply(button);
  float density=getResources().getDisplayMetrics().density;
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(
      (int)(getResources().getDisplayMetrics().widthPixels*.65f),Math.round(54*density));
  params.gravity=Gravity.CENTER_HORIZONTAL;
  int gap=Math.round(5*density);
  params.setMargins(0,gap,0,gap);
  panel.addView(button,params);
  buttons.add(button);
 }

 private Button add(LinearLayout panel,String key,Runnable action){
  Button button=new Button(this);
  button.setText(label(key));
  style(panel,button);
  button.setOnClickListener(view->action.run());
  return button;
 }

 private String label(String key){return SetupStrings.get(this,key);}
 private Runnable textResult(String key){return ()->status.setText(label(key));}

 private void confirm(String key,View extra,Runnable action){
  AlertDialog.Builder builder=new AlertDialog.Builder(this)
      .setMessage(label(key)).setNegativeButton(Strings.get(this,"cancel"),null)
      .setPositiveButton(android.R.string.ok,(d,w)->action.run());
  if(extra!=null)builder.setView(extra);
  AlertDialog dialog=builder.create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus());
  dialog.show();
 }

 private void confirmWipe(){
  CheckBox resetAir=new CheckBox(this);
  resetAir.setText(label("backup_air_option"));
  confirm("backup_confirm_wipe",resetAir,()->{
   boolean openAir=resetAir.isChecked();
   run(()->{
    SettingsBackup.checkIdle(this);
    SettingsReset.run(this);
    return ()->{
     status.setText(label("backup_wiped"));
     if(openAir){
      try{
       startActivity(new Intent("android.settings.AMBILIGHT_AIR"));
      }catch(android.content.ActivityNotFoundException|SecurityException error){
       status.setText(label("backup_air_unavailable"));
      }
     }
    };
   },true);
  });
 }

 private void export(){
  if(Build.VERSION.SDK_INT<29){
   openPicker(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream")
       .putExtra(Intent.EXTRA_TITLE,fileName()),CREATE);
   return;
  }
  run(()->{
   byte[] data=SettingsBackup.export(this);
   String name=fileName();
   ContentResolver resolver=getContentResolver();
   ContentValues values=new ContentValues();
   values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
   values.put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream");
   values.put(MediaStore.MediaColumns.RELATIVE_PATH,PATH);
   values.put(MediaStore.MediaColumns.IS_PENDING,1);
   Uri file=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
   if(file==null)throw new IOException("Cannot create backup");
   boolean published=false;
   try{
    write(file,data);
    values.clear();
    values.put(MediaStore.MediaColumns.IS_PENDING,0);
    if(resolver.update(file,values,null,null)!=1)throw new IOException("Cannot publish backup");
    published=true;
   }finally{if(!published)resolver.delete(file,null,null);}
   return ()->status.setText(label("backup_created")+"\n"+PATH+name);
  },false);
 }

 private void choose(){
  if(Build.VERSION.SDK_INT<29){browse();return;}
  run(()->{
   List<Uri> files=new ArrayList<>();
   List<String> labels=new ArrayList<>();
   Uri downloads=MediaStore.Downloads.EXTERNAL_CONTENT_URI;
   String path=MediaStore.MediaColumns.RELATIVE_PATH;
   String name=MediaStore.MediaColumns.DISPLAY_NAME;
   try(Cursor cursor=getContentResolver().query(downloads,new String[]{"_id",name},
       path+"=? AND "+name+" LIKE ?",new String[]{PATH,"%"+EXT},MediaStore.MediaColumns.DATE_ADDED+" DESC")){
    while(cursor!=null&&cursor.moveToNext()){
     files.add(android.content.ContentUris.withAppendedId(downloads,cursor.getLong(0)));
     labels.add(cursor.getString(1));
    }
   }
   return ()->{
    if(files.isEmpty()){browse();return;}
    labels.add(label("backup_browse"));
    new AlertDialog.Builder(this).setTitle(label("backup_import"))
        .setItems(labels.toArray(new String[0]),(dialog,which)->{
         if(which==files.size())browse();else restore(files.get(which));
        }).setNegativeButton(Strings.get(this,"cancel"),null).show();
   };
  },false);
 }

 private void browse(){openPicker(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*"),OPEN);}

 private void openPicker(Intent intent,int request){
  try{startActivityForResult(intent.addCategory(Intent.CATEGORY_OPENABLE),request);}
  catch(android.content.ActivityNotFoundException unavailable){status.setText(label("backup_picker_missing"));}
 }

 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(result!=RESULT_OK||data==null||data.getData()==null)return;
  Uri file=data.getData();
  if(request==OPEN)restore(file);
  else if(request==CREATE)run(()->{write(file,SettingsBackup.export(this));return textResult("backup_created");},false);
 }

 private void restore(Uri file){
  confirm("backup_confirm_import",null,()->run(()->{
   org.json.JSONObject incoming;
   try(InputStream stream=getContentResolver().openInputStream(file)){incoming=SettingsBackup.read(stream);}
   catch(Exception error){
    Diagnostics.error("backup","backup.invalid",error);
    return textResult("backup_invalid");
   }
   SettingsBackup.restore(this,incoming);
   return textResult("backup_restored");
  },true));
 }

 private void write(Uri file,byte[] data)throws IOException{
  try(OutputStream out=getContentResolver().openOutputStream(file,"w")){
   if(out==null)throw new IOException("Cannot open backup");
   out.write(data);
  }
 }

 private static String fileName(){
  return "ambilight-hue-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS",Locale.ROOT))+EXT;
 }

 private interface Operation {Runnable run()throws Exception;}
 private void run(Operation operation,boolean mutates){
  if(busy)return;
  busy=true;
  for(Button button:buttons)button.setEnabled(false);
  status.setText(Strings.get(this,"wait"));
  worker.execute(()->{
   Runnable completion;
   try{completion=operation.run();}
   catch(Exception error){
    Diagnostics.error("backup","backup.operation.failed",error);
    completion=textResult("backup_error");
   }
   Runnable display=completion;
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;
    busy=false;
    if(mutates)setResult(RESULT_OK);
    if(SettingsBackup.hasInterruptedRestore(this)||buttons.size()==2)showMenu();
    else for(Button button:buttons)button.setEnabled(true);
    display.run();
   });
  });
 }

 @Override public void onBackPressed(){if(!busy)super.onBackPressed();}
 @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
