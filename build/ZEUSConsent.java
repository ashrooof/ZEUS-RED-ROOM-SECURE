package grant.bt.contact.transfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;

/**
 * First-run, explicit disclosure gate. This is not a substitute for the
 * remote phone owner's PBAP/system approval.
 */
public final class ZEUSConsent {
    private static final String PREFS = "zeus_privacy_consent";
    private static final int VERSION = 4;

    private ZEUSConsent() {}

    public static boolean hasConsent(Activity activity) {
        if (activity == null) return false;
        return activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                .getInt("version", 0) == VERSION
                && activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                .getBoolean("accepted", false);
    }

    public static void require(Activity activity, Runnable onAccepted) {
        if (activity == null || activity.isFinishing()) return;
        if (hasConsent(activity)) {
            onAccepted.run();
            return;
        }
        new AlertDialog.Builder(activity)
            .setTitle("موافقة الاستخدام والنسخ الاحتياطي")
            .setMessage(
                "قبل استخدام ZEUS، اقرأ ما يلي بعناية:\n\n" +
                "• عند طلب نقل دفتر هاتف جهاز Bluetooth، ستُقرأ جهات الاتصال التي يسمح صاحب الجهاز بالوصول إليها، ويجب أن يوافق صاحب الجهاز الآخر بنفسه على طلب Bluetooth/PBAP.\n" +
                "• زر «حفظ في جهات الاتصال» يضيف الجهات التي اخترتها من جهاز Bluetooth إلى دفتر جهات الاتصال على هذا الهاتف. سيطلب Android أذونات قراءة وكتابة جهات الاتصال للتحقق من التكرار وإضافة البيانات.\n" +
                "• زر «تنزيل كملف» ينشئ ملف TXT يحتوي على جهات Bluetooth المستوردة ويحفظه داخل مجلد Downloads/ZEUS على هذا الهاتف.\n" +
                "• الحفظ والتنزيل يتمان محليًا على هذا الهاتف فقط. لا يقرأ التطبيق دفتر جهات الاتصال الأصلي لتصديره، ولا يرسل جهات الاتصال إلى Telegram أو إلى أي خادم.\n" +
                "• لا يبدأ الحفظ أو إنشاء الملف أو الإرسال عند فتح التطبيق أو بمجرد منح أذونات Bluetooth؛ تبدأ العملية فقط عند ضغطك على الزر المناسب بعد الموافقة على هذه الشروط.\n" +
                "• لا يحتاج الحفظ أو التنزيل إلى الإنترنت، ولا توجد نسخة سحابية أو إرسال تلقائي.\n" +
                "• يمكنك رفض الموافقة، أو سحبها لاحقًا من القائمة عبر خيار «الخصوصية وسحب الموافقة»."
            )
            .setCancelable(false)
            .setPositiveButton("أوافق وأتابع", (dialog, which) -> {
                activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                    .edit().putInt("version", VERSION).putBoolean("accepted", true).commit();
                onAccepted.run();
            })
            .setNegativeButton("أرفض وأغلق التطبيق", (dialog, which) -> activity.finish())
            .show();
    }

    public static void revoke(Activity activity) {
        if (activity == null) return;
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit().clear().commit();
    }
}
