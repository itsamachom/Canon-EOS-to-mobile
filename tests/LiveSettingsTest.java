package art.photoui.companion;
import java.io.*;
import java.util.*;
public final class LiveSettingsTest {
    static int tests;
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);tests++;}
    interface Task{void run()throws Exception;}
    static void rejects(Task task,String why)throws Exception{boolean failed=false;try{task.run();}catch(IOException e){failed=true;}check(failed,why);}
    static byte[] choices(int property,int type,int...values){int[] a=new int[values.length+3];a[0]=property;a[1]=type;a[2]=values.length;System.arraycopy(values,0,a,3,values.length);return EosCameraTest.event(0xc18a,a);}
    static class Port extends EosCameraTest.Port {
        boolean confirm=true,reject=false;int writes;
        @Override public Ptp.Reply set(int property,long value){writes++;super.set(property,value);if(reject)return EosCameraTest.reply(0x2019,new byte[0]);if(confirm)nextEvent=EosCameraTest.event(0xc189,property,(int)value);return EosCameraTest.reply(Ptp.OK,new byte[0]);}
    }
    static EosCamera camera(Port port)throws Exception{
        EosCamera e=EosCameraTest.camera(port);e.properties.put(0xd101,40L);e.properties.put(0xd103,72L);e.properties.put(0xd104,0L);
        e.events(EosCameraTest.join(choices(0xd103,3,72,80,88),choices(0xd101,3,40,48,56),choices(0xd102,3,104,112,120),choices(0xd104,3,253,0,3)));
        return e;
    }
    public static void main(String[] args)throws Exception{
        Port p=new Port();EosCamera e=camera(p);check(e.available.get(0xd103).equals(Arrays.asList(72L,80L,88L)),"camera enum parsed");
        check(e.changeSetting(0xd103,80)&&e.properties.get(0xd103)==80L,"ISO write confirmed from event");check(p.writes==1,"one write per explicit change");
        int before=p.writes;check(e.changeSetting(0xd103,80)&&p.writes==before,"same confirmed value needs no write");
        p.confirm=false;check(!e.changeSetting(0xd103,88),"accepted but missing readback is not confirmed");check(e.properties.get(0xd103)==80L,"no optimistic cache value");
        p.confirm=true;p.reject=true;final EosCamera rejected=e;rejects(()->rejected.changeSetting(0xd103,72),"busy write reported without retry");check(e.properties.get(0xd103)==80L,"rejected write retains actual value");
        p=new Port();e=camera(p);final EosCamera guarded=e;
        rejects(()->guarded.changeSetting(0xd103,999),"unadvertised value blocked");rejects(()->guarded.changeSetting(0xd1b1,0),"bridge cannot write EVF or arbitrary properties");check(p.writes==0,"blocked settings issue no write");
        e.properties.put(0xd105,0L);check(e.settingBlock(0xd101)!=null&&e.settingBlock(0xd102)!=null&&e.settingBlock(0xd103)==null&&e.settingBlock(0xd104)==null,"P mode controls");
        e.properties.put(0xd105,1L);check(e.settingBlock(0xd102)==null&&e.settingBlock(0xd101)!=null,"Tv mode controls");
        e.properties.put(0xd105,2L);check(e.settingBlock(0xd101)==null&&e.settingBlock(0xd102)!=null,"Av mode controls");check(e.changeSetting(0xd104,253),"negative EV write uses byte code");check("−1/3 EV".equals(ExposureValues.label(0xd104,253)),"negative EV label");
        e.properties.put(0xd105,3L);check(e.settingBlock(0xd101)==null&&e.settingBlock(0xd102)==null&&e.settingBlock(0xd104)!=null,"M mode controls");
        e.properties.put(0xd1b8,4L);rejects(()->guarded.changeSetting(0xd103,80),"recording write blocked");
        e.properties.put(0xd1b8,0L);e.status=1;check(e.settingBlock(0xd103)!=null,"camera busy disables settings");e.status=0;e.properties.put(0xd105,20L);check(e.settingBlock(0xd103)!=null,"movie mode settings not guessed");
        p=new Port();e=camera(p);e.events(choices(0xd103,3));check(e.settingBlock(0xd103)!=null,"empty enum disables field");
        e.events(choices(0xd103,3,72,80));e.events(choices(0xd103,1,88,96));check(!e.available.containsKey(0xd103),"unknown enum type removes stale capabilities");
        byte[] truncated=choices(0xd103,3,72);Ptp.put(truncated,16,500,4);final EosCamera bounds=e;rejects(()->bounds.events(truncated),"enum count bounded to event");
        e.events(choices(0xd103,3,0xbeef0048,0xbeef0050));check(e.available.get(0xd103).equals(Arrays.asList(72L,80L)),"enum slots decode uint16 not padding");
        check(ExposureValues.label(0xd102,12)==null,"Bulb never offered");check("+2/3 EV".equals(ExposureValues.label(0xd104,5)),"positive EV thirds");check("f/4".equals(ExposureValues.label(0xd101,40)),"aperture label");
        LatestFrames m=new LatestFrames();String stream="a";m.begin(stream);byte[] b={1,2,3};
        check(m.request(stream,0)==null,"request waits for first real frame");LatestFrames.Frame one=m.publish(stream,b,960,640,100,12);check(one!=null&&one.sequence==1,"pending display gets first frame");
        boolean noQueue=true;for(int i=2;i<=100;i++)noQueue&=m.publish(stream,new byte[]{(byte)i},960,640,100+i,12)==null;check(noQueue,"producer never queues 99 unrequested display events");
        check(m.image(stream,1)==one,"in-flight image remains stable while producer runs");LatestFrames.Frame newest=m.request(stream,1);check(newest.sequence==100&&newest.jpeg[0]==100,"slow renderer skips to latest instead of replaying backlog");
        check(m.image(stream,1)==null,"older offered URL expires when next is requested");check(m.request("old",0)==null,"stale stream request rejected");
        m.stop();check(m.image(stream,100)==null&&m.request(stream,0)==null,"stop invalidates all frame URLs and pending requests");m.begin("b");m.request("b",0);check(m.publish("a",b,1,1,1,1)==null,"late old stream cannot populate new stream");check(m.publish("b",b,1,1,1,1).sequence==1,"sequence resets per stream");
        check(LatestFrames.delayMs(24,10,true)==32,"24 target budget includes work, not extra 150 ms");check(LatestFrames.delayMs(24,80,true)==1,"slow USB adds no long fixed delay");check(LatestFrames.delayMs(12,10,true)==74,"12 target budget");check(LatestFrames.delayMs(24,80,false)>=12,"not-ready reads back off");
        byte[] jpeg={(byte)255,(byte)216,(byte)255,(byte)192,0,8,8,2,(byte)128,3,(byte)192,0};int[] size=LatestFrames.dimensions(jpeg);check(size[0]==960&&size[1]==640,"SOF dimensions without raster decode");
        byte[] malformed=jpeg.clone();malformed[4]=127;rejects(()->LatestFrames.dimensions(malformed),"JPEG segment length bounded");rejects(()->LatestFrames.dimensions(new byte[4]),"non-JPEG rejected");
        System.out.println("PASS: "+tests+" Live/settings assertions (mock protocol and frame mailbox, not hardware FPS)");
    }
}
