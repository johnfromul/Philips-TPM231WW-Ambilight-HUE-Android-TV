package local.tv.hue;

import android.content.Context;

/** User-selected direct colour response, stored in hundredths of a second. */
final class DirectSmoothing {
 static final int MIN=20, MAX=200, DEFAULT=70;
 private DirectSmoothing(){}
 static int clamp(int value){return Math.max(MIN,Math.min(MAX,value));}
 static int load(Context context){return clamp(context.getSharedPreferences("direct_tuning",0).getInt("smoothing_centiseconds",DEFAULT));}
 static double seconds(Context context){return load(context)/100.0;}
}
