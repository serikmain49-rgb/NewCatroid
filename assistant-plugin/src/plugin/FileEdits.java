package plugin;
import java.io.*;
import java.security.*;
import java.util.*;

/** Never overwrite original assets; copies are staged and rechecked at commit. */
final class FileEdits {
    static final long MAX_COPY=512L*1024*1024;
    static String digest(File f)throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:hash.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    static final class Copy {
        final File source,target;final String hash;boolean created;
        Copy(File s,File t)throws Exception{source=s.getCanonicalFile();target=t.getAbsoluteFile();if(!s.isFile())throw new IOException("Не найден ресурс или ссылка на внешний файл: "+s.getName());hash=digest(s);}
        void apply()throws Exception{
            if(!digest(source).equals(hash))throw new IOException("Ресурс изменился после предпросмотра: "+source.getName());
            if(!target.getCanonicalFile().equals(target))throw new IOException("Недопустимый путь ресурса");
            if(target.exists()){if(target.isFile()&&digest(target).equals(hash))return;throw new IOException("Конфликт файла ресурса: "+target.getName());}
            if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Не удалось создать папку ресурсов");
            if(!target.createNewFile())throw new IOException("Конфликт файла ресурса");created=true;
            try(InputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(target)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.getFD().sync();}
            if(!digest(target).equals(hash))throw new IOException("Ошибка копирования ресурса");
        }
        void rollback(){if(created){target.delete();created=false;}}
    }
    final List<Copy> copies=new ArrayList<Copy>();long bytes;
    void add(File source,File target)throws Exception {
        if(source.getCanonicalFile().equals(target.getCanonicalFile()))return;
        for(Copy c:copies)if(c.target.equals(target.getAbsoluteFile())){if(!c.hash.equals(digest(source)))throw new IOException("Конфликт ресурсов: "+target.getName());return;}
        bytes+=source.length();if(bytes>MAX_COPY)throw new IOException("Перенос ресурсов больше 512 МиБ: раздели патч");
        copies.add(new Copy(source,target));
    }
    void tree(File source,File target,int depth)throws Exception{
        if(depth>24)throw new IOException("Слишком глубокая папка ресурсов");if(!source.exists())return;
        if(!source.getAbsoluteFile().equals(source.getCanonicalFile()))throw new IOException("Символические ссылки в ресурсах не поддерживаются");
        if(source.isFile()){add(source,target);return;}File[] files=source.listFiles();if(files==null)throw new IOException("Не удалось прочитать ресурсы сцены");
        for(File f:files)tree(f,new File(target,f.getName()),depth+1);
    }
    void apply()throws Exception{for(Copy c:copies)c.apply();}
    void rollback(){for(int i=copies.size()-1;i>=0;i--)copies.get(i).rollback();}
}
