package art.photoui.companion;
import java.io.*;
import java.util.*;
public final class EosCameraTest {
    static int tests;
    static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);tests++;}
    interface Task{void run()throws Exception;}
    static void rejects(Task t,String s)throws Exception{boolean failed=false;try{t.run();}catch(IOException e){failed=true;}check(failed,s);}
    static Ptp.Reply reply(int code,byte[] data){Ptp.Reply r=new Ptp.Reply();r.code=code;r.data=data;return r;}
    static byte[] event(int type,int...args){byte[] b=new byte[8+args.length*4];Ptp.put(b,0,b.length,4);Ptp.put(b,4,type,4);for(int i=0;i<args.length;i++)Ptp.put(b,8+i*4,args[i],4);return b;}
    static byte[] join(byte[]...chunks)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();for(byte[] a:chunks)b.write(a);return b.toByteArray();}
    static class Clock implements EosCamera.Clock{long time;public long now(){return time;}public void pause(long ms){time+=ms;}}
    static class Port implements EosCamera.Port {
        final List<String> trace=new ArrayList<String>();int shots,failFull,busyPolls,failOutput,failMode,addedPending;boolean fullHeld;byte[] nextEvent=new byte[0],frame;
        int frameCode=Ptp.OK;
        public Ptp.Reply call(int op,int...args)throws IOException{
            trace.add(String.format("%04x",op)+Arrays.toString(args));
            if(op==0x9116){byte[] data=nextEvent;nextEvent=new byte[0];
                if(data.length==0&&busyPolls>0){busyPolls--;data=event(0xc18b,1);}
                else if(data.length==0&&addedPending>0){addedPending--;try{data=join(event(0xc18b,0),event(0xc181,shots));}catch(Exception e){throw new IOException(e);}}
                else if(data.length==0)data=event(0xc18b,fullHeld?1:0);
                return reply(Ptp.OK,data);
            }
            if(op==0x9128&&args[0]==2){if(failFull>0){failFull--;return reply(0x2019,new byte[0]);}shots++;fullHeld=true;}
            if(op==0x9129&&args[0]==1&&fullHeld){fullHeld=false;addedPending++;}
            if(op==0x9153)return reply(frameCode,frame==null?new byte[0]:frame);
            return reply(Ptp.OK,new byte[0]);
        }
        public Ptp.Reply set(int p,long v){trace.add(String.format("set:%04x=%d",p,v));if(p==0xd1b1&&failMode>0){failMode--;return reply(0x2019,new byte[0]);}if(p==0xd1b0&&failOutput>0){failOutput--;return reply(0x2019,new byte[0]);}return reply(Ptp.OK,new byte[0]);}
    }
    static EosCamera camera(Port p){EosCamera e=new EosCamera(p,new Clock(),new HashSet<Integer>(Arrays.asList(0x9116,0x9128,0x9129,0x9110,0x9153)));
        for(int[] a:new int[][]{{0xd11c,2},{0xd105,3},{0xd106,0},{0xd108,3},{0xd102,112},{0xd11b,428},{0xd1b0,1},{0xd1b1,0},{0xd1b8,0}})e.properties.put(a[0],(long)a[1]);return e;}
    static byte[] segment(int type,byte[] data)throws Exception{byte[] h=event(type);Ptp.put(h,0,data.length+8,4);return join(h,data);}
    public static void main(String[] a)throws Exception{
        Port p=new Port();EosCamera e=camera(p);check(e.capture(),"first capture confirmed");check(e.capture(),"second capture confirmed without reconnect");check(p.shots==2,"exactly two full presses");
        int fullRelease=p.trace.indexOf("9129[2]");check(p.trace.get(fullRelease+1).equals("9116[]")&&p.trace.get(fullRelease+2).equals("9129[1]"),"drain events between full and half release");
        for(int mode=0;mode<4;mode++){Port m=new Port();EosCamera c=camera(m);c.properties.put(0xd105,(long)mode);check(c.capture(),"mode "+mode+" capture");}
        p=new Port();e=camera(p);p.failFull=1;final EosCamera rejected=e;rejects(()->rejected.capture(),"failed shutter reported");check(p.shots==0,"no retry of failed full press");check(p.trace.contains("9129[2]")&&p.trace.contains("9129[1]"),"both buttons released on failure");check(e.capture()&&p.shots==1,"next explicit press works after busy response");
        p=new Port();e=camera(p);p.busyPolls=3;check(e.capture(),"ready wait drains busy events");
        p=new Port();e=camera(p);e.properties.put(0xd105,20L);final EosCamera movie=e;rejects(()->movie.capture(),"no still capture in movie mode");check(p.shots==0,"movie guard sends no shutter");
        p=new Port();e=camera(p);e.startLive();check(e.live&&e.changedMode&&e.changedOutput,"live started with owned properties");
        check(p.trace.indexOf("set:d1b1=1")<p.trace.indexOf("set:d1b0=2"),"EVF enable precedes PC output");
        e.stopLive();check(!e.live&&e.properties.get(0xd1b0)==1L&&e.properties.get(0xd1b1)==0L,"live stop restores owned settings");
        p=new Port();e=camera(p);p.failOutput=1;final EosCamera failedStart=e;rejects(()->failedStart.startLive(),"live start rejection reported");check(!e.live&&e.properties.get(0xd1b1)==0L,"partial start rolls back EVF mode");
        p=new Port();e=camera(p);e.startLive();e.events(join(event(0xc189,0xd105,20),event(0xc189,0xd1b8,4)));e.stopLive();check(e.properties.get(0xd1b1)==1L,"do not disable EVF while recording");check(!p.trace.contains("set:d1b8=0"),"never send movie record-stop property");
        p=new Port();e=camera(p);e.startLive();e.events(event(0xc189,0xd1b0,4));e.stopLive();check(e.properties.get(0xd1b0)==4L,"preserve externally changed output");
        p=new Port();e=camera(p);e.startLive();e.properties.remove(0xd1b8);e.stopLive();check(e.properties.get(0xd1b1)==1L,"do not disable EVF when recording state unknown");
        p=new Port();e=camera(p);e.properties.put(0xd105,20L);p.failMode=1;e.startLive();check(e.live&&e.changedOutput,"movie mode busy EVF enable can still start output");check(!e.changedMode,"do not own a rejected EVF mode write");e.stopLive();
        byte[] jpeg={(byte)255,(byte)216,(byte)255,(byte)224,1,(byte)255,(byte)217};
        for(int type:new int[]{1,9,11}){p=new Port();e=camera(p);p.frame=segment(type,jpeg);check(Arrays.equals(e.frame(),jpeg),"JPEG segment "+type);}
        p=new Port();e=camera(p);p.frame=join(segment(17,new byte[8]),segment(1,jpeg));check(Arrays.equals(e.frame(),jpeg),"skip metadata segment");
        p=new Port();e=camera(p);p.frameCode=0xa102;check(e.frame()==null,"not-ready frame tolerated");p.frameCode=0x2019;check(e.frame()==null,"busy frame tolerated");
        p=new Port();e=camera(p);p.frame=segment(9,new byte[24]);final EosCamera raw=e;rejects(()->raw.frame(),"raw video segment clearly unsupported");
        p=new Port();e=camera(p);p.frame=new byte[8];final EosCamera corrupt=e;rejects(()->corrupt.frame(),"zero frame length rejected");
        byte[] oversized=segment(1,jpeg);Ptp.put(oversized,0,100000,4);p=new Port();e=camera(p);p.frame=oversized;final EosCamera over=e;rejects(()->over.frame(),"out-of-bounds frame rejected");
        p=new Port();e=camera(p);final EosCamera shortEvent=e;rejects(()->shortEvent.events(event(0xc18b)),"short camera status rejected");
        File dir=new File(System.getProperty("java.io.tmpdir"),"companion-test-"+UUID.randomUUID());dir.mkdirs();AppMedia cache=new AppMedia(dir);
        String url=cache.put(jpeg);check(cache.exists(url),"cache token registered");String token=url.substring(url.lastIndexOf('/')+1);try(InputStream in=cache.open(token)){check(in.read()==255,"cache streams bytes");}
        check(cache.open("../../etc/passwd")==null,"unregistered paths inaccessible");cache.clear();check(!cache.exists(url),"cache cleared");new File(dir,"camera-media").delete();dir.delete();
        System.out.println("PASS: "+tests+" EOS state-machine, repeated-shutter, Live View and media-cache assertions");
    }
}
