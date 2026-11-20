package grant.bt.contact.transfer;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ZEUSWebUI {
    private static volatile Bridge activeBridge;
    private ZEUSWebUI() {}

    public static void onPermissionResult(Activity activity, int requestCode, int[] grantResults) {
        if (requestCode != 41) return;
        Bridge bridge = activeBridge;
        if (bridge == null || activity == null) return;
        boolean granted = grantResults != null && grantResults.length > 0;
        if (granted) for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) { granted = false; break; }
        if (granted) bridge.scan();
        else bridge.scanMessage = "إذن الموقع أو Bluetooth مرفوض. اسمح بالإذن من إعدادات التطبيق ثم اضغط العدسة مرة أخرى.";
    }

    public static void attach(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < 17) return;
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) content;
        if (root.findViewWithTag("ZEUS_INTERFACE_WEBVIEW") != null) return;

        WebView web = new WebView(activity);
        web.setTag("ZEUS_INTERFACE_WEBVIEW");
        web.setBackgroundColor(Color.BLACK);
        // Disable Android WebView long-press selection/copy menus; keep screenshots and recording allowed.
        web.setLongClickable(false);
        web.setOnLongClickListener(v -> true);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setDefaultTextEncodingName("UTF-8");
        if (Build.VERSION.SDK_INT >= 21) settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(activity), "ZEUS");
        web.setWebViewClient(new WebViewClient() {
            private boolean routeUrl(String url) {
                if (url == null) return true;
                if (url.startsWith("file:///android_asset/zeus/") || "about:blank".equals(url)) return false;
                try {
                    Uri uri = Uri.parse(url);
                    String scheme = uri.getScheme();
                    if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {}
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return routeUrl(url); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return routeUrl(request == null || request.getUrl() == null ? null : request.getUrl().toString());
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        web.loadUrl("file:///android_asset/zeus/interface.html");
    }

    public static final class Bridge {
        private final Activity activity;
        private volatile String pendingAddress = "";
        private volatile String pairingMessage = "";
        private volatile String pairingState = "idle";
        private volatile String scanMessage = "";
        private volatile String currentDeviceName = "Bluetooth-Device";
        private final ArrayList<BluetoothDevice> foundDevices = new ArrayList<>();
        private volatile boolean discoveryReceiverRegistered = false;
        private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    try {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (device != null) synchronized (foundDevices) {
                            boolean exists = false;
                            for (BluetoothDevice old : foundDevices) if (old.getAddress().equals(device.getAddress())) { exists = true; break; }
                            if (!exists) foundDevices.add(device);
                        }
                    } catch (SecurityException ignored) {}
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    if (foundDevices.isEmpty() && scanMessage.isEmpty()) scanMessage = "انتهى الفحص؛ تأكد أن الجهاز الآخر في وضع الظهور (Discoverable) وأن الموقع مفعّل.";
                }
            }
        };
        private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) return;
                try {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device == null || pendingAddress.isEmpty() || !pendingAddress.equals(device.getAddress())) return;
                    int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                    if (state == BluetoothDevice.BOND_BONDED) {
                        currentDeviceName = safeName(device);
                        pendingAddress = "";
                        pairingMessage = "";
                        pairingState = "idle";
                        ZEUSPBAP.start(device);
                    } else if (state == BluetoothDevice.BOND_NONE) {
                        pendingAddress = "";
                        pairingState = "error";
                        pairingMessage = "لم يكتمل الاقتران. تأكد من قبول طلب الاقتران على الجهاز الآخر.";
                    }
                } catch (SecurityException ignored) {}
            }
        };

        Bridge(Activity a) {
            activity = a;
            try {
                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                if (Build.VERSION.SDK_INT >= 33)
                    activity.registerReceiver(bondReceiver, filter, Context.RECEIVER_EXPORTED);
                else activity.registerReceiver(bondReceiver, filter);
                IntentFilter discovery = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                discovery.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
                discovery.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                if (Build.VERSION.SDK_INT >= 33)
                    activity.registerReceiver(discoveryReceiver, discovery, Context.RECEIVER_EXPORTED);
                else activity.registerReceiver(discoveryReceiver, discovery);
                discoveryReceiverRegistered = true;
            } catch (Exception registrationError) {
                scanMessage = "تعذّر تفعيل استقبال نتائج Bluetooth: " + registrationError.getClass().getSimpleName();
            }
            activeBridge = this;
        }

        @JavascriptInterface public void scan() {
            activity.runOnUiThread(() -> {
                clearNearby();
                synchronized (foundDevices) { foundDevices.clear(); }
                scanMessage = "";
                pendingAddress = "";
                pairingState = "idle";
                pairingMessage = "";
                if (!ensureScanPermissions()) { scanMessage = "اسمح بإذن الموقع أثناء استخدام التطبيق حتى يستطيع Android اكتشاف أجهزة Bluetooth القريبة."; return; }
                if (!discoveryReceiverRegistered) { scanMessage = "مستقبل نتائج البحث غير جاهز. أغلق التطبيق وافتحه مرة أخرى؛ لو استمرت المشكلة أرسل تقرير التشخيص."; return; }
                try {
                    LocationManager location = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
                    if (location != null && Build.VERSION.SDK_INT >= 28 && !location.isLocationEnabled()) {
                        scanMessage = "خدمة الموقع في الهاتف مقفولة. فعّل الموقع من شريط الإعدادات السريعة ثم أعد البحث.";
                        return;
                    }
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter == null) { scanMessage = "هذا الهاتف لا يحتوي على Bluetooth أو أن محول Bluetooth غير متاح."; return; }
                    if (!adapter.isEnabled()) {
                        scanMessage = "Bluetooth مقفول. شغّله ثم اضغط العدسة مرة أخرى.";
                        activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 73);
                        return;
                    }
                    if (adapter.isDiscovering()) adapter.cancelDiscovery();
                    boolean started = adapter.startDiscovery();
                    if (!started) scanMessage = "Android لم يبدأ الفحص. تأكد من تشغيل الموقع وإذن الموقع ثم أعد المحاولة.";
                } catch (SecurityException denied) {
                    scanMessage = "تم رفض إذن Bluetooth أو الموقع. افتح إعدادات التطبيق واسمح بالأذونات المطلوبة.";
                } catch (Exception error) {
                    scanMessage = "تعذّر تشغيل البحث: " + error.getClass().getSimpleName();
                }
            });
        }

        private boolean ensureScanPermissions() {
            if (Build.VERSION.SDK_INT < 23) return true;
            try {
                ArrayList<String> missing = new ArrayList<>();
                if (Build.VERSION.SDK_INT >= 31) {
                    // Android 12+ enforces the Nearby devices runtime permissions even
                    // when this legacy APK keeps targetSdkVersion 28.
                    if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                        missing.add(Manifest.permission.BLUETOOTH_SCAN);
                    if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                        missing.add(Manifest.permission.BLUETOOTH_CONNECT);
                } else {
                    if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                        missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
                }
                if (!missing.isEmpty()) {
                    activity.requestPermissions(missing.toArray(new String[0]), 41);
                    return false;
                }
                return true;
            } catch (Exception error) {
                scanMessage = "تعذّر التحقق من أذونات Bluetooth: " + error.getClass().getSimpleName();
                return false;
            }
        }

        private void clearNearby() {
            try {
                Field field = activity.getClass().getDeclaredField("availableDevices");
                field.setAccessible(true);
                Object value = field.get(activity);
                if (value instanceof ArrayList) ((ArrayList<?>) value).clear();
                Method update = activity.getClass().getMethod("updateNearbyDeviceList");
                update.invoke(activity);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface public String discoveredDevices() {
            StringBuilder out = new StringBuilder("[");
            try {
                synchronized (foundDevices) {
                    boolean first = true;
                    for (BluetoothDevice d : foundDevices) {
                        if (!first) out.append(",");
                        first = false;
                        out.append("{\"name\":\"").append(escape(safeName(d))).append("\",\"address\":\"")
                           .append(escape(d.getAddress())).append("\",\"paired\":")
                           .append(d.getBondState() == BluetoothDevice.BOND_BONDED).append("}");
                    }
                }
            } catch (SecurityException denied) { scanMessage = "تعذّر قراءة أسماء الأجهزة بسبب إذن Bluetooth."; }
            return out.append("]").toString();
        }

        @JavascriptInterface public String scanMessage() { return scanMessage; }

        @JavascriptInterface public boolean isDiscovering() {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                return adapter != null && adapter.isDiscovering();
            } catch (Exception ignored) { return false; }
        }

        @JavascriptInterface public String connect(String address) {
            if (address == null || !address.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")) return "error";
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null || !adapter.isEnabled()) return "error";
                BluetoothDevice device = adapter.getRemoteDevice(address);
                currentDeviceName = safeName(device);
                if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    pendingAddress = "";
                    pairingMessage = "";
                    pairingState = "idle";
                    ZEUSPBAP.start(device);
                    return "connecting";
                }
                pendingAddress = address;
                pairingState = "pairing";
                pairingMessage = "في انتظار قبول الاقتران على الجهاز الآخر...";
                activity.runOnUiThread(() -> {
                    try {
                        if (adapter.isDiscovering()) adapter.cancelDiscovery();
                        if (!device.createBond()) {
                            pendingAddress = "";
                            pairingState = "error";
                            pairingMessage = "تعذّر إرسال طلب الاقتران.";
                        }
                    } catch (Exception error) {
                        pendingAddress = "";
                        pairingState = "error";
                        pairingMessage = "تعذّر بدء الاقتران. تحقق من قرب الجهاز.";
                    }
                });
                return "pairing";
            } catch (SecurityException denied) {
                pairingState = "error";
                pairingMessage = "إذن Bluetooth غير متاح. أعد البحث واسمح بإذن Bluetooth.";
                return "error";
            } catch (Exception error) {
                pairingState = "error";
                pairingMessage = "تعذّر الوصول إلى الجهاز المحدد.";
                return "error";
            }
        }

        @JavascriptInterface public String transferStatus() {
            if (!pendingAddress.isEmpty() || "error".equals(pairingState)) {
                try {
                    org.json.JSONObject result = new org.json.JSONObject();
                    result.put("state", pendingAddress.isEmpty() ? "error" : "pairing");
                    result.put("message", pairingMessage);
                    result.put("count", 0);
                    return result.toString();
                } catch (Exception ignored) {}
            }
            return ZEUSPBAP.statusJson();
        }

        @JavascriptInterface public String contacts() { return ZEUSPBAP.contactsJson(); }

        @JavascriptInterface public String currentDeviceName() { return currentDeviceName == null ? "Bluetooth-Device" : currentDeviceName; }

        @JavascriptInterface public String saveContactsTxt(String content) {
            if (content == null || content.trim().isEmpty()) return "ERROR:لا توجد بيانات لحفظها.";
            String device = currentDeviceName == null ? "Bluetooth-Device" : currentDeviceName.trim();
            if (device.isEmpty()) device = "Bluetooth-Device";
            device = device.replaceAll("[\\\\/:*?\"<>|]+", "_").replaceAll("\\s+", "-");
            if (device.length() > 64) device = device.substring(0, 64);
            String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
            String fileName = device + "-ZEUS-Contacts-" + date + ".txt";
            OutputStream output = null;
            Uri created = null;
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ZEUS");
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    created = activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (created == null) return "ERROR:Android لم ينشئ ملف الحفظ داخل مجلد Downloads/ZEUS.";
                    output = activity.getContentResolver().openOutputStream(created, "w");
                } else {
                    File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (folder == null || (!folder.exists() && !folder.mkdirs()))
                        return "ERROR:تعذّر إنشاء مجلد التنزيلات.";
                    File target = new File(folder, fileName);
                    output = new FileOutputStream(target);
                }
                if (output == null) throw new IllegalStateException("تعذّر فتح ملف الحفظ.");
                output.write(content.getBytes("UTF-8"));
                output.flush();
                output.close();
                output = null;
                if (Build.VERSION.SDK_INT >= 29 && created != null) {
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    int updated = activity.getContentResolver().update(created, done, null, null);
                    if (updated < 1) throw new IllegalStateException("لم يتم اعتماد الملف في التنزيلات.");
                }
                return "OK:" + fileName;
            } catch (Exception error) {
                try { if (output != null) output.close(); } catch (Exception ignored) {}
                if (created != null) try { activity.getContentResolver().delete(created, null, null); } catch (Exception ignored) {}
                return "ERROR:فشل حفظ الملف (" + error.getClass().getSimpleName() + ").";
            }
        }

        @JavascriptInterface public void cancelTransfer() {
            pendingAddress = "";
            pairingMessage = "";
            pairingState = "idle";
            ZEUSPBAP.cancel();
        }

        @JavascriptInterface public void closeApp() {
            activity.runOnUiThread(() -> activity.finish());
        }

        private static String safeName(BluetoothDevice device) {
            try {
                String name = device.getName();
                return name == null || name.trim().isEmpty() ? "Bluetooth Device" : name;
            } catch (Exception ignored) { return "Bluetooth Device"; }
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", " ").replace("\r", " ");
        }
    }
}
