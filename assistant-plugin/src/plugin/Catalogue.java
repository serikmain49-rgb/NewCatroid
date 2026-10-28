package plugin;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Availability is probed against installed host; seeds are adapters, not a capability claim. */
final class Catalogue {
    final JSONArray blocks=new JSONArray();
    final JSONObject language=new JSONObject(), host=new JSONObject();
    final Map<String,JSONObject> byClass=new LinkedHashMap<String,JSONObject>();
    final Map<String,Integer> arities=new LinkedHashMap<String,Integer>();
    final Set<String> operators=new HashSet<String>(),sensors=new HashSet<String>();
    static List<Field> fields(Class<?> c){
        List<Field> out=new ArrayList<Field>();for(Class<?> t=c;t!=null&&t.getName().startsWith("org.catrobat.catroid.");t=t.getSuperclass())
            for(Field f:t.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&!Modifier.isTransient(f.getModifiers())){f.setAccessible(true);out.add(f);}return out;
    }
    static boolean method(Object o,String name){for(Method m:o.getClass().getMethods())if(m.getName().equals(name))return true;return false;}
    static JSONArray enumNames(String cls)throws Exception {JSONArray a=new JSONArray();for(Object x:Reflect.type(cls).getEnumConstants())a.put(((Enum<?>)x).name());return a;}
    Catalogue(File folder,String packageName,String version)throws Exception {this(folder,packageName,version,new String[0]);}
    Catalogue(File folder,String packageName,String version,String[] apks)throws Exception {
        host.put("package",packageName).put("version",version).put("catalogue_version",2).put("plugin","0.7.0").put("availability","probed_installed_host").put("scope","supported adapters; unknown opaque blocks are excluded");
        JSONArray seeds=new JSONArray(Main.read(new File(folder,"catalogue-seeds.json"),2*1024*1024));
        Set<String> seeded=new HashSet<String>();for(int i=0;i<seeds.length();i++)seeded.add(seeds.getJSONObject(i).getString("class_name"));
        // Discover lighting additions in the installed APK. Only plain FormulaBrick adapters are accepted below.
        for(String apk:apks){Object dex=null;try{dex=Reflect.make("dalvik.system.DexFile",apk);Enumeration<?> names=(Enumeration<?>)Reflect.call(dex,"entries");while(names.hasMoreElements()){
            String cls=String.valueOf(names.nextElement());if(cls.startsWith(Bridge.B)&&cls.endsWith("Brick")&&cls.indexOf('$')<0&&cls.substring(Bridge.B.length()).contains("Light")){
                String name=cls.substring(Bridge.B.length());if(seeded.add(name))seeds.put(new JSONObject().put("class_name",name).put("property_names",new JSONArray()).put("field_labels",new JSONObject()).put("discovered",true));
            }
        }}catch(Exception ignored){}finally{if(dex!=null)try{Reflect.call(dex,"close");}catch(Exception ignored){}}}
        for(int i=0;i<seeds.length();i++){
            JSONObject seed=seeds.getJSONObject(i);String name=seed.getString("class_name");
            try{
                Object brick=Reflect.make(Bridge.B+name);
                if(!Reflect.type(Bridge.B+"Brick").isInstance(brick))continue;
                if(seed.optBoolean("discovered")){
                    if(!Reflect.type(Bridge.B+"FormulaBrick").isInstance(brick))continue;
                    boolean opaque=false;for(Field f:fields(brick.getClass()))if(f.getDeclaringClass()==brick.getClass()&&!f.getType().isPrimitive()&&f.getType()!=String.class&&!f.getType().isEnum())opaque=true;
                    if(opaque)continue;
                }
                JSONObject entry=new JSONObject().put("class",name).put("available",true);
                JSONArray formulas=new JSONArray(),mandatory=new JSONArray(),properties=new JSONArray();JSONObject defaults=new JSONObject();
                if(method(brick,"getAllFormulaFieldsWithFormulas")){
                    Map<?,?> map=(Map<?,?>)Reflect.call(brick,"getAllFormulaFieldsWithFormulas");
                    for(Object key:map.keySet()){formulas.put(key.toString());if(map.get(key)==null)mandatory.put(key.toString());else{Object root=Reflect.call(map.get(key),"getRoot");defaults.put(key.toString(),new JSONObject().put("type",Reflect.call(root,"getElementType").toString()).put("value",Reflect.call(root,"getValue")));}}
                }
                entry.put("formulas",formulas).put("required_formulas",mandatory).put("formula_defaults",defaults).put("field_labels",seed.optJSONObject("field_labels"));
                JSONArray wanted=seed.getJSONArray("property_names");
                for(Field f:fields(brick.getClass())){
                    boolean selected=false;for(int j=0;j<wanted.length();j++)if(f.getName().equals(wanted.getString(j)))selected=true;
                    if(seed.optBoolean("discovered")&&f.getDeclaringClass()==brick.getClass())selected=true;
                    if(!selected||Modifier.isFinal(f.getModifiers()))continue;
                    Class<?> t=f.getType();if(!(t==String.class||t==int.class||t==float.class||t==double.class||t==boolean.class||t.isEnum()))continue;
                    JSONObject prop=new JSONObject().put("name",f.getName()).put("type",t.getName());
                    if(t.isEnum()){JSONArray values=new JSONArray();for(Object e:t.getEnumConstants())values.put(((Enum<?>)e).name());prop.put("enum",values);}
                    Object value=f.get(brick);prop.put("default",value==null?JSONObject.NULL:value.toString());properties.put(prop);
                }
                if(name.equals("RuntimeAudioBrick"))entry.put("required_properties",new JSONArray().put("command")).put("required_formulas",new JSONArray().put("VALUE"))
                    .put("semantics",new JSONObject().put("waits",true).put("result","PICK=selection:UUID|cancelled|error:CODE; IMPORT=audio:UUID|error:CODE; controls=ok|error:CODE")
                    .put("storage","project/files/imported-audio-v1; no audio bytes sent to bridge").put("units",new JSONObject().put("SEEK","seconds").put("VOLUME","0..100")));
                entry.put("properties",properties).put("variable_required",method(brick,"setUserVariable")).put("list_required",method(brick,"setUserList"));
                entry.put("look_reference",method(brick,"setLook")).put("sound_reference",method(brick,"setSound")).put("sprite_reference",name.equals("CloneBrick")&&method(brick,"onItemSelected"));
                entry.put("children",method(brick,"getNestedBricks")).put("else",method(brick,"getSecondaryNestedBricks"));
                if(name.equals("SceneTransitionBrick")||name.equals("SceneStartBrick"))entry.put("scene_required",true);
                blocks.put(entry);byClass.put(name,entry);
            }catch(Exception ignored){ /* Missing adapter/class/constructor is not exported as available. */ }catch(LinkageError ignored){}
        }
        JSONArray installed=enumNames(Bridge.F+"Functions"), functions=new JSONArray();
        JSONObject probes=new JSONObject(Main.read(new File(folder,"function-resources.json"),2*1024*1024));
        Object keyboard=Reflect.make(Bridge.F+"InternFormulaKeyboardAdapter");
        for(int i=0;i<installed.length();i++){
            String name=installed.getString(i);JSONObject f=new JSONObject().put("name",name).put("supported",false);
            try{
                JSONObject p=probes.getJSONObject(name);Class<?> ids=Reflect.type("org.catrobat.catroid.R$"+p.getString("resource_class"));
                int resource=ids.getField(p.getString("resource_name")).getInt(null);
                List<?> tokens=(List<?>)Reflect.call(keyboard,"createInternTokenListByResourceId",resource,"");
                int commas=0;boolean params=false,seen=false;
                for(Object token:tokens){String type=Reflect.call(token,"getInternTokenType").toString();
                    if(type.equals("FUNCTION_NAME")&&name.equals(Reflect.call(token,"getTokenStringValue")))seen=true;
                    if(type.equals("FUNCTION_PARAMETERS_BRACKET_OPEN"))params=true;
                    if(type.equals("FUNCTION_PARAMETER_DELIMITER"))commas++;
                }
                if(seen){int arity=params?commas+1:0;arities.put(name,arity);JSONArray layout=new JSONArray();if(arity>0)layout.put("left");if(arity>1)layout.put("right");for(int n=2;n<arity;n++)layout.put("additional["+(n-2)+"]");f.put("supported",true).put("arity",arity).put("arguments",layout);}
            }catch(Exception ignored){f.put("reason","Installed enum; parameter layout not verifiable with this adapter");}
            if(name.startsWith("AUDIO_")&&f.optBoolean("supported")) {
                boolean text=name.equals("AUDIO_NAME")||name.equals("AUDIO_ERROR")||name.equals("AUDIO_STATE");
                f.put("return_type",text?"string":"number");
                if(name.equals("AUDIO_DURATION")||name.equals("AUDIO_POSITION"))f.put("unit","seconds");
                if(name.equals("AUDIO_EXISTS"))f.put("values",new JSONArray().put(0).put(1));
                JSONArray signature=new JSONArray();if(arities.get(name)>0)signature.put(new JSONObject().put("name","id").put("type","string").put("format","audio:UUID").put("position","left"));
                f.put("parameters",signature);
            }
            functions.put(f);
        }
        JSONArray ops=enumNames(Bridge.F+"Operators"),ss=enumNames(Bridge.F+"Sensors");for(int i=0;i<ops.length();i++)operators.add(ops.getString(i));for(int i=0;i<ss.length();i++)sensors.add(ss.getString(i));
        language.put("functions",functions).put("operators",ops).put("sensors",ss).put("unary_operators",new JSONArray().put("MINUS").put("LOGICAL_NOT")).put("unary_argument","right").put("binary_arguments",new JSONArray().put("left").put("right")).put("bracket_argument","right");
        if(blocks.length()==0)throw new Exception("Ни один блок не прошёл проверку установленного приложения");
        JSONArray events=new JSONArray();for(String[] event:new String[][]{{"start","StartScript"},{"tap","WhenScript"},{"cloned","WhenClonedScript"},{"before_update","BeforeUpdateScript"},{"after_update","AfterUpdateScript"},{"broadcast","BroadcastScript"},{"condition","WhenConditionScript"}}){
            try{if(event[0].equals("broadcast"))Reflect.make(Bridge.C+event[1],"catalogue-probe");else if(event[0].equals("condition")){Object e=Reflect.make(Bridge.F+"FormulaElement",Reflect.enumeration(Bridge.F+"FormulaElement$ElementType","NUMBER"),"0",null);Reflect.make(Bridge.C+event[1],Reflect.make(Bridge.F+"Formula",e));}else Reflect.make(Bridge.C+event[1]);events.put(event[0]);}catch(Exception ignored){}
        }
        blocks.getJSONObject(0).put("catalogue",new JSONObject().put("host",host).put("script_events",events).put("formula_language",language).put("operations",new JSONArray("[\"scene.create\",\"scene.rename\",\"sprite.create\",\"sprite.rename\",\"sprite.delete\",\"sprite.move\",\"look.add\",\"variable.create\",\"list.create\",\"script.add\",\"script.append\",\"script.replace\",\"script.update\",\"script.splice\",\"script.delete\"]")));
    }
    // Fixture-only constructor: tests provide exact installed-model metadata.
    Catalogue(JSONArray entries,Map<String,Integer> functions,Set<String> ops,Set<String> ss)throws Exception {
        for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);blocks.put(e);byClass.put(e.getString("class"),e);}arities.putAll(functions);operators.addAll(ops);sensors.addAll(ss);
    }
    Set<String> allowed(){return byClass.keySet();}
    boolean property(String cls,String key)throws Exception {JSONArray p=byClass.get(cls).optJSONArray("properties");if(p!=null)for(int i=0;i<p.length();i++)if(key.equals(p.getJSONObject(i).getString("name")))return true;return false;}
}
