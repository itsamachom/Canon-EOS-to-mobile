package art.photoui.companion;
import java.util.*;
/** Canon property codes and photographic labels, not a list of camera capabilities. */
public final class ExposureValues {
    public static final int[] PROPERTIES={0xd103,0xd101,0xd102,0xd104};
    private static final Map<Integer,String> av=new HashMap<Integer,String>(),tv=new HashMap<Integer,String>(),iso=new HashMap<Integer,String>();
    private static void load(Map<Integer,String> map,String text){for(String pair:text.split(";")){String[] p=pair.split("=");map.put(Integer.parseInt(p[0],16),p[1]);}}
    static{
        load(av,"08=1;0b=1.1;0c=1.2;0d=1.2;10=1.4;13=1.6;14=1.8;15=1.8;18=2;1b=2.2;1c=2.5;1d=2.5;20=2.8;23=3.2;24=3.5;25=3.5;28=4;2b=4.5;2c=4.5;2d=5;30=5.6;33=6.3;34=6.7;35=7.1;38=8;3b=9;3c=9.5;3d=10;40=11;43=13;44=13;45=14;48=16;4b=18;4c=19;4d=20;50=22;53=25;54=27;55=29;58=32;60=45;68=64");
        load(tv,"10=30″;13=25″;14=20.3″;15=20″;18=15″;1b=13″;1c=10″;1d=10.3″;20=8″;23=6.3″;24=6″;25=5″;28=4″;2b=3.2″;2c=3″;2d=2.5″;30=2″;33=1.6″;34=1.5″;35=1.3″;38=1″;3b=0.8″;3c=0.7″;3d=0.6″;40=1/2;43=0.4″;44=0.3″;45=0.3″;48=1/4;4b=1/5;4c=1/6;4d=1/6;50=1/8;53=1/10;54=1/10;55=1/13;58=1/15;5b=1/20;5c=1/20;5d=1/25;60=1/30;63=1/40;64=1/45;65=1/50;68=1/60;6b=1/80;6c=1/90;6d=1/100;70=1/125;73=1/160;74=1/180;75=1/200;78=1/250;7b=1/320;7c=1/350;7d=1/400;80=1/500;83=1/640;84=1/750;85=1/800;88=1/1000;8b=1/1250;8c=1/1500;8d=1/1600;90=1/2000;93=1/2500;94=1/3000;95=1/3200;98=1/4000");
        load(iso,"00=Auto;01=Auto;40=50;48=100;4b=125;4d=160;50=200;53=250;55=320;58=400;5b=500;5d=640;60=800;63=1000;65=1250;68=1600;6b=2000;6d=2500;70=3200;73=4000;75=5000;78=6400;7b=8000;7d=10000;80=12800");
    }
    public static boolean supported(int p){for(int code:PROPERTIES)if(code==p)return true;return false;}
    public static String name(int p){switch(p){case 0xd103:return "ISO";case 0xd101:return "รูรับแสง";case 0xd102:return "ชัตเตอร์";case 0xd104:return "ชดเชย EV";default:return "ไม่รองรับ";}}
    public static String label(int p,long value){
        if(value<0||value>65535)return null;int code=(int)value;
        if(p==0xd101){String s=av.get(code);return s==null?null:"f/"+s;}
        if(p==0xd102)return tv.get(code);if(p==0xd103)return iso.get(code);
        if(p==0xd104&&code<=255){int n=(byte)code,abs=Math.abs(n),whole=abs/8,rem=abs%8;
            String fraction=rem==0?"":rem==3?"1/3":rem==4?"1/2":rem==5?"2/3":null;if(fraction==null||whole>5)return null;
            String s=whole==0?fraction:String.valueOf(whole)+(fraction.isEmpty()?"":" "+fraction);
            return n==0?"0 EV":(n<0?"−":"+")+s+" EV";}
        return null;
    }
    private ExposureValues(){}
}
