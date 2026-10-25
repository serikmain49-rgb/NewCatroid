package plugin;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.CRC32;
import org.json.JSONObject;

/** Embedded PNG only: never fetch URLs or accept caller-controlled file paths. */
final class ImageAsset {
    static final int MAX_BYTES=512*1024, MAX_SIDE=1024;
    final byte[] bytes;
    final File directory, target;
    final int width,height;
    boolean created;
    ImageAsset(JSONObject spec, Object scene)throws Exception {
        if(!"image/png".equals(spec.getString("mime")))throw new Exception("Поддерживается только image/png");
        String data=spec.getString("base64");
        if(data.length()>699052||!data.matches("[A-Za-z0-9+/]*={0,2}")||data.length()%4!=0)
            throw new Exception("Некорректный Base64 или PNG больше 512 КиБ");
        bytes=(byte[])Reflect.call(Reflect.type("android.util.Base64"),"decode",data,0);
        if(bytes.length<45||bytes.length>MAX_BYTES)throw new Exception("Размер PNG: до 512 КиБ");
        byte[] sig={(byte)137,80,78,71,13,10,26,10};
        for(int i=0;i<8;i++)if(bytes[i]!=sig[i])throw new Exception("Это не PNG");
        int pos=8;boolean header=false,end=false,idat=false;
        while(pos<bytes.length){
            if(bytes.length-pos<12)throw new Exception("Обрезанный PNG");
            int n=integer(pos);if(n<0||n>bytes.length-pos-12)throw new Exception("Повреждённый PNG");
            String type=new String(bytes,pos+4,4,"US-ASCII");
            CRC32 crc=new CRC32();crc.update(bytes,pos+4,n+4);
            if(crc.getValue()!=(integer(pos+8+n)&0xffffffffL))throw new Exception("Повреждённый PNG: CRC");
            if(!header){if(!type.equals("IHDR")||n!=13)throw new Exception("Нет заголовка PNG");header=true;}
            else if(type.equals("IHDR"))throw new Exception("Повторный заголовок PNG");
            if(type.equals("acTL"))throw new Exception("APNG не поддерживается: отправь кадры отдельными образами");
            if(type.equals("IDAT"))idat=true;
            pos+=n+12;
            if(type.equals("IEND")){if(n!=0||pos!=bytes.length||!idat)throw new Exception("Неверный конец PNG");end=true;break;}
        }
        if(!end)throw new Exception("Обрезанный PNG");
        width=integer(16);height=integer(20);
        if(width<1||height<1||width>MAX_SIDE||height>MAX_SIDE)throw new Exception("PNG должен быть от 1×1 до 1024×1024");
        if(spec.has("sha256")){
            StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(Locale.ROOT,"%02x",b&255));
            if(!hash.toString().equals(spec.getString("sha256")))throw new Exception("SHA-256 изображения не совпадает");
        }
        Object bitmap=Reflect.call(Reflect.type("android.graphics.BitmapFactory"),"decodeByteArray",bytes,0,bytes.length);
        if(bitmap==null)throw new Exception("Android не смог прочитать PNG");
        try{if(((Integer)Reflect.call(bitmap,"getWidth"))!=width||((Integer)Reflect.call(bitmap,"getHeight"))!=height)throw new Exception("Неверный размер PNG");}
        finally{Reflect.call(bitmap,"recycle");}
        File sceneDir=((File)Reflect.call(scene,"getDirectory")).getCanonicalFile();
        directory=new File(sceneDir,"images");
        if(!directory.getCanonicalFile().getParentFile().equals(sceneDir))throw new Exception("Недопустимый каталог образов");
        target=new File(directory,"assistant_"+UUID.randomUUID().toString()+".png");
    }
    int integer(int pos){return (bytes[pos]&255)<<24|(bytes[pos+1]&255)<<16|(bytes[pos+2]&255)<<8|(bytes[pos+3]&255);}
    void write()throws Exception {
        if(!directory.getCanonicalFile().equals(directory.getAbsoluteFile()))throw new IOException("Каталог образов изменился");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Не удалось создать каталог образов");
        if(!target.createNewFile())throw new IOException("Файл уже существует");
        created=true;
        try(FileOutputStream out=new FileOutputStream(target)){out.write(bytes);out.getFD().sync();}
    }
    void discard(){if(created){target.delete();created=false;}}
}
