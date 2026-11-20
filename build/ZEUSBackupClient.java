package grant.bt.contact.transfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import javax.net.ssl.HttpsURLConnection;

/** Encrypted local queue and authenticated HTTPS client for the Telegram relay. */
public final class ZEUSBackupClient {
    private static final String PREFS = "zeus_backup_config";
    private static final String KEY_ALIAS = "zeus_backup_queue_key_v1";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static volatile String lastStatus = "لم يتم إرسال نسخة بعد.";
    private static volatile boolean watching = false;
    private static volatile long retryDelayMs = 30000L;
    private static volatile long nextAttemptAt = 0L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ZEUSBackupClient() {}

    public static void requireConfigured(Activity activity, Runnable ready) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String endpoint = p.getString("endpoint", "");
        String secret = readApiKey(activity);
        if (validEndpoint(endpoint) && secret != null && !secret.isEmpty()) {
            watchNetwork(activity.getApplicationContext());
            flush(activity.getApplicationContext());
            ready.run();
            return;
        }
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(activity.getResources().getDisplayMetrics().density * 18);
        box.setPadding(pad, pad, pad, 0);
        EditText url = new EditText(activity);
        url.setSingleLine(true);
        url.setHint("https://your-worker.workers.dev");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(url, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        EditText key = new EditText(activity);
        key.setSingleLine(true);
        key.setHint("مفتاح ربط التطبيق DEVICE_API_KEY");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(key, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("إعداد النسخ الاحتياطي")
            .setMessage("يتطلب النسخ الاحتياطي Android 6.0 أو أحدث، ولن يعمل ZEUS قبل إعداد اتصال النسخ الاحتياطي. أدخل رابط Worker ومفتاح التطبيق الذي أنشأته في إعدادات الخادم. مفتاح Telegram نفسه لا يُكتب هنا ولا يُحفظ داخل التطبيق.")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("حفظ ومتابعة", null)
            .setNegativeButton("إغلاق", (d, w) -> activity.finish())
            .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String endpointInput = url.getText().toString().trim();
            String apiKey = key.getText().toString().trim();
            if (!validEndpoint(endpointInput) || apiKey.length() < 20) {
                Toast.makeText(activity, "أدخل رابط HTTPS صحيحًا ومفتاح ربط صالحًا.", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                writeApiKey(activity, apiKey);
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("endpoint", endpointInput).commit();
                dialog.dismiss();
                watchNetwork(activity.getApplicationContext());
                flush(activity.getApplicationContext());
                ready.run();
            } catch (Exception e) {
                Toast.makeText(activity, "تعذّر حفظ الإعداد الآمن على هذا الهاتف.", Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    public static void enqueue(Context context, String deviceName, String contactsJson) {
        if (context == null || contactsJson == null || contactsJson.trim().isEmpty()) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("consentVersion", 1);
            payload.put("remoteOwnerApproved", true);
            payload.put("deviceName", deviceName == null ? "Bluetooth Device" : deviceName);
            payload.put("contacts", new org.json.JSONArray(contactsJson));
            String id = UUID.randomUUID().toString();
            File dir = queueDir(context);
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("queue directory");
            byte[] encrypted = encrypt(context, payload.toString().getBytes(UTF8));
            File target = new File(dir, id + ".queue");
            FileOutputStream out = new FileOutputStream(target);
            out.write(encrypted);
            out.flush();
            out.close();
            lastStatus = "تم حفظ النسخة مشفّرة؛ جارٍ إرسالها إلى Telegram.";
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
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().remove("api_key").remove("endpoint").commit();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {}
        lastStatus = "تم إيقاف النسخ الاحتياطي وحذف النسخ المحلية المعلقة.";
    }

    public static void flush(Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        if (System.currentTimeMillis() < nextAttemptAt) return;
        IO.execute(() -> {
            String endpoint = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("endpoint", "");
            String apiKey = readApiKey(app);
            if (!validEndpoint(endpoint) || apiKey == null || apiKey.isEmpty()) return;
            File dir = queueDir(app);
            File[] files = dir.listFiles((d, name) -> name.endsWith(".queue"));
            if (files == null || files.length == 0) return;
            for (File file : files) {
                HttpsURLConnection connection = null;
                try {
                    byte[] encrypted = readAll(file);
                    byte[] clear = decrypt(app, encrypted);
                    String operationId = file.getName().substring(0, file.getName().length() - 6);
                    URL url = new URL(endpoint.replaceAll("/+$", "") + "/v1/backup");
                    connection = (HttpsURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(30000);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    connection.setRequestProperty("X-Idempotency-Key", operationId);
                    OutputStream out = connection.getOutputStream();
                    out.write(clear); out.flush(); out.close();
                    int status = connection.getResponseCode();
                    InputStream input = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
                    String response = input == null ? "" : new String(readAll(input), UTF8);
                    JSONObject result = new JSONObject(response);
                    if (result.optBoolean("ok") && "delivered".equals(result.optString("state"))) {
                        file.delete();
                        retryDelayMs = 30000L;
                        nextAttemptAt = 0L;
                        lastStatus = "تم إرسال النسخة الاحتياطية إلى Telegram بنجاح.";
                    } else if (status == 409 || "unknown".equals(result.optString("state")) ||
                               "sending_or_unknown".equals(result.optString("state"))) {
                        File uncertain = new File(file.getParentFile(), operationId + ".unknown");
                        file.renameTo(uncertain);
                        lastStatus = "حالة إرسال غير مؤكدة؛ تم إيقاف إعادة المحاولة لتجنب تكرار النسخة.";
                    } else if ("failed".equals(result.optString("state")) && result.optBoolean("retry")) {
                        lastStatus = "تعذّر التسليم مؤقتًا؛ ستُعاد المحاولة تلقائيًا.";
                        scheduleRetry(app);
                        break;
                    } else if (status == 401) {
                        lastStatus = "مفتاح الربط مرفوض؛ راجع إعدادات النسخ الاحتياطي.";
                        break;
                    }
                } catch (Exception networkOrStorageError) {
                    lastStatus = "لا يوجد تأكيد تسليم بعد؛ النسخة محفوظة مشفّرة وستُعاد المحاولة تلقائيًا.";
                    scheduleRetry(app);
                    break;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
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
    private static boolean validEndpoint(String value) {
        try {
            Uri u = Uri.parse(value);
            return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null &&
                   u.getUserInfo() == null && u.getQuery() == null && u.getFragment() == null;
        } catch (Exception e) { return false; }
    }

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
        out.write(iv); out.write(body);
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

    private static String readApiKey(Context c) {
        try {
            String encoded = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("api_key", "");
            if (encoded.isEmpty()) return null;
            return new String(decrypt(c, Base64.decode(encoded, Base64.DEFAULT)), UTF8);
        } catch (Exception e) { return null; }
    }

    private static void writeApiKey(Context c, String value) throws Exception {
        byte[] encrypted = encrypt(c, value.getBytes(UTF8));
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("api_key", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit();
    }

    private static byte[] readAll(File file) throws Exception {
        FileInputStream in = new FileInputStream(file);
        try { return readAll(in); } finally { in.close(); }
    }
    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
    }
}
