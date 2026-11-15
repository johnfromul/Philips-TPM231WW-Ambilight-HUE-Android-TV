package local.tv.hue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.graphics.Color;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Temporary local receiver for the Philips Air wizard. */
public final class AirSetupActivity extends Activity {
    private static volatile AirSetupActivity current;
    static void stopCurrent(){
        AirSetupActivity active=current;
        if(active!=null)active.closeSetup();
    }

    static boolean isRunning(){AirSetupActivity active=current;return active!=null&&!active.stopped;}

    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppLanguage.wrap(base));}
    private final String diagnosticId=Diagnostics.id();
    private TextView status,remoteHint;
    private Button wizard,remotePermission;
    private boolean wizardLaunched;
    private final Handler handler = new Handler(android.os.Looper.getMainLooper());
    private volatile ServerSocket server;
    private volatile boolean stopped;
    private int configured;
    private boolean streaming;
    private String sessionKey;
    private String angle = "TopRight";
    private int brightness = 5;
    private static final int PORT = AirLocalDiscovery.PORT;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(isRunning()||RootSessionGate.busy()||RootRecovery.pending(this)
                ||RootSyncService.sessionActive()){
            android.widget.Toast.makeText(this,DirectText.get(this,"busy"),android.widget.Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        current=this;
        Diagnostics.init(this);
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48,32,48,32);
        layout.setBackgroundColor(Color.rgb(25,38,65));
        status=new TextView(this);
        status.setTextColor(Color.WHITE);status.setText(DirectText.get(this,"air_setup_starting"));
        layout.addView(status);
        wizard=addButton(layout,"air_settings","android.settings.AMBILIGHT_AIR");
        wizard.setEnabled(hasSavedAirSetup());
        remotePermission=addButton(layout,"air_remote_permission",android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        remoteHint=new TextView(this);
        remoteHint.setText(DirectText.get(this,"air_remote_hint"));
        remoteHint.setTextColor(Color.LTGRAY);
        remoteHint.setTextSize(12);
        remoteHint.setGravity(android.view.Gravity.CENTER);
        Button stop=new Button(this);
        stop.setText(DirectText.get(this,"air_setup_stop"));
        MenuStyle.apply(stop);
        int buttonWidth=(int)(getResources().getDisplayMetrics().widthPixels*.65f);
        int gap=(int)(5*getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams hintParams=new LinearLayout.LayoutParams(buttonWidth,-2);
        hintParams.gravity=android.view.Gravity.CENTER_HORIZONTAL;
        hintParams.bottomMargin=gap;
        layout.addView(remoteHint,hintParams);
        layout.addView(stop);
        for(Button button:new Button[]{wizard,remotePermission,stop}){
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(buttonWidth,-2);
            params.gravity=android.view.Gravity.CENTER_HORIZONTAL;params.setMargins(0,gap,0,gap);
            button.setLayoutParams(params);
        }
        stop.setOnClickListener(v->closeSetup());
        setContentView(layout);
        handler.postDelayed(this::closeSetup,10*60*1000L);
        new Thread(this::serve,"air-setup").start();
    }

    private Button addButton(LinearLayout layout,String label,String action){
        Button button=new Button(this);
        button.setText(DirectText.get(this,label));
        MenuStyle.apply(button);layout.addView(button);
        button.setOnClickListener(v->{
            boolean airSettings="android.settings.AMBILIGHT_AIR".equals(action);
            try{
                if(airSettings){
                    PairingAccessibility.setAirRemote(true);
                    wizardLaunched=true;
                }
                startActivity(new Intent(action));
            }
            catch(RuntimeException error){
                if(airSettings)endRemoteAssist();Diagnostics.error(diagnosticId,"air.setup.settings.error",error);
                status.setText(DirectText.get(this,"air_setup_failed"));}
        });
        return button;
    }

    @Override protected void onResume(){
        super.onResume();
        if(wizardLaunched)endRemoteAssist();
        if(remotePermission!=null){
            int visibility=PairingAccessibility.ready()?android.view.View.GONE:android.view.View.VISIBLE;
            remotePermission.setVisibility(visibility);
            remoteHint.setVisibility(visibility);
        }
    }

    private void endRemoteAssist(){
        if(!wizardLaunched)return;
        wizardLaunched=false;
        PairingAccessibility.setAirRemote(false);
    }

    private boolean hasSavedAirSetup(){
        return getSharedPreferences("air_setup_state",0).getBoolean("configured",false);
    }

    private void fail(String stage,Exception error){
        Diagnostics.error(diagnosticId,"air.setup."+stage+".error",error);
        closeListener();
        status.setText(DirectText.get(this,"air_setup_failed"));
        wizard.setEnabled(hasSavedAirSetup());
    }

    private void serve() {
        try (ServerSocket listener = new ServerSocket()) {
            server = listener;
            if(stopped)return;
            listener.bind(new InetSocketAddress("127.0.0.1", PORT));
            new Thread(() -> {
                try {
                    AirLocalDiscovery.advertise(this, true, () -> stopped, () -> handler.post(() -> {
                        if (stopped) return;
                        wizard.setEnabled(true);
                        status.setText(DirectText.get(this,"air_setup_ready"));
                        Diagnostics.event(diagnosticId,"air.setup.ready","local port="+PORT);
                    }));
                } catch (IOException | InterruptedException error) {
                    handler.post(() -> { if (!stopped) fail("mdns",error); });
                }
            }, "air-local-discovery").start();
            while (!stopped) {
                try (Socket connection = listener.accept()) {
                    connection.setSoTimeout(1500);
                    respond(connection);
                } catch (Exception error) {
                    if (!stopped && !(error instanceof EOFException)) Diagnostics.event(diagnosticId,"air.setup.request.error",error.getClass().getSimpleName());
                }
            }
        } catch (IOException error) {
            handler.post(()->{if(!stopped)fail("listen",error);});
        }
    }

    private void respond(Socket socket) throws Exception {
        BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
        String[] request = line(in).split(" ", 3);
        if (request.length != 3) return;
        int count = 0;
        while (!line(in).isEmpty()) if (++count > 64) return;
        String target = request[1];
        if (!target.startsWith(AirLocalDiscovery.PATH_PREFIX + "/1/ambiair/")) {
            reply(socket,404,"application/json","{}");
            return;
        }
        String path = URLDecoder.decode(target.substring(AirLocalDiscovery.PATH_PREFIX.length()), "UTF-8");
        int separator = path.indexOf("/post_value=");
        String route = separator < 0 ? path : path.substring(0, separator);
        String base = "/1/ambiair/multicast/";
        JSONObject result = new JSONObject().put("success", true);
        int status = 200;
        if ((base + "config").equals(route) && "GET".equals(request[0])) {
            result = new JSONObject().put("capability", true).put("multicast", streaming)
                .put("speaker type", "Ambilight Air TV").put("MAC address", "02:00:00:00:29:20")
                .put("configured", configured).put("status", new JSONObject().put("angle", angle).put("brightness", brightness));
        } else if ("POST".equals(request[0])) {
            JSONObject value = separator < 0 ? new JSONObject() : new JSONObject(path.substring(separator + 12));
            if ((base + "key").equals(route)) {
                String key = value.optString("key");
                if (!key.matches("[0-9A-Fa-f]{16}") || (sessionKey != null && !sessionKey.equals(key))) {
                    reply(socket,200,"application/json","{\"error\":\"invalid pairing key\"}");
                    return;
                }
                sessionKey = key;
            } else if (!"/1/ambiair/light/state".equals(route) && !value.optString("key").equals(sessionKey)) {
                reply(socket,200,"application/json","{\"error\":\"invalid pairing key\"}");
                return;
            } else if ((base + "configured").equals(route)) {configured = value.optInt("configured");getSharedPreferences("air_setup_state",0).edit().putBoolean("configured",configured==1).apply();}
            else if ((base + "stream").equals(route)) streaming = value.optBoolean("status");
            else if ((base + "config").equals(route)) {
                JSONObject settings = value.getJSONObject("status");
                angle = settings.optString("angle", angle);
                brightness = settings.optInt("brightness", brightness);
            } else if ((base + "clearkey").equals(route)) {
                configured = 0; streaming = false;getSharedPreferences("air_setup_state",0).edit().putBoolean("configured",false).apply();
            } else if (!(base + "key").equals(route) && !(base + "checkkey").equals(route)
                    && !"/1/ambiair/light/state".equals(route)) status = 404;
        } else status = 404;
        if (status == 200) Diagnostics.event(diagnosticId,"air.setup.request",request[0]+" "+route+" configured="+configured);
        reply(socket, status, "application/json", status == 200 ? result.toString() : "{}");
    }

    private static void reply(Socket socket, int status, String contentType, String content) throws IOException {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 " + (status == 200 ? "200 OK" : "404 Not Found") +
            "\r\nContent-Type: " + contentType + "\r\nConnection: close\r\nContent-Length: " + body.length + "\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (bytes.size() < 4096) {
            int next = in.read();
            if (next == -1) throw new EOFException();
            if (next == '\n') return bytes.toString("UTF-8");
            if (next != '\r') bytes.write(next);
        }
        throw new IOException("HTTP line too long");
    }

    private void closeSetup(){
        closeListener();
        finish();
    }

    private void closeListener(){
        if(stopped)return;
        stopped=true;
        endRemoteAssist();
        handler.removeCallbacksAndMessages(null);
        if(server!=null)try{server.close();}catch(IOException ignored){}
        if(current==this)current=null;
        Diagnostics.event(diagnosticId,"air.setup.stopped","listener=closed");
    }

    @Override public void onDestroy(){
        if(current==this)closeListener();
        super.onDestroy();
    }
}
