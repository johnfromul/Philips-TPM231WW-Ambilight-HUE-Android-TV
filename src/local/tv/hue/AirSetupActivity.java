package local.tv.hue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.graphics.Color;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;

/** Temporary Air receiver. Proxy settings are controlled outside this activity. */
public final class AirSetupActivity extends Activity {
    private static volatile AirSetupActivity current;
    static void stopCurrent(){
        AirSetupActivity active=current;
        if(active!=null)active.closeSetup();
    }

    static boolean isRunning(){AirSetupActivity active=current;return active!=null&&!active.stopped;}

    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppLanguage.wrap(base));}
    private final String diagnosticId=Diagnostics.id();
    private TextView status;
    private Button wizard,remotePermission;
    private boolean wizardLaunched;
    private final Handler handler = new Handler(android.os.Looper.getMainLooper());
    private volatile ServerSocket server;
    private volatile boolean stopped;
    private NsdManager nsd;
    private boolean requested;
    private int configured;
    private final Set<String> tvAddresses = new HashSet<>();
    private boolean streaming;
    private String angle = "TopRight";
    private int brightness = 5;
    private static final int PORT = 18180;

    private final NsdManager.RegistrationListener registration = new NsdManager.RegistrationListener() {
        @Override public void onRegistrationFailed(NsdServiceInfo info, int error) {
            handler.post(()->{
                if(!stopped)fail("mdns",new IOException("Registration code "+error));
            });
        }
        @Override public void onUnregistrationFailed(NsdServiceInfo info, int error) {}
        @Override public void onServiceUnregistered(NsdServiceInfo info) {}
        @Override public void onServiceRegistered(NsdServiceInfo info) {
            handler.post(()->{
                if(stopped)return;
                wizard.setEnabled(true);
                status.setText(DirectText.get(AirSetupActivity.this,"air_setup_ready"));
                Diagnostics.event(diagnosticId,"air.setup.ready","port="+PORT);
            });
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(isRunning()||RootSessionGate.busy()||RootRecovery.pending(this)
                ||"starting".equals(RootSyncService.state())||"running".equals(RootSyncService.state())){
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
        Button network=addButton(layout,"air_network",android.provider.Settings.ACTION_WIFI_SETTINGS);
        wizard=addButton(layout,"air_settings","android.settings.AMBILIGHT_AIR");
        wizard.setEnabled(hasSavedAirSetup());
        remotePermission=addButton(layout,"air_remote_permission",android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        Button stop=new Button(this);
        stop.setText(DirectText.get(this,"air_setup_stop"));
        MenuStyle.apply(stop);layout.addView(stop);
        int buttonWidth=(int)(getResources().getDisplayMetrics().widthPixels*.65f);
        int gap=(int)(5*getResources().getDisplayMetrics().density);
        for(Button button:new Button[]{network,wizard,remotePermission,stop}){
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
        if(remotePermission!=null)remotePermission.setVisibility(PairingAccessibility.ready()?android.view.View.GONE:android.view.View.VISIBLE);
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
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces()))
                if(network.isUp())
                    for (InetAddress address : Collections.list(network.getInetAddresses()))
                        if (address instanceof Inet4Address && !address.isLoopbackAddress())
                            tvAddresses.add(address.getHostAddress());
            if (tvAddresses.isEmpty()) throw new IOException("No local IPv4 address");
            handler.post(() -> {
                if (stopped) return;
                nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);
                NsdServiceInfo info = new NsdServiceInfo();
                info.setServiceName("tpv_Ambilight-Air-TV");
                info.setServiceType("_tpv_al._tcp.");
                info.setPort(PORT);
                try{
                    nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,registration);
                    requested=true;
                }catch(RuntimeException error){fail("mdns",error);}
            });
            while (!stopped) {
                try (Socket connection = listener.accept()) {
                    connection.setSoTimeout(1500);
                    respond(connection);
                } catch (Exception error) {
                    if (!stopped && !(error instanceof EOFException)) Diagnostics.error(diagnosticId,"air.setup.request.error",error);
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
        // Forward-proxy requests must target the TV itself on the firmware's fixed port.
        if (!target.startsWith("http://")) {
            reply(socket,404,"application/json","{}");
            return;
        }
        URI absolute=URI.create(target.replace("{","%7B").replace("}","%7D").replace("\"","%22"));
        int port=absolute.getPort();
        if(!tvAddresses.contains(absolute.getHost())||(port!=80&&port!=-1)||absolute.getRawUserInfo()!=null){
            reply(socket,404,"application/json","{}");
            return;
        }
        target=absolute.getRawPath();
        String path = URLDecoder.decode(target, "UTF-8");
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
            if ((base + "configured").equals(route)) {configured = value.optInt("configured");getSharedPreferences("air_setup_state",0).edit().putBoolean("configured",configured==1).apply();}
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
        if(requested)try{nsd.unregisterService(registration);}catch(RuntimeException ignored){}
        if(server!=null)try{server.close();}catch(IOException ignored){}
        if(current==this)current=null;
        Diagnostics.event(diagnosticId,"air.setup.stopped","listener=closed");
    }

    @Override public void onDestroy(){
        if(current==this)closeListener();
        super.onDestroy();
    }
}
