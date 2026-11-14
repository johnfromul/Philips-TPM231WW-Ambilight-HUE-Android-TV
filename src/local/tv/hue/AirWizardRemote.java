package local.tv.hue;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

/** Supplies standard TV buttons for Philips Air screens with touch-only action rows. */
final class AirWizardRemote {
 static final String PACKAGE="org.droidtv.ambiair";
 private static final String WIDGET="org.droidtv.ui.tvwidget2k15:id/";
 private final AccessibilityService service;
 private final WindowManager windows;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private LinearLayout panel;
 private long waitUntil;
 private boolean closed;
 private int shownWindow=-1;
 private final Runnable poll=new Runnable(){
  @Override public void run(){
   if(closed)return;
   try{update();}catch(RuntimeException e){
    Diagnostics.error("air","air.remote.overlay.error",e);close();return;
   }
   handler.postDelayed(this,400);
  }
 };

 private static final class Row {
  final String text,description;
  final Rect bounds;
  final boolean enabled,focused;
  Row(String text,String description,Rect bounds,boolean enabled,boolean focused){
   this.text=text;this.description=description;this.bounds=bounds;this.enabled=enabled;this.focused=focused;
  }
 }
 AirWizardRemote(AccessibilityService service){
  this.service=service;
  windows=service.getSystemService(WindowManager.class);
 }
 void start(){handler.post(poll);}
 void close(){closed=true;handler.removeCallbacksAndMessages(null);hide();}

 private void update(){
  if(SystemClock.elapsedRealtime()<waitUntil)return;
  AccessibilityNodeInfo root=topApplication();
  if(root==null)return;
  try{
   String owner=String.valueOf(root.getPackageName());
   if(panel!=null&&root.getWindowId()==shownWindow&&PACKAGE.equals(owner))return;
   // A focusable overlay can temporarily be the only accessible window.
   if(panel!=null&&service.getPackageName().equals(owner))return;
   if(!PACKAGE.equals(owner)){hide();return;}
   List<Row> rows=rows(root);
   if(!supported(root,rows)){hide();return;}
   hide();
   show(rows,root.getWindowId());
  }finally{root.recycle();}
 }

 private AccessibilityNodeInfo topApplication(){
  List<AccessibilityWindowInfo> list=service.getWindows();
  try{
   for(AccessibilityWindowInfo window:list){
    if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
    AccessibilityNodeInfo root=window.getRoot();
    if(root!=null)return root; // Android returns windows in descending layer order.
   }
   return service.getRootInActiveWindow();
  }finally{for(AccessibilityWindowInfo window:list)window.recycle();}
 }
 private static boolean supported(AccessibilityNodeInfo root,List<Row> rows){
  return (rows.size()==6&&hasNode(root,PACKAGE+":id/key"))
      ||(rows.size()>=2&&rows.size()<=11&&hasNode(root,WIDGET+"list_descriptor_RL"));
 }
 private static boolean hasNode(AccessibilityNodeInfo root,String id){
  List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(id);
  try{return !nodes.isEmpty();}
  finally{for(AccessibilityNodeInfo node:nodes)node.recycle();}
 }
 private static List<AccessibilityNodeInfo> actionNodes(AccessibilityNodeInfo root){
  List<AccessibilityNodeInfo> result=new ArrayList<>(root.findAccessibilityNodeInfosByViewId(WIDGET+"list_RL"));
  result.addAll(root.findAccessibilityNodeInfosByViewId(WIDGET+"list_descriptor_RL"));
  return result;
 }
 private static List<Row> rows(AccessibilityNodeInfo root){
  List<AccessibilityNodeInfo> nodes=actionNodes(root);
  List<Row> result=new ArrayList<>();
  try{
   for(AccessibilityNodeInfo node:nodes){
    if(!node.isVisibleToUser())continue;
    List<AccessibilityNodeInfo> labels=node.findAccessibilityNodeInfosByViewId(WIDGET+"action_title");
    List<AccessibilityNodeInfo> content=node.findAccessibilityNodeInfosByViewId(WIDGET+"action_content");
    List<AccessibilityNodeInfo> descriptions=node.findAccessibilityNodeInfosByViewId(WIDGET+"action_description");
    try{
     if(labels.size()!=1||content.size()!=1||labels.get(0).getText()==null)continue;
     Rect bounds=new Rect();node.getBoundsInScreen(bounds);
     if(!bounds.isEmpty())result.add(new Row(labels.get(0).getText().toString(),
       descriptions.isEmpty()||descriptions.get(0).getText()==null?"":descriptions.get(0).getText().toString(),bounds,
       node.isEnabled()&&content.get(0).isEnabled()&&node.isClickable(),content.get(0).isFocused()));
    }finally{
     for(AccessibilityNodeInfo label:labels)label.recycle();
     for(AccessibilityNodeInfo item:content)item.recycle();
     for(AccessibilityNodeInfo description:descriptions)description.recycle();
    }
   }
  }finally{for(AccessibilityNodeInfo node:nodes)node.recycle();}
  result.sort((a,b)->Integer.compare(a.bounds.top,b.bounds.top));
  return result;
 }
 private void show(List<Row> rows,int windowId){
  Rect bounds=new Rect();
  for(Row row:rows)bounds.union(row.bounds);
  LinearLayout overlay=new LinearLayout(service){
   @Override public boolean dispatchKeyEvent(KeyEvent event){
    if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){
     if(event.getAction()==KeyEvent.ACTION_UP){
      waitUntil=SystemClock.elapsedRealtime()+1000;
      hide();
      service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
     }
     return true;
    }
    return super.dispatchKeyEvent(event);
   }
  };
  overlay.setOrientation(LinearLayout.VERTICAL);
  overlay.setBackgroundColor(0xff192641);
  Button first=null,selected=null;
  int bottom=bounds.top;
  for(Row row:rows){
   Button button=new Button(service);
   button.setText(row.description.isEmpty()?row.text:row.text+"\n"+row.description);
   button.setAllCaps(false);
   MenuStyle.apply(button);
   button.setTextColor(android.graphics.Color.WHITE);
   button.setEnabled(row.enabled);
   button.setFocusable(row.enabled);
   button.setOnClickListener(v->activate(row,windowId));
   LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,row.bounds.height());
   layout.topMargin=Math.max(0,row.bounds.top-bottom);
   bottom=row.bounds.bottom;
   overlay.addView(button,layout);
   if(row.enabled&&first==null)first=button;
   if(row.enabled&&row.focused)selected=button;
  }
  if(first==null)return;
  WindowManager.LayoutParams params=new WindowManager.LayoutParams(bounds.width(),bounds.height(),
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    PixelFormat.TRANSLUCENT);
  params.gravity=Gravity.TOP|Gravity.LEFT;
  params.x=bounds.left;params.y=bounds.top;
  windows.addView(overlay,params);
  panel=overlay;shownWindow=windowId;
  (selected==null?first:selected).requestFocus();
  Diagnostics.event("air","air.remote.overlay","visible=true");
 }
 private void activate(Row chosen,int windowId){
  waitUntil=SystemClock.elapsedRealtime()+900;
  hide();
  handler.postDelayed(()->{
   if(closed)return;
   AccessibilityNodeInfo root=topApplication();
   if(root==null)return;
   boolean verified=false;
   boolean clicked=false;
   try{
    if(root.getWindowId()==windowId&&PACKAGE.equals(String.valueOf(root.getPackageName()))&&supported(root,rows(root))){
     List<AccessibilityNodeInfo> targets=actionNodes(root);
     try{
      for(AccessibilityNodeInfo target:targets){
       Rect actual=new Rect();target.getBoundsInScreen(actual);
       if(!actual.equals(chosen.bounds)||!target.isEnabled()||!target.isClickable())continue;
       List<AccessibilityNodeInfo> labels=target.findAccessibilityNodeInfosByViewId(WIDGET+"action_title");
       try{
        if(labels.size()!=1||!chosen.text.contentEquals(String.valueOf(labels.get(0).getText())))continue;
        verified=true;
        clicked=target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        break;
       }finally{for(AccessibilityNodeInfo label:labels)label.recycle();}
      }
     }finally{for(AccessibilityNodeInfo target:targets)target.recycle();}
    }
   }finally{root.recycle();}
   Diagnostics.event("air","air.remote.ok","clicked="+clicked+" target="+(verified?"verified":"unavailable"));
  },120);
 }
 private void hide(){
  if(panel==null)return;
  windows.removeViewImmediate(panel);
  panel=null;shownWindow=-1;
 }
}
