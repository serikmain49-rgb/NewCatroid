package plugin;
import java.io.*;
import org.json.*;

/** Stored outside the plugin directory; survives restart and plugin replacement. */
final class PatchJournal {
    final File file;JSONObject data;
    PatchJournal(File file)throws Exception{this.file=file;data=file.exists()?new JSONObject(Main.read(file,8*1024*1024)):new JSONObject();}
    synchronized void persist()throws Exception{
        File tmp=new File(file.getPath()+".tmp");Main.write(tmp,data.toString());if(!tmp.renameTo(file))throw new IOException("Не удалось сохранить журнал патчей");
    }
    static String projectKey(Object project)throws Exception{return Bridge.sha(((File)Reflect.call(project,"getDirectory")).getCanonicalPath());}
    static String identity(Object project)throws Exception {
        String path=((File)Reflect.call(project,"getDirectory")).getCanonicalPath();String firstScene="";
        java.util.List<Object> scenes=Reflect.list(project,"getSceneList");if(!scenes.isEmpty())try{Object id=ModelOps.field(scenes.get(0),"sceneId").get(scenes.get(0));if(id!=null)firstScene=id.toString();}catch(NoSuchFieldException ignored){}catch(Exception ignored){}
        return Bridge.sha(path+"\n"+firstScene);
    }
    String key(Object p,String id)throws Exception{return projectKey(p)+":"+id;}
    synchronized void receipt(Object p,String rid,String sha)throws Exception{data.put("snapshot:"+projectKey(p),new JSONObject().put("request_id",rid).put("base_sha256",sha));persist();}
    synchronized void check(Object p,JSONObject patch)throws Exception{
        if(patch.has("project_id")&&!identity(p).equals(patch.getString("project_id")))throw new Exception("WRONG_PROJECT: ID открытого проекта не совпадает");
        String id=Bridge.required(patch,"id");JSONObject record=data.optJSONObject(key(p,id));
        if(record!=null&&(record.optString("status").equals("applied")||record.optString("status").equals("applying")))throw new Exception("DUPLICATE_PATCH: ID уже применён или применение было прервано: "+id);
        checkReceipt(p,patch.optString("request_id",""),patch.optString("base_sha256"));
    }
    synchronized void checkReceipt(Object p,String rid,String hash)throws Exception {
        if(!rid.isEmpty()){
            JSONObject receipt=data.optJSONObject("snapshot:"+projectKey(p));
            if(receipt==null||!rid.equals(receipt.optString("request_id"))||!hash.equals(receipt.optString("base_sha256")))throw new Exception("STALE_EXPORT: request_id или SHA-256 не совпадает. Запроси свежий экспорт");
        }
    }
    synchronized void mark(Object p,String id,String state)throws Exception{data.put(key(p,id),new JSONObject().put("status",state).put("at",System.currentTimeMillis()));persist();}
    synchronized void event(JSONObject e)throws Exception{JSONArray a=data.optJSONArray("outbox");if(a==null)a=new JSONArray();if(a.length()>=200)throw new IOException("Очередь отчётов заполнена: восстанови соединение с bridge");a.put(e);data.put("outbox",a);persist();}
    synchronized void failedEvent(String id,String reason)throws Exception{JSONArray a=data.optJSONArray("failed_reports");if(a==null)a=new JSONArray();a.put(new JSONObject().put("event_id",id).put("reason",reason));data.put("failed_reports",a);ack(id);}
    synchronized JSONObject peek(){JSONArray a=data.optJSONArray("outbox");return a==null||a.length()==0?null:a.optJSONObject(0);}
    synchronized void ack(String eventId)throws Exception{JSONArray a=data.optJSONArray("outbox"),next=new JSONArray();if(a==null)return;for(int i=0;i<a.length();i++)if(!eventId.equals(a.getJSONObject(i).optString("event_id")))next.put(a.getJSONObject(i));data.put("outbox",next);persist();}
}
