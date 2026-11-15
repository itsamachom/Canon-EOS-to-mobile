package art.photoui.companion;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final String PERMISSION="art.photoui.companion.USB_PERMISSION";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private WebView web;private UsbManager usb;private UsbDevice candidate;private AppMedia media;
    private volatile Session session;private volatile boolean foreground,connecting,destroyed,paused,waitingPicker,cancelBatch;
    private volatile String visiblePage="connect";
    private volatile int targetFps=24;
    private PendingBatch pendingBatch;
    private final ArrayDeque<String> diagnostics=new ArrayDeque<String>();
    private static JSONObject json(Object...pairs){JSONObject j=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)j.put((String)pairs[i],pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return j;}
    private synchronized void log(String t){if(diagnostics.size()>=100)diagnostics.removeFirst();diagnostics.addLast(t);}
    private void emit(final JSONObject j){main.post(new Runnable(){public void run(){if(!destroyed)web.evaluateJavascript("window.onNative("+j.toString()+")",null);}});}
    private void emitFor(final Session s,final JSONObject j){main.post(new Runnable(){public void run(){if(!destroyed&&s==session&&!s.cancelled)web.evaluateJavascript("window.onNative("+j.toString()+")",null);}});}
    private void message(String t){log(t);emit(json("type","message","text",t));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);usb=(UsbManager)getSystemService(USB_SERVICE);
        try{media=new AppMedia(getCacheDir());}catch(IOException e){throw new IllegalStateException(e);}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web=new WebView(this);web.setBackgroundColor(Color.rgb(12,19,25));
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setDomStorageEnabled(false);web.getSettings().setAllowFileAccessFromFileURLs(false);web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.addJavascriptInterface(new Bridge(),"Android");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                try{
                    Uri u=r.getUrl();
                    if(!"GET".equals(r.getMethod())||!"https".equals(u.getScheme())||!"companion.local".equals(u.getHost())||u.getPort()!=-1)return missing();
                    if("/index.html".equals(u.getPath())||"/".equals(u.getPath()))return new WebResourceResponse("text/html","UTF-8",getAssets().open("index.html"));
                    if(u.getPath()!=null&&u.getPath().matches("/media/[a-f0-9]{32}")){
                        InputStream in=media.open(u.getLastPathSegment());if(in!=null)return new WebResourceResponse("image/jpeg",null,in);
                    }
                    if(u.getPath()!=null&&u.getPath().matches("/live/[a-f0-9]{32}/[0-9]{1,19}\\.jpg")){
                        Session s=session;List<String> parts=u.getPathSegments();
                        if(s!=null){long seq=Long.parseLong(parts.get(2).replace(".jpg",""));LatestFrames.Frame frame=s.frames.image(parts.get(1),seq);
                            if(frame!=null)return new WebResourceResponse("image/jpeg",null,200,"OK",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(frame.jpeg));}
                    }
                }catch(Exception ignored){}return missing();
            }
            private WebResourceResponse missing(){return new WebResourceResponse("text/plain","UTF-8",404,"Not found",Collections.<String,String>emptyMap(),new ByteArrayInputStream(new byte[0]));}
            @Override public void onPageFinished(WebView v,String url){emit(json("type","ready","native",true));}
        });
        web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets i){
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);}
            else v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;
        }});
        setContentView(web);web.loadUrl("https://companion.local/index.html");
        IntentFilter f=new IntentFilter(PERMISSION);f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);
        worker.schedule(heartbeat,1000,TimeUnit.MILLISECONDS);
    }
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(PERMISSION.equals(i.getAction())){
            if(connecting&&candidate!=null&&usb.hasPermission(candidate))open(candidate);
            else{connecting=false;emit(json("type","busy","value",false));message("ไม่ได้รับสิทธิ์ USB กดเชื่อมต่อเพื่อลองใหม่");}
        }else{
            UsbDevice d=i.getParcelableExtra(UsbManager.EXTRA_DEVICE);Session s=session;
            if(s!=null&&d!=null&&s.device.getDeviceId()==d.getDeviceId())drop(s,"ถอดสาย USB แล้ว");
        }
    }};
    public final class Bridge{
        @JavascriptInterface public void connect(){main.post(new Runnable(){public void run(){scan();}});}
        @JavascriptInterface public void disconnect(){main.post(new Runnable(){public void run(){disconnectCamera();}});}
        @JavascriptInterface public void refresh(){submit("อ่านค่ากล้อง",new Job(){public void run(Session s)throws Exception{s.poll();}});}
        @JavascriptInterface public void capture(){submit("กำลังสั่งชัตเตอร์",new Job(){public void run(Session s)throws Exception{
            boolean confirmed=s.camera.capture();s.values();message(confirmed?"พบไฟล์ใหม่จากกล้อง พร้อมถ่ายครั้งต่อไป":"ส่งชัตเตอร์และปล่อยปุ่มแล้ว ยังไม่พบเหตุการณ์ไฟล์ใหม่ กรุณาตรวจภาพในการ์ด");
            emitFor(s,json("type","captureDone","confirmed",confirmed));
        }});}
        @JavascriptInterface public void photos(){submit("อ่านภาพในการ์ด",new Job(){public void run(Session s)throws Exception{s.photos(false);}});}
        @JavascriptInterface public void morePhotos(){submit("อ่านภาพเพิ่มเติม",new Job(){public void run(Session s)throws Exception{s.photos(true);}});}
        @JavascriptInterface public void preview(final String h){submit("รับ JPEG เต็มเพื่อดูชัด",new Job(){public void run(Session s)throws Exception{s.preview(parseHandle(h));}});}
        @JavascriptInterface public void saveMany(final String handles){main.post(new Runnable(){public void run(){chooseFolder(handles);}});}
        @JavascriptInterface public void cancelBatch(){cancelBatch=true;}
        @JavascriptInterface public void liveStart(){submit("เปิด USB Live View",new Job(){public void run(Session s)throws Exception{
            try{s.camera.startLive();s.liveSince=SystemClock.elapsedRealtime();s.liveToken=UUID.randomUUID().toString().replace("-","");s.frames.begin(s.liveToken);s.receivedCount=0;s.receiveWindow=s.liveSince;s.receivedFps=-1;s.emitLive("waiting","รอเฟรมจากกล้อง");}
            catch(Exception e){s.emitLive("stopped","เปิด Live View ไม่สำเร็จ");throw e;}
        }});}
        @JavascriptInterface public void liveStop(){submit("หยุดรับ Live View",new Job(){public void run(Session s)throws Exception{s.stopLive();}});}
        @JavascriptInterface public void frameNext(String stream,String sequence){
            Session s=session;if(s==null)return;try{LatestFrames.Frame frame=s.frames.request(stream,Long.parseLong(sequence));if(frame!=null)s.deliver(frame);}catch(NumberFormatException ignored){}
        }
        @JavascriptInterface public void frameAck(String id){
            Session s=session;if(s==null)return;int split=id.lastIndexOf(':');if(split<0)return;
            try{LatestFrames.Frame f=s.frames.image(id.substring(0,split),Long.parseLong(id.substring(split+1)));long now=SystemClock.elapsedRealtime();
                if(f!=null&&now-s.lastStats>500){s.lastStats=now;s.lastPresentAge=now-f.receivedAt;emitFor(s,json("type","frameStats","stream",f.stream,"ageMs",s.lastPresentAge,"usbMs",f.usbMs,"receivedFps",s.receivedFps));}}
            catch(NumberFormatException ignored){}
        }
        @JavascriptInterface public void liveRate(int fps){if(fps==12||fps==24)targetFps=fps;}
        @JavascriptInterface public void settings(){submit("อ่านตัวเลือกกล้อง",new Job(){public void run(Session s)throws Exception{s.poll();s.settings();}});}
        @JavascriptInterface public void changeSetting(final int property,final String code){submit("ปรับ "+ExposureValues.name(property),new Job(){public void run(Session s)throws Exception{
            long value;try{value=Long.parseLong(code);}catch(NumberFormatException e){throw new EosCamera.Notice("ค่าที่เลือกไม่ถูกต้อง");}
            boolean confirmed=s.camera.changeSetting(property,value);s.values();s.settings();
            message(confirmed?ExposureValues.name(property)+" = "+ExposureValues.label(property,value)+" · กล้องยืนยันแล้ว":"กล้องรับคำสั่งแล้ว แต่ยังไม่ยืนยันค่าใหม่ กดอ่านค่าอีกครั้ง");
        }});}
        @JavascriptInterface public void page(final String name){
            if(!Arrays.asList("connect","shoot","photos","monitor").contains(name))return;visiblePage=name;
            final Session s=session;if(s!=null&&!"monitor".equals(name))worker.execute(new Runnable(){public void run(){if(s==session&&s.camera.live)try{s.stopLive();}catch(Exception e){error(s,e,"หยุด Live View");}}});
        }
        @JavascriptInterface public void diagnostics(){main.post(new Runnable(){public void run(){
            StringBuilder b=new StringBuilder("600D Companion 0.3.0\nAndroid "+Build.VERSION.RELEASE+" / "+Build.MANUFACTURER+" "+Build.MODEL+"\n");
            Session s=session;b.append("foreground=").append(foreground).append(" page=").append(visiblePage).append('\n');if(s!=null)b.append("last USB operation=").append(s.lastOperation).append(" awaitingResponse=").append(s.operationPending).append('\n');
            if(s!=null)b.append("targetFps=").append(targetFps).append(" receivedFps=").append(s.receivedFps).append(" usbFrameMs=").append(s.lastUsbMs).append(" postReceiveDisplayMs=").append(s.lastPresentAge).append('\n');
            synchronized(MainActivity.this){for(String line:diagnostics)b.append(line).append('\n');}
            android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("600D diagnostics",b.toString()));message("คัดลอกสถานะแล้ว ไม่มี serial number หรือข้อมูลภาพในข้อความ");
        }});}
    }
    private static int parseHandle(String h)throws IOException{try{long n=Long.parseLong(h);if(n<=0||n>0xffffffffL)throw new NumberFormatException();return(int)n;}catch(NumberFormatException e){throw new IOException("รหัสภาพไม่ถูกต้อง");}}
    private void scan(){
        if(connecting||foreground||waitingPicker||session!=null){message("มีการเชื่อมต่อหรืองานที่ยังไม่เสร็จ");return;}
        candidate=null;for(UsbDevice d:usb.getDeviceList().values())if(d.getVendorId()==0x04a9&&d.getProductId()==0x3218){candidate=d;break;}
        if(candidate==null){message("ยังไม่พบ Canon 600D ตรวจสาย OTG / Mini-B และเปิดกล้อง");return;}
        connecting=true;emit(json("type","busy","value",true,"text","กำลังเชื่อมต่อ"));
        if(usb.hasPermission(candidate))open(candidate);
        else usb.requestPermission(candidate,PendingIntent.getBroadcast(this,0,new Intent(PERMISSION).setPackage(getPackageName()),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
    }
    private void open(final UsbDevice d){worker.execute(new Runnable(){public void run(){Session s=null;
        try{s=new Session(d);session=s;s.start();if(s!=session||s.cancelled)return;
            emitFor(s,json("type","connected","model",s.info.model,"firmware",s.info.version,"capture",s.camera.canCapture(),"live",s.camera.canLive()));s.values();s.settingsDigest="";s.settings();
        }catch(Exception e){if(s!=null){s.connection.close();if(session==s)session=null;}message("เชื่อมต่อไม่สำเร็จ: "+e.getMessage());emit(json("type","disconnected","text","เชื่อมต่อไม่สำเร็จ"));}
        finally{connecting=false;emit(json("type","busy","value",false));}
    }});}
    interface Job{void run(Session s)throws Exception;}
    private void submit(final String label,final Job job){main.post(new Runnable(){public void run(){
        final Session s=session;if(s==null||connecting||s.cancelled){message("เชื่อมต่อกล้องก่อน");return;}
        if(foreground||waitingPicker){message("กำลังทำงานก่อนหน้า กรุณารอสถานะเสร็จ");return;}
        foreground=true;log(label);emit(json("type","busy","value",true,"text",label));
        worker.execute(new Runnable(){public void run(){try{if(s==session&&!s.cancelled)job.run(s);}catch(Exception e){error(s,e,label);}finally{
            foreground=false;if(s==session)emitFor(s,json("type","busy","value",false));
        }}});
    }});}
    private void error(Session s,Exception e,String label){
        log(label+": "+e.getClass().getSimpleName()+" "+e.getMessage());
        if(e instanceof Ptp.Rejected||e instanceof EosCamera.Notice){if(s==session)message(label+": "+e.getMessage());return;}
        if(s==session)drop(s,label+": "+e.getMessage()+" กรุณาเชื่อมต่อใหม่");
    }
    private void drop(Session s,String reason){if(s!=session)return;session=null;s.cancelled=true;cancelBatch=true;s.connection.close();connecting=false;emit(json("type","disconnected","text",reason));log(reason);}
    private final Runnable heartbeat=new Runnable(){public void run(){
        if(destroyed)return;Session s=session;long started=SystemClock.elapsedRealtime();
        try{if(s!=null&&!s.cancelled&&!connecting&&!foreground&&!waitingPicker&&!paused)s.tick();}
        catch(Ptp.Rejected e){if(e.code!=0x2019&&e.code!=0xa102)error(s,e,"อ่านสถานะ");}
        catch(EosCamera.Notice e){if(s!=null){try{s.stopLive();}catch(Exception cleanup){log("Live cleanup: "+cleanup.getMessage());}error(s,e,"Live View");}}
        catch(Exception e){if(s!=null)error(s,e,"USB");}
        finally{if(!destroyed)worker.schedule(this,s!=null&&s.camera!=null&&s.camera.live?LatestFrames.delayMs(targetFps,SystemClock.elapsedRealtime()-started,s.lastFrameReady):300,TimeUnit.MILLISECONDS);}
    }};
    private void disconnectCamera(){
        if(foreground||waitingPicker||connecting){message("รอให้งานเสร็จก่อน หรือหยุดหลังไฟล์ปัจจุบัน");return;}
        final Session s=session;if(s==null)return;foreground=true;
        worker.execute(new Runnable(){public void run(){try{s.close();}finally{if(session==s)session=null;foreground=false;emit(json("type","disconnected","text","ยกเลิกการเชื่อมต่อแล้ว"));}}});
    }
    private final class Session{
        final UsbDevice device;final UsbDeviceConnection connection;final UsbInterface iface;final Ptp ptp;
        final String generation=UUID.randomUUID().toString();Ptp.Device info;EosCamera camera;
        volatile boolean cancelled,operationPending;volatile String lastOperation="";boolean opened,remote,eventMode,lastFrameReady;
        final LatestFrames frames=new LatestFrames();volatile double receivedFps=-1;volatile long lastUsbMs,lastPresentAge,lastStats;
        long lastPoll,lastKeep,lastFrame,liveSince,receiveWindow,receivedCount;String liveToken="",settingsDigest="";int timeout=15000,scanCursor;int[] allHandles=new int[0];
        final Map<Integer,Ptp.Photo> catalog=new LinkedHashMap<Integer,Ptp.Photo>();
        final Map<Integer,File> originals=new HashMap<Integer,File>();final Map<Integer,String> fullUrls=new HashMap<Integer,String>();
        final Map<Integer,String> thumbs=new HashMap<Integer,String>();final ArrayDeque<Integer> thumbQueue=new ArrayDeque<Integer>();
        Session(UsbDevice d)throws IOException{
            device=d;UsbInterface chosen=null;UsbEndpoint input=null,output=null;
            for(int i=0;i<d.getInterfaceCount();i++){UsbInterface f=d.getInterface(i);if(f.getInterfaceClass()!=6)continue;UsbEndpoint in=null,out=null;
                for(int k=0;k<f.getEndpointCount();k++){UsbEndpoint e=f.getEndpoint(k);if(e.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){if(e.getDirection()==UsbConstants.USB_DIR_IN)in=e;else out=e;}}
                if(in!=null&&out!=null){chosen=f;input=in;output=out;break;}}
            if(chosen==null)throw new IOException("ไม่พบ PTP interface");iface=chosen;connection=usb.openDevice(d);
            if(connection==null)throw new IOException("เปิด USB ไม่สำเร็จ");if(!connection.claimInterface(iface,true)){connection.close();throw new IOException("USB ถูกแอปอื่นใช้งาน");}
            final UsbEndpoint in=input,out=output;
            ptp=new Ptp(new Ptp.Wire(){public int read(byte[] b)throws IOException{if(cancelled)throw new IOException("ถอดสายแล้ว");int n=connection.bulkTransfer(in,b,b.length,timeout);if(n<0)log("USB read timeout/error");return n;}
                public int write(byte[] b,int off,int n)throws IOException{if(cancelled)throw new IOException("ถอดสายแล้ว");return connection.bulkTransfer(out,b,off,n,15000);}});
            ptp.setObserver(new Ptp.Observer(){public void start(int op,int transaction){lastOperation=String.format(Locale.US,"%04X tx=%d",op,transaction);operationPending=true;}
                public void transaction(int op,int code,long ms){operationPending=false;if(op!=0x9153&&op!=0x9116)log(String.format(Locale.US,"PTP %04X → %04X (%d ms)",op,code,ms));}});
        }
        Ptp.Reply call(int op,int...args)throws IOException{Ptp.Reply r=ptp.call(op,args);r.requireOK();return r;}
        void start()throws IOException{
            info=Ptp.device(call(0x1001).data);if(!info.is600D())throw new IOException("รองรับเฉพาะ Canon 600D / T3i / Kiss X5");
            call(0x1002,1);opened=true;if(info.operations.contains(0x9114)){call(0x9114,1);remote=true;}if(info.operations.contains(0x9115)){call(0x9115,1);eventMode=true;}
            info=Ptp.device(call(0x1001).data);
            camera=new EosCamera(new EosCamera.Port(){public Ptp.Reply call(int op,int...args)throws IOException{return ptp.call(op,args);}public Ptp.Reply set(int p,long v)throws IOException{return ptp.setProperty(p,v);}},
                new EosCamera.Clock(){public long now(){return SystemClock.elapsedRealtime();}public void pause(long ms)throws IOException{try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("ยกเลิกงาน",e);}}},info.operations);
            poll();log(info.model+" firmware "+info.version);
        }
        void values(){JSONObject v=json("type","values","cameraBusy",camera.status==1,"recording",camera.recording());
            JSONObject labels=new JSONObject();for(int p:ExposureValues.PROPERTIES)if(camera.properties.containsKey(p))try{labels.put(Integer.toHexString(p),ExposureValues.label(p,camera.properties.get(p)));}catch(JSONException ignored){}
            try{v.put("exposure",labels);}catch(JSONException ignored){}
            for(Map.Entry<Integer,Long> e:camera.properties.entrySet())try{v.put(Integer.toHexString(e.getKey()),e.getValue());}catch(JSONException ignored){}emitFor(this,v);}
        void settings(){
            JSONArray a=new JSONArray();for(int p:ExposureValues.PROPERTIES){JSONArray choices=new JSONArray();List<Long> values=camera.available.get(p);
                if(values!=null)for(long code:values)choices.put(json("value",code,"label",ExposureValues.label(p,code)));
                String reason=camera.settingBlock(p);a.put(json("property",p,"name",ExposureValues.name(p),"value",camera.properties.containsKey(p)?camera.properties.get(p):JSONObject.NULL,"enabled",reason==null,"reason",reason==null?"":reason,"choices",choices));}
            JSONObject data=json("type","settings","items",a);String digest=data.toString();if(!digest.equals(settingsDigest)){settingsDigest=digest;emitFor(this,data);}
        }
        void poll()throws IOException{camera.poll();lastPoll=SystemClock.elapsedRealtime();values();settings();}
        void deliver(LatestFrames.Frame frame){emitFor(this,json("type","frame","id",frame.stream+":"+frame.sequence,"stream",frame.stream,"sequence",frame.sequence,"url","https://companion.local/live/"+frame.stream+"/"+frame.sequence+".jpg","width",frame.width,"height",frame.height,"usbMs",frame.usbMs));}
        void emitLive(String state,String text){emitFor(this,json("type","live","state",state,"text",text,"stream",liveToken));}
        void stopLive()throws IOException{frames.stop();try{camera.stopLive();}finally{emitLive("stopped","หยุดรับภาพแล้ว");}}
        void tick()throws IOException{
            long now=SystemClock.elapsedRealtime();if(now-lastPoll>(camera.live?500:1500))poll();
            if(camera.live&&"monitor".equals(visiblePage)){
                if(now-lastKeep>10000&&info.operations.contains(0x911d)){call(0x911d);lastKeep=now;}
                lastFrameReady=false;long requested=SystemClock.elapsedRealtime();
                byte[] jpeg;timeout=3000;try{jpeg=camera.frame();}finally{timeout=15000;}lastUsbMs=SystemClock.elapsedRealtime()-requested;
                if(jpeg==null){if(now-liveSince>3000&&now-lastFrame>2000){emitLive("waiting","กล้องยังไม่ส่งเฟรม อาจกำลังถ่ายหรือบันทึกวิดีโอ");lastFrame=now;}return;}
                if(jpeg.length>2*1024*1024)throw new EosCamera.Notice("เฟรม Live View ใหญ่เกิน 2 MiB");
                int[] size=LatestFrames.dimensions(jpeg);lastFrame=SystemClock.elapsedRealtime();lastFrameReady=true;receivedCount++;
                if(lastFrame-receiveWindow>=1000){receivedFps=receivedCount*1000.0/(lastFrame-receiveWindow);receivedCount=0;receiveWindow=lastFrame;}
                LatestFrames.Frame offer=frames.publish(liveToken,jpeg,size[0],size[1],lastFrame,lastUsbMs);if(offer!=null)deliver(offer);
            }else if("photos".equals(visiblePage)&&!thumbQueue.isEmpty()){
                int h=thumbQueue.removeFirst();Ptp.Photo p=catalog.get(h);if(p==null)return;
                try{ByteArrayOutputStream b=new ByteArrayOutputStream();Ptp.Reply r=ptp.transfer(0x100a,b,2*1024*1024,h);r.requireOK();byte[] bytes=b.toByteArray();jpeg(bytes);
                    String url=media.put(bytes);thumbs.put(h,url);int[] size=dimensions(bytes);emitFor(this,json("type","thumb","handle",Integer.toUnsignedString(h),"url",url,"width",size[0],"height",size[1]));
                }catch(Ptp.Rejected e){log("Thumb "+Integer.toUnsignedString(h)+": "+e.getMessage());emitFor(this,json("type","thumbFailed","handle",Integer.toUnsignedString(h)));}
            }
        }
        void photos(boolean more)throws IOException{
            if(camera.live)stopLive();
            if(!more){allHandles=Ptp.handles(call(0x1007,0xffffffff,0,0).data);scanCursor=allHandles.length;catalog.clear();thumbQueue.clear();}
            JSONArray a=new JSONArray();int scanned=0;
            while(scanCursor>0&&scanned<80){if(cancelled||session!=this)throw new IOException("ยกเลิกการเชื่อมต่อ");int h=allHandles[--scanCursor];scanned++;
                Ptp.Reply r=ptp.call(0x1008,h);if(r.code!=Ptp.OK)continue;Ptp.Photo p=Ptp.photo(h,r.data);if(p==null)continue;catalog.put(h,p);
                String thumb=thumbs.get(h);if(!media.exists(thumb))thumb=null;a.put(json("handle",Integer.toUnsignedString(h),"name",p.name,"size",p.size,"thumb",thumb==null?JSONObject.NULL:thumb));
                if(thumb==null&&info.operations.contains(0x100a))thumbQueue.addLast(h);
                if(scanned%10==0)emitFor(this,json("type","progress","text","อ่านรายการ "+scanned+" / ไม่เกิน 80"));
            }
            emitFor(this,json("type","photos","append",more,"items",a,"more",scanCursor>0,"scanned",scanned));
        }
        Ptp.Photo lookup(int h)throws EosCamera.Notice{Ptp.Photo p=catalog.get(h);if(p==null)throw new EosCamera.Notice("กดอ่านภาพในการ์ดใหม่ก่อน");return p;}
        File full(int h)throws IOException{
            Ptp.Photo p=lookup(h);File cached=originals.get(h);if(cached!=null&&cached.exists()&&cached.length()==p.size)return cached;
            File f=media.temporary();boolean ok=false;
            try{Ptp.Reply r;try(OutputStream out=new FileOutputStream(f)){r=ptp.transfer(0x1009,out,64*1024*1024,h);}r.requireOK();
                if(r.bytes!=p.size)throw new IOException("ขนาด JPEG ไม่ตรงกับข้อมูลกล้อง");byte[] header=new byte[3];try(InputStream in=new FileInputStream(f)){if(in.read(header)!=3)throw new IOException("JPEG ไม่ครบ");}jpeg(header);
                if(cancelled||session!=this)throw new IOException("การเชื่อมต่อถูกยกเลิก");String url=media.register(f);originals.put(h,f);fullUrls.put(h,url);ok=true;return f;
            }finally{if(!ok)f.delete();}
        }
        void preview(int h)throws IOException{
            if(camera.live)stopLive();File f=full(h);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),opts);
            if(opts.outWidth<=0||opts.outHeight<=0||opts.outWidth*(long)opts.outHeight>25000000)throw new EosCamera.Notice("JPEG นี้ใหญ่เกินขนาดแสดงผลของรุ่นทดลอง");
            emitFor(this,json("type","preview","handle",Integer.toUnsignedString(h),"name",lookup(h).name,"url",fullUrls.get(h),"width",opts.outWidth,"height",opts.outHeight,"bytes",f.length()));
        }
        void close(){
            if(!cancelled){try{if(camera!=null&&(camera.live||camera.changedOutput||camera.changedMode))camera.stopLive();}catch(Exception e){log("Live cleanup "+e.getMessage());}
                try{if(eventMode)call(0x9115,0);}catch(Exception ignored){}try{if(remote)call(0x9114,0);}catch(Exception ignored){}try{if(opened)call(0x1003);}catch(Exception ignored){}
            }cancelled=true;connection.releaseInterface(iface);connection.close();
        }
    }
    private static int[] dimensions(byte[] b){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(b,0,b.length,o);return new int[]{o.outWidth,o.outHeight};}
    static void jpeg(byte[] b)throws IOException{if(b.length<3||(b[0]&255)!=255||(b[1]&255)!=216||(b[2]&255)!=255)throw new IOException("ข้อมูลไม่ใช่ JPEG");}
    private static final class PendingBatch{final Session session;final int[] handles;PendingBatch(Session s,int[] h){session=s;handles=h;}}
    private void chooseFolder(String encoded){
        final Session s=session;if(s==null||foreground||waitingPicker||connecting){message("เชื่อมต่อกล้องและรอให้งานปัจจุบันเสร็จก่อน");emit(json("type","busy","value",foreground||waitingPicker));return;}
        try{JSONArray a=new JSONArray(encoded);if(a.length()<1||a.length()>80)throw new IOException("เลือกได้ครั้งละ 1–80 ภาพ");
            LinkedHashSet<Integer> set=new LinkedHashSet<Integer>();for(int i=0;i<a.length();i++)set.add(parseHandle(a.getString(i)));
            int[] handles=new int[set.size()];int i=0;for(int h:set)handles[i++]=h;pendingBatch=new PendingBatch(s,handles);cancelBatch=false;waitingPicker=true;emit(json("type","busy","value",true,"text","เลือกโฟลเดอร์ปลายทาง"));
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent,9);
        }catch(Exception e){pendingBatch=null;waitingPicker=false;emit(json("type","busy","value",false));message("เลือกโฟลเดอร์ไม่ได้: "+e.getMessage());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=9)return;final PendingBatch batch=pendingBatch;pendingBatch=null;waitingPicker=false;
        if(result!=RESULT_OK||data==null||data.getData()==null){emit(json("type","busy","value",false));message("ยกเลิกเลือกโฟลเดอร์ ไม่มีไฟล์ถูกส่ง");return;}
        if(batch==null||batch.session!=session){emit(json("type","busy","value",false));message("กล้องถูกถอดหรือเปลี่ยน กรุณาเลือกภาพใหม่");return;}final Uri tree=data.getData();
        submit("ดาวน์โหลดภาพที่เลือก",new Job(){public void run(Session s)throws Exception{
            if(s.camera.live)s.stopLive();int completed=0,failed=0;boolean storageFailure=false;JSONArray failures=new JSONArray(),saved=new JSONArray();
            for(int i=0;i<batch.handles.length&&!cancelBatch;i++){
                int h=batch.handles[i];Uri created=null;boolean writingDocument=false;
                try{Ptp.Photo p=s.lookup(h);emitFor(s,json("type","batch","done",completed,"total",batch.handles.length,"name",p.name,"running",true));File f=s.full(h);
                    writingDocument=true;Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
                    created=DocumentsContract.createDocument(getContentResolver(),parent,"image/jpeg",p.name);if(created==null)throw new IOException("สร้างไฟล์ปลายทางไม่ได้");
                    long copied=0;try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(created,"w")){
                        if(out==null)throw new IOException("เปิดไฟล์ปลายทางไม่ได้");byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(s.cancelled)throw new IOException("ถอดสายระหว่างบันทึก");out.write(b,0,n);copied+=n;}
                    }if(copied!=p.size)throw new IOException("เขียนไฟล์ไม่ครบ");completed++;saved.put(Integer.toUnsignedString(h));
                }catch(Exception e){failed++;failures.put(json("handle",Integer.toUnsignedString(h),"reason",String.valueOf(e.getMessage())));
                    if(created!=null)try{DocumentsContract.deleteDocument(getContentResolver(),created);}catch(Exception cleanup){log("ลบไฟล์ที่เขียนไม่ครบไม่ได้");}
                    if(writingDocument){storageFailure=true;break;}
                    if(!(e instanceof Ptp.Rejected)&&!(e instanceof EosCamera.Notice)){emitFor(s,json("type","batch","done",completed,"failed",failed,"total",batch.handles.length,"running",false,"failures",failures,"saved",saved));throw e;}
                }
            }
            emitFor(s,json("type","batch","done",completed,"failed",failed,"total",batch.handles.length,"running",false,"cancelled",cancelBatch,"failures",failures,"saved",saved));
            message("บันทึกแล้ว "+completed+" / "+batch.handles.length+" ภาพ"+(failed>0?" · ไม่สำเร็จ "+failed:"")+(cancelBatch?" · หยุดตามคำขอ":"")+(storageFailure?" · เขียนโฟลเดอร์ปลายทางไม่ได้ ลองเลือกโฟลเดอร์อื่น":""));
        }});
    }
    @Override protected void onPause(){super.onPause();paused=true;final Session s=session;if(s!=null)worker.execute(new Runnable(){public void run(){if(s==session&&s.camera.live)try{s.stopLive();}catch(Exception e){error(s,e,"พัก Live View");}}});}
    @Override protected void onResume(){super.onResume();paused=false;}
    @Override public void onBackPressed(){web.evaluateJavascript("window.goBack()",null);}
    @Override protected void onDestroy(){destroyed=true;main.removeCallbacksAndMessages(null);unregisterReceiver(receiver);Session s=session;session=null;if(s!=null){s.cancelled=true;s.connection.close();}worker.shutdownNow();web.removeJavascriptInterface("Android");web.destroy();media.clear();super.onDestroy();}
}
