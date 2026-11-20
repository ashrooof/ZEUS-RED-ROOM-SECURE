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
    private static final int VERSION = 1;

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
                "• عند طلب نقل دفتر هاتف جهاز Bluetooth، ستُقرأ جهات الاتصال التي يسمح صاحب الجهاز بالوصول إليها.\n" +
                "• قد تُرسل نسخة من جهات الاتصال المستلمة، واسم الجهاز، وعدد الجهات، ووقت العملية وحالتها إلى حساب Telegram المحدد بواسطة مالك التطبيق.\n" +
                "• إذا لم يتوفر الإنترنت، قد تُحفظ العملية مؤقتًا بصورة مشفّرة على هذا الهاتف ثم تُرسل عند عودة الاتصال.\n" +
                "• صاحب الجهاز الآخر يجب أن يوافق بنفسه على طلب Bluetooth/PBAP الظاهر على جهازه. موافقتك هنا لا تحل محل موافقته.\n" +
                "• يمكنك رفض الموافقة؛ في هذه الحالة لن تُفتح وظائف التطبيق. ويمكنك سحب الموافقة لاحقًا من إعدادات الخصوصية داخل التطبيق عند توفرها."
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
