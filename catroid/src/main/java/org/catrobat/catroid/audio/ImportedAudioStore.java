/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.catrobat.catroid.audio;

import java.io.*;
import java.util.*;

/** Atomic project assets. IDs are independent of paths, filenames and provider URIs. */
public final class ImportedAudioStore {
    public static final String DIRECTORY = "imported-audio-v1";
    public static final long MAX_BYTES = 64L * 1024 * 1024;
    private final File root;
    public interface Cancelled { boolean get(); }
    public interface Validator { Metadata validate(File file, String title) throws Exception; }
    public static final class Metadata {
        public final String title, mime;
        public final long durationMs;
        public Metadata(String title,String mime,long durationMs) { this.title=title;this.mime=mime;this.durationMs=durationMs; }
    }
    public ImportedAudioStore(File project) throws IOException {
        root=new File(new File(project,"files"),DIRECTORY);
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("STORAGE_UNAVAILABLE");
        if(!root.getCanonicalPath().startsWith(project.getCanonicalPath()+File.separator))throw new IOException("INVALID_STORAGE_PATH");
    }
    public static boolean validId(String id) {return id!=null&&id.matches("audio:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private File directory(String id)throws IOException {
        if(!validId(id))throw new IOException("INVALID_ID");
        File dir=new File(root,id.substring(6));
        if(!dir.getCanonicalPath().equals(root.getCanonicalPath()+File.separator+id.substring(6)))throw new IOException("INVALID_STORAGE_PATH");
        return dir;
    }
    public File audio(String id)throws IOException {
        File dir=directory(id),file=new File(dir,"track");
        if(!file.isFile()||!file.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile()))throw new IOException("MISSING_AUDIO");
        return file;
    }
    public Metadata metadata(String id)throws IOException {
        audio(id);File f=new File(directory(id),"metadata.properties");
        if(!f.isFile()||f.length()>16384)throw new IOException("INVALID_METADATA");
        Properties p=new Properties();
        try(InputStream in=new FileInputStream(f)){p.load(in);}
        catch(IllegalArgumentException e){throw new IOException("INVALID_METADATA",e);}
        try {
            long ms=Long.parseLong(p.getProperty("duration_ms","-1"));String mime=p.getProperty("mime","");
            if(ms<=0||!validMime(mime))throw new IOException("INVALID_METADATA");
            return new Metadata(p.getProperty("title","Audio"),mime,ms);
        }catch(NumberFormatException e){throw new IOException("INVALID_METADATA",e);}
    }
    private static boolean validMime(String mime){return "audio/mpeg".equals(mime)||"audio/vorbis".equals(mime)||"audio/opus".equals(mime);}
    public boolean exists(String id){try{metadata(id);return true;}catch(IOException e){return false;}}
    public List<String> ids(){List<String> out=new ArrayList<>();File[] entries=root.listFiles();if(entries!=null)for(File f:entries)if(f.isDirectory()&&validId("audio:"+f.getName()))out.add("audio:"+f.getName());Collections.sort(out);return out;}
    /** Closes input on every path. A whole-directory rename is the only publication point. */
    public String importAudio(InputStream input,String title,Validator validator,Cancelled cancelled)throws Exception {
        String uuid=UUID.randomUUID().toString();File pending=new File(root,".pending-audio-"+uuid);
        if(!pending.mkdir()){input.close();throw new IOException("STORAGE_UNAVAILABLE");}
        boolean published=false;
        try {
            File track=new File(pending,"track");long bytes=0,deadline=System.nanoTime()+120000000000L;
            try(InputStream in=input;FileOutputStream out=new FileOutputStream(track)){
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1){
                    if(cancelled.get())throw new IOException("CANCELLED");
                    if(System.nanoTime()>deadline)throw new IOException("IMPORT_TIMEOUT");
                    bytes+=n;if(bytes>MAX_BYTES)throw new IOException("FILE_TOO_LARGE");out.write(buffer,0,n);
                }out.getFD().sync();
            }
            if(bytes==0)throw new IOException("INVALID_AUDIO");
            Metadata m=validator.validate(track,clean(title));
            if(m.durationMs<=0||!validMime(m.mime))throw new IOException("INVALID_AUDIO");
            Properties props=new Properties();props.setProperty("title",clean(m.title));props.setProperty("mime",m.mime);props.setProperty("duration_ms",Long.toString(m.durationMs));
            try(FileOutputStream out=new FileOutputStream(new File(pending,"metadata.properties"))){props.store(out,"NewCatroid imported audio v1");out.getFD().sync();}
            synchronized(cancelled){
                if(cancelled.get())throw new IOException("CANCELLED");
                if(!pending.renameTo(directory("audio:"+uuid)))throw new IOException("STORAGE_UNAVAILABLE");published=true;
            }return "audio:"+uuid;
        }finally{if(!published)delete(pending);}
    }
    private static String clean(String s){if(s==null||s.trim().isEmpty())return "Audio";s=s.replaceAll("[\\p{Cntrl}]"," ");return s.substring(0,Math.min(s.length(),300));}
    private static void delete(File f){File[] c=f.listFiles();if(c!=null)for(File x:c)delete(x);f.delete();}
}
