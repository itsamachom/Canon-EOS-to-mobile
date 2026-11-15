package art.photoui.companion;
import java.io.*;

/** Latest-only bounded mailbox: acquisition never waits for browser rendering. */
public final class LatestFrames {
    public static final class Frame {
        public final String stream;public final long sequence,receivedAt,usbMs;public final byte[] jpeg;public final int width,height;
        Frame(String s,long n,byte[] b,int w,int h,long at,long ms){stream=s;sequence=n;jpeg=b;width=w;height=h;receivedAt=at;usbMs=ms;}
    }
    private String stream="";private Frame latest,offered;private long sequence,requestedAfter;private boolean requested;
    public synchronized void begin(String token){stream=token;latest=null;offered=null;sequence=0;requested=false;}
    public synchronized void stop(){begin("");}
    private Frame offer(){offered=latest;requested=false;return offered;}
    public synchronized Frame request(String token,long after){
        if(stream.isEmpty()||!stream.equals(token)||after<0)return null;
        if(latest!=null&&latest.sequence>after)return offer();requested=true;requestedAfter=after;return null;
    }
    public synchronized Frame publish(String token,byte[] jpeg,int width,int height,long at,long usbMs){
        if(stream.isEmpty()||!stream.equals(token)||jpeg.length>2*1024*1024)return null;
        latest=new Frame(token,++sequence,jpeg,width,height,at,usbMs);
        return requested&&latest.sequence>requestedAfter?offer():null;
    }
    public synchronized Frame image(String token,long seq){return offered!=null&&offered.stream.equals(token)&&offered.sequence==seq?offered:null;}
    public static long delayMs(int targetFps,long workMs,boolean ready){
        int fps=targetFps==12?12:24;return Math.max(ready?1:12,(long)Math.ceil(1000.0/fps)-Math.max(0,workMs));
    }
    public static int[] dimensions(byte[] b)throws IOException{
        if(b.length<4||(b[0]&255)!=255||(b[1]&255)!=216)throw new IOException("ไม่ใช่ JPEG");int p=2;
        while(p<b.length){
            if((b[p++]&255)!=255)throw new IOException("JPEG marker ไม่ถูกต้อง");while(p<b.length&&(b[p]&255)==255)p++;
            if(p>=b.length)break;int marker=b[p++]&255;if(marker==0xd9||marker==0xda)break;
            if(marker==0x01||(marker>=0xd0&&marker<=0xd8))continue;
            if(p>b.length-2)break;int n=((b[p]&255)<<8)|(b[p+1]&255);
            if(n<2||n>b.length-p)throw new IOException("JPEG segment ไม่ครบ");
            boolean sof=marker>=0xc0&&marker<=0xcf&&marker!=0xc4&&marker!=0xc8&&marker!=0xcc;
            if(sof){if(n<8)break;int h=((b[p+3]&255)<<8)|(b[p+4]&255),w=((b[p+5]&255)<<8)|(b[p+6]&255);
                if(w<=0||h<=0||w*(long)h>12000000)break;return new int[]{w,h};}
            p+=n;
        }throw new IOException("อ่านขนาด JPEG ไม่ได้");
    }
}
