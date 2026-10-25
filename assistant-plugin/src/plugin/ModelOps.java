package plugin;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

final class ModelOps {
    static Field field(Object owner,String key)throws Exception{
        for(Class<?> c=owner.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(key);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new Exception(owner.getClass().getSimpleName()+": нет поля "+key);
    }
    static final class ValueEdit {
        final Object owner,before;Object after;final Field field;
        ValueEdit(Object o,String key,Object v)throws Exception{owner=o;field=field(o,key);before=field.get(o);after=v;}
        void set(boolean forward)throws Exception{field.set(owner,forward?after:before);}
    }
    static String label(Bridge.Plan p,Object o)throws Exception{return String.valueOf(p.value(o,"name"));}
    static Object find(Bridge.Plan p,List<Object> objects,String name)throws Exception {
        Object found=null;for(Object o:objects)if(label(p,o).equals(name)){if(found!=null)throw new Exception("Неоднозначное имя: "+name);found=o;}if(found==null)throw new Exception("Не найдено: "+name);return found;
    }
    static void free(Bridge.Plan p,List<Object> objects,String name,Object except)throws Exception{for(Object o:objects)if(o!=except&&label(p,o).equals(name))throw new Exception("Имя уже занято: "+name);}
    static void validName(String name)throws Exception {if(name.trim().isEmpty()||name.equals(".")||name.equals("..")||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf('\0')>=0)throw new Exception("Недопустимое имя");}
    static File sceneDirectory(Bridge.Plan p,Object scene)throws Exception {
        Object probe=Reflect.make(Bridge.C+"Scene",label(p,scene),p.project);return ((File)Reflect.call(probe,"getDirectory")).getCanonicalFile();
    }
    static void resource(Bridge.Plan p,Object resource,File dst)throws Exception{
        File original=(File)p.value(resource,"file");if(original==null)throw new Exception("Не найден файл ресурса "+Bridge.name(resource));
        // A resource may already have a staged target. Always copy from its original live file.
        File source=(File)Reflect.call(resource,"getFile");
        String root=((File)Reflect.call(p.project,"getDirectory")).getCanonicalPath()+File.separator;if(!source.getCanonicalPath().startsWith(root))throw new Exception("Ресурс вне каталога проекта: "+Bridge.name(resource));
        File target=new File(dst,original.getName());p.files.add(source,target);
        p.change(resource,"file",target);p.change(resource,"fileName",target.getName());
    }
    static void relocate(Bridge.Plan p,Object sprite,Object destination)throws Exception{
        File dir=sceneDirectory(p,destination);
        for(Object look:p.stage(Reflect.list(sprite,"getLookList")))resource(p,look,new File(dir,"images"));
        for(Object sound:p.stage(Reflect.list(sprite,"getSoundList")))resource(p,sound,new File(dir,"sounds"));
    }
    static boolean operation(Bridge bridge,Bridge.Plan p,JSONObject op,String kind)throws Exception {
        List<Object> scenes=p.stage(Reflect.list(p.project,"getSceneList"));String sceneName=Bridge.required(op,"scene");validName(sceneName);
        if(kind.equals("scene.create")){
            free(p,scenes,sceneName,null);Object scene=Reflect.call(Reflect.type("org.catrobat.catroid.ui.recyclerview.controller.SceneController"),"newSceneWithBackgroundSprite",sceneName,op.optString("background","Фон"),p.project);
            if(sceneDirectory(p,scene).exists())throw new Exception("Каталог новой сцены уже существует: выбери другое имя");scenes.add(scene);p.summary.append("Создать сцену: ").append(sceneName).append("\n");return true;
        }
        if(kind.equals("scene.rename")){
            Object scene=find(p,scenes,sceneName);String next=Bridge.required(op,"name");validName(next);free(p,scenes,next,scene);
            File old=sceneDirectory(p,scene);p.change(scene,"name",next);File dest=sceneDirectory(p,scene);if(!old.equals(dest)&&dest.exists())throw new Exception("Каталог переименованной сцены уже существует: выбери другое имя");
            p.files.tree(old,dest,0);
            for(Object sprite:p.stage(Reflect.list(scene,"getSpriteList")))relocate(p,sprite,scene);
            for(Object sc:scenes)for(Object sp:p.stage(Reflect.list(sc,"getSpriteList")))for(Object script:p.stage(Reflect.list(sp,"getScriptList")))for(Object brick:ReferenceCheck.bricks(p,script)){
                String field=brick.getClass().getSimpleName().equals("SceneTransitionBrick")?"sceneForTransition":brick.getClass().getSimpleName().equals("SceneStartBrick")?"sceneToStart":null;
                if(field!=null&&sceneName.equals(p.value(brick,field)))p.change(brick,field,next);
            }
            p.summary.append("Переименовать сцену: ").append(sceneName).append(" → ").append(next).append("\n");return true;
        }
        if(!kind.equals("sprite.rename")&&!kind.equals("sprite.move")&&!kind.equals("sprite.delete"))return false;
        Object scene=find(p,scenes,sceneName);List<Object> sprites=p.stage(Reflect.list(scene,"getSpriteList"));Object sprite=find(p,sprites,Bridge.required(op,"sprite"));
        if(!sprite.getClass().getName().equals(Bridge.C+"Sprite"))throw new Exception("Групповой или специальный спрайт: операция пока не поддерживается");
        if(sprites.indexOf(sprite)==0)throw new Exception("Фон сцены нельзя удалять, переносить или переименовывать этой операцией");
        if(kind.equals("sprite.rename")){
            String next=Bridge.required(op,"name");validName(next);free(p,sprites,next,sprite);String old=label(p,sprite);p.change(sprite,"name",next);
            for(Object sp:sprites)for(Object script:p.stage(Reflect.list(sp,"getScriptList")))ReferenceCheck.renameCollision(p,script,old,next);
        }else if(kind.equals("sprite.delete")){sprites.remove(sprite);p.removed.add(sprite);}
        else{
            Object dest=find(p,scenes,Bridge.required(op,"to_scene"));if(dest==scene)throw new Exception("Спрайт уже в этой сцене");
            List<Object> targets=p.stage(Reflect.list(dest,"getSpriteList"));String next=op.optString("name",label(p,sprite));validName(next);free(p,targets,next,sprite);
            p.change(sprite,"name",next);relocate(p,sprite,dest);sprites.remove(sprite);targets.add(sprite);p.moved.add(sprite);
        }
        p.summary.append(kind).append(": ").append(sceneName).append(" / ").append(op.getString("sprite")).append("\n");return true;
    }
}
