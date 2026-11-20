package grant.bt.contact.transfer;

import android.content.Context;
import android.net.Uri;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class ZEUSExport {
    private ZEUSExport() {}

    public static int countSelected(ArrayList<?> choices) {
        if (choices == null) return 0;
        int count = 0;
        for (Object choice : choices) if (Boolean.TRUE.equals(choice)) count++;
        return count;
    }

    public static boolean write(Context context, Uri uri, ArrayList<?> choices) {
        if (context == null || uri == null || countSelected(choices) == 0) return false;
        OutputStream output = null;
        OutputStreamWriter writer = null;
        try {
            Class<?> cacheClass = Class.forName("grant.bt.contact.transfer.cache.ReceivedContacts");
            Method instance = cacheClass.getMethod("instance");
            Object cache = instance.invoke(null);
            Field contactsField = cacheClass.getField("received_contacts");
            ArrayList<?> contacts = (ArrayList<?>) contactsField.get(cache);
            if (contacts == null) return false;

            StringBuilder text = new StringBuilder();
            text.append("Operation completed by ZEUS\n");
            text.append("Date: ").append(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())).append('\n');
            text.append("Time: ").append(new SimpleDateFormat("hh:mm a", Locale.US).format(new Date())).append('\n');
            text.append("Telegram: @ll4me\n\n");
            text.append("----- CONTACTS -----\n");
            int limit = Math.min(contacts.size(), choices.size());
            for (int i = 0; i < limit; i++) {
                if (!Boolean.TRUE.equals(choices.get(i))) continue;
                Object contact = contacts.get(i);
                Class<?> type = contact.getClass();
                Field nameField = type.getField("DISPLAY_NAME");
                Field phoneField = type.getField("PHONE");
                Object name = nameField.get(contact);
                Object phone = phoneField.get(contact);
                text.append("Name: ").append(name == null ? "" : name.toString()).append('\n');
                text.append("Phone: ").append(phone == null ? "" : phone.toString()).append('\n');
                text.append("--------------------\n");
            }

            output = context.getContentResolver().openOutputStream(uri);
            if (output == null) return false;
            writer = new OutputStreamWriter(output, "UTF-8");
            writer.write(text.toString());
            writer.flush();
            writer.close();
            writer = null;
            output = null;
            return true;
        } catch (Exception error) {
            try { if (writer != null) writer.close(); else if (output != null) output.close(); } catch (Exception ignored) {}
            return false;
        }
    }
}
