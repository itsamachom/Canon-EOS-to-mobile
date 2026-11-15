package art.photoui.companion;
import java.io.*;
import java.util.*;

/** Bounded app-private media cache; URLs expose registered tokens, never paths. */
public final class AppMedia {
    private final File dir;
    private final LinkedHashMap<String,File> files=new LinkedHashMap<String,File>(32,0.75f,true);
    private long bytes;
    public AppMedia(File parent)throws IOException{dir=new File(parent,"camera-media");if(!dir.exists()&&!dir.mkdirs())throw new IOException("สร้าง cache ไม่สำเร็จ");clear();}
    public synchronized File temporary()throws IOException{return File.createTempFile("image-",".jpg",dir);}
    public synchronized String register(File file)throws IOException{
        if(!file.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile()))throw new IOException("Invalid cache path");
        String token=UUID.randomUUID().toString().replace("-","");files.put(token,file);bytes+=file.length();
        Iterator<Map.Entry<String,File>> i=files.entrySet().iterator();
        while((bytes>128L*1024*1024||files.size()>256)&&files.size()>1&&i.hasNext()){File old=i.next().getValue();bytes-=old.length();old.delete();i.remove();}
        return "https://companion.local/media/"+token;
    }
    public synchronized String put(byte[] bytes)throws IOException{File f=temporary();try(OutputStream out=new FileOutputStream(f)){out.write(bytes);}return register(f);}
    public synchronized InputStream open(String token)throws IOException{File f=files.get(token);return f==null||!f.exists()?null:new FileInputStream(f);}
    public synchronized boolean exists(String url){if(url==null)return false;int at=url.lastIndexOf('/');File f=files.get(url.substring(at+1));return f!=null&&f.exists();}
    public synchronized void clear(){files.clear();bytes=0;File[] fs=dir.listFiles();if(fs!=null)for(File f:fs)if(f.isFile())f.delete();}
}
