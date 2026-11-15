package art.photoui.companion;
import java.util.Map;
public final class CameraRules{
    private static boolean is(Map<Integer,Long> p,int k,long v){return p.containsKey(k)&&p.get(k).longValue()==v;}
    public static String captureBlock(Map<Integer,Long> p){
        if(!is(p,0xd11c,2))return "ยังยืนยันไม่ได้ว่าบันทึกลงการ์ด กรุณาอ่านค่าใหม่ แอปจะไม่เปลี่ยนปลายทางให้เอง";
        Long mode=p.get(0xd105);
        if(mode==null||mode<0||mode>3)return "เลือกโหมดภาพนิ่ง P / Tv / Av / M บนกล้อง ไม่ใช้ Bulb หรือโหมดวิดีโอ";
        if(!is(p,0xd106,0))return "ตั้งขับเคลื่อนภาพเดี่ยวบนกล้อง แล้วอ่านค่าใหม่";
        Long shutter=p.get(0xd102);
        if(mode==3&&(shutter==null||shutter.longValue()<0x10||shutter.longValue()>0x98))return "ตั้งชัตเตอร์ปกติ เช่น 1/125 วินาที ไม่ใช้ Bulb แล้วอ่านค่าใหม่";
        if(p.containsKey(0xd11b)&&p.get(0xd11b).longValue()==0)return "กล้องรายงานว่าถ่ายได้อีก 0 ภาพ กรุณาตรวจพื้นที่การ์ด";return null;
    }
    private CameraRules(){}
}
