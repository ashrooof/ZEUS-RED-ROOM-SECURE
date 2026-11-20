package grant.bt.contact.transfer;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.Set;

public final class ZEUSBluetooth {
    private static final String INIT_TAG = "ZEUS_PAIRED_PANEL_READY";
    private ZEUSBluetooth() {}

    private static int id(Activity a, String name) {
        return a.getResources().getIdentifier(name, "id", a.getPackageName());
    }

    private static int dp(Activity a, float value) {
        return (int)(value * a.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static void loadPaired(Activity activity) {
        attachPairedPanel(activity);
        renderPaired(activity);
    }

    public static void attachPairedPanel(final Activity activity) {
        if (activity == null) return;
        final TextView header = activity.findViewById(id(activity, "paired_toggle"));
        final ScrollView list = activity.findViewById(id(activity, "paired_scroll"));
        if (header == null || list == null) return;
        if (INIT_TAG.equals(header.getTag())) return;
        header.setTag(INIT_TAG);
        header.setOnClickListener(v -> {
            boolean open = list.getVisibility() != View.VISIBLE;
            list.setVisibility(open ? View.VISIBLE : View.GONE);
            header.setText(open ? "الأجهزة المقترنة     ⌃" : "الأجهزة المقترنة     ⌄");
            if (open) renderPaired(activity);
        });
        header.setText("الأجهزة المقترنة     ⌄");
        renderPaired(activity);
    }

    private static void renderPaired(final Activity activity) {
        if (activity == null) return;
        try {
            LinearLayout rows = activity.findViewById(id(activity, "paired_items"));
            TextView header = activity.findViewById(id(activity, "paired_toggle"));
            if (rows == null || header == null) return;
            rows.removeAllViews();

            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> bonded = adapter == null ? null : adapter.getBondedDevices();
            int count = bonded == null ? 0 : bonded.size();
            header.setText((header.getText().toString().contains("⌃") ? "الأجهزة المقترنة     ⌃" : "الأجهزة المقترنة     ⌄") + "   (" + count + ")");

            if (count == 0) {
                TextView empty = row(activity, "لا توجد أجهزة مقترنة محفوظة على الهاتف");
                empty.setTextColor(Color.LTGRAY);
                rows.addView(empty);
                return;
            }

            for (final BluetoothDevice device : bonded) {
                String name;
                try { name = device.getName(); } catch (SecurityException denied) { name = null; }
                if (name == null || name.trim().isEmpty()) name = "جهاز Bluetooth";
                TextView item = row(activity, "ᛒ    " + name + "     •     مقترن");
                item.setOnClickListener(v -> openPairedDevice(activity, device));
                rows.addView(item);
            }
        } catch (SecurityException denied) {
            LinearLayout rows = activity.findViewById(id(activity, "paired_items"));
            if (rows != null) {
                rows.removeAllViews();
                rows.addView(row(activity, "اسمح بصلاحية البلوتوث لعرض الأجهزة المقترنة"));
            }
        } catch (Exception ignored) {}
    }

    private static TextView row(Activity activity, String label) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16f);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 48));
        lp.bottomMargin = dp(activity, 5);
        view.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(24, 8, 15));
        bg.setCornerRadius(dp(activity, 12));
        bg.setStroke(dp(activity, 1), Color.rgb(130, 20, 48));
        view.setBackground(bg);
        return view;
    }

    private static void openPairedDevice(Activity activity, BluetoothDevice device) {
        try {
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                Method connect = activity.getClass().getMethod("connectToPhonebook", BluetoothDevice.class);
                connect.invoke(activity, device);
            } else {
                Method pair = activity.getClass().getMethod("pairDevice", BluetoothDevice.class);
                pair.invoke(activity, device);
            }
        } catch (SecurityException denied) {
            show(activity, "اسمح بصلاحية البلوتوث أولاً");
        } catch (Exception error) {
            show(activity, "تعذر فتح الجهاز، جرّب البحث مرة أخرى");
        }
    }

    public static void refresh(Activity activity) {
        if (activity == null) return;
        loadPaired(activity);
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isEnabled()) {
                Method find = activity.getClass().getMethod("findDevices");
                find.invoke(activity);
            } else show(activity, "شغّل البلوتوث للبحث عن أجهزة جديدة");
        } catch (SecurityException denied) {
            show(activity, "اسمح بصلاحية البلوتوث للبحث");
        } catch (Exception ignored) {}
    }

    private static void show(Activity activity, String message) {
        try {
            Method method = activity.getClass().getMethod("showSnackBar", String.class);
            method.invoke(activity, message);
        } catch (Exception ignored) {}
    }
}
