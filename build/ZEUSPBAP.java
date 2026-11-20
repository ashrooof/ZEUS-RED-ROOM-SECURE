package grant.bt.contact.transfer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ZEUSPBAP {
    private static final UUID PBAP_UUID = UUID.fromString("0000112f-0000-1000-8000-00805f9b34fb");
    private static final byte[] PBAP_TARGET = {
        0x79,0x61,0x35,(byte)0xF0,(byte)0xF0,(byte)0xC5,0x11,(byte)0xD8,
        0x09,0x66,0x08,0x00,0x20,0x0C,(byte)0x9A,0x66
    };
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Charset UTF16 = Charset.forName("UTF-16BE");
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static volatile String state = "idle";
    private static volatile String message = "";
    private static volatile ArrayList<Contact> contacts = new ArrayList<>();
    private static volatile BluetoothSocket socket;
    private static volatile boolean cancelled = false;

    private ZEUSPBAP() {}

    public static synchronized void start(BluetoothDevice device) {
        if (device == null) {
            fail("تعذّر تحديد الجهاز.");
            return;
        }
        if ("connecting".equals(state) || "transferring".equals(state)) return;
        cancelled = false;
        contacts = new ArrayList<>();
        state = "connecting";
        message = "جارٍ الاتصال بخدمة دفتر الهاتف...";
        WORKER.execute(() -> transfer(device));
    }

    public static synchronized void cancel() {
        cancelled = true;
        BluetoothSocket current = socket;
        socket = null;
        if (current != null) try { current.close(); } catch (Exception ignored) {}
        state = "idle";
        message = "";
        contacts = new ArrayList<>();
    }

    public static String statusJson() {
        try {
            JSONObject out = new JSONObject();
            out.put("state", state);
            out.put("message", message);
            out.put("count", contacts.size());
            return out.toString();
        } catch (Exception ignored) {
            return "{\"state\":\"error\",\"message\":\"تعذّر قراءة حالة النقل.\",\"count\":0}";
        }
    }

    public static String contactsJson() {
        JSONArray out = new JSONArray();
        try {
            for (Contact c : contacts) {
                JSONObject item = new JSONObject();
                item.put("name", c.name);
                item.put("phone", c.phone);
                out.put(item);
            }
        } catch (Exception ignored) {}
        return out.toString();
    }

    private static void transfer(BluetoothDevice device) {
        BluetoothSocket active = null;
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery();
            if (cancelled) return;

            active = device.createRfcommSocketToServiceRecord(PBAP_UUID);
            socket = active;
            active.connect();
            if (cancelled) return;

            state = "transferring";
            message = "تم الاتصال. جارٍ طلب دفتر الهاتف من الجهاز...";
            InputStream in = active.getInputStream();
            OutputStream out = active.getOutputStream();

            out.write(connectPacket());
            out.flush();
            byte[] response = readPacket(in);
            if (response.length < 7 || (response[0] & 0xFF) != 0xA0) {
                throw new IOException("الجهاز لم يقبل اتصال PBAP. تأكد من دعم مشاركة جهات الاتصال.");
            }
            int maxPacket = ((response[5] & 0xFF) << 8) | (response[6] & 0xFF);
            if (maxPacket < 255) throw new IOException("الجهاز أعاد حجم حزمة غير صالح.");
            byte[] connectionId = findHeader(response, 0xCB);

            ByteArrayOutputStream received = new ByteArrayOutputStream();
            byte[] name = unicodeHeader("telecom/pb.vcf");
            byte[] type = header(0x42, "x-bt/phonebook\0".getBytes(UTF8));
            byte[] appParams = header(0x4C, new byte[] {0x07,0x01,0x00,0x04,0x02,(byte)0xFF,(byte)0xFF});
            ByteArrayOutputStream request = new ByteArrayOutputStream();
            request.write(0x83); request.write(0); request.write(0);
            request.write(name); request.write(type); request.write(appParams);
            if (connectionId != null) request.write(connectionId);
            byte[] packet = request.toByteArray();
            setLength(packet);
            if (packet.length > maxPacket) throw new IOException("طلب دفتر الهاتف أكبر من الحد المسموح به.");
            out.write(packet);
            out.flush();

            int pages = 0;
            while (true) {
                if (cancelled) return;
                byte[] part = readPacket(in);
                if (part.length < 3) throw new IOException("استجابة دفتر الهاتف غير مكتملة.");
                int code = part[0] & 0xFF;
                appendBodyHeaders(part, received);
                if (code == 0xA0) break;
                if (code != 0x90) throw new IOException("الجهاز رفض قراءة دفتر الهاتف أو لم يمنح الإذن.");
                ByteArrayOutputStream next = new ByteArrayOutputStream();
                next.write(0x83); next.write(0); next.write(0);
                if (connectionId != null) next.write(connectionId);
                byte[] continuation = next.toByteArray();
                setLength(continuation);
                out.write(continuation);
                out.flush();
                if (++pages > 500) throw new IOException("تجاوز النقل الحد المتوقع للصفحات.");
            }

            ArrayList<Contact> parsed = parseVcards(new String(received.toByteArray(), UTF8));
            if (cancelled) return;
            if (parsed.isEmpty()) throw new IOException("تم الاتصال لكن دفتر الهاتف لم يُرجع أرقامًا قابلة للعرض.");
            contacts = parsed;
            state = "done";
            message = "اكتمل استلام " + parsed.size() + " جهة اتصال.";
        } catch (SecurityException denied) {
            fail("إذن Bluetooth غير متاح. ابدأ البحث وامنح إذن Bluetooth عند طلبه.");
        } catch (Exception error) {
            if (!cancelled) {
                String detail = error.getMessage();
                if (detail == null || detail.trim().isEmpty()) detail = "الجهاز لا يدعم PBAP أو لم يسمح بالوصول إلى دفتر الهاتف.";
                fail(detail);
            }
        } finally {
            socket = null;
            if (active != null) try { active.close(); } catch (Exception ignored) {}
        }
    }

    private static void fail(String text) {
        state = "error";
        message = text;
        contacts = new ArrayList<>();
    }

    private static byte[] connectPacket() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0x80); b.write(0); b.write(0);
        b.write(0x10); b.write(0x00); b.write(0xFF); b.write(0xFF);
        b.write(0x46); b.write(0x00); b.write(0x13);
        b.write(PBAP_TARGET);
        byte[] packet = b.toByteArray();
        setLength(packet);
        return packet;
    }

    private static byte[] unicodeHeader(String value) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0x01);
        byte[] bytes = (value + "\0").getBytes(UTF16);
        int length = bytes.length + 3;
        b.write(length >> 8); b.write(length); b.write(bytes);
        return b.toByteArray();
    }

    private static byte[] header(int id, byte[] value) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(id);
        int length = value.length + 3;
        b.write(length >> 8); b.write(length); b.write(value);
        return b.toByteArray();
    }

    private static void setLength(byte[] packet) {
        int length = packet.length;
        packet[1] = (byte)(length >> 8);
        packet[2] = (byte)length;
    }

    private static byte[] readPacket(InputStream in) throws IOException {
        int opcode = in.read();
        if (opcode < 0) throw new EOFException("انقطع اتصال Bluetooth.");
        int hi = in.read(), lo = in.read();
        if (hi < 0 || lo < 0) throw new EOFException("انقطعت استجابة Bluetooth.");
        int length = (hi << 8) | lo;
        if (length < 3 || length > 65535) throw new IOException("حجم حزمة OBEX غير صالح.");
        byte[] packet = new byte[length];
        packet[0] = (byte)opcode; packet[1] = (byte)hi; packet[2] = (byte)lo;
        int offset = 3;
        while (offset < length) {
            int n = in.read(packet, offset, length - offset);
            if (n < 0) throw new EOFException("انتهت استجابة Bluetooth قبل اكتمالها.");
            offset += n;
        }
        return packet;
    }

    private static byte[] findHeader(byte[] packet, int wanted) {
        int pos = 7;
        while (pos < packet.length) {
            int id = packet[pos] & 0xFF;
            int kind = id & 0xC0;
            if (kind == 0x00 || kind == 0x40) {
                if (pos + 3 > packet.length) break;
                int length = ((packet[pos+1] & 0xFF) << 8) | (packet[pos+2] & 0xFF);
                if (length < 3 || pos + length > packet.length) break;
                if (id == wanted) {
                    byte[] out = new byte[length];
                    System.arraycopy(packet, pos, out, 0, length);
                    return out;
                }
                pos += length;
            } else if (kind == 0x80) {
                if (pos + 2 > packet.length) break;
                pos += 2;
            } else if (kind == 0xC0) {
                if (pos + 5 > packet.length) break;
                if (id == wanted) {
                    byte[] out = new byte[5];
                    System.arraycopy(packet, pos, out, 0, 5);
                    return out;
                }
                pos += 5;
            } else break;
        }
        return null;
    }

    private static void appendBodyHeaders(byte[] packet, ByteArrayOutputStream out) throws IOException {
        int pos = 3;
        while (pos + 2 <= packet.length) {
            int id = packet[pos] & 0xFF;
            int kind = id & 0xC0;
            if (kind == 0x00 || kind == 0x40) {
                if (pos + 3 > packet.length) break;
                int length = ((packet[pos+1] & 0xFF) << 8) | (packet[pos+2] & 0xFF);
                if (length < 3 || pos + length > packet.length) break;
                if (id == 0x48 || id == 0x49) out.write(packet, pos + 3, length - 3);
                pos += length;
            } else if (kind == 0x80) pos += 2;
            else if (kind == 0xC0) pos += 5;
            else break;
        }
    }

    private static ArrayList<Contact> parseVcards(String raw) {
        ArrayList<Contact> result = new ArrayList<>();
        Map<String, Contact> unique = new LinkedHashMap<>();
        String normalized = raw.replace("\r\n ", "").replace("\n ", "").replace("\r\t", "").replace("\n\t", "");
        String currentName = null;
        ArrayList<String> phones = new ArrayList<>();
        for (String line : normalized.replace("\r", "").split("\n")) {
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("BEGIN:VCARD")) {
                currentName = null;
                phones = new ArrayList<>();
                continue;
            }
            if (trimmed.equalsIgnoreCase("END:VCARD")) {
                if (currentName == null || currentName.trim().isEmpty()) currentName = "بدون اسم";
                for (String phone : phones) {
                    String p = phone.trim();
                    if (p.isEmpty()) continue;
                    String key = (currentName + "|" + p).toLowerCase(Locale.ROOT);
                    if (!unique.containsKey(key)) unique.put(key, new Contact(currentName, p));
                }
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon < 1) continue;
            String property = trimmed.substring(0, colon).toUpperCase(Locale.ROOT);
            String base = property.split(";", 2)[0];
            String value = unescape(trimmed.substring(colon + 1));
            if ("FN".equals(base)) {
                if (!value.trim().isEmpty()) currentName = value.trim();
            } else if ("N".equals(base) && currentName == null) {
                String[] parts = value.split(";", -1);
                StringBuilder name = new StringBuilder();
                for (int i = parts.length - 1; i >= 0; i--) {
                    if (!parts[i].trim().isEmpty()) {
                        if (name.length() > 0) name.append(' ');
                        name.append(parts[i].trim());
                    }
                }
                if (name.length() > 0) currentName = name.toString();
            } else if ("TEL".equals(base)) {
                if (!value.trim().isEmpty()) phones.add(value.trim());
            }
        }
        result.addAll(unique.values());
        return result;
    }

    private static String unescape(String value) {
        return value.replace("\\n", " ").replace("\\N", " ")
                    .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim();
    }

    private static final class Contact {
        final String name;
        final String phone;
        Contact(String n, String p) { name = n; phone = p; }
    }
}
