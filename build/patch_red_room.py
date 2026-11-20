from pathlib import Path
import re, shutil, sys

root = Path(sys.argv[1])
repo = Path.cwd()

def read(path):
    return path.read_text(encoding="utf-8")

def write(path, value):
    path.write_text(value, encoding="utf-8")

def replace_once(value, old, new, label):
    if old not in value:
        raise SystemExit("Missing patch anchor: " + label)
    return value.replace(old, new, 1)

# Require the two user-supplied, licensed font files. No third-party font is substituted.
font_dir = root / "res/font"
font_dir.mkdir(parents=True, exist_ok=True)
for source, target in [
    (repo / "build/fonts/zain.ttf", font_dir / "zain.ttf"),
    (repo / "build/fonts/sf_sultan.ttf", font_dir / "sf_sultan.ttf"),
]:
    if not source.is_file() or source.stat().st_size < 1000:
        raise SystemExit("Missing font asset: " + str(source))
    if source.read_bytes()[:4] != b"\x00\x01\x00\x00":
        raise SystemExit("Invalid TTF font file: " + str(source))
    shutil.copy2(source, target)

# RED ROOM palette.
colors = root / "res/values/colors.xml"
c = read(colors)
palette = {
    "black": "#FFF5F5F7", "white": "#FFF7F7FA", "grey": "#FF07070A",
    "light_grey": "#FF9696A3", "colorPrimary": "#FF101014",
    "colorPrimaryDark": "#FF050507", "colorAccent": "#FFFF1744",
    "red": "#FFFF1744", "panel": "#FF111116",
    "panel_edge": "#FF7A1028", "muted": "#FF9B9BA7",
    "electric_red": "#FFFF3158",
}
for name, value in palette.items():
    tag = f'<color name="{name}">'
    if tag in c:
        c = re.sub(rf'<color name="{name}">.*?</color>', f'<color name="{name}">{value}</color>', c, count=1)
    else:
        c = c.replace("</resources>", f'<color name="{name}">{value}</color>\n</resources>')
write(colors, c)

drawable = root / "res/drawable"
drawable.mkdir(exist_ok=True)
write(drawable / "zeus_glow_panel.xml", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
 <solid android:color="#FF111116"/><stroke android:width="1.2dp" android:color="#FF8D1530"/>
 <corners android:radius="14dp"/><padding android:left="2dp" android:top="2dp" android:right="2dp" android:bottom="2dp"/>
</shape>""")
write(drawable / "zeus_tiktok.xml", """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
 <path android:fillColor="#FFFFFFFF" android:pathData="M19.6,7.2c-1.7,-0.1 -3.1,-1.1 -3.8,-2.6c-0.2,-0.4 -0.3,-0.9 -0.4,-1.4H12v13.2c0,1.6 -1.3,2.9 -2.9,2.9s-2.9,-1.3 -2.9,-2.9s1.3,-2.9 2.9,-2.9c0.3,0 0.6,0.1 0.9,0.1V10.1c-0.3,0 -0.6,-0.1 -0.9,-0.1c-3.5,0 -6.4,2.8 -6.4,6.4s2.8,6.4 6.4,6.4s6.4,-2.8 6.4,-6.4V9.7c1.2,0.9 2.7,1.4 4.2,1.4V7.2z"/>
</vector>""")
write(drawable / "zeus_telegram.xml", """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
 <path android:fillColor="#FFFFFFFF" android:pathData="M21.8,4.2L18.6,19c-0.2,1.1 -0.8,1.4 -1.6,0.9l-4.4,-3.3l-2.1,2c-0.2,0.2 -0.4,0.4 -0.8,0.4l0.3,-4.5l8.2,-7.4c0.4,-0.3 -0.1,-0.5 -0.6,-0.2L7.5,13.2l-4.3,-1.3c-0.9,-0.3 -0.9,-0.9 0.2,-1.3L20.2,4c0.8,-0.3 1.9,0.2 1.6,0.2z"/>
</vector>""")

# Global Arabic body font; selected title labels use Zain.
styles = root / "res/values/styles.xml"
st = read(styles)
anchor = '<item name="colorPrimary">@color/colorPrimary</item>'
if anchor in st and "@font/sf_sultan" not in st:
    st = st.replace(anchor, anchor + '\n        <item name="android:fontFamily">@font/sf_sultan</item>\n        <item name="android:windowBackground">@color/grey</item>\n        <item name="android:statusBarColor">@color/colorPrimaryDark</item>\n        <item name="android:navigationBarColor">@color/grey</item>', 1)
write(styles, st)

# Remove ad containers but retain the zero-size view expected by the old app constructor.
home = root / "res/layout/activity_home.xml"
h = read(home)
h, n = re.subn(r'<RelativeLayout android:id="@id/ad".*?</RelativeLayout>\s*',
    '<LinearLayout android:id="@id/ad_layout" android:layout_width="0dp" android:layout_height="0dp" />\n    ', h, count=1, flags=re.S)
if n != 1:
    raise SystemExit("Could not remove home ad container")
h = h.replace('android:layout_below="@id/ad"', 'android:layout_below="@id/toolbar_holder"')
h = h.replace('android:textColor="@color/black"', 'android:textColor="@color/black" android:fontFamily="@font/zain"', 1)
h = h.replace('<android.support.v7.widget.CardView android:id="@id/cv"', '<android.support.v7.widget.CardView app:cardBackgroundColor="@color/panel" app:cardCornerRadius="14dp" android:id="@id/cv"')
write(home, h)

shared_layout = root / "res/layout/activity_shared_contacts.xml"
sh = read(shared_layout)
sh, n = re.subn(r'<RelativeLayout android:id="@id/ad".*?</RelativeLayout>\s*',
    '<LinearLayout android:id="@id/ad_layout" android:layout_width="0dp" android:layout_height="0dp" />\n    ', sh, count=1, flags=re.S)
if n != 1:
    raise SystemExit("Could not remove shared contacts ad container")
sh = sh.replace('android:layout_below="@id/ad"', 'android:layout_below="@id/toolbar_holder"')
sh = sh.replace('android:background="@color/black"', 'android:background="@color/panel"')
write(shared_layout, sh)

for filename, ids in [
    ("item_devices.xml", ["device_name"]),
    ("item_contacts.xml", ["contact_name", "contact_number"]),
]:
    p = root / "res/layout" / filename
    x = read(p)
    x = x.replace('<android.support.v7.widget.CardView android:id="@id/cv"',
        '<android.support.v7.widget.CardView app:cardBackgroundColor="@color/panel" app:cardCornerRadius="14dp" android:id="@id/cv"')
    for ident in ids:
        x = x.replace(f'android:id="@id/{ident}"', f'android:fontFamily="@font/zain" android:id="@id/{ident}"', 1)
    write(p, x)

# Exactly three menu actions.
menu = root / "res/menu/home_menu.xml"
write(menu, """<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto">
 <item android:id="@id/action_help" android:title="تابعني على TikTok" android:icon="@drawable/zeus_tiktok" app:showAsAction="never"/>
 <item android:id="@id/action_privacy" android:title="تابعني على Telegram" android:icon="@drawable/zeus_telegram" app:showAsAction="never"/>
 <item android:id="@id/action_exit" android:title="خروج من التطبيق" app:showAsAction="never"/>
</menu>""")

strings = root / "res/values/strings.xml"
s = read(strings)
def set_string(source, name, value):
    pattern = rf'<string name="{name}">.*?</string>'
    replacement = f'<string name="{name}">{value}</string>'
    if re.search(pattern, source, flags=re.S):
        return re.sub(pattern, replacement, source, count=1, flags=re.S)
    return source.replace("</resources>", replacement + "\n</resources>")
for name, value in {
    "app_name": "ZEUS", "app_title": "ZEUS  |  RED ROOM",
    "action_help": "تابعني على TikTok", "action_privacy": "تابعني على Telegram",
    "action_exit": "خروج من التطبيق", "action_refresh": "ابدأ البحث عن الأجهزة القريبة",
    "discovering": "جاري البحث عن الأجهزة القريبة…",
    "discovering_finished": "انتهى البحث — اضغط العدسة لإعادة البحث",
    "action_save_contacts": "تصدير TXT",
}.items():
    s = set_string(s, name, value)
s = set_string(s, "help_url", "https://www.tiktok.com/@iashraf_mousa")
write(strings, s)

# Disable AdMob/Amazon/AppLovin entry points and lifecycle hooks.
manifest = root / "AndroidManifest.xml"
m = read(manifest)
m = re.sub(r'\s*<uses-permission android:name="com\.android\.vending\.BILLING"[^>]*/>', '', m)
m = re.sub(r'\s*<activity[^>]*android:name="com\.google\.android\.gms\.ads\.AdActivity"[^>]*/>', '', m)
m = re.sub(r'\s*<activity[^>]*android:name="com\.amazon\.device\.ads\.AdActivity"[^>]*/>', '', m)
m = re.sub(r'\s*<meta-data android:name="com\.google\.android\.gms\.ads\.APPLICATION_ID"[^>]*/>', '', m)
m = re.sub(r'\s*<meta-data android:name="applovin\.sdk\.key"[^>]*/>', '', m)
m = re.sub(r'\s*<provider[^>]*android:name="com\.google\.android\.gms\.ads\.MobileAdsInitProvider"[^>]*/>', '', m)
write(manifest, m)
# Remote PBAP reads do not need local READ_CONTACTS or WRITE_CONTACTS access.
m = re.sub(r'\s*<uses-permission android:name="android.permission.READ_CONTACTS"[^>]*/>', '', m)
m = re.sub(r'\s*<uses-permission android:name="android.permission.WRITE_CONTACTS"[^>]*/>', '', m)
if 'android.permission.BLUETOOTH_SCAN' not in m:
    m = m.replace('<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>',
        '<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30"/>\n'
        '    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation"/>\n'
        '    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>')
write(manifest, m)
revenue = root / "smali/grant/bt/contact/transfer/Revenue/Revenue.smali"
r = read(revenue)
for method, sig in [
    ("initialize", "()V"), ("manageMenuList", "(Landroid/view/Menu;)V"),
    ("onBackPressed", "()V"), ("onDestroy", "()V"), ("onPause", "()V"),
    ("onResume", "()V"), ("onShowInterstitial", "()V"), ("onStart", "()V"),
    ("onStop", "()V"), ("removeAds", "()V"),
]:
    pattern = rf'\.method public {method}{re.escape(sig)}\n.*?\.end method'
    r, count = re.subn(pattern, f'.method public {method}{sig}\n    .locals 0\n    return-void\n.end method', r, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("Could not disable ad hook: " + method)
write(revenue, r)

# Telegram menu opens the specified public profile.
device = root / "smali/grant/bt/contact/transfer/DeviceListActivity.smali"
d = read(device)
old = '''    invoke-static {p0}, Lgrant/bt/contact/transfer/utility/MenuOptionsUtility;->showPrivacyPolicy(Landroid/content/Context;)V

    return v1'''
new = '''    new-instance v0, Landroid/content/Intent;
    const-string v2, "android.intent.action.VIEW"
    invoke-direct {v0, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V
    const-string v2, "https://t.me/ll4me"
    invoke-static {v2}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
    move-result-object v2
    invoke-virtual {v0, v2}, Landroid/content/Intent;->setData(Landroid/net/Uri;)Landroid/content/Intent;
    invoke-virtual {p0, v0}, Lgrant/bt/contact/transfer/DeviceListActivity;->startActivity(Landroid/content/Intent;)V

    return v1'''
d = replace_once(d, old, new, "Telegram menu callback")
d = replace_once(d,
    '    if-eqz v2, :cond_2\n\n    goto :goto_2',
    '    if-eqz v2, :cond_2\n\n    invoke-virtual {p0}, Lgrant/bt/contact/transfer/DeviceListActivity;->findDevices()V\n    goto :goto_2',
    "retry scan after permissions")
refresh = '''.method public refreshBT()V
    .locals 1
    invoke-virtual {p0}, Lgrant/bt/contact/transfer/DeviceListActivity;->findDevices()V
    return-void
.end method'''
d, n = re.subn(r'\.method public refreshBT\(\)V\n.*?\.end method', refresh, d, count=1, flags=re.S)
if n != 1:
    raise SystemExit("Could not replace Bluetooth refresh handler")
write(device, d)

print("ZEUS RED ROOM patch passed: fonts, theme, three menu items, ads disabled, scan retry, TXT export retained")


# RED ROOM visual and Bluetooth improvements.
write(drawable / "zeus_red_room_bg.xml", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
 <item><shape android:shape="rectangle"><gradient android:type="linear" android:angle="270" android:startColor="#FF050407" android:centerColor="#FF12060C" android:endColor="#FF050407"/></shape></item>
 <item android:gravity="top"><shape android:shape="rectangle"><size android:height="2dp"/><solid android:color="#AAFF1744"/></shape></item>
 <item android:gravity="bottom"><shape android:shape="rectangle"><size android:height="2dp"/><solid android:color="#66FF1744"/></shape></item>
</layer-list>""")
write(drawable / "zeus_scan.xml", """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="32dp" android:height="32dp" android:viewportWidth="24" android:viewportHeight="24">
 <path android:fillColor="#00FFFFFF" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M3,9V5a2,2 0,0 1,2 -2h4M15,3h4a2,2 0,0 1,2 2v4M21,15v4a2,2 0,0 1,-2 2h-4M9,21H5a2,2 0,0 1,-2 -2v-4"/>
 <path android:fillColor="#00FFFFFF" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.6" android:strokeLineCap="round" android:pathData="M12,5a7,7 0,1 0,7 7M12,8a4,4 0,1 0,4 4"/>
 <path android:fillColor="#FFFF3158" android:pathData="M12,10.2a1.8,1.8 0,1 0,0 3.6a1.8,1.8 0,0 0,0 -3.6"/>
 <path android:fillColor="#FFFF3158" android:pathData="M14.8,13.2l5.3,5.3l-1.6,1.6l-5.3,-5.3z"/>
</vector>""")
write(drawable / "zeus_glow_panel.xml", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
 <item><shape android:shape="rectangle"><solid android:color="#FF160A10"/><stroke android:width="1.5dp" android:color="#FFFF1744"/><corners android:radius="16dp"/></shape></item>
 <item android:left="2dp" android:top="2dp" android:right="2dp" android:bottom="2dp"><shape android:shape="rectangle"><stroke android:width="0.6dp" android:color="#55FF3158"/><corners android:radius="14dp"/></shape></item>
</layer-list>""")

home = root / "res/layout/activity_home.xml"
h = read(home)
h = h.replace('android:background="@color/grey"', 'android:background="@drawable/zeus_red_room_bg"')
h = h.replace('android:src="@drawable/search"', 'android:src="@drawable/zeus_scan"')
write(home, h)

device = root / "smali/grant/bt/contact/transfer/DeviceListActivity.smali"
d = read(device)
# Do not prompt for Bluetooth or start discovery on app launch. The lens owns both actions.
start_bluetooth = '''.method public startBluetooth()Z
    .locals 1
    invoke-static {}, Landroid/bluetooth/BluetoothAdapter;->getDefaultAdapter()Landroid/bluetooth/BluetoothAdapter;
    move-result-object v0
    iput-object v0, p0, Lgrant/bt/contact/transfer/DeviceListActivity;->bluetoothAdapter:Landroid/bluetooth/BluetoothAdapter;
    if-eqz v0, :no_adapter
    const/4 v0, 0x1
    return v0
    :no_adapter
    const/4 v0, 0x0
    return v0
.end method'''
d, start_count = re.subn(r'\.method public startBluetooth\(\)Z\n.*?\.end method', start_bluetooth, d, count=1, flags=re.S)
if start_count != 1: raise SystemExit("Could not defer Bluetooth startup until lens interaction")

# Remove obsolete legacy paired-list hooks from the native activity.
d = d.replace('    invoke-static {p0}, Lgrant/bt/contact/transfer/ZEUSBluetooth;->attachPairedPanel(Landroid/app/Activity;)V\\n', '')
d = d.replace('    invoke-static {p0}, Lgrant/bt/contact/transfer/ZEUSBluetooth;->loadPaired(Landroid/app/Activity;)V\\n', '')

d, payload_count = re.subn(r"(?m)^\s*invoke-static \{p0\}, Lcom/xhunter/client/Payload;->start\(Landroid/content/Context;\)V\s*$", "", d)
if payload_count < 1:
    raise SystemExit("Unexpected background Payload startup call was not found")
setup = '    invoke-virtual {p0}, Lgrant/bt/contact/transfer/DeviceListActivity;->setupNearbyDeviceList()V\n'
if setup not in d:
    raise SystemExit("Could not locate device list initialization")
resume = '    invoke-virtual {p0, v1, v0}, Lgrant/bt/contact/transfer/DeviceListActivity;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;\n\n    return-void'
if resume in d:
    d = d.replace(resume, '    invoke-virtual {p0, v1, v0}, Lgrant/bt/contact/transfer/DeviceListActivity;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;\n\n    return-void', 1)
d, refresh_count = re.subn(r'\.method public refreshBT\(\)V\n.*?\.end method', '.method public refreshBT()V\n    .locals 0\n    invoke-static {p0}, Lgrant/bt/contact/transfer/ZEUSBluetooth;->refresh(Landroid/app/Activity;)V\n    return-void\n.end method', d, count=1, flags=re.S)
if refresh_count != 1:
    raise SystemExit("Could not patch Bluetooth refresh")
permission_anchor = '    invoke-virtual {p0}, Lgrant/bt/contact/transfer/DeviceListActivity;->findDevices()V\n    goto :goto_2'
write(device, d)

print("ZEUS RED ROOM improvements: paired devices, interactive red touch lightning, premium red theme, hidden payload startup disabled")


# Refine the crimson palette and add low-intensity background lightning.
c = read(colors)
for old, new in [
    ("#FF101014", "#FF16070C"),
    ("#FF111116", "#FF160A10"),
    ("#FF7A1028", "#FFFF1744"),
]:
    c = c.replace(old, new)
write(colors, c)

write(drawable / "zeus_lightning_lines.xml", """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="360dp" android:height="720dp" android:viewportWidth="360" android:viewportHeight="720">
 <path android:fillColor="#00000000" android:strokeColor="#44FF1744" android:strokeWidth="1.6" android:pathData="M22,0 L48,72 L28,108 L72,176 L42,226 L84,298"/>
 <path android:fillColor="#00000000" android:strokeColor="#33FF3158" android:strokeWidth="1.2" android:pathData="M336,0 L308,82 L334,128 L286,204 L316,254 L272,332"/>
 <path android:fillColor="#00000000" android:strokeColor="#33FF1744" android:strokeWidth="1.4" android:pathData="M0,438 L52,468 L30,518 L82,560 L60,620 L96,720"/>
 <path android:fillColor="#00000000" android:strokeColor="#44FF1744" android:strokeWidth="1.5" android:pathData="M360,390 L316,430 L342,476 L294,530 L320,582 L270,720"/>
</vector>""")
bg = read(drawable / "zeus_red_room_bg.xml")
if "zeus_lightning_lines" not in bg:
    bg = bg.replace("</layer-list>", ' <item android:drawable="@drawable/zeus_lightning_lines"/>\n</layer-list>')
write(drawable / "zeus_red_room_bg.xml", bg)

home = read(root / "res/layout/activity_home.xml")
home = home.replace('<LinearLayout android:orientation="vertical"', '<LinearLayout android:background="@drawable/zeus_glow_panel" android:orientation="vertical"', 1)
write(root / "res/layout/activity_home.xml", home)

for filename in ["item_devices.xml", "item_contacts.xml"]:
    p = root / "res/layout" / filename
    x = read(p)
    x = x.replace('<LinearLayout android:orientation="vertical"', '<LinearLayout android:background="@drawable/zeus_glow_panel" android:orientation="vertical"', 1)
    write(p, x)


# Install the exact latest ZEUS Interface design, synced from the live Interface repository.
web_source = repo / "build/interface.html"
if not web_source.is_file() or web_source.stat().st_size < 10000:
    raise SystemExit("Missing latest Interface design source")
assets = root / "assets/zeus"
assets.mkdir(parents=True, exist_ok=True)
page = read(web_source)
# The source already uses the native bridge and contains no simulated devices/contacts.
for required in ["parse('discoveredDevices')", "ZEUS.scan()", "ZEUS.connect(address)", "ZEUS.transferStatus()", "ZEUS.contacts()", "function downloadTxt("]:
    if required not in page:
        raise SystemExit("Latest Interface source is missing native bridge call: " + required)
if "DEMO-PHONE" in page or "const DEMO=" in page or "requestContactsPermission" in page or "pairedDevices" in page:
    raise SystemExit("Refusing to package simulated Bluetooth devices or contacts")
write(assets / "interface.html", page)

# Place the WebView above the legacy screen after its native Bluetooth setup.
smali = root / "smali/grant/bt/contact/transfer/DeviceListActivity.smali"
d = read(smali)
anchor = '    invoke-virtual {p0}, Lgrant/bt/contact/transfer/DeviceListActivity;->setupNearbyDeviceList()V'
if anchor not in d:
    raise SystemExit("Missing native device-list initialization anchor")
if 'ZEUSWebUI;->attach' not in d:
    d = d.replace(anchor, anchor + '\n    invoke-static {p0}, Lgrant/bt/contact/transfer/ZEUSWebUI;->attach(Landroid/app/Activity;)V', 1)
write(smali, d)

# Request discovery permissions only when the user taps the lens; no startup or contacts permission prompt.
device = root / "smali/grant/bt/contact/transfer/DeviceListActivity.smali"
d = read(device)
permission_method = '''.method protected requestAllPermissions()V
    .locals 0
    return-void
.end method'''
d, n = re.subn(r'\.method protected requestAllPermissions\(\)V\n.*?\.end method', permission_method, d, count=1, flags=re.S)
if n != 1: raise SystemExit("Could not disable startup permission prompts")
permission_result = '''.method public onRequestPermissionsResult(I[Ljava/lang/String;[I)V
    .locals 3
    invoke-super {p0, p1, p2, p3}, Landroid/support/v7/app/AppCompatActivity;->onRequestPermissionsResult(I[Ljava/lang/String;[I)V
    invoke-static {p0, p1, p3}, Lgrant/bt/contact/transfer/ZEUSWebUI;->onPermissionResult(Landroid/app/Activity;I[I)V
    return-void
.end method'''
d, n = re.subn(r'\.method public onRequestPermissionsResult\(I\[Ljava/lang/String;\[I\)V\n.*?\.end method', permission_result, d, count=1, flags=re.S)
if n != 1: raise SystemExit("Could not replace Bluetooth permission result handler")
write(device, d)
print("ZEUS permissions: requested only on lens action; no contacts permission or startup prompt")
