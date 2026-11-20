package com.ashrof.bluetoothcollector;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final int BT_PERMISSION=10, ENABLE_BT=13;
    static final UUID PBAP_UUID=UUID.fromString("0000112f-0000-1000-8000-00805f9b34fb");
    BluetoothAdapter adapter;
    LinearLayout deviceList;
    TextView status, btState, preview, countView;
    Button powerButton, saveButton;
    final Map<String,BluetoothDevice> discovered=new LinkedHashMap<>();
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    String exportText="", deviceName="Bluetooth_Contacts";
    int count=0, blue=Color.rgb(42,94,207), ink=Color.rgb(25,32,42), muted=Color.rgb(102,113,128);
    boolean receiverRegistered=false, scanning=false;

    final BroadcastReceiver btReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c, Intent i){
            String action=i.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(d!=null) addDiscovered(d);
            } else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                scanning=false; status.setText(discovered.isEmpty()?"لم يتم العثور على أجهزة قابلة للاكتشاف.":"انتهى البحث. اختر جهازًا للاتصال.");
            } else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int state=i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,BluetoothDevice.ERROR);
                if(d!=null && discovered.containsKey(d.getAddress())){
                    addDiscovered(d);
                    if(state==BluetoothDevice.BOND_BONDED) status.setText("تم الاقتران مع "+safeName(d)+". اضغط «سحب جهات الاتصال» لطلب الوصول عبر Bluetooth.");
                    else if(state==BluetoothDevice.BOND_NONE) status.setText("لم يكتمل الاقتران مع "+safeName(d)+".");
                }
            } else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                updatePowerUi();
                if(adapter!=null && adapter.isEnabled()) startScan();
            }
        }
    };

    @Override public void onCreate(Bundle b){super.onCreate(b);adapter=BluetoothAdapter.getDefaultAdapter();buildUi();registerBtReceiver();updatePowerUi();}
    @Override protected void onDestroy(){stopScan();if(receiverRegistered){try{unregisterReceiver(btReceiver);}catch(Exception ignored){}}worker.shutdownNow();super.onDestroy();}

    void buildUi(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(26),dp(20),dp(26));root.setBackgroundColor(Color.rgb(246,248,251));scroll.addView(root);
        TextView brand=text("BT / CONTACTS",12,blue,true);brand.setLetterSpacing(.14f);root.addView(brand);
        add(root,text("Bluetooth Collector",27,ink,true),8);
        add(root,text("اكتشف الأجهزة القريبة، اقترن بها، واطلب نقل جهات الاتصال مباشرةً بموافقة صاحب الجهاز.",14,muted,false),8);
        LinearLayout powerCard=card();add(root,powerCard,20);
        LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);powerCard.addView(row);
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        labels.addView(text("Bluetooth",17,ink,true));
        btState=text("جارٍ التحقق من الحالة",12,muted,false);labels.addView(btState);
        powerButton=button("تشغيل البلوتوث");row.addView(powerButton);powerButton.setOnClickListener(v->toggleBluetooth());
        add(root,text("الأجهزة المكتشفة الآن",17,ink,true),22);
        add(root,text("القائمة دي بتعرض الأجهزة اللي اتكتشفت في البحث الحالي فقط، ومش بتعرض الأجهزة القديمة المقترنة.",12,muted,false),5);
        deviceList=new LinearLayout(this);deviceList.setOrientation(1);add(root,deviceList,10);
        Button scan=button("بحث عن الأجهزة القريبة");add(root,scan,12);scan.setOnClickListener(v->startScan());
        status=text("فعّل Bluetooth ثم ابدأ البحث.",13,blue,true);add(root,status,16);
        LinearLayout received=card();add(root,received,16);
        received.addView(text("جهات الاتصال المستلمة",16,ink,true));
        countView=text("0 جهة اتصال",12,muted,false);add(received,countView,5);
        preview=text("بعد نجاح النقل المباشر ستظهر معاينة الأسماء والأرقام هنا.",12,muted,false);preview.setPadding(dp(10),dp(10),dp(10),dp(10));preview.setBackground(round(Color.rgb(246,248,251),12));add(received,preview,10);
        saveButton=button("حفظ جهات الاتصال كـ TXT");add(root,saveButton,14);saveButton.setEnabled(false);saveButton.setAlpha(.45f);saveButton.setOnClickListener(v->save());
        add(root,text("النقل المباشر يستخدم خدمة Bluetooth PBAP إذا كان الجهاز الآخر يدعمها ويسمح بالوصول. قد يظهر طلب موافقة على الجهاز الآخر؛ بعض الأجهزة أو أنظمة iPhone لا تتيح النقل بهذه الطريقة.",11,muted,false),12);
        setContentView(scroll);
    }

    void registerBtReceiver(){
        IntentFilter f=new IntentFilter();f.addAction(BluetoothDevice.ACTION_FOUND);f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(btReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(btReceiver,f);
        receiverRegistered=true;
    }
    void toggleBluetooth(){
        if(adapter==null){status.setText("هذا الهاتف لا يدعم Bluetooth.");return;}
        if(!hasPermissions())return;
        try{if(adapter.isEnabled()){stopScan();if(Build.VERSION.SDK_INT<33)adapter.disable();else{startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));status.setText("أوقف Bluetooth من إعدادات النظام.");}}
        else startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE_BT);}catch(Exception e){status.setText("تعذّر تغيير حالة Bluetooth.");}
    }
    boolean hasPermissions(){
        if(Build.VERSION.SDK_INT>=31){
            ArrayList<String> p=new ArrayList<>();
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_SCAN);
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_CONNECT);
            if(!p.isEmpty()){requestPermissions(p.toArray(new String[0]),BT_PERMISSION);return false;}
        }else if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},BT_PERMISSION);return false;
        }
        return true;
    }
    void updatePowerUi(){
        if(adapter==null){btState.setText("غير مدعوم");powerButton.setEnabled(false);return;}
        try{boolean on=adapter.isEnabled();btState.setText(on?"مفعّل":"متوقف");powerButton.setText(on?"Bluetooth مفعّل":"تشغيل Bluetooth");}catch(SecurityException e){btState.setText("بانتظار الإذن");}
    }
    void startScan(){
        if(adapter==null){status.setText("هذا الهاتف لا يدعم Bluetooth.");return;}
        if(!hasPermissions())return;
        try{
            if(!adapter.isEnabled()){status.setText("شغّل Bluetooth أولًا.");return;}
            stopScan();discovered.clear();deviceList.removeAllViews();
            boolean started=adapter.startDiscovery();scanning=started;
            status.setText(started?"جاري البحث عن الأجهزة القريبة…":"تعذّر بدء البحث. تأكد من الأذونات وحاول مرة أخرى.");
        }catch(SecurityException e){status.setText("تأكد من منح أذونات Bluetooth.");}
    }
    void stopScan(){if(adapter!=null&&scanning){try{adapter.cancelDiscovery();}catch(Exception ignored){}scanning=false;}}
    void addDiscovered(BluetoothDevice d){
        try{
            if(d.getBondState()==BluetoothDevice.BOND_BONDED) { /* Keep only devices found in this scan; paired devices are not preloaded. */ }
            String addr=d.getAddress();discovered.put(addr,d);
            LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setPadding(dp(13),dp(13),dp(13),dp(13));item.setBackground(round(Color.WHITE,14));
            int old=-1;for(int i=0;i<deviceList.getChildCount();i++){Object tag=deviceList.getChildAt(i).getTag();if(addr.equals(tag)){old=i;break;}}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);
            if(old>=0)deviceList.removeViewAt(old);
            deviceList.addView(item,lp);item.setTag(addr);
            LinearLayout line=new LinearLayout(this);line.setGravity(android.view.Gravity.CENTER_VERTICAL);item.addView(line);
            line.addView(text("ᛒ",22,blue,true));
            LinearLayout info=new LinearLayout(this);info.setOrientation(1);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=dp(12);line.addView(info,ip);
            info.addView(text(safeName(d),14,ink,true));
            info.addView(text(d.getBondState()==BluetoothDevice.BOND_BONDED?"مقترن — اكتُشف الآن":"جهاز قريب — اكتُشف الآن",11,muted,false));
            Button action=button(d.getBondState()==BluetoothDevice.BOND_BONDED?"سحب جهات الاتصال":"اقتران");line.addView(action);
            action.setOnClickListener(v->{if(d.getBondState()==BluetoothDevice.BOND_BONDED)pullContacts(d);else pairDevice(d);});
            if(d.getBondState()==BluetoothDevice.BOND_BONDED){Button pull=button("سحب جهات الاتصال");LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(10);item.addView(pull,bp);pull.setOnClickListener(v->pullContacts(d));}
        }catch(SecurityException ignored){}
    }
    void pairDevice(BluetoothDevice d){
        if(!hasPermissions())return;stopScan();
        try{if(d.getBondState()==BluetoothDevice.BOND_BONDED){addDiscovered(d);return;}
            status.setText("جارٍ إرسال طلب الاقتران إلى "+safeName(d)+"…");if(!d.createBond())status.setText("تعذّر بدء الاقتران. تأكد أن الجهاز ما زال قريبًا وقابلًا للاكتشاف.");
        }catch(SecurityException e){status.setText("تعذّر الاقتران بسبب أذونات Bluetooth.");}
    }
    void pullContacts(BluetoothDevice d){
        if(!hasPermissions())return;
        try{if(d.getBondState()!=BluetoothDevice.BOND_BONDED){status.setText("اقترن بالجهاز أولًا.");return;}stopScan();status.setText("جارٍ الاتصال بخدمة نقل جهات الاتصال في "+safeName(d)+"…");}
        catch(Exception e){status.setText("تعذّر بدء الاتصال.");return;}
        worker.execute(()->{
            BluetoothSocket socket=null;
            try{
                socket=d.createRfcommSocketToServiceRecord(PBAP_UUID);
                socket.connect();
                InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();
                // PBAP OBEX CONNECT includes the standard PBAP Target UUID.
                byte[] connect=buildPbapConnectPacket();
                out.write(connect);out.flush();
                byte[] response=readObexPacket(in);
                if(response.length<7||(response[0]&255)!=0xA0)
                    throw new IOException("الجهاز رفض اتصال PBAP أو لا يوفّر Phone Book Access.");
                int serverPacketSize=((response[5]&255)<<8)|(response[6]&255);
                if(serverPacketSize<255)throw new IOException("حجم حزمة OBEX غير مدعوم.");
                byte[] connectionHeader=extractConnectionHeader(response);
                ByteArrayOutputStream data=new ByteArrayOutputStream();
                byte[] name=unicodeHeader("telecom/pb.vcf");
                byte[] type=header(0x42,"x-bt/phonebook\0".getBytes(StandardCharsets.US_ASCII));
                // PBAP Application Parameters: vCard 2.1 + all available entries.
                byte[] app={0x07,0x01,0x00,0x04,0x02,(byte)0xFF,(byte)0xFF};
                byte[] appHeader=header(0x4C,app);
                ByteArrayOutputStream req=new ByteArrayOutputStream();req.write(0x83);req.write(0);req.write(0);req.write(name);req.write(type);req.write(appHeader);
                if(connectionHeader!=null)req.write(connectionHeader);
                byte[] packet=req.toByteArray();int len=packet.length;packet[1]=(byte)(len>>8);packet[2]=(byte)len;
                if(len>serverPacketSize)throw new IOException("طلب PBAP أكبر من الحد الذي يسمح به الجهاز.");
                out.write(packet);out.flush();
                int code=0;
                while(true){
                    byte[] p=readObexPacket(in);if(p.length<3)throw new IOException("استجابة PBAP غير مكتملة.");
                    code=p[0]&255;int pos=3;
                    while(pos+2<=p.length){
                        int id=p[pos]&255;
                        if(id==0x48||id==0x49){int n=((p[pos+1]&255)<<8)|(p[pos+2]&255);if(n>=3&&pos+n<=p.length)data.write(p,pos+3,n-3);pos+=n;}
                        else if((id&0xC0)==0x00){int n=((p[pos+1]&255)<<8)|(p[pos+2]&255);if(n<3||pos+n>p.length)break;pos+=n;}
                        else if((id&0xC0)==0x40){int n=((p[pos+1]&255)<<8)|(p[pos+2]&255);if(n<3||pos+n>p.length)break;pos+=n;}
                        else if((id&0xC0)==0x80)pos+=2;
                        else if((id&0xC0)==0xC0)pos+=5;
                        else break;
                    }
                    if(code==0xA0)break;
                    if(code!=0x90)throw new IOException("الجهاز لم يسمح بقراءة دفتر الهاتف (PBAP).");
                    ByteArrayOutputStream next=new ByteArrayOutputStream();next.write(0x83);next.write(0);next.write(0);
                    if(connectionHeader!=null)next.write(connectionHeader);
                    byte[] continuation=next.toByteArray();int nextLen=continuation.length;
                    continuation[1]=(byte)(nextLen>>8);continuation[2]=(byte)nextLen;
                    out.write(continuation);out.flush();
                }
                String vcf=new String(data.toByteArray(),StandardCharsets.UTF_8);
                String txt=parseVcard(vcf,safeName(d));
                if(txt.isEmpty())throw new IOException("الجهاز لم يرجع جهات اتصال قابلة للقراءة.");
                runOnUiThread(()->{deviceName=safeFileName(safeName(d));exportText=txt;count=countContacts(txt);countView.setText(count+" جهة اتصال");preview.setText(txt.length()>2200?txt.substring(0,2200)+"\n…":txt);saveButton.setEnabled(true);saveButton.setAlpha(1f);status.setText("تم استلام "+count+" جهة اتصال من "+safeName(d)+".");});
            }catch(Exception e){String msg=e.getMessage()==null?"الجهاز لا يدعم النقل المباشر أو لم يمنح الإذن.":e.getMessage();runOnUiThread(()->status.setText("تعذّر سحب الجهات: "+msg));}
            finally{if(socket!=null)try{socket.close();}catch(Exception ignored){}}
        });
    }
    byte[] buildPbapConnectPacket()throws IOException{
        byte[] target={
            0x79,0x61,0x35,(byte)0xF0,(byte)0xF0,(byte)0xC5,0x11,(byte)0xD8,
            0x09,0x66,0x08,0x00,0x20,0x0C,(byte)0x9A,0x66
        };
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        b.write(0x80); b.write(0); b.write(0);
        b.write(0x10); b.write(0x00); b.write(0xFF); b.write(0xFF);
        b.write(0x46); b.write(0x00); b.write(0x13);
        b.write(target);
        byte[] packet=b.toByteArray();
        int len=packet.length; packet[1]=(byte)(len>>8); packet[2]=(byte)len;
        return packet;
    }

    byte[] extractConnectionHeader(byte[] packet)throws IOException{
        int pos=7;
        while(pos<packet.length){
            int id=packet[pos]&255;
            int kind=id&0xC0;
            if(kind==0x00||kind==0x40){
                if(pos+3>packet.length)throw new IOException("رأس OBEX غير مكتمل.");
                int n=((packet[pos+1]&255)<<8)|(packet[pos+2]&255);
                if(n<3||pos+n>packet.length)throw new IOException("طول رأس OBEX غير صالح.");
                if(id==0xCB)return Arrays.copyOfRange(packet,pos,pos+n);
                pos+=n;
            }else if(kind==0x80){pos+=2;}
            else if(kind==0xC0){
                if(pos+5>packet.length)throw new IOException("معرّف اتصال OBEX غير مكتمل.");
                if(id==0xCB)return Arrays.copyOfRange(packet,pos,pos+5);
                pos+=5;
            }else break;
        }
        return null;
    }

    byte[] readObexPacket(InputStream in)throws IOException{
        int op=in.read();if(op<0)throw new EOFException("انقطع اتصال Bluetooth.");
        int hi=in.read(),lo=in.read();if(hi<0||lo<0)throw new EOFException();
        int len=(hi<<8)|lo;if(len<3||len>65535)throw new IOException("حجم استجابة غير صالح.");
        byte[] b=new byte[len];b[0]=(byte)op;b[1]=(byte)hi;b[2]=(byte)lo;
        int off=3;while(off<len){int n=in.read(b,off,len-off);if(n<0)throw new EOFException();off+=n;}return b;
    }
    byte[] unicodeHeader(String s)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(0x01);byte[] u=(s+"\0").getBytes("UTF-16BE");int n=u.length+3;b.write(n>>8);b.write(n);b.write(u);return b.toByteArray();
    }
    byte[] header(int id,byte[] value)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(id);int n=value.length+3;b.write(n>>8);b.write(n);b.write(value);return b.toByteArray();}
    String parseVcard(String raw,String source){
        StringBuilder out=new StringBuilder("Source: "+source+"\\nReceived: "+new Date()+"\\n\\n");
        ArrayList<String> lines=new ArrayList<>();
        for(String line:raw.replace("\\r","").split("\\n")){
            if(!lines.isEmpty()&&(line.startsWith(" ")||line.startsWith("\\t"))){
                int last=lines.size()-1;
                String previous=lines.get(last);
                if(previous.endsWith("="))previous=previous.substring(0,previous.length()-1);
                lines.set(last,previous+line.substring(1));
            }else lines.add(line);
        }
        String name="",fallback="",phone="";int total=0;
        for(String line:lines){
            int colon=line.indexOf(':');if(colon<0)continue;
            String[] params=line.substring(0,colon).split(";");
            String property=params[0].toUpperCase(Locale.ROOT);
            String value=line.substring(colon+1).trim();
            boolean quotedPrintable=false;String charset="UTF-8";
            for(int i=1;i<params.length;i++){
                String p=params[i].toUpperCase(Locale.ROOT);
                if(p.contains("QUOTED-PRINTABLE"))quotedPrintable=true;
                if(p.startsWith("CHARSET="))charset=params[i].substring(params[i].indexOf('=')+1);
            }
            if(quotedPrintable)value=decodeQuotedPrintable(value,charset);
            value=unescapeVcard(value);
            if(property.equals("BEGIN")&&value.equalsIgnoreCase("VCARD")){
                name="";fallback="";phone="";
            }else if(property.equals("FN"))name=value;
            else if(property.equals("N")&&fallback.isEmpty())fallback=value.replace(';',' ').trim();
            else if(property.equals("TEL")&&!value.isEmpty())phone+=(phone.isEmpty()?"":" | ")+value;
            else if(property.equals("END")&&value.equalsIgnoreCase("VCARD")){
                String display=name.isEmpty()?fallback:name;
                if(!display.isEmpty()||!phone.isEmpty()){
                    total++;out.append(total).append(". ").append(display.isEmpty()?"(بدون اسم)":display)
                        .append("\\n").append(phone.isEmpty()?"(بدون رقم)":phone).append("\\n\\n");
                }
            }
        }
        return total==0?"":out.toString();
    }
    String decodeQuotedPrintable(String value,String charset){
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(int i=0;i<value.length();){
            char c=value.charAt(i);
            if(c=='='&&i+2<value.length()){
                try{bytes.write(Integer.parseInt(value.substring(i+1,i+3),16));i+=3;continue;}catch(Exception ignored){}
            }
            byte[] encoded=String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            bytes.write(encoded,0,encoded.length);i++;
        }
        try{return new String(bytes.toByteArray(),java.nio.charset.Charset.forName(charset));}
        catch(Exception e){return new String(bytes.toByteArray(),StandardCharsets.UTF_8);}
    }
    String unescapeVcard(String value){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c=='\\'&&i+1<value.length()){
                char next=value.charAt(++i);
                if(next=='n'||next=='N')out.append(' ');
                else out.append(next);
            }else out.append(c);
        }
        return out.toString();
    }
    int countContacts(String txt){int n=0;for(String l:txt.split("\n"))if(l.matches("\\d+\\..*"))n++;return n;}
    String safeName(BluetoothDevice d){try{String n=d.getName();return n==null||n.trim().isEmpty()?"جهاز Bluetooth":n;}catch(Exception e){return "جهاز Bluetooth";}}
    String safeFileName(String s){return s.replaceAll("[^A-Za-z0-9_\\-\\u0600-\\u06FF ]","_").trim().replace(' ','_');}
    TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setAllCaps(false);b.setBackground(round(blue,13));return b;}
    LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(14),dp(14),dp(14),dp(14));l.setBackground(round(Color.WHITE,17));return l;}
    GradientDrawable round(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));d.setStroke(dp(1),Color.rgb(228,233,240));return d;}
    void add(LinearLayout p,View v,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);p.addView(v,lp);}
    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    void save(){
        if(exportText.isEmpty())return;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,deviceName+"_contacts.txt");
        startActivityForResult(i,12);
    }
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==ENABLE_BT){updatePowerUi();if(res==RESULT_OK)startScan();return;}if(req==12&&res==RESULT_OK&&data!=null&&data.getData()!=null){try(OutputStream o=getContentResolver().openOutputStream(data.getData())){o.write(exportText.getBytes(StandardCharsets.UTF_8));status.setText("تم حفظ ملف TXT بنجاح.");}catch(Exception e){status.setText("فشل حفظ الملف.");}}}
    @Override public void onRequestPermissionsResult(int q,String[] p,int[] g){super.onRequestPermissionsResult(q,p,g);if(q==BT_PERMISSION){boolean ok=true;for(int x:g)if(x!=PackageManager.PERMISSION_GRANTED)ok=false;if(ok){updatePowerUi();startScan();}else status.setText("لازم تسمح بأذونات Bluetooth للبحث والاقتران.");}}
}