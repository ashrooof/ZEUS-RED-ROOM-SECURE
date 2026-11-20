from pathlib import Path
import sys
import re
import shutil

root = Path(sys.argv[1])
activity = root / 'smali/grant/bt/contact/transfer/SharedContactsActivity.smali'
strings = root / 'res/values/strings.xml'
apktool_meta = root / 'apktool.yml'
meta_text = apktool_meta.read_text(encoding='utf-8')
meta_text = meta_text.replace('renameManifestPackage: hack.aaashraf', 'renameManifestPackage: hack.aaashraf.zeus')
apktool_meta.write_text(meta_text, encoding='utf-8')

manifest = root / 'AndroidManifest.xml'
manifest_text = manifest.read_text(encoding='utf-8')
manifest_text, changed = re.subn(r'(<manifest\b[^>]*\bpackage=")[^"]*(")', r'\1hack.aaashraf.zeus\2', manifest_text, count=1)
if changed != 1:
    raise SystemExit('Could not restore the original Android package identifier')
manifest.write_text(manifest_text, encoding='utf-8')
manifest_text = re.sub(r'android:icon="[^"]+"', 'android:icon="@drawable/zeus_icon"', manifest_text, count=1)
manifest_text = manifest_text.replace('hack.aaashraf.mobileadsinitprovider', 'hack.aaashraf.zeus.mobileadsinitprovider')
manifest.write_text(manifest_text, encoding='utf-8')
icon_source = root / 'res/drawable-mdpi/ic_launcher.png'
icon_target = root / 'res/drawable/zeus_icon.png'
if not icon_source.is_file():
    raise SystemExit('Original launcher icon source is missing')
shutil.copy2(icon_source, icon_target)

s = activity.read_text(encoding='utf-8')
marker = '.method public onBackPressed()V\n'
if marker not in s:
    raise SystemExit('Could not find SharedContactsActivity insertion point')

methods = r''' .method private exportContacts()V
    .locals 3

    iget-object v0, p0, Lgrant/bt/contact/transfer/SharedContactsActivity;->choices:Ljava/util/ArrayList;
    invoke-static {v0}, Lgrant/bt/contact/transfer/ZEUSExport;->countSelected(Ljava/util/ArrayList;)I
    move-result v0
    if-lez v0, :no_selection

    new-instance v0, Landroid/content/Intent;
    const-string v1, "android.intent.action.CREATE_DOCUMENT"
    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V
    const-string v1, "text/plain"
    invoke-virtual {v0, v1}, Landroid/content/Intent;->setType(Ljava/lang/String;)Landroid/content/Intent;
    const-string v1, "android.intent.category.OPENABLE"
    invoke-virtual {v0, v1}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;
    const-string v1, "android.intent.extra.TITLE"
    const-string v2, "ZEUS-Contacts.txt"
    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;
    const/16 v1, 0x2bd
    invoke-virtual {p0, v0, v1}, Lgrant/bt/contact/transfer/SharedContactsActivity;->startActivityForResult(Landroid/content/Intent;I)V
    return-void

    :no_selection
    const-string v0, "Select at least one contact"
    const/4 v1, 0x0
    invoke-static {p0, v0, v1}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;
    move-result-object v0
    invoke-virtual {v0}, Landroid/widget/Toast;->show()V
    return-void
.end method

.method protected onActivityResult(IILandroid/content/Intent;)V
    .locals 3

    invoke-super {p0, p1, p2, p3}, Landroid/support/v7/app/AppCompatActivity;->onActivityResult(IILandroid/content/Intent;)V
    const/16 v0, 0x2bd
    if-ne p1, v0, :done
    const/4 v0, -0x1
    if-ne p2, v0, :done
    if-eqz p3, :done
    invoke-virtual {p3}, Landroid/content/Intent;->getData()Landroid/net/Uri;
    move-result-object v0
    if-eqz v0, :done
    iget-object v1, p0, Lgrant/bt/contact/transfer/SharedContactsActivity;->choices:Ljava/util/ArrayList;
    invoke-static {p0, v0, v1}, Lgrant/bt/contact/transfer/ZEUSExport;->write(Landroid/content/Context;Landroid/net/Uri;Ljava/util/ArrayList;)Z
    move-result v0
    if-eqz v0, :write_failed
    const-string v1, "ZEUS TXT exported successfully"
    goto :show_result
    :write_failed
    const-string v1, "Could not export TXT file"
    :show_result
    const/4 v2, 0x1
    invoke-static {p0, v1, v2}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;
    move-result-object v1
    invoke-virtual {v1}, Landroid/widget/Toast;->show()V
    :done
    return-void
.end method

'''
methods = methods.lstrip()
s = s.replace(marker, methods + marker, 1)
start = s.index('.method public onOptionsItemSelected(Landroid/view/MenuItem;)Z')
end = s.index('.end method', start) + len('.end method')
block = s[start:end]
old = 'invoke-direct {p0}, Lgrant/bt/contact/transfer/SharedContactsActivity;->saveContacts()V'
if old not in block:
    raise SystemExit('Could not find menu save action')
block = block.replace(old, 'invoke-direct {p0}, Lgrant/bt/contact/transfer/SharedContactsActivity;->exportContacts()V', 1)
s = s[:start] + block + s[end:]
activity.write_text(s, encoding='utf-8')

st = strings.read_text(encoding='utf-8')
old_title = '<string name="action_save_contacts">حًفُـظُ ⤋</string>'
if old_title not in st:
    raise SystemExit('Could not find existing menu title string')
st = st.replace(old_title, '<string name="action_save_contacts">تصدير TXT</string>', 1)
strings.write_text(st, encoding='utf-8')
print('Patched export menu and activity')
