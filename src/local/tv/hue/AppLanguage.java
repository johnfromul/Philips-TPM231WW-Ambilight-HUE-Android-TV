package local.tv.hue;
import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;
final class AppLanguage {
 static Context wrap(Context base){
  String language=base.getSharedPreferences("app_language",0).getString("language","");
  if(language.isEmpty())return base;
  Configuration config=new Configuration(base.getResources().getConfiguration());
  config.setLocale(new Locale(language));return base.createConfigurationContext(config);
 }
}
