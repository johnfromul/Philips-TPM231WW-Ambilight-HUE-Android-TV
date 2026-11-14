package local.tv.hue;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit recovery screen; does not clear pending state unless recovery succeeds. */
public final class RootRecoveryActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private TextView status;private Button retry,exit;private boolean running;
 @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(AppLanguage.wrap(base));}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  getWindow().getDecorView().setBackgroundColor(Color.rgb(25,38,65));
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER);panel.setPadding(64,48,64,48);panel.setBackgroundColor(Color.rgb(25,38,65));
  TextView title=new TextView(this);title.setText(DirectText.get(this,"recovery_title"));title.setTextSize(28);title.setTextColor(Color.WHITE);panel.addView(title);
  TextView body=new TextView(this);body.setText(DirectText.get(this,"recovery_help"));body.setTextSize(18);body.setTextColor(Color.WHITE);body.setPadding(0,24,0,24);panel.addView(body);
  status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(17);panel.addView(status);
  int buttonWidth=(int)(getResources().getDisplayMetrics().widthPixels*.65f);
  int buttonGap=(int)(8*getResources().getDisplayMetrics().density);
  retry=new Button(this);retry.setText(DirectText.get(this,"recover"));retry.setAllCaps(false);MenuStyle.apply(retry);retry.setOnClickListener(v->recover());
  LinearLayout.LayoutParams retryLayout=new LinearLayout.LayoutParams(buttonWidth,-2);retryLayout.gravity=Gravity.CENTER_HORIZONTAL;retryLayout.bottomMargin=buttonGap;panel.addView(retry,retryLayout);
  exit=new Button(this);exit.setText(DirectText.get(this,"close_app"));exit.setAllCaps(false);MenuStyle.apply(exit);exit.setOnClickListener(v->finishAffinity());
  LinearLayout.LayoutParams exitLayout=new LinearLayout.LayoutParams(buttonWidth,-2);exitLayout.gravity=Gravity.CENTER_HORIZONTAL;panel.addView(exit,exitLayout);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(25,38,65));scroll.addView(panel);setContentView(scroll);retry.requestFocus();
 }
 private void recover(){
  if(running)return;running=true;retry.setEnabled(false);exit.setEnabled(false);status.setText(DirectText.get(this,"restoring"));
  worker.execute(()->{
   Exception failure=null;try{new RootRecovery(this).run();}catch(Exception e){failure=e;}
   final Exception result=failure;runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;running=false;
    if(result==null&&!RootRecovery.pending(this)){setResult(RESULT_OK);finish();return;}
    retry.setEnabled(true);exit.setEnabled(true);
    status.setText(DirectText.get(this,"recover_failed"));retry.requestFocus();
   });
  });
 }
 @Override public void onBackPressed(){if(!running)finishAffinity();}
 @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
