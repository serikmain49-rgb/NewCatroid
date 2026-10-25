package plugin;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Lossless XML slicing: content is never reparsed/reserialized, preserving XStream references. */
final class SyncExport {
    static final int CHUNK=250000,MAX_PARTS=1000;
    final JSONObject manifest=new JSONObject(),blocks=new JSONObject();
    final JSONObject project;final String requestId,projectId,hash;
    SyncExport(JSONObject payload)throws Exception {
        project=payload.getJSONObject("project");requestId=payload.getString("request_id");projectId=project.getString("project_id");hash=project.getString("base_sha256");
        manifest.put("project.json",parts(project.toString(2),"project","", ""));
        manifest.put("PROTOCOL.md",parts(payload.getString("protocol"),"protocol","", ""));
        manifest.put("supported-blocks.json",parts(payload.getJSONArray("supported_blocks").toString(2),"catalogue","", ""));
        String xml=payload.getString("code_xml");manifest.put("code.xml",xmlParts(xml));
        if(count()>MAX_PARTS){manifest.put("code.xml",parts(xml,"xml_chunk","",""));rebuildBlocks(payload);}
        if(count()>MAX_PARTS)throw new Exception("Слишком много частей экспорта");
    }
    void rebuildBlocks(JSONObject payload)throws Exception{
        Set<String> needed=new HashSet<String>();Iterator<String> files=manifest.keys();while(files.hasNext()){JSONArray a=manifest.getJSONArray(files.next());for(int i=0;i<a.length();i++)needed.add(a.getJSONObject(i).getString("sha256"));}
        List<String> remove=new ArrayList<String>();Iterator<String> keys=blocks.keys();while(keys.hasNext()){String k=keys.next();if(!needed.contains(k))remove.add(k);}for(String k:remove)blocks.remove(k);
    }
    int count()throws Exception{int n=0;Iterator<String> it=manifest.keys();while(it.hasNext())n+=manifest.getJSONArray(it.next()).length();return n;}
    JSONArray parts(String text,String kind,String scene,String script)throws Exception {
        JSONArray a=new JSONArray();for(int start=0;start<text.length()||start==0;){int end=Math.min(text.length(),start+CHUNK);if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;
            String part=text.substring(start,end),sha=Bridge.sha(part);blocks.put(sha,part);a.put(new JSONObject().put("sha256",sha).put("kind",kind).put("scene_id",scene).put("script_id",script));if(end==text.length())break;start=end;
        }return a;
    }
    static final class Tag {String name,scene;int start;Tag(String n,String sc,int s){name=n;scene=sc;start=s;}}
    JSONArray xmlParts(String xml)throws Exception {
        if(!xml.trim().startsWith("<program"))throw new Exception("NOT_A_PROJECT: ожидается Catrobat <program>");
        JSONArray out=new JSONArray();Deque<Tag> stack=new ArrayDeque<Tag>();int pos=0,last=0;String scene="";
        while(pos<xml.length()){
            int open=xml.indexOf('<',pos);if(open<0)break;
            if(xml.startsWith("<!--",open)){int end=xml.indexOf("-->",open+4);if(end<0)throw new Exception("Некорректный XML-комментарий");pos=end+3;continue;}
            if(xml.startsWith("<![CDATA[",open)){int end=xml.indexOf("]]>",open+9);if(end<0)throw new Exception("Некорректный CDATA");pos=end+3;continue;}
            if(xml.startsWith("<!",open))throw new Exception("DTD в экспорте запрещён");
            boolean quoted=false;char quote=0;int end=open+1;for(;end<xml.length();end++){char c=xml.charAt(end);if(quoted){if(c==quote)quoted=false;}else if(c=='\''||c=='\"'){quoted=true;quote=c;}else if(c=='>')break;}
            if(end>=xml.length())throw new Exception("Некорректный XML-тег");String token=xml.substring(open+1,end).trim();pos=end+1;if(token.startsWith("?"))continue;
            boolean close=token.startsWith("/"),self=token.endsWith("/");String name=token.substring(close?1:0).split("[\\s/]",2)[0];
            if(close){if(stack.isEmpty()||!stack.peek().name.equals(name))throw new Exception("Неверная вложенность XML");Tag t=stack.pop();
                if(name.equals("script")&&t.start>=0){append(out,parts(xml.substring(last,t.start),"scene",t.scene,""));String body=xml.substring(t.start,pos);Matcher id=Pattern.compile("<scriptId>([^<]+)</scriptId>").matcher(body);String script=id.find()?id.group(1):"";append(out,parts(body,"script",t.scene,script));last=pos;}
                if(name.equals("scene"))scene="";
            }else if(!self){if(name.equals("scene")&&!stack.isEmpty()&&stack.peek().name.equals("scenes")){Matcher id=Pattern.compile("sceneId=[\"']([^\"']+)").matcher(token);scene=id.find()?id.group(1):"";}
                boolean script=name.equals("script")&&!stack.isEmpty()&&stack.peek().name.equals("scriptList");stack.push(new Tag(name,scene,script?open:-1));
            }
        }
        if(!stack.isEmpty())throw new Exception("Обрезанный XML проекта");append(out,parts(xml.substring(last),"scene",scene,""));return out;
    }
    static void append(JSONArray to,JSONArray from)throws Exception{for(int i=0;i<from.length();i++)to.put(from.get(i));}
    JSONObject payload(JSONObject baseline,boolean forceFull)throws Exception {
        boolean delta=!forceFull&&baseline!=null&&projectId.equals(baseline.optString("project_id"));Set<String> known=new HashSet<String>();
        if(delta){JSONArray hashes=baseline.getJSONArray("hashes");for(int i=0;i<hashes.length();i++)known.add(hashes.getString(i));}
        JSONObject changed=new JSONObject();Iterator<String> it=blocks.keys();while(it.hasNext()){String key=it.next();if(!delta||!known.contains(key))changed.put(key,blocks.getString(key));}
        return new JSONObject().put("sync_version",1).put("mode",delta?"delta":"full").put("project_id",projectId).put("project",project.getString("project")).put("request_id",requestId).put("base_sha256",delta?baseline.getString("base_sha256"):"").put("target_sha256",hash).put("files",manifest).put("changed",changed);
    }
    JSONObject baseline()throws Exception{JSONArray a=new JSONArray();Iterator<String> it=blocks.keys();while(it.hasNext())a.put(it.next());return new JSONObject().put("project_id",projectId).put("base_sha256",hash).put("hashes",a);}
    static JSONObject cached(File dir,String server,String id)throws Exception{File f=new File(dir,Bridge.sha(server+":"+id)+".json");try{return f.exists()?new JSONObject(Main.read(f,2*1024*1024)):null;}catch(Exception corrupt){return null;}}
    void save(File dir,String server)throws Exception{if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Не удалось сохранить базу синхронизации");File f=new File(dir,Bridge.sha(server+":"+projectId)+".json"),tmp=new File(f+".tmp");Main.write(tmp,baseline().toString());if(!tmp.renameTo(f))throw new IOException("Не удалось сохранить квитанцию синхронизации");}
}
