package local.tv.hue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.BooleanSupplier;

final class AirLocalDiscovery {
    static final int PORT = 18180;
    static final String PATH_PREFIX = "/route:80";

    static void advertise(Context context, boolean pairing, BooleanSupplier stopped, Runnable ready)
            throws IOException, InterruptedException {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        LinkProperties links = manager.getLinkProperties(manager.getActiveNetwork());
        NetworkInterface nic = links == null ? null : NetworkInterface.getByName(links.getInterfaceName());
        if (nic == null) throw new IOException("No active interface");
        InetAddress local = null;
        for (InetAddress address : Collections.list(nic.getInetAddresses())) {
            if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                local = address;
                break;
            }
        }
        if (local == null) throw new IOException("No active IPv4 address");
        // Philips extracts the endpoint from the comma-delimited instance name.
        String name = "tpv_Ambilight-Air-TV";
        if (pairing) name += ",127.0.0.1:" + PORT + "/route,0,0";
        InetAddress group = InetAddress.getByName("224.0.0.251");
        try (MulticastSocket sender = new MulticastSocket(null)) {
            sender.setReuseAddress(true);
            sender.bind(new InetSocketAddress(5353));
            sender.setNetworkInterface(nic);
            sender.setTimeToLive(0);
            sender.setLoopbackMode(false);
            byte[] payload = dnsResponse(local, name, 5);
            DatagramPacket announcement = new DatagramPacket(payload, payload.length, group, 5353);
            if (stopped.getAsBoolean()) return;
            sender.send(announcement);
            ready.run();
            while (!stopped.getAsBoolean()) {
                Thread.sleep(900);
                if (!stopped.getAsBoolean()) sender.send(announcement);
            }
            byte[] goodbye = dnsResponse(local, name, 0);
            sender.send(new DatagramPacket(goodbye, goodbye.length, group, 5353));
        }
    }

    private static byte[] dnsResponse(InetAddress address, String name, int ttl) throws IOException {
        String[] type = {"_tpv_al", "_tcp", "local"};
        String[] instance = {name, "_tpv_al", "_tcp", "local"};
        String[] host = {"ambilight-air-tv-local", "local"};
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream writer = new DataOutputStream(buffer);
        writer.writeShort(0);
        writer.writeShort(0x8400);
        writer.writeShort(0);
        writer.writeShort(4);
        writer.writeShort(0);
        writer.writeShort(0);
        record(writer, type, 12, 1, ttl, dnsName(instance));

        ByteArrayOutputStream srv = new ByteArrayOutputStream();
        DataOutputStream srvWriter = new DataOutputStream(srv);
        srvWriter.writeShort(0);
        srvWriter.writeShort(0);
        srvWriter.writeShort(PORT);
        srvWriter.write(dnsName(host));
        record(writer, instance, 33, 0x8001, ttl, srv.toByteArray());
        record(writer, instance, 16, 0x8001, ttl, new byte[]{0});
        record(writer, host, 1, 0x8001, ttl, address.getAddress());
        return buffer.toByteArray();
    }

    private static void record(DataOutputStream out, String[] name, int type, int klass, int ttl, byte[] value) throws IOException {
        out.write(dnsName(name));
        out.writeShort(type);
        out.writeShort(klass);
        out.writeInt(ttl);
        out.writeShort(value.length);
        out.write(value);
    }

    private static byte[] dnsName(String[] labels) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (String label : labels) {
            byte[] value = label.getBytes(StandardCharsets.UTF_8);
            output.write(value.length);
            output.write(value, 0, value.length);
        }
        output.write(0);
        return output.toByteArray();
    }

}
