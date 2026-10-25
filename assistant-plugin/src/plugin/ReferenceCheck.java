package plugin;
import java.lang.reflect.*;
import java.util.*;

/** Cross-scene links have no safe meaning in Pocket Code. Reject them rather than silently detach. */
final class ReferenceCheck {
    static List<Object> bricks(Bridge.Plan p,Object script)throws Exception {
        List<Object> result=new ArrayList<Object>();collect(p,p.stage(Reflect.list(script,"getBrickList")),result,0);return result;
    }
    static void collect(Bridge.Plan p,List<Object> list,List<Object> out,int depth)throws Exception{
        if(depth>32)throw new Exception("Слишком глубокий существующий скрипт");
        for(Object b:list){out.add(b);for(String method:new String[]{"getNestedBricks","getSecondaryNestedBricks"})if(Catalogue.method(b,method)){
            List<Object> children=Reflect.list(b,method);if(children!=null)collect(p,p.stage(children),out,depth+1);
        }}
    }
    static List<Object> roots(Object owner)throws Exception {
        List<Object> out=new ArrayList<Object>();for(Field f:Catalogue.fields(owner.getClass())){
            Object v=f.get(owner);if(v==null)continue;
            if(v.getClass().getName().equals(Bridge.F+"Formula"))out.add(Reflect.call(v,"getRoot"));
            if(v instanceof Map)for(Object x:((Map<?,?>)v).values())if(x!=null&&x.getClass().getName().equals(Bridge.F+"Formula"))out.add(Reflect.call(x,"getRoot"));
        }return out;
    }
    static List<Object> nodes(Object root)throws Exception {
        List<Object> out=new ArrayList<Object>();walk(root,out,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()));return out;
    }
    static void walk(Object n,List<Object> out,Set<Object> seen)throws Exception{
        if(n==null||!seen.add(n))return;out.add(n);if(out.size()>200000)throw new Exception("Слишком много узлов формулы");
        for(String key:new String[]{"leftChild","rightChild"})walk(ModelOps.field(n,key).get(n),out,seen);
        Object more=ModelOps.field(n,"additionalChildren").get(n);if(more instanceof List)for(Object c:(List<?>)more)walk(c,out,seen);
    }
    static void renameCollision(Bridge.Plan p,Object script,String old,String next)throws Exception{
        List<Object> owners=bricks(p,script);owners.add(script);
        for(Object o:owners)for(Object root:roots(o))for(Object n:nodes(root)){
            String type=Reflect.call(n,"getElementType").toString();
            if("COLLISION_FORMULA".equals(type)&&old.equals(p.value(n,"value")))p.change(n,"value",next);
            if("FUNCTION".equals(type)&&"TOUCHES_OBJECT_BY_NAME".equals(p.value(n,"value"))){
                Object child=ModelOps.field(n,"leftChild").get(n);
                if(child==null||!"STRING".equals(Reflect.call(child,"getElementType").toString()))throw new Exception("Нельзя безопасно переименовать спрайт: TOUCHES_OBJECT_BY_NAME вычисляет имя динамически");
                if(old.equals(p.value(child,"value")))p.change(child,"value",next);
            }
        }
    }
    static void verify(Bridge.Plan p)throws Exception {
        if(p.structuralOperation==0)return;
        List<Object> scenes=p.stage(Reflect.list(p.project,"getSceneList"));
        for(Object scene:scenes){List<Object> sprites=p.stage(Reflect.list(scene,"getSpriteList"));
            for(Object sprite:sprites)for(Object script:p.stage(Reflect.list(sprite,"getScriptList"))){
                List<Object> owners=bricks(p,script);owners.add(script);
                for(Object owner:owners){String path="сцена "+ModelOps.label(p,scene)+", спрайт "+ModelOps.label(p,sprite)+", "+owner.getClass().getSimpleName();
                    for(Field f:Catalogue.fields(owner.getClass())){
                        Object v=p.value(owner,f.getName());if(v==null)continue;
                        if(Reflect.type(Bridge.C+"Sprite").isInstance(v)&&!sprites.contains(v))throw new Exception(path+": ссылка на удалённый спрайт или другую сцену ("+f.getName()+")");
                        for(String[] ref:new String[][]{{Bridge.F+"UserVariable","getUserVariables"},{Bridge.F+"UserList","getUserLists"},{"org.catrobat.catroid.common.LookData","getLookList"},{"org.catrobat.catroid.common.SoundInfo","getSoundList"}}){
                            Class<?> type;try{type=Reflect.type(ref[0]);}catch(ClassNotFoundException absent){continue;}
                            if(type.isInstance(v)){
                                boolean valid=p.stage(Reflect.list(sprite,ref[1])).contains(v);
                                if(ref[1].equals("getUserVariables")||ref[1].equals("getUserLists"))valid|=p.stage(Reflect.list(p.project,ref[1])).contains(v);
                                if(!valid)throw new Exception(path+": ссылка "+f.getName()+" на локальные данные или ресурс вне спрайта");
                            }
                        }
                    }
                    for(Object root:roots(owner))for(Object n:nodes(root)){
                        String type=Reflect.call(n,"getElementType").toString(),name=String.valueOf(p.value(n,"value"));
                        if(type.equals("FUNCTION")&&name.equals("TOUCHES_OBJECT_BY_NAME")&&(!p.removed.isEmpty()||!p.moved.isEmpty())){
                            Object child=ModelOps.field(n,"leftChild").get(n);
                            if(child==null||!"STRING".equals(Reflect.call(child,"getElementType").toString()))throw new Exception(path+": нельзя проверить вычисляемое имя TOUCHES_OBJECT_BY_NAME при переносе/удалении");
                            ModelOps.find(p,sprites,String.valueOf(p.value(child,"value")));
                        }
                        if(type.equals("COLLISION_FORMULA")&&(p.moved.contains(sprite)||!p.removed.isEmpty()||!p.moved.isEmpty())){
                            ModelOps.find(p,sprites,name);
                        }
                    }
                }
            }
        }
    }
}
