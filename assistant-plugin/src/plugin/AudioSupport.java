package plugin;
import org.json.*;
/** Optional new host API; metadata only, never phone URIs, paths or audio bytes. */
final class AudioSupport {
    static void export(Object project,JSONObject out)throws Exception {
        try {out.put("imported_audio",new JSONArray((String)Reflect.call(Reflect.type("org.catrobat.catroid.audio.RuntimeAudioSession"),"describe",project))).put("runtime_audio_api",1);}
        catch(ClassNotFoundException absent){}catch(NoSuchMethodException absent){}
    }
    static void validate(Object project,JSONObject spec)throws Exception {
        JSONObject props=spec.optJSONObject("properties");
        if(props==null||!props.has("command"))throw new Exception("RuntimeAudioBrick.properties.command: требуется команда");
        String cmd=props.getString("command");
        if(!java.util.Arrays.asList("PICK","IMPORT","PLAY","PAUSE","RESUME","STOP","SEEK","VOLUME").contains(cmd))throw new Exception("RuntimeAudioBrick.properties.command: неизвестная команда "+cmd);
        JSONObject formulas=spec.optJSONObject("formulas");
        if(formulas==null||!formulas.has("VALUE"))throw new Exception("RuntimeAudioBrick.formulas.VALUE: требуется аргумент (пустая строка для PICK/PAUSE/RESUME/STOP)");
        Object value=formulas.get("VALUE");
        if(value instanceof JSONObject){JSONObject node=(JSONObject)value;String type=node.optString("type");if(type.equals("STRING")||type.equals("NUMBER"))value=node.get("value");else return;}
        if(cmd.equals("PLAY")){
            if(!(value instanceof String))throw new Exception("RuntimeAudioBrick.formulas.VALUE: ожидается ID audio:UUID");
            Object store=Reflect.make("org.catrobat.catroid.audio.ImportedAudioStore",Reflect.call(project,"getDirectory"));
            if(!Boolean.TRUE.equals(Reflect.call(store,"exists",value)))throw new Exception("RuntimeAudioBrick.formulas.VALUE: MISSING_AUDIO — постоянный ID отсутствует в проекте");
        }
        if(cmd.equals("SEEK")||cmd.equals("VOLUME")){
            double n;try{n=Double.parseDouble(String.valueOf(value));}catch(Exception e){throw new Exception("RuntimeAudioBrick.formulas.VALUE: требуется число");}
            if(Double.isNaN(n)||Double.isInfinite(n)||n<0||(cmd.equals("VOLUME")&&n>100))throw new Exception("RuntimeAudioBrick.formulas.VALUE: значение вне диапазона "+(cmd.equals("VOLUME")?"0–100":"≥ 0 секунд"));
        }
    }
}
