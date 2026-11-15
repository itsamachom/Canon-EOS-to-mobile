package art.photoui.companion;
import java.io.*;
import java.util.*;

/** EOS state machine, independent of Android for protocol regression testing. */
public final class EosCamera {
    public interface Port{Ptp.Reply call(int op,int...args)throws IOException;Ptp.Reply set(int property,long value)throws IOException;}
    public interface Clock{long now();void pause(long ms)throws IOException;}
    public static final class Notice extends IOException{private static final long serialVersionUID=1L;public Notice(String s){super(s);}}
    public final Map<Integer,Long> properties=new HashMap<Integer,Long>();
    public final Set<Integer> operations;
    private final Port port;private final Clock clock;
    public int status=-1,objectsAdded,lastFrameType;
    public boolean live,changedMode,changedOutput;
    private Long oldMode,oldOutput;
    public EosCamera(Port port,Clock clock,Set<Integer> operations){this.port=port;this.clock=clock;this.operations=operations;}
    public Ptp.Reply call(int op,int...args)throws IOException{Ptp.Reply r=port.call(op,args);r.requireOK();return r;}
    private void set(int property,long value)throws IOException{Ptp.Reply r=port.set(property,value);r.requireOK();properties.put(property,value);}
    public boolean canCapture(){return operations.contains(0x9128)&&operations.contains(0x9129)&&operations.contains(0x9116);}
    public boolean canLive(){return operations.contains(0x9153)&&operations.contains(0x9110)&&operations.contains(0x9116);}
    public void poll()throws IOException{if(operations.contains(0x9116))events(call(0x9116).data);}
    public void events(byte[] bytes)throws IOException{
        properties.putAll(Ptp.properties(bytes));
        for(int p=0;p<bytes.length;){int n=(int)Ptp.u32(bytes,p);long type=Ptp.u32(bytes,p+4);
            if(type==0xc18b){if(n<12)throw new IOException("EOS camera status สั้นเกินไป");status=(int)Ptp.u32(bytes,p+8);}
            if(type==0xc181||type==0xc1a7){if(n<12)throw new IOException("EOS object event สั้นเกินไป");objectsAdded++;}
            p+=n;
        }
    }
    public boolean capture()throws IOException{
        if(!canCapture())throw new Notice("กล้องไม่ประกาศรองรับ remote shutter");
        poll();long beforeReady=clock.now();
        while(status==1&&clock.now()-beforeReady<8000){clock.pause(150);poll();}
        if(status==1)throw new Notice("กล้องยังไม่พร้อม กำลังบันทึกหรือประมวลผล รอแล้วกดใหม่");
        String reason=CameraRules.captureBlock(properties);if(reason!=null)throw new Notice(reason);
        int before=objectsAdded;IOException error=null;boolean half=false,full=false;
        try{
            half=true;call(0x9128,1,0);poll();before=objectsAdded; // Exclude older queued files before full press.
            full=true;call(0x9128,2,0);
        }catch(IOException e){error=e;}
        finally{
            if(full)try{call(0x9129,2);poll();}catch(IOException e){if(error==null)error=e;else error.addSuppressed(e);}
            if(half)try{call(0x9129,1);poll();}catch(IOException e){if(error==null)error=e;else error.addSuppressed(e);}
        }
        if(error!=null)throw error; // Never retry a shutter command automatically.
        long settled=clock.now();
        while(clock.now()-settled<35000){
            poll();if(objectsAdded>before&&status!=1)return true;
            if(status!=1&&clock.now()-settled>=600)return objectsAdded>before;
            clock.pause(150);
        }
        throw new Notice("กล้องรับชัตเตอร์แล้ว แต่ยังรายงานไม่พร้อม อย่ากดซ้ำทันที ตรวจภาพและสถานะบนกล้อง");
    }
    public void requestLiveProperties()throws IOException{
        poll();if(operations.contains(0x9127))for(int code:new int[]{0xd1b0,0xd1b1,0xd1b8}){
            if(!properties.containsKey(code)){Ptp.Reply r=port.call(0x9127,code);if(r.code==Ptp.OK)poll();}
        }
    }
    public boolean recording(){Long r=properties.get(0xd1b8);return r!=null&&(r==3||r==4);}
    public void startLive()throws IOException{
        if(live)return;if(!canLive())throw new Notice("กล้องไม่ประกาศรองรับ USB Live View");requestLiveProperties();
        oldMode=properties.get(0xd1b1);oldOutput=properties.get(0xd1b0);
        if(oldMode==null||oldOutput==null)throw new Notice("ยังอ่านสถานะ Live View ไม่ได้ เปิด Live View บนกล้องแล้วลองใหม่");
        try{
            if(oldMode!=1){
                if(recording())throw new Notice("เริ่มมอนิเตอร์ก่อนเริ่มบันทึกวิดีโอบนกล้อง");
                try{set(0xd1b1,1);changedMode=true;}
                catch(Ptp.Rejected e){if(e.code!=0x2019||!Long.valueOf(20).equals(properties.get(0xd105)))throw e;}
            }
            if(oldOutput!=2){set(0xd1b0,2);changedOutput=true;}live=true;poll();
        }catch(IOException e){try{stopLive();}catch(IOException cleanup){e.addSuppressed(cleanup);}throw e;}
    }
    public void stopLive()throws IOException{
        if(!live&&!changedMode&&!changedOutput)return;
        live=false;IOException error=null;
        try{poll();}catch(IOException e){error=e;}
        if(changedOutput)try{if(Long.valueOf(2).equals(properties.get(0xd1b0)))set(0xd1b0,oldOutput);changedOutput=false;}catch(IOException e){if(error==null)error=e;else error.addSuppressed(e);}
        // Do not turn off EVF mode during a movie recording or in movie mode.
        if(error==null&&changedMode&&Long.valueOf(0).equals(properties.get(0xd1b8))&&!Long.valueOf(20).equals(properties.get(0xd105)))try{
            if(Long.valueOf(1).equals(properties.get(0xd1b1)))set(0xd1b1,oldMode);changedMode=false;
        }catch(IOException e){error=e;}
        if(error!=null)throw error;
    }
    public byte[] frame()throws IOException{
        Ptp.Reply r=port.call(0x9153,0x00200000,0,0);
        if(r.code==0x2019||r.code==0xa102)return null;r.requireOK();
        byte[] b=r.data;
        for(int p=0;p<b.length;){
            if(b.length-p<8)throw new IOException("Live View segment header ขาดหาย");
            long n=Ptp.u32(b,p),type=Ptp.u32(b,p+4);
            if(n<8||n>b.length-p)throw new IOException("Live View segment length ไม่ถูกต้อง");
            if(type==1||type==9||type==11){
                lastFrameType=(int)type;int start=p+8,end=p+(int)n;
                if(end-start>=4&&(b[start]&255)==255&&(b[start+1]&255)==216){
                    int e=end;while(e>start+2&&!((b[e-2]&255)==255&&(b[e-1]&255)==217))e--;
                    if(e>start+2)return Arrays.copyOfRange(b,start,e);
                }
                throw new Notice("Live View ชนิด "+type+" ไม่ใช่ JPEG ที่รุ่นนี้แสดงได้ โดยเฉพาะโหมดวิดีโออาจส่งข้อมูลอีกแบบ");
            }p+=(int)n;
        }return null;
    }
}
