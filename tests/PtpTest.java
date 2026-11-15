package art.photoui.companion;
import java.io.*;
import java.util.*;
public final class PtpTest{
    static int tests;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);tests++;}
    interface Task{void run()throws Exception;}
    static void rejects(Task t,String name)throws Exception{boolean failed=false;try{t.run();}catch(IOException e){failed=true;}check(failed,name);}
    static byte[] join(byte[]...chunks)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();for(byte[] c:chunks)b.write(c);return b.toByteArray();}
    static byte[] data(int op,int tid,byte[] payload)throws IOException{byte[] h=Ptp.container(2,op,tid);Ptp.put(h,0,12+payload.length,4);return join(h,payload);}
    static final class Wire implements Ptp.Wire{
        byte[] input;int pos,fragment;ByteArrayOutputStream output=new ByteArrayOutputStream();
        Wire(byte[] b,int f){input=b;fragment=f;}
        public int read(byte[] b){if(pos==input.length)return -1;int n=Math.min(Math.min(fragment,b.length),input.length-pos);System.arraycopy(input,pos,b,0,n);pos+=n;return n;}
        public int write(byte[] b,int off,int n){int take=Math.min(n,5);output.write(b,off,take);return take;}
    }
    public static void main(String[] args)throws Exception{
        byte[] command=Ptp.container(1,0x1007,0,0xffffffff,0,0);
        check(Ptp.u32(command,0)==24&&Ptp.u16(command,4)==1,"command header");check(Ptp.u32(command,12)==0xffffffffL,"unsigned handle");
        byte[] payload={1,2,3,4,5};
        for(int fragment:new int[]{1,7,16384}){Wire w=new Wire(join(data(0x1001,0,payload),Ptp.container(3,Ptp.OK,0)),fragment);Ptp.Reply r=new Ptp(w).call(0x1001);r.requireOK();check(Arrays.equals(r.data,payload),"fragmented/coalesced "+fragment);check(w.output.size()==12,"partial writes "+fragment);}
        Wire stream=new Wire(join(data(0x1009,0,payload),Ptp.container(3,Ptp.OK,0)),3);ByteArrayOutputStream sink=new ByteArrayOutputStream();
        check(new Ptp(stream).transfer(0x1009,sink,5,1).bytes==5&&Arrays.equals(sink.toByteArray(),payload),"streamed payload");
        final Wire afterZlp=new Wire(Ptp.container(3,Ptp.OK,0),100);
        Ptp.Wire zlp=new Ptp.Wire(){boolean first=true;public int read(byte[] b){if(first){first=false;return 0;}return afterZlp.read(b);}public int write(byte[] b,int o,int n){return afterZlp.write(b,o,n);}};
        check(new Ptp(zlp).call(0x1001).code==Ptp.OK,"USB zero-length terminator");
        rejects(()->new Ptp(new Wire(Ptp.container(3,0x2019,0),100)).call(0x1001).requireOK(),"busy response");
        rejects(()->new Ptp(new Wire(Ptp.container(3,Ptp.OK,44),100)).call(0x1001),"wrong transaction");
        rejects(()->new Ptp(new Wire(data(0x1009,0,payload),100)).transfer(0x1009,new ByteArrayOutputStream(),4,1),"size limit");
        rejects(()->new Ptp(new Wire(new byte[]{1,2},100)).call(0x1001),"disconnect during header");
        byte[] invalid=Ptp.container(3,Ptp.OK,0);Ptp.put(invalid,0,11,4);rejects(()->new Ptp(new Wire(invalid,100)).call(0x1001),"invalid length");
        Wire open=new Wire(join(Ptp.container(3,Ptp.OK,0),Ptp.container(3,Ptp.OK,0),Ptp.container(3,Ptp.OK,1)),100);Ptp p=new Ptp(open);p.call(0x1001);p.call(0x1002,1);p.call(0x1003);
        byte[] emitted=open.output.toByteArray();check(Ptp.u32(emitted,20)==0&&Ptp.u32(emitted,36)==1,"OpenSession IDs");
        check(Ptp.handles(new byte[]{1,0,0,0,-1,-1,-1,-1})[0]==-1,"unsigned handles");
        rejects(()->Ptp.handles(new byte[]{2,0,0,0,1,0,0,0}),"truncated handles");
        byte[] event=new byte[16];Ptp.put(event,0,16,4);Ptp.put(event,4,0xc189,4);Ptp.put(event,8,0xd103,4);Ptp.put(event,12,72,4);
        check(Ptp.properties(event).get(0xd103)==72L,"EOS properties");byte[] corrupt=event.clone();corrupt[0]=0;rejects(()->Ptp.properties(corrupt),"zero event length");
        rejects(()->Ptp.device(new byte[1]),"short device info");
        ByteArrayOutputStream device=new ByteArrayOutputStream();device.write(new byte[8]);device.write(0);device.write(new byte[2]);device.write(new byte[]{1,0,0,0,0x28,(byte)0x91});for(int i=0;i<4;i++)device.write(new byte[4]);
        for(String s:new String[]{"Canon","Canon EOS 600D","1.0.2","PRIVATE"})device.write(string(s));
        Ptp.Device info=Ptp.device(device.toByteArray());check(info.is600D()&&info.operations.contains(0x9128)&&info.version.equals("1.0.2"),"model and capabilities");
        check(Ptp.photo(1,new byte[53])==null,"non JPEG ignored");byte[] photo=new byte[52];Ptp.put(photo,4,0x3801,2);Ptp.put(photo,8,5000,4);
        Ptp.Photo image=Ptp.photo(1,join(photo,string("../IMG_0001.JPG")));check(image!=null&&!image.name.contains("/"),"filename sanitized");
        byte[] small=Arrays.copyOf(event,14);Ptp.put(small,0,14,4);check(Ptp.properties(small).get(0xd103)==72L,"16-bit EOS property");
        Map<Integer,Long> settings=new HashMap<Integer,Long>();check(CameraRules.captureBlock(settings)!=null,"unknown settings blocked");
        for(int[] v:new int[][]{{0xd11c,2},{0xd108,3},{0xd105,3},{0xd106,0},{0xd102,0x70},{0xd11b,42}})settings.put(v[0],(long)v[1]);check(CameraRules.captureBlock(settings)==null,"single MF card capture allowed");
        for(int[] v:new int[][]{{0xd11c,4},{0xd108,0},{0xd105,4},{0xd106,1},{0xd102,0},{0xd11b,0}}){Long previous=settings.put(v[0],(long)v[1]);check(CameraRules.captureBlock(settings)!=null,"blocked "+v[0]);settings.put(v[0],previous);}
        Wire shutter=new Wire(join(Ptp.container(3,Ptp.OK,0),Ptp.container(3,0x2019,1),Ptp.container(3,Ptp.OK,2),Ptp.container(3,Ptp.OK,3)),100);rejects(()->new Ptp(shutter).releaseSingle(),"shutter rejected without retry");
        byte[] requests=shutter.output.toByteArray();check(requests.length==72,"four shutter operations");check(Ptp.u16(requests,46)==0x9129&&Ptp.u32(requests,52)==2&&Ptp.u16(requests,62)==0x9129&&Ptp.u32(requests,68)==1,"release after rejection");
        Random rng=new Random(600);for(int i=0;i<2000;i++){byte[] fuzz=new byte[rng.nextInt(150)];rng.nextBytes(fuzz);try{Ptp.properties(fuzz);}catch(IOException expected){}try{Ptp.device(fuzz);}catch(IOException expected){}try{Ptp.handles(fuzz);}catch(IOException expected){}}
        check(true,"2000 malformed datasets bounded");System.out.println("PASS: "+tests+" PTP assertions + 2000 parser fuzz cases");
    }
    static byte[] string(String s)throws IOException{return join(new byte[]{(byte)(s.length()+1)},s.getBytes("UTF-16LE"),new byte[2]);}
}
