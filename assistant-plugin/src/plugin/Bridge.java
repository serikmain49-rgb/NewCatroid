package plugin;

import org.json.*;
import java.util.*;
import java.security.MessageDigest;

/** Edits are staged in copied lists. No live model mutation occurs during preview. */
final class Bridge {
    static final String C="org.catrobat.catroid.content.";
    static final String F="org.catrobat.catroid.formulaeditor.";
    static final String B=C+"bricks.";
    static final int LIMIT=3000;
    int count;
    final Object project;
    final Set<String> allowed;
    final Catalogue catalogue;
    Object formulaSprite,formulaScene;Plan formulaPlan;String location="patch";

    Bridge(Object project,Set<String> allowed){this.project=project;this.allowed=allowed;this.catalogue=null;}
    Bridge(Object project,Catalogue catalogue){this.project=project;this.catalogue=catalogue;this.allowed=catalogue.allowed();}

    static String sha(String text) throws Exception {
        byte[] b=MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"));
        StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return s.toString();
    }
    static String name(Object obj)throws Exception{return String.valueOf(Reflect.call(obj,"getName"));}
    static Object named(List<Object> list,String name)throws Exception {
        Object found=null;
        for(Object obj:list)if(name(obj).equals(name)){if(found!=null)throw new Exception("Неоднозначное имя: "+name);found=obj;}
        if(found==null)throw new Exception("Не найдено: "+name);return found;
    }
    static String required(JSONObject obj,String key)throws Exception {
        String s=obj.getString(key);if(s.trim().isEmpty()||s.length()>500)throw new Exception("Неверное поле "+key);return s;
    }

    static final class Edit {
        final List<Object> live,before,after;
        Edit(List<Object> live){this.live=live;before=new ArrayList<Object>(live);after=new ArrayList<Object>(live);}
        void set(boolean forward){live.clear();live.addAll(forward?after:before);}
    }
    static final class Plan {
        final Object project;
        final String beforeSha,title;
        String id,requestId,projectId;
        final List<ModelOps.ValueEdit> values=new ArrayList<ModelOps.ValueEdit>();
        final Set<Object> removed=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()),moved=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        final FileEdits files=new FileEdits();
        String afterSha;boolean filesReady;int structuralOperation;
        final List<ImageAsset> assets=new ArrayList<ImageAsset>();
        int assetBytes;
        final List<Edit> edits=new ArrayList<Edit>();
        final List<Object> touchedScripts=new ArrayList<Object>();
        final List<Object> variableOwners=new ArrayList<Object>();
        final StringBuilder summary=new StringBuilder();
        Plan(Object p,String sha,String title){project=p;beforeSha=sha;this.title=title;}
        List<Object> stage(List<Object> live){
            for(Edit e:edits)if(e.live==live)return e.after;
            Edit e=new Edit(live);edits.add(e);return e.after;
        }
        Object value(Object owner,String key)throws Exception{for(ModelOps.ValueEdit e:values)if(e.owner==owner&&e.field.getName().equals(key))return e.after;return ModelOps.field(owner,key).get(owner);}
        void change(Object owner,String key,Object v)throws Exception{for(ModelOps.ValueEdit e:values)if(e.owner==owner&&e.field.getName().equals(key)){e.after=v;return;}values.add(new ModelOps.ValueEdit(owner,key,v));}
        void forwardModel()throws Exception{for(ModelOps.ValueEdit e:values)e.set(true);for(Edit e:edits)e.set(true);clearCaches();reparent();}
        void prepareFiles()throws Exception{try{files.apply();for(ImageAsset asset:assets)asset.write();filesReady=true;}catch(Exception e){discardFiles();throw e;}}
        void discardFiles(){for(ImageAsset asset:assets)asset.discard();files.rollback();filesReady=false;}
        void apply()throws Exception {
            if(!sha(Reflect.xml(project)).equals(beforeSha))throw new Exception("Проект изменился после предпросмотра. Экспортируй его заново.");
            try {
                if(!filesReady)prepareFiles();
                forwardModel();
                afterSha=sha(Reflect.xml(project));
            }catch(Exception ex){rollback();throw ex;}
        }
        void reparent()throws Exception{for(Object scene:Reflect.list(project,"getSceneList"))for(Object sprite:Reflect.list(scene,"getSpriteList"))for(Object script:Reflect.list(sprite,"getScriptList"))Reflect.call(script,"setParents");}
        void restoreModel()throws Exception{for(int i=edits.size()-1;i>=0;i--)edits.get(i).set(false);for(int i=values.size()-1;i>=0;i--)values.get(i).set(false);clearCaches();reparent();}
        void rollback()throws Exception{restoreModel();discardFiles();}
        void clearCaches(){for(Object owner:variableOwners)try{
            for(String key:new String[]{"variableCache","listCache"}){java.lang.reflect.Field f=owner.getClass().getDeclaredField(key);f.setAccessible(true);f.set(owner,null);}
        }catch(Exception ignored){}}
        void undo()throws Exception {
            if(afterSha==null||!sha(Reflect.xml(project)).equals(afterSha))throw new Exception("После применения проект уже менялся. Автоотмена недоступна; используй резервную копию.");
            restoreModel(); // Keep files for backups and recovery if saving undo fails.
        }
    }

    Plan prepare(JSONObject patch)throws Exception {
        if(patch.getInt("format")!=1)throw new Exception("Нужен формат 1");
        if(patch.toString().getBytes("UTF-8").length>1024*1024)throw new Exception("PATCH_TOO_LARGE: JSON больше 1 МиБ");
        if(patch.has("project_id")&&!PatchJournal.identity(project).equals(required(patch,"project_id")))throw new Exception("WRONG_PROJECT: это другой проект, даже если название совпадает");
        String current=sha(Reflect.xml(project));
        if(!required(patch,"project").equals(name(project)))throw new Exception("Патч для другого проекта");
        if(!required(patch,"base_sha256").equals(current))throw new Exception("Патч устарел. Снова отправь состояние проекта в чат.");
        required(patch,"id");
        Plan plan=new Plan(project,current,required(patch,"title"));plan.projectId=PatchJournal.identity(project);plan.id=required(patch,"id");plan.requestId=patch.optString("request_id","");formulaPlan=plan;
        JSONArray ops=patch.getJSONArray("operations");
        if(ops.length()<1||ops.length()>200)throw new Exception("Допустимо 1–200 операций");
        for(int i=0;i<ops.length();i++){
            location="operations["+i+"]";
            try{JSONObject op=ops.getJSONObject(i);String kind=required(op,"op");
            if(ModelOps.operation(this,plan,op,kind)){plan.structuralOperation=i+1;continue;}
            Object scene=ModelOps.find(plan,plan.stage(Reflect.list(project,"getSceneList")),required(op,"scene"));formulaScene=scene;
            List<Object> sprites=plan.stage(Reflect.list(scene,"getSpriteList"));
            String spriteName=required(op,"sprite");
            if(kind.equals("sprite.create")){
                for(Object s:sprites)if(ModelOps.label(plan,s).equals(spriteName))throw new Exception("Объект уже есть: "+spriteName);
                Object s=Reflect.make(C+"Sprite",spriteName);sprites.add(s);
                if(op.has("image"))addLook(op.getJSONObject("image"),spriteName,s,scene,plan);
                plan.summary.append("Создать объект: ").append(spriteName).append("\n");continue;
            }
            Object sprite=ModelOps.find(plan,sprites,spriteName);formulaSprite=sprite;
            if(kind.equals("look.add")){addLook(op.getJSONObject("image"),required(op,"name"),sprite,scene,plan);continue;}
            if(kind.equals("variable.create")||kind.equals("list.create")){
                boolean isList=kind.equals("list.create");
                String variable=required(op,"name");
                Object owner=op.optString("scope","sprite").equals("project")?project:sprite;
                if(op.has("scope")&&!op.getString("scope").equals("project")&&!op.getString("scope").equals("sprite"))throw new Exception("scope должен быть project или sprite");
                plan.variableOwners.add(owner);
                List<Object> vars=plan.stage(Reflect.list(owner,isList?"getUserLists":"getUserVariables"));
                for(Object v:vars)if(name(v).equals(variable))throw new Exception("Переменная уже есть: "+variable);
                Object val=op.opt("value");
                if(isList){List<Object> items=new ArrayList<Object>();if(val!=null&&val!=JSONObject.NULL){if(!(val instanceof JSONArray))throw new Exception("Список: требуется массив value");JSONArray a=(JSONArray)val;for(int n=0;n<a.length();n++){Object x=a.get(n);if(!(x instanceof Number||x instanceof String))throw new Exception("Элемент списка: число или строка");items.add(x);}}vars.add(Reflect.make(F+"UserList",variable,items));}
                else{if(val==null||val==JSONObject.NULL)val=Double.valueOf(0);if(!(val instanceof Number||val instanceof String))throw new Exception("Значение переменной: строка или число");vars.add(Reflect.make(F+"UserVariable",variable,val));}
                plan.summary.append("Создать переменную: ").append(variable).append("\n");continue;
            }
            List<Object> scripts=plan.stage(Reflect.list(sprite,"getScriptList"));
            if(kind.equals("script.add")){
                Object script=script(op.getJSONObject("script"),sprite,plan);
                scripts.add(script);plan.touchedScripts.add(script);
                plan.summary.append("Добавить скрипт: ").append(spriteName).append(" / ").append(script.getClass().getSimpleName()).append("\n");
            }else if(kind.equals("script.delete")||kind.equals("script.update")||kind.equals("script.splice")||kind.equals("script.append")||kind.equals("script.replace")){
                String id=required(op,"script_id");Object target=null;
                for(Object s:scripts)if(String.valueOf(Reflect.call(s,"getScriptId")).equals(id)){if(target!=null)throw new Exception("Неоднозначный script_id: "+id);target=s;}
                if(target==null)throw new Exception("Скрипт не найден: "+id);
                if(kind.equals("script.delete")){scripts.remove(target);plan.summary.append("Удалить скрипт: ").append(id).append("\n");continue;}
                List<Object> bricks=plan.stage(Reflect.list(target,"getBrickList"));
                if(kind.equals("script.update")&&op.has("script")){
                    JSONObject event=new JSONObject(op.getJSONObject("script").toString());event.put("bricks",new JSONArray());Object replacement=script(event,sprite,plan);
                    ModelOps.field(replacement,"scriptId").set(replacement,ModelOps.field(target,"scriptId").get(target));
                    for(String key:new String[]{"commentedOut","posX","posY"}){java.lang.reflect.Field f=ModelOps.field(target,key);ModelOps.field(replacement,key).set(replacement,f.get(target));}
                    Reflect.list(replacement,"getBrickList").addAll(bricks);scripts.set(scripts.indexOf(target),replacement);target=replacement;bricks=plan.stage(Reflect.list(target,"getBrickList"));
                }
                if(kind.equals("script.replace")||(kind.equals("script.update")&&op.has("bricks")))bricks.clear();
                int insert=bricks.size();if(kind.equals("script.splice")){insert=op.getInt("index");int remove=op.getInt("delete_count");if(insert<0||remove<0||insert>bricks.size()||remove>bricks.size()-insert)throw new Exception("Неверный диапазон script.splice");for(int n=0;n<remove;n++)bricks.remove(insert);}
                JSONArray arr=op.optJSONArray("bricks");if(arr==null&&!kind.equals("script.update"))throw new Exception("Требуется bricks");
                if(arr!=null)for(int j=0;j<arr.length();j++){location="operations["+i+"].bricks["+j+"]";bricks.add(insert++,brick(arr.getJSONObject(j),sprite,plan,0));}
                plan.touchedScripts.add(target);
                plan.summary.append(kind.equals("script.append")?"Дополнить скрипт: ":"Заменить тело скрипта: ").append(spriteName).append(" / ").append(id).append("\n");
            }else throw new Exception("Неизвестная операция: "+kind);
            }catch(Exception ex){throw new Exception("Операция "+(i+1)+", "+location+": "+(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage()),ex);}
        }
        try{ReferenceCheck.verify(plan);}catch(Exception ex){throw new Exception("Проверка итоговых ссылок после операции "+plan.structuralOperation+": "+ex.getMessage(),ex);}
        plan.summary.append("\nНовых блоков: ").append(count);
        return plan;
    }

    void addLook(JSONObject image,String lookName,Object sprite,Object scene,Plan plan)throws Exception {
        List<Object> looks=plan.stage(Reflect.list(sprite,"getLookList"));
        for(Object look:looks)if(name(look).equals(lookName))throw new Exception("Образ уже есть: "+lookName);
        if(plan.assets.size()>=16)throw new Exception("Не больше 16 изображений на патч");
        Object sceneProbe=Reflect.make(C+"Scene",ModelOps.label(plan,scene),project);
        ImageAsset asset=new ImageAsset(image,sceneProbe);
        if(plan.assetBytes+asset.bytes.length>640*1024)throw new Exception("Суммарный размер изображений больше 640 КиБ");
        plan.assetBytes+=asset.bytes.length;plan.assets.add(asset);
        looks.add(Reflect.make("org.catrobat.catroid.common.LookData",lookName,asset.target));
        plan.summary.append("Добавить PNG: ").append(name(sprite)).append(" / ").append(lookName).append(" (").append(asset.width).append("×").append(asset.height).append(", ").append(asset.bytes.length).append(" байт)\n");
    }

    Object script(JSONObject spec,Object sprite,Plan plan)throws Exception {
        String event=spec.getString("event");Object script;
        if(event.equals("start"))script=Reflect.make(C+"StartScript");
        else if(event.equals("tap"))script=Reflect.make(C+"WhenScript");
        else if(event.equals("broadcast"))script=Reflect.make(C+"BroadcastScript",required(spec,"message"));
        else if(event.equals("condition"))script=Reflect.make(C+"WhenConditionScript",formula(spec.get("condition"),0));
        else if(event.equals("cloned"))script=Reflect.make(C+"WhenClonedScript");
        else if(event.equals("before_update"))script=Reflect.make(C+"BeforeUpdateScript");
        else if(event.equals("after_update"))script=Reflect.make(C+"AfterUpdateScript");
        else throw new Exception("Неизвестное событие: "+event);
        List<Object> list=Reflect.list(script,"getBrickList");JSONArray arr=spec.getJSONArray("bricks");
        for(int i=0;i<arr.length();i++)list.add(brick(arr.getJSONObject(i),sprite,plan,0));
        return script;
    }

    Object brick(JSONObject spec,Object sprite,Plan plan,int depth)throws Exception {
        if(depth>24||++count>LIMIT)throw new Exception("Слишком много блоков или уровней вложенности");
        String cls=required(spec,"class");if(!allowed.contains(cls))throw new Exception("Этот блок пока не поддерживается: "+cls);
        Object brick=Reflect.make(B+cls);
        JSONObject fields=spec.optJSONObject("formulas");
        if(catalogue!=null){JSONArray must=catalogue.byClass.get(cls).optJSONArray("required_formulas");if(must!=null)for(int i=0;i<must.length();i++)if(fields==null||!fields.has(must.getString(i)))throw new Exception(cls+": обязательная формула "+must.getString(i));}
        if(fields!=null&&fields.length()>0){
            Map<?,?> known=(Map<?,?>)Reflect.call(brick,"getAllFormulaFieldsWithFormulas");
            Iterator<String> it=fields.keys();while(it.hasNext()){
                String key=it.next();String parentFormulaLocation=location;location+=".formulas."+key;Object f=Reflect.enumeration(B+"Brick$BrickField",key);
                if(!known.containsKey(f))throw new Exception(cls+": нет поля "+key);
                Reflect.call(brick,"setFormulaWithBrickField",f,formula(fields.get(key),0));location=parentFormulaLocation;
            }
        }
        if(spec.has("variable")){
            String vn=spec.getString("variable");Object v=null;
            for(Object x:plan.stage(Reflect.list(project,"getUserVariables")))if(name(x).equals(vn))v=x;
            for(Object x:plan.stage(Reflect.list(sprite,"getUserVariables")))if(name(x).equals(vn))v=x;
            if(v==null)throw new Exception("Нет переменной "+vn);Reflect.call(brick,"setUserVariable",v);
        }
        if(spec.has("list")){String ln=spec.getString("list");Object list=null;for(Object x:plan.stage(Reflect.list(project,"getUserLists")))if(name(x).equals(ln))list=x;for(Object x:plan.stage(Reflect.list(sprite,"getUserLists")))if(name(x).equals(ln))list=x;if(list==null)throw new Exception("Нет списка "+ln);Reflect.call(brick,"setUserList",list);}
        if(Catalogue.method(brick,"setUserList")&&!spec.has("list"))throw new Exception(cls+": требуется list");
        if(cls.equals("SceneTransitionBrick")||cls.equals("SceneStartBrick")){
            String sceneName=required(spec,"scene");ModelOps.find(plan,plan.stage(Reflect.list(project,"getSceneList")),sceneName);
            Reflect.call(brick,cls.equals("SceneTransitionBrick")?"setSceneForTransition":"setSceneToStart",sceneName);
        }
        if(spec.has("sprite")){if(!cls.equals("CloneBrick"))throw new Exception(cls+": ссылка sprite не поддерживается адаптером");Object target=ModelOps.find(plan,plan.stage(Reflect.list(formulaScene,"getSpriteList")),required(spec,"sprite"));Reflect.call(brick,"onItemSelected",Integer.valueOf(0),target);}
        if(spec.has("message"))Reflect.call(brick,"setBroadcastMessage",spec.getString("message"));
        if(spec.has("look"))Reflect.call(brick,"setLook",named(plan.stage(Reflect.list(sprite,"getLookList")),spec.getString("look")));
        if(spec.has("sound"))Reflect.call(brick,"setSound",named(Reflect.list(sprite,"getSoundList"),spec.getString("sound")));
        JSONObject props=spec.optJSONObject("properties");
        if(props!=null){Iterator<String> it=props.keys();while(it.hasNext()){
            String key=it.next();if(catalogue!=null&&!catalogue.property(cls,key))throw new Exception(cls+": свойство отсутствует в каталоге: "+key);Object value=props.get(key);java.lang.reflect.Field field=null;
            for(Class<?> t=brick.getClass();t!=null&&t.getName().startsWith(B);t=t.getSuperclass()){
                try{field=t.getDeclaredField(key);break;}catch(NoSuchFieldException ignore){}
            }
            if(field==null||java.lang.reflect.Modifier.isStatic(field.getModifiers())||java.lang.reflect.Modifier.isFinal(field.getModifiers())||java.lang.reflect.Modifier.isTransient(field.getModifiers()))throw new Exception("Нельзя задать свойство "+key);
            Class<?> t=field.getType();Object converted;
            if(t==String.class&&value instanceof String)converted=value;
            else if(t==int.class&&value instanceof Number){double n=((Number)value).doubleValue();if(n!=Math.rint(n)||n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw new Exception("Ожидалось целое "+key);converted=Integer.valueOf((int)n);}
            else if(t==float.class&&value instanceof Number){float n=((Number)value).floatValue();if(Float.isNaN(n)||Float.isInfinite(n))throw new Exception("Неверное число "+key);converted=Float.valueOf(n);}
            else if(t==double.class&&value instanceof Number){double n=((Number)value).doubleValue();if(Double.isNaN(n)||Double.isInfinite(n))throw new Exception("Неверное число "+key);converted=Double.valueOf(n);}
            else if(t==boolean.class&&value instanceof Boolean)converted=value;
            else if(t.isEnum()&&value instanceof String)converted=Reflect.enumeration(t.getName(),(String)value);
            else throw new Exception("Неверный тип свойства "+key);
            field.setAccessible(true);field.set(brick,converted);
        }}
        if(cls.equals("RuntimeAudioBrick"))AudioSupport.validate(project,spec);
        String parentLocation=location;
        JSONArray children=spec.optJSONArray("children");
        if(children!=null){List<Object> list=Reflect.list(brick,"getNestedBricks");for(int i=0;i<children.length();i++){location=parentLocation+".children["+i+"]";list.add(brick(children.getJSONObject(i),sprite,plan,depth+1));}}
        JSONArray other=spec.optJSONArray("else");
        if(other!=null){List<Object> list=Reflect.list(brick,"getSecondaryNestedBricks");if(list==null)throw new Exception(cls+": нет ветки иначе");for(int i=0;i<other.length();i++){location=parentLocation+".else["+i+"]";list.add(brick(other.getJSONObject(i),sprite,plan,depth+1));}}
        // Reject unbound selector bricks instead of silently creating broken scripts.
        if(Catalogue.method(brick,"setUserVariable")&&!spec.has("variable"))throw new Exception(cls+": требуется variable");
        location=parentLocation;return brick;
    }

    Object formula(Object value,int depth)throws Exception {return Reflect.make(F+"Formula",element(value,depth));}
    Object element(Object value,int depth)throws Exception {
        if(depth>32)throw new Exception("Слишком глубокая формула");
        String type,text;JSONObject node=value instanceof JSONObject?(JSONObject)value:null;
        if(node!=null){type=node.getString("type");text=node.getString("value");validateNode(node,type,text);}
        else if(value instanceof Number){type="NUMBER";text=String.valueOf(value);}
        else if(value instanceof String){type="STRING";text=(String)value;}
        else throw new Exception("Формула: число, строка или дерево выражения");
        if(type.equals("NUMBER")){double n=Double.parseDouble(text);if(Double.isInfinite(n)||Double.isNaN(n))throw new Exception("Неверное число");}
        if(type.equals("FUNCTION"))Reflect.enumeration(F+"Functions",text);
        if(type.equals("OPERATOR"))Reflect.enumeration(F+"Operators",text);
        if(type.equals("SENSOR"))Reflect.enumeration(F+"Sensors",text);
        Object e=Reflect.make(F+"FormulaElement",Reflect.enumeration(F+"FormulaElement$ElementType",type),text,null);
        if(node!=null){
            if(node.has("left"))Reflect.call(e,"setLeftChild",element(node.get("left"),depth+1));
            if(node.has("right"))Reflect.call(e,"setRightChild",element(node.get("right"),depth+1));
            JSONArray extra=node.optJSONArray("additional");if(extra!=null)for(int i=0;i<extra.length();i++)Reflect.call(e,"addAdditionalChild",element(extra.get(i),depth+1));
        }
        return e;
    }

    void validateNode(JSONObject node,String type,String text)throws Exception {
        if(!Arrays.asList("NUMBER","STRING","USER_VARIABLE","USER_LIST","OPERATOR","FUNCTION","SENSOR","COLLISION_FORMULA","BRACKET").contains(type))throw new Exception("Неподдерживаемый тип формулы: "+type);
        boolean left=node.has("left"),right=node.has("right");JSONArray extra=node.optJSONArray("additional");int more=extra==null?0:extra.length();
        if(type.equals("FUNCTION")){
            Reflect.enumeration(F+"Functions",text);if(catalogue!=null){Integer n=catalogue.arities.get(text);if(n==null)throw new Exception("Функция не подтверждена каталогом: "+text);if(left!=(n>0)||right!=(n>1)||more!=Math.max(0,n-2))throw new Exception("Функция "+text+": ожидается "+n+" аргументов: left, right, additional");}
        }else if(type.equals("OPERATOR")){
            Reflect.enumeration(F+"Operators",text);if(more>0||!right||(!left&&!text.equals("MINUS")&&!text.equals("LOGICAL_NOT"))||(left&&text.equals("LOGICAL_NOT")))throw new Exception("Неверные аргументы оператора "+text);
        }else if(type.equals("BRACKET")){if(left||!right||more>0)throw new Exception("BRACKET требует только right");}
        else if(left||right||more>0)throw new Exception(type+": дочерние аргументы запрещены");
        if(type.equals("SENSOR"))Reflect.enumeration(F+"Sensors",text);
        if(type.equals("USER_VARIABLE")||type.equals("USER_LIST")){
            if(formulaPlan!=null&&formulaSprite!=null){String getter=type.equals("USER_LIST")?"getUserLists":"getUserVariables";boolean found=false;for(Object owner:new Object[]{project,formulaSprite})for(Object v:formulaPlan.stage(Reflect.list(owner,getter)))if(name(v).equals(text))found=true;if(!found)throw new Exception("Не найдена "+type+": "+text);}
        }
        if(type.equals("COLLISION_FORMULA")&&formulaPlan!=null&&formulaScene!=null)ModelOps.find(formulaPlan,formulaPlan.stage(Reflect.list(formulaScene,"getSpriteList")),text);
        if(type.equals("FUNCTION")&&text.equals("TOUCHES_OBJECT_BY_NAME")&&node.optJSONObject("left")!=null&&node.getJSONObject("left").optString("type").equals("STRING")&&formulaPlan!=null&&formulaScene!=null)ModelOps.find(formulaPlan,formulaPlan.stage(Reflect.list(formulaScene,"getSpriteList")),node.getJSONObject("left").getString("value"));
    }
    static JSONArray lists(Object owner)throws Exception {JSONArray a=new JSONArray();for(Object x:Reflect.list(owner,"getUserLists")){JSONArray values=new JSONArray();for(Object v:(List<?>)Reflect.call(x,"getValue"))values.put(v);a.put(new JSONObject().put("name",name(x)).put("value",values));}return a;}

    JSONObject context(String xml)throws Exception {
        JSONObject out=new JSONObject();out.put("format",1);out.put("plugin","NewCatroid Assistant 0.7.0");
        out.put("project",name(project));out.put("project_id",PatchJournal.identity(project));out.put("base_sha256",sha(xml));
        JSONArray scenes=new JSONArray();out.put("scenes",scenes);
        for(Object scene:Reflect.list(project,"getSceneList")){
            JSONObject sc=new JSONObject();sc.put("name",name(scene));Object sceneId=ModelOps.field(scene,"sceneId").get(scene);sc.put("id",sceneId==null?JSONObject.NULL:sceneId.toString());scenes.put(sc);JSONArray sprites=new JSONArray();sc.put("sprites",sprites);
            for(Object sprite:Reflect.list(scene,"getSpriteList")){
                JSONObject sp=new JSONObject();sp.put("name",name(sprite));sprites.put(sp);
                sp.put("variables",variables(sprite));sp.put("lists",lists(sprite));sp.put("looks",names(Reflect.list(sprite,"getLookList")));sp.put("sounds",names(Reflect.list(sprite,"getSoundList")));
                JSONArray scripts=new JSONArray();sp.put("scripts",scripts);
                for(Object s:Reflect.list(sprite,"getScriptList")){
                    JSONObject js=new JSONObject();js.put("id",String.valueOf(Reflect.call(s,"getScriptId")));js.put("class",s.getClass().getSimpleName());
                    js.put("blocks",Reflect.list(s,"getBrickList").size());scripts.put(js);
                }
            }
        }
        AudioSupport.export(project,out);
        out.put("variables",variables(project));out.put("lists",lists(project));out.put("capabilities",new JSONObject().put("project_identity",true).put("partial_export",1).put("strict_request_id",true).put("patch_results",true).put("catalogue_version",2));out.put("instructions","Read code.xml for complete block trees. Read PROTOCOL.md and supported-blocks.json before generating a patch. Use exact project, scene names, script IDs and base_sha256. Return JSON format 1. No direct connection to ChatGPT is provided.");
        return out;
    }
    static JSONArray names(List<Object> list)throws Exception {JSONArray a=new JSONArray();for(Object x:list)a.put(name(x));return a;}
    static JSONArray variables(Object owner)throws Exception {
        JSONArray a=new JSONArray();for(Object x:Reflect.list(owner,"getUserVariables")){JSONObject v=new JSONObject();v.put("name",name(x));Object val=Reflect.call(x,"getValue");v.put("value",val==null?JSONObject.NULL:String.valueOf(val));a.put(v);}return a;
    }
}
