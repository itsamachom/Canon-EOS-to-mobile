package art.photoui.companion;
import java.io.*;
import java.util.*;

/** Independent PTP/USB implementation. No firmware writes or object deletion. */
public final class Ptp {
    public interface Wire { int read(byte[] b) throws IOException; int write(byte[] b,int off,int n) throws IOException; }
    public static final int OK=0x2001;
    private final Wire wire;
    private byte[] pending=new byte[0];
    private int cursor,transaction;
    public interface Observer { default void start(int operation,int transaction){} void transaction(int operation,int code,long durationMs); }
    private Observer observer;
    public void setObserver(Observer observer){this.observer=observer;}
    public Ptp(Wire wire){this.wire=wire;}
    public static long u32(byte[] b,int p)throws IOException{
        if(p<0||p>b.length-4)throw new IOException("ข้อมูล PTP สั้นเกินไป");
        return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24);
    }
    static int u16(byte[] b,int p)throws IOException{
        if(p<0||p>b.length-2)throw new IOException("ข้อมูล PTP สั้นเกินไป");return (b[p]&255)|((b[p+1]&255)<<8);
    }
    static void put(byte[] b,int p,long value,int n){for(int i=0;i<n;i++)b[p+i]=(byte)(value>>>(8*i));}
    public static byte[] container(int type,int code,int id,int...args){
        if(args.length>5)throw new IllegalArgumentException("PTP parameters");
        byte[] b=new byte[12+args.length*4];put(b,0,b.length,4);put(b,4,type,2);put(b,6,code,2);put(b,8,id,4);
        for(int i=0;i<args.length;i++)put(b,12+i*4,args[i],4);return b;
    }
    private byte[] readExactly(int n)throws IOException{
        byte[] out=new byte[n];int done=0,emptyPackets=0;
        while(done<n){
            if(cursor==pending.length){
                byte[] packet=new byte[16384];int got=wire.read(packet);
                if(got==0&&++emptyPackets<=3)continue;
                if(got<=0||got>packet.length)throw new IOException("USB ขาดการตอบสนอง กรุณาเชื่อมต่อใหม่");
                emptyPackets=0;pending=Arrays.copyOf(packet,got);cursor=0;
            }
            int take=Math.min(n-done,pending.length-cursor);System.arraycopy(pending,cursor,out,done,take);cursor+=take;done+=take;
        }return out;
    }
    public static final class Reply{
        public int code;public byte[] data=new byte[0];public int[] params=new int[0];public long bytes;
        public void requireOK()throws IOException{if(code!=OK)throw new Rejected(code);}
    }
    public static final class Rejected extends IOException{
        private static final long serialVersionUID=1L;public final int code;
        Rejected(int c){super(c==0x2019?"กล้องไม่ว่าง รอแล้วลองใหม่":String.format(Locale.US,"กล้องตอบกลับ PTP 0x%04X",c));code=c;}
    }
    public synchronized Reply call(int op,int...args)throws IOException{return transfer(op,null,8*1024*1024,args);}
    public synchronized void releaseSingle()throws IOException{
        try{call(0x9128,1,0).requireOK();call(0x9128,2,0).requireOK();}
        finally{try{call(0x9129,2).requireOK();}finally{call(0x9129,1).requireOK();}}
    }
    public synchronized Reply transfer(int op,OutputStream sink,long limit,int...args)throws IOException{
        return exchange(op,sink,limit,null,args);
    }
    public synchronized Reply setProperty(int property,long value)throws IOException{
        byte[] data=new byte[12];put(data,0,12,4);put(data,4,property,4);put(data,8,value,4);
        return exchange(0x9110,null,0,data);
    }
    private void writeAll(byte[] bytes)throws IOException{
        int offset=0;while(offset<bytes.length){int n=wire.write(bytes,offset,bytes.length-offset);if(n<=0||n>bytes.length-offset)throw new IOException("ส่ง USB ไม่สำเร็จ");offset+=n;}
    }
    private Reply exchange(int op,OutputStream sink,long limit,byte[] outbound,int...args)throws IOException{
        long started=System.nanoTime();
        if(op==0x1002)transaction=0;
        int id=transaction++;if(observer!=null)observer.start(op,id);writeAll(container(1,op,id,args));
        if(outbound!=null){byte[] header=container(2,op,id);put(header,0,12+outbound.length,4);writeAll(header);writeAll(outbound);}
        Reply result=new Reply();boolean hadData=false;
        for(int phase=0;phase<2;phase++){
            byte[] h=readExactly(12);long length=u32(h,0);int type=u16(h,4),code=u16(h,6);
            if(length<12||(int)u32(h,8)!=id)throw new IOException("PTP transaction ไม่ตรงกัน กรุณาเชื่อมต่อใหม่");
            long size=length-12;
            if(type==2&&code==op&&!hadData&&outbound==null){
                if(size>limit)throw new IOException("ไฟล์ใหญ่เกินขอบเขตต้นแบบ กรุณาเชื่อมต่อใหม่");
                hadData=true;ByteArrayOutputStream memory=sink==null?new ByteArrayOutputStream():null;OutputStream target=sink==null?memory:sink;
                for(long left=size;left>0;){byte[] block=readExactly((int)Math.min(16384,left));target.write(block);left-=block.length;}
                if(memory!=null)result.data=memory.toByteArray();result.bytes=size;
            }else if(type==3&&size<=20&&size%4==0){
                result.code=code;byte[] body=readExactly((int)size);result.params=new int[body.length/4];
                for(int i=0;i<result.params.length;i++)result.params[i]=(int)u32(body,i*4);
                if(observer!=null)observer.transaction(op,code,(System.nanoTime()-started)/1000000);
                return result;
            }else throw new IOException("รูปแบบ PTP ไม่ถูกต้อง กรุณาเชื่อมต่อใหม่");
        }throw new IOException("ไม่พบคำตอบ PTP");
    }
    static final class Reader{
        final byte[] b;int pos;Reader(byte[] b){this.b=b;}
        int word()throws IOException{int v=u16(b,pos);pos+=2;return v;}
        long dword()throws IOException{long v=u32(b,pos);pos+=4;return v;}
        String string()throws IOException{
            if(pos>=b.length)throw new IOException("PTP string ขาดหาย");int n=b[pos++]&255;if(n==0)return "";
            if(pos>b.length-n*2||b[pos+(n-1)*2]!=0||b[pos+(n-1)*2+1]!=0)throw new IOException("PTP string ไม่ถูกต้อง");
            String value=new String(b,pos,(n-1)*2,"UTF-16LE");pos+=n*2;return value;
        }
        int[] words()throws IOException{long n=dword();if(n>65536||n>(b.length-pos)/2)throw new IOException("PTP array ไม่ถูกต้อง");int[] out=new int[(int)n];for(int i=0;i<n;i++)out[i]=word();return out;}
    }
    public static final class Device{
        public String manufacturer,model,version;public Set<Integer> operations=new HashSet<Integer>();
        public boolean is600D(){return model.equals("Canon EOS 600D")||model.equals("Canon EOS REBEL T3i")||model.equals("Canon EOS Kiss X5");}
    }
    public static Device device(byte[] b)throws IOException{
        Reader r=new Reader(b);r.word();r.dword();r.word();r.string();r.word();Device d=new Device();
        for(int op:r.words())d.operations.add(op);r.words();r.words();r.words();r.words();d.manufacturer=r.string();d.model=r.string();d.version=r.string();r.string();return d;
    }
    public static int[] handles(byte[] b)throws IOException{
        long n=u32(b,0);if(n>1000000||n!=(b.length-4)/4||(b.length-4)%4!=0)throw new IOException("รายการไฟล์ไม่ถูกต้อง");
        int[] out=new int[(int)n];for(int i=0;i<n;i++)out[i]=(int)u32(b,4+i*4);return out;
    }
    public static final class Photo{public int handle;public long size;public String name;}
    public static Photo photo(int handle,byte[] b)throws IOException{
        if(b.length<53||u16(b,4)!=0x3801)return null;
        Reader r=new Reader(b);r.pos=52;Photo p=new Photo();p.handle=handle;p.size=u32(b,8);p.name=r.string();
        if(p.size==0||p.size>64*1024*1024||!(p.name.toLowerCase(Locale.US).endsWith(".jpg")||p.name.toLowerCase(Locale.US).endsWith(".jpeg")))return null;
        p.name=p.name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");return p;
    }
    public static Map<Integer,Long> properties(byte[] b)throws IOException{
        Map<Integer,Long> values=new HashMap<Integer,Long>();int p=0;
        while(p<b.length){
            if(b.length-p<8)throw new IOException("EOS event ขาดหาย");long size=u32(b,p),type=u32(b,p+4);
            if(size<8||size>b.length-p)throw new IOException("EOS event ไม่ถูกต้อง");
            if(type==0xc189&&size>=14){int property=(int)u32(b,p+8);
                boolean word=(property>=0xd101&&property<=0xd107)||property==0xd109||property==0xd1b1||property==0xd1b8;
                values.put(property,word||size<16?(long)u16(b,p+12):u32(b,p+12));
            }p+=(int)size;
        }return values;
    }
}
