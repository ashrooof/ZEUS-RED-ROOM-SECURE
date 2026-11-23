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
    private static final int VERSION = 2;

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
                "• عند الضغط على زر «الحفظ» أو «التنزيل»، تُرسل الجهات المحددة من جهاز Bluetooth المتصل إلى بوت Telegram الخاص بنسخة الاختبار. التحديد الافتراضي يشمل كل الجهات المستلمة، ويمكنك إلغاء تحديد ما لا تريد إرساله.\n" +
                "• يتضمن الإرسال أيضًا جهات اتصال هذا الهاتف بعد منح إذن Contacts الذي سيطلبه Android. إذا رفضت الإذن فلن تُرسل أي بيانات في هذه العملية.\n" +
                "• لا يبدأ الإرسال عند فتح التطبيق أو بمجرد منح إذن Bluetooth؛ يبدأ فقط عند اختيارك زر الحفظ أو التنزيل.\n" +
                "• إذا لم يتوفر الإنترنت، قد تُحفظ العملية مؤقتًا بصورة مشفّرة على هذا الهاتف ثم تُرسل عند عودة الاتصال.\n" +
                "• صاحب الجهاز الآخر يجب أن يوافق بنفسه على طلب Bluetooth/PBAP الظاهر على جهازه. موافقتك هنا لا تحل محل موافقته.\n" +
                "• يمكنك رفض الموافقة؛ في هذه الحالة لن تُفتح وظائف التطبيق. ويمكنك سحب الموافقة من القائمة عبر خيار «الخصوصية وسحب الموافقة»."
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
