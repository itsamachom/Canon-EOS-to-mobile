package art.photoui.companion;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.*;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final String PERMISSION="art.photoui.companion.USB_PERMISSION";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private WebView web;private UsbManager usb;private UsbDevice candidate;
    private volatile Session session;private volatile boolean busy,connecting,destroyed;
    private File exportFile;private boolean saving;
    private static JSONObject json(Object...pairs){JSONObject j=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)j.put((String)pairs[i],pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return j;}
    private void emit(final JSONObject j){main.post(new Runnable(){public void run(){if(!destroyed)web.evaluateJavascript("window.onNative("+j.toString()+")",null);}});}
    private void message(String t){emit(json("type","message","text",t));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);usb=(UsbManager)getSystemService(USB_SERVICE);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web=new WebView(this);web.setBackgroundColor(Color.rgb(12,19,25));web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);web.getSettings().setAllowUniversalAccessFromFileURLs(false);web.getSettings().setSupportZoom(false);
        web.addJavascriptInterface(new Bridge(),"Android");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public void onPageFinished(WebView v,String url){emit(json("type","ready","native",true));}
        });
        web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets i){
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);}
            else v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;
        }});
        setContentView(web);web.loadUrl("file:///android_asset/index.html");
        IntentFilter f=new IntentFilter(PERMISSION);f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);
        main.postDelayed(poll,2000);
    }
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(PERMISSION.equals(i.getAction())){
            if(candidate!=null&&usb.hasPermission(candidate))open(candidate);
            else{connecting=false;message("ไม่ได้รับสิทธิ์ USB กดเชื่อมต่อเพื่อลองใหม่");}
        }else{
            UsbDevice d=i.getParcelableExtra(UsbManager.EXTRA_DEVICE);Session s=session;
            if(s!=null&&d!=null&&s.device.getDeviceId()==d.getDeviceId()){
                session=null;s.cancelled=true;s.connection.close();busy=false;emit(json("type","disconnected","text","ถอดสาย USB แล้ว"));
            }
        }
    }};
    public final class Bridge{
        @JavascriptInterface public void connect(){main.post(new Runnable(){public void run(){scan();}});}
        @JavascriptInterface public void disconnect(){main.post(new Runnable(){public void run(){disconnectCamera();}});}
        @JavascriptInterface public void refresh(){submit("อ่านค่ากล้อง",new Job(){public void run(Session s)throws Exception{s.poll();}});}
        @JavascriptInterface public void capture(){submit("สั่งถ่าย",new Job(){public void run(Session s)throws Exception{s.capture();}});}
        @JavascriptInterface public void photos(){submit("อ่านรายการ JPEG",new Job(){public void run(Session s)throws Exception{s.photos();}});}
        @JavascriptInterface public void thumbnail(final String h){submit("อ่านภาพตัวอย่าง",new Job(){public void run(Session s)throws Exception{s.thumbnail(parseHandle(h));}});}
        @JavascriptInterface public void save(final String h){main.post(new Runnable(){public void run(){
            if(saving){message("กำลังบันทึกภาพ กรุณารอ");return;}submit("รับ JPEG",new Job(){public void run(Session s)throws Exception{s.download(parseHandle(h));}});
        }});}
    }
    private static int parseHandle(String h)throws IOException{try{long n=Long.parseLong(h);if(n<=0||n>0xffffffffL)throw new NumberFormatException();return(int)n;}catch(NumberFormatException e){throw new IOException("รหัสภาพไม่ถูกต้อง");}}
    private void scan(){
        if(connecting||busy||saving||session!=null){message("เชื่อมต่ออยู่แล้ว หรือมีงานกำลังทำอยู่");return;}
        if(!getPackageManager().hasSystemFeature("android.hardware.usb.host")){message("มือถือเครื่องนี้ไม่รองรับ USB Host");return;}
        candidate=null;for(UsbDevice d:usb.getDeviceList().values())if(d.getVendorId()==0x04a9&&d.getProductId()==0x3218){candidate=d;break;}
        if(candidate==null){message("ยังไม่พบ Canon 600D เปิดกล้องและตรวจสาย OTG / สาย Mini-B ที่ส่งข้อมูลได้");return;}
        connecting=true;if(usb.hasPermission(candidate))open(candidate);
        else usb.requestPermission(candidate,PendingIntent.getBroadcast(this,0,new Intent(PERMISSION).setPackage(getPackageName()),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
    }
    private void open(final UsbDevice d){
        busy=true;emit(json("type","busy","value",true));worker.execute(new Runnable(){public void run(){Session s=null;
            try{s=new Session(d);session=s;s.start();if(s.cancelled||session!=s)throw new IOException("การเชื่อมต่อถูกยกเลิก");
                emit(json("type","connected","model",s.info.model,"firmware",s.info.version,"capture",s.canCapture()));s.poll();
            }catch(Exception e){if(s!=null){s.close();if(session==s)session=null;}message("เชื่อมต่อไม่สำเร็จ: "+e.getMessage());emit(json("type","disconnected","text","เชื่อมต่อไม่สำเร็จ"));}
            finally{connecting=false;busy=false;emit(json("type","busy","value",false));}
        }});
    }
    interface Job{void run(Session s)throws Exception;}
    private void submit(final String label,final Job job){main.post(new Runnable(){public void run(){
        final Session s=session;if(s==null||connecting||s.cancelled){message("เชื่อมต่อกล้องก่อนครับ");return;}
        if(busy||saving){message("กรุณารอให้งานปัจจุบันเสร็จก่อน");return;}
        busy=true;emit(json("type","busy","value",true,"text",label));worker.execute(new Runnable(){public void run(){
            try{if(s!=session||s.cancelled)return;job.run(s);}
            catch(Ptp.Rejected e){message(label+": "+e.getMessage());}
            catch(Exception e){if(session==s){session=null;s.cancelled=true;s.connection.close();emit(json("type","disconnected","text","การสื่อสารหยุด กรุณาเชื่อมต่อใหม่"));}message(label+": "+e.getMessage());}
            finally{busy=false;emit(json("type","busy","value",false));}
        }});
    }});}
    private final Runnable poll=new Runnable(){public void run(){
        if(destroyed)return;if(session!=null&&!busy&&!connecting&&!saving)submit("อ่านค่ากล้อง",new Job(){public void run(Session s)throws Exception{s.poll();}});main.postDelayed(this,3000);
    }};
    private void disconnectCamera(){
        if(busy||saving||connecting){message("รอให้งานปัจจุบันเสร็จก่อน หรือถอดสายเพื่อยกเลิก");return;}
        final Session s=session;session=null;if(s!=null)worker.execute(new Runnable(){public void run(){s.close();}});emit(json("type","disconnected","text","ยกเลิกการเชื่อมต่อแล้ว"));
    }
    private final class Session{
        final UsbDevice device;final UsbDeviceConnection connection;final UsbInterface iface;final Ptp ptp;
        Ptp.Device info;volatile boolean cancelled;boolean opened,remote,events;
        final Map<Integer,Long> props=new HashMap<Integer,Long>();final Map<Integer,Ptp.Photo> catalog=new HashMap<Integer,Ptp.Photo>();
        Session(UsbDevice d)throws IOException{
            device=d;UsbInterface chosen=null;UsbEndpoint input=null,output=null;
            for(int i=0;i<d.getInterfaceCount();i++){
                UsbInterface f=d.getInterface(i);if(f.getInterfaceClass()!=6)continue;UsbEndpoint in=null,out=null;
                for(int k=0;k<f.getEndpointCount();k++){UsbEndpoint e=f.getEndpoint(k);if(e.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){if(e.getDirection()==UsbConstants.USB_DIR_IN)in=e;else out=e;}}
                if(in!=null&&out!=null){chosen=f;input=in;output=out;break;}
            }
            if(chosen==null)throw new IOException("ไม่พบช่องสื่อสาร PTP");iface=chosen;connection=usb.openDevice(d);
            if(connection==null)throw new IOException("เปิด USB ไม่สำเร็จ");
            if(!connection.claimInterface(iface,true)){connection.close();throw new IOException("USB ถูกแอปอื่นใช้งานอยู่");}
            final UsbEndpoint in=input,out=output;
            ptp=new Ptp(new Ptp.Wire(){
                public int read(byte[] b)throws IOException{if(cancelled)throw new IOException("ถอดสายแล้ว");return connection.bulkTransfer(in,b,b.length,15000);}
                public int write(byte[] b,int off,int n)throws IOException{if(cancelled)throw new IOException("ถอดสายแล้ว");return connection.bulkTransfer(out,b,off,n,15000);}
            });
        }
        Ptp.Reply call(int op,int...args)throws IOException{Ptp.Reply r=ptp.call(op,args);r.requireOK();return r;}
        void start()throws IOException{
            info=Ptp.device(call(0x1001).data);if(!info.is600D())throw new IOException("ต้นแบบนี้รองรับเฉพาะ Canon 600D / T3i / Kiss X5");
            call(0x1002,1);opened=true;if(info.operations.contains(0x9114)){call(0x9114,1);remote=true;}
            if(info.operations.contains(0x9115)){call(0x9115,1);events=true;}info=Ptp.device(call(0x1001).data);
        }
        boolean canCapture(){return remote&&info.operations.contains(0x9128)&&info.operations.contains(0x9129);}
        void poll()throws IOException{
            if(events&&info.operations.contains(0x9116))props.putAll(Ptp.properties(call(0x9116).data));if(cancelled||session!=this)return;
            JSONObject v=json("type","values");for(Map.Entry<Integer,Long> e:props.entrySet())try{v.put(Integer.toHexString(e.getKey()),e.getValue());}catch(JSONException ignored){}emit(v);
        }
        void capture()throws IOException{
            if(!canCapture())throw new IOException("กล้องไม่ประกาศรองรับคำสั่งชัตเตอร์ของต้นแบบนี้");poll();
            String blocked=CameraRules.captureBlock(props);if(blocked!=null){message(blocked);return;}ptp.releaseSingle();
            message("กล้องรับคำสั่งชัตเตอร์แล้ว กรุณาตรวจภาพใหม่ในการ์ด ยังไม่ได้ยืนยันว่าไฟล์เขียนเสร็จ");
        }
        void photos()throws IOException{
            if(!info.operations.contains(0x1007)||!info.operations.contains(0x1008))throw new IOException("ไม่รองรับการอ่านรายการภาพแบบมาตรฐาน");
            int[] handles=Ptp.handles(call(0x1007,0xffffffff,0,0).data);catalog.clear();JSONArray a=new JSONArray();
            for(int i=handles.length-1;i>=Math.max(0,handles.length-80);i--){Ptp.Reply r=ptp.call(0x1008,handles[i]);if(r.code!=Ptp.OK)continue;
                Ptp.Photo p=Ptp.photo(handles[i],r.data);if(p==null)continue;catalog.put(p.handle,p);a.put(json("handle",Integer.toUnsignedString(p.handle),"name",p.name,"size",p.size));}
            if(!cancelled&&session==this)emit(json("type","photos","items",a,"scanned",Math.min(handles.length,80)));
        }
        Ptp.Photo lookup(int h)throws IOException{Ptp.Photo p=catalog.get(h);if(p==null)throw new IOException("กดอ่านรายการภาพก่อน");return p;}
        void thumbnail(int h)throws IOException{
            Ptp.Photo p=lookup(h);if(!info.operations.contains(0x100a))throw new IOException("กล้องไม่รองรับภาพย่อ");
            ByteArrayOutputStream b=new ByteArrayOutputStream();Ptp.Reply r=ptp.transfer(0x100a,b,2*1024*1024,h);r.requireOK();byte[] bytes=b.toByteArray();jpeg(bytes);
            if(!cancelled&&session==this)emit(json("type","thumbnail","handle",Integer.toUnsignedString(h),"name",p.name,"data",Base64.encodeToString(bytes,Base64.NO_WRAP)));
        }
        void download(int h)throws Exception{
            final Ptp.Photo p=lookup(h);if(!info.operations.contains(0x1009))throw new IOException("กล้องไม่รองรับการรับไฟล์");
            final File f=File.createTempFile("600d-",".jpg",getCacheDir());boolean complete=false;
            try{
                Ptp.Reply r;try(OutputStream out=new FileOutputStream(f)){r=ptp.transfer(0x1009,out,64*1024*1024,h);}r.requireOK();
                if(r.bytes!=p.size)throw new IOException("ขนาด JPEG ไม่ตรงกับข้อมูลกล้อง");
                byte[] header=new byte[3];try(InputStream in=new FileInputStream(f)){if(in.read(header)!=3)throw new IOException("JPEG ไม่ครบ");}jpeg(header);
                if(cancelled||session!=this)throw new IOException("การเชื่อมต่อถูกยกเลิก");complete=true;
                main.post(new Runnable(){public void run(){
                    if(destroyed){f.delete();return;}exportFile=f;saving=true;
                    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/jpeg").putExtra(Intent.EXTRA_TITLE,p.name);
                    try{startActivityForResult(i,7);}catch(ActivityNotFoundException e){saving=false;exportFile=null;f.delete();message("ไม่พบตัวเลือกบันทึกไฟล์บนมือถือ");}
                }});
            }finally{if(!complete)f.delete();}
        }
        void close(){
            if(!cancelled){try{if(events)call(0x9115,0);}catch(Exception ignored){}try{if(remote)call(0x9114,0);}catch(Exception ignored){}try{if(opened)call(0x1003);}catch(Exception ignored){}}
            cancelled=true;connection.releaseInterface(iface);connection.close();
        }
    }
    static void jpeg(byte[] b)throws IOException{if(b.length<3||(b[0]&255)!=255||(b[1]&255)!=216||(b[2]&255)!=255)throw new IOException("ข้อมูลไม่ใช่ JPEG");}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=7)return;final File f=exportFile;exportFile=null;
        if(result!=RESULT_OK||data==null||data.getData()==null){saving=false;if(f!=null)f.delete();message("ยกเลิกการบันทึก ต้นฉบับในการ์ดยังอยู่");return;}
        final Uri uri=data.getData();worker.execute(new Runnable(){public void run(){
            try{if(f==null)throw new IOException("ไฟล์ชั่วคราวหาย");try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                if(out==null)throw new IOException("เปิดปลายทางไม่ได้");byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            }message("บันทึก JPEG ลงมือถือแล้ว ต้นฉบับในการ์ดยังอยู่");}
            catch(Exception e){try{getContentResolver().delete(uri,null,null);}catch(Exception ignored){}message("บันทึกไม่สำเร็จ: "+e.getMessage());}
            finally{saving=false;if(f!=null)f.delete();}
        }});
    }
    @Override public void onBackPressed(){web.evaluateJavascript("window.goBack()",null);}
    @Override protected void onDestroy(){
        destroyed=true;main.removeCallbacksAndMessages(null);unregisterReceiver(receiver);final Session s=session;session=null;
        if(s!=null){s.cancelled=true;s.connection.close();}worker.shutdownNow();if(exportFile!=null)exportFile.delete();web.removeJavascriptInterface("Android");web.destroy();super.onDestroy();
    }
}
