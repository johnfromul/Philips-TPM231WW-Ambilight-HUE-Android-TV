package local.tv.hue;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
final class MenuStyle {
 private static GradientDrawable shape(View v,int fill,int stroke){
  float density=v.getResources().getDisplayMetrics().density;
  GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(8*density);
  if(stroke!=0)d.setStroke(Math.max(4,Math.round(5*density)),stroke);return d;
 }
 static void apply(View v){
  StateListDrawable states=new StateListDrawable();
  states.addState(new int[]{-android.R.attr.state_enabled},shape(v,Color.rgb(47,53,65),0));
  states.addState(new int[]{android.R.attr.state_pressed},shape(v,Color.rgb(65,110,160),UiColors.YELLOW));
  states.addState(new int[]{android.R.attr.state_focused},shape(v,Color.rgb(40,105,170),Color.YELLOW));
  states.addState(new int[]{},shape(v,Color.rgb(48,55,68),0));
  v.setBackgroundTintList(null);v.setBackground(states);
 }
}
