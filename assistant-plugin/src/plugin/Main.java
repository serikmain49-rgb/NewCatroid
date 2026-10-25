package plugin;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.catrobat.catroid.plugins.PluginEntry;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.*;

/** NewCatroid Assistant: explicit context export and reviewed patch import. */
public class Main implements PluginEntry,Application.ActivityLifecycleCallbacks {
    private static Main instance;
    private Application app;
    private Handler handler;
    private Set<String> allowed;
    private Catalogue catalogue;
    private PatchJournal journal;
    private final Map<Activity,View> buttons=new WeakHashMap<Activity,View>();
    private Bridge.Plan last;
    private File pluginDir;
    private boolean busy;
    private boolean remoteBusy;
    private String remoteStatus="Не подключён";
    private long remoteGeneration;
    private android.content.SharedPreferences prefs;

    public void onLoad(final Context context) {
        if(instance!=null)return;instance=this;
        app=(Application)context.getApplicationContext();
        Reflect.loader=app.getClassLoader();handler=new Handler(Looper.getMainLooper());
        prefs=app.getSharedPreferences("nc-assistant-remote",Context.MODE_PRIVATE);
        handler.postDelayed(pollRemote,3000);
        pluginDir=new File(app.getFilesDir(),"plugins/dev.vanya.newcatroid.assistant");
        handler.post(new Runnable(){public void run(){
            try{
                catalogue=new Catalogue(pluginDir,app.getPackageName(),app.getPackageManager().getPackageInfo(app.getPackageName(),0).versionName,hostApks());
                allowed=catalogue.allowed();
                journal=new PatchJournal(new File(app.getFilesDir(),"newcatroid-assistant-journal.json"));
                app.registerActivityLifecycleCallbacks(Main.this);
                // The first activity may already be resumed when the plugin is loaded.
                Object overlay=Reflect.call(Reflect.type("org.catrobat.catroid.plugins.PluginOverlayManager"),"getInstance");
                Field f=overlay.getClass().getDeclaredField("currentActivity");f.setAccessible(true);
                Activity a=(Activity)f.get(overlay);if(a!=null)attach(a);
            }catch(Exception e){toast("Помощник: "+message(e));}
        }});
    }

    private String[] hostApks(){
        ArrayList<String> paths=new ArrayList<String>();paths.add(app.getApplicationInfo().sourceDir);
        try{Object splits=app.getApplicationInfo().getClass().getField("splitSourceDirs").get(app.getApplicationInfo());if(splits instanceof String[])paths.addAll(Arrays.asList((String[])splits));}catch(Exception ignored){}
        return paths.toArray(new String[paths.size()]);
    }
    private void attach(final Activity a){
        String n=a.getClass().getSimpleName();
        if(buttons.containsKey(a)||n.contains("Stage")||n.contains("GameActivity")||n.contains("EditorActivity"))return;
        View root=a.getWindow().getDecorView();if(!(root instanceof ViewGroup))return;
        CubeButton b=new CubeButton(a);
        float density=a.getResources().getDisplayMetrics().density;
        int pad=(int)(8*density),size=(int)(48*density);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(size,size,Gravity.BOTTOM|Gravity.LEFT);
        lp.setMargins(pad,pad,pad,(int)(40*density));
        ((ViewGroup)root).addView(b,lp);buttons.put(a,b);
        b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){panel(a);}});
    }
    private Object project()throws Exception {
        Object listener=Reflect.call(Reflect.type("org.catrobat.catroid.stage.StageActivity"),"getActiveStageListener");
        if(listener!=null&&!Boolean.TRUE.equals(Reflect.call(listener,"isFinished")))throw new Exception("Сначала полностью останови запущенный проект и вернись в редактор");
        Object p=Reflect.call(Reflect.manager(),"getCurrentProject");if(p==null)throw new Exception("Сначала открой проект в редакторе");return p;
    }
    private void panel(final Activity a){
        if(busy){toast("Дождись завершения операции");return;}
        String title="Помощник 0.5";try{title+=" · "+Bridge.name(project());}catch(Exception ignore){}
        new AlertDialog.Builder(a).setTitle(title)
            .setItems(new String[]{"1. Отправить проект в чат","2. Вставить изменения","3. Открыть JSON-файл изменений","4. Отменить последнее применение","Проверка: добавить счётчик","Резервные копии кода","Как это работает","Bridge: настройки","Отправить состояние на сервер","Проверить изменения на сервере"},new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface d,int item){
                    if(item==0)exportContext(a);else if(item==1)input(a,"");else if(item==2)pick(a);
                    else if(item==3)undo(a);else if(item==4)demo(a);else if(item==5)backups(a);else if(item==7)remoteSettings(a);else if(item==8)uploadRemote(a);else if(item==9)fetchRemote(a);else info(a,"Как работать", "Открой игру в редакторе и останови её запуск.\n\n1. Отправь архив состояния в этот чат и напиши, что сделать.\n2. Я подготовлю JSON изменений.\n3. Вставь его сюда или выбери скачанный JSON-файл.\n4. Проверь список изменений и примени.\n5. Перейди на другой экран проекта и вернись, чтобы обновить список блоков.\n\nДоступен обмен файлами и сервер Termux. Включи соединение в настройках сервера. Для доступа из чата сервер нужно подключить отдельно. Автоматического подключения к ChatGPT нет. Подписка ChatGPT и API-ключ не требуются для работы плагина. Можно создавать спрайты с PNG и добавлять образы через патч. Изображения сохраняются только после применения. Копия code.xml сохраняется перед применением.");
                }
            }).setNegativeButton("Закрыть",null).show();
    }
    private void input(final Activity a,String text){
        final EditText edit=new EditText(a);edit.setText(text);edit.setHint("Вставь JSON, который я подготовлю в чате");edit.setMinLines(6);edit.setMaxLines(14);edit.setGravity(Gravity.TOP);edit.setTextSize(13);
        ScrollView scroll=new ScrollView(a);scroll.addView(edit);
        final AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Изменения проекта").setView(scroll)
            .setPositiveButton("Предпросмотр",null).setNeutralButton("Вставить из буфера",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){
            dialog.getButton(-3).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                ClipboardManager cm=(ClipboardManager)app.getSystemService(Context.CLIPBOARD_SERVICE);
                if(cm.hasPrimaryClip())edit.setText(cm.getPrimaryClip().getItemAt(0).coerceToText(a));
            }});
            dialog.getButton(-1).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                try{String raw=edit.getText().toString().trim();if(raw.startsWith("```")){int start=raw.indexOf('\n');int end=raw.lastIndexOf("```");if(start>=0&&end>start)raw=raw.substring(start+1,end).trim();}
                    if(raw.length()>1024*1024)throw new Exception("Файл изменений больше 1 МБ");
                    final Object p=project();final Bridge.Plan plan=preparePatch(p,new JSONObject(raw));
                    dialog.dismiss();infoPreview(a,p,plan);
                }catch(Exception e){info(a,"Не удалось проверить изменения",message(e));}
            }});
        }});dialog.show();
    }
    private void infoPreview(final Activity a,final Object p,final Bridge.Plan plan){
        report(plan,"preview",plan.summary.toString());
        new AlertDialog.Builder(a).setTitle(plan.title).setMessage(plan.summary.toString()+"\n\nПеред применением будет сохранена резервная копия кода.")
            .setPositiveButton("Применить",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                applyReviewed(a,p,plan);
            }}).setNegativeButton("Отмена",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){report(plan,"rejected","Пользователь отменил предпросмотр");}}).setOnCancelListener(new DialogInterface.OnCancelListener(){public void onCancel(DialogInterface d){report(plan,"rejected","Предпросмотр закрыт");}}).show();
    }
    private void applyReviewed(final Activity a,final Object p,final Bridge.Plan plan){
        if(busy){toast("Дождись завершения операции");return;}
        try{
            if(project()!=p)throw new Exception("Открыт другой проект");
            journal.check(p,new JSONObject().put("id",plan.id).put("request_id",plan.requestId).put("base_sha256",plan.beforeSha));
            String before=Reflect.xml(p);if(!Bridge.sha(before).equals(plan.beforeSha))throw new Exception("STALE_PATCH: проект изменился после предпросмотра");
            backup(before,plan.beforeSha);journal.mark(p,plan.id,"applying");busy=true;
            final ProgressDialog progress=new ProgressDialog(a);progress.setMessage("Подготовка ресурсов…");progress.setCancelable(false);progress.show();
            new Thread(new Runnable(){public void run(){
                try{plan.prepareFiles();handler.post(new Runnable(){public void run(){
                    try{
                        if(project()!=p)throw new Exception("Открыт другой проект");
                        journal.checkReceipt(p,plan.requestId,plan.beforeSha);
                        plan.apply();
                        try{save(p);journal.mark(p,plan.id,"applied");}catch(Exception ex){plan.rollback();try{save(p);journal.mark(p,plan.id,"error");}catch(Exception recovery){throw new Exception("Ошибка сохранения: "+message(ex)+". Откат на диск не подтверждён: "+message(recovery)+". Восстанови резервную копию кода.");}throw ex;}
                        report(plan,"applied","code.xml сохранён и проверен");try{reconcile(p);}catch(Exception ui){toast("Обнови экран проекта: "+message(ui));}last=plan;
                        if(prefs.getBoolean("enabled",false))handler.postDelayed(new Runnable(){public void run(){sendSnapshot(null,"");}},1000);
                        info(a,"Изменения сохранены","Готово. Обнови экран проекта. Последнее применение можно отменить до следующих изменений.");
                    }catch(Exception e){plan.discardFiles();report(plan,"error",message(e));info(a,"Не удалось применить",message(e));}
                    finally{busy=false;progress.dismiss();}
                }});}catch(final Exception e){handler.post(new Runnable(){public void run(){busy=false;progress.dismiss();report(plan,"error",message(e));info(a,"Не удалось подготовить ресурсы",message(e));}});}
            }},"NCAssistantAssets").start();
        }catch(Exception e){report(plan,"error",message(e));info(a,"Не удалось применить",message(e));}
    }
    private Bridge.Plan preparePatch(Object p,JSONObject patch)throws Exception {
        try{journal.check(p,patch);return new Bridge(p,catalogue).prepare(patch);}
        catch(Exception e){reportRaw(patch,"error",message(e));throw e;}
    }
    private void report(Bridge.Plan p,String status,String details){
        try{reportRaw(new JSONObject().put("id",p.id).put("project",Bridge.name(p.project)).put("base_sha256",p.beforeSha).put("request_id",p.requestId).put("after_sha256",p.afterSha==null?"":p.afterSha),status,details);}catch(Exception e){toast("Отчёт не сохранён: "+message(e));}
    }
    private void reportRaw(JSONObject patch,String status,String details){
        try{if(patch.optString("id").isEmpty()||patch.optString("project").isEmpty())return;JSONObject event=new JSONObject().put("event_id",UUID.randomUUID().toString()).put("patch_id",patch.optString("id")).put("project",patch.optString("project")).put("base_sha256",patch.optString("base_sha256")).put("request_id",patch.optString("request_id")).put("status",status).put("details",details.substring(0,Math.min(4000,details.length()))).put("after_sha256",patch.optString("after_sha256"));journal.event(event);}catch(Exception e){toast("Не удалось сохранить отчёт bridge: "+message(e));}
    }
    private void flushReports(String base,String token)throws Exception {
        for(int i=0;i<20;i++){JSONObject event=journal.peek();if(event==null)return;try{Remote.call(base,token,"POST","/api/device/result",event);}catch(Exception e){if(message(e).contains("HTTP 404"))return;if(message(e).contains("HTTP 400")||message(e).contains("HTTP 409")){journal.failedEvent(event.getString("event_id"),message(e));continue;}throw e;}journal.ack(event.getString("event_id"));}
    }
    private void reconcile(Object p)throws Exception {
        List<Object> scenes=Reflect.list(p,"getSceneList");Object scene=Reflect.call(Reflect.manager(),"getCurrentlyEditedScene");
        if(!scenes.contains(scene)){scene=scenes.get(0);Reflect.call(Reflect.manager(),"setCurrentlyEditedScene",scene);}
        List<Object> sprites=Reflect.list(scene,"getSpriteList");Object sprite=Reflect.call(Reflect.manager(),"getCurrentSprite");if(!sprites.contains(sprite))Reflect.call(Reflect.manager(),"setCurrentSprite",sprites.get(0));
    }
    private void undo(final Activity a){
        if(last==null){info(a,"Отмена","В этой сессии ещё ничего не применялось.");return;}
        new AlertDialog.Builder(a).setTitle("Отменить «"+last.title+"»?").setPositiveButton("Отменить изменения",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            try{if(project()!=last.project)throw new Exception("Открой проект, в котором применялись изменения");
                Bridge.Plan plan=last;plan.undo();
                try{save(plan.project);}catch(Exception e){plan.forwardModel();throw e;}
                last=null;if(prefs.getBoolean("enabled",false))uploadRemote(null);info(a,"Отменено","Обнови экран проекта, перейдя на другой экран и обратно.");
            }catch(Exception e){info(a,"Отмена не выполнена",message(e));}
        }}).setNegativeButton("Назад",null).show();
    }
    private void demo(Activity a){
        try{
            Object p=project();Object scene=Reflect.call(Reflect.manager(),"getCurrentlyEditedScene");
            if(scene==null)scene=Reflect.list(p,"getSceneList").get(0);
            Object sprite=Reflect.call(Reflect.manager(),"getCurrentSprite");
            if(sprite==null||!Reflect.list(scene,"getSpriteList").contains(sprite))sprite=Reflect.list(scene,"getSpriteList").get(0);
            String var="ИИ_счётчик_"+System.currentTimeMillis();
            JSONObject base=new JSONObject().put("scene",Bridge.name(scene)).put("sprite",Bridge.name(sprite));
            JSONObject create=new JSONObject(base.toString()).put("op","variable.create").put("name",var).put("value",0);
            JSONObject set=new JSONObject().put("class","SetVariableBrick").put("variable",var).put("formulas",new JSONObject().put("VARIABLE",0));
            JSONObject show=new JSONObject().put("class","ShowTextBrick").put("variable",var).put("formulas",new JSONObject().put("X_POSITION",0).put("Y_POSITION",0));
            JSONObject change=new JSONObject().put("class","ChangeVariableBrick").put("variable",var).put("formulas",new JSONObject().put("VARIABLE_CHANGE",1));
            JSONObject wait=new JSONObject().put("class","WaitBrick").put("formulas",new JSONObject().put("TIME_TO_WAIT_IN_SECONDS",1));
            JSONObject loop=new JSONObject().put("class","ForeverBrick").put("children",new JSONArray().put(change).put(wait));
            JSONObject script=new JSONObject().put("event","start").put("bricks",new JSONArray().put(set).put(show).put(loop));
            JSONObject add=new JSONObject(base.toString()).put("op","script.add").put("script",script);
            JSONObject patch=new JSONObject().put("format",1).put("project",Bridge.name(p)).put("id","demo-"+System.currentTimeMillis()).put("title","Проверка: счётчик раз в секунду").put("base_sha256",Bridge.sha(Reflect.xml(p))).put("operations",new JSONArray().put(create).put(add));
            infoPreview(a,p,new Bridge(p,catalogue).prepare(patch));
        }catch(Exception e){info(a,"Проверка недоступна",message(e));}
    }
    private void backups(final Activity a){
        File dir=new File(app.getFilesDir(),"newcatroid-assistant-backups");final File[] files=dir.listFiles();
        if(files==null||files.length==0){info(a,"Копии кода","Копии появятся после первого применения изменений.");return;}
        Arrays.sort(files,Collections.reverseOrder());String[] names=new String[files.length];for(int i=0;i<files.length;i++)names[i]=files[i].getName();
        new AlertDialog.Builder(a).setTitle("Отправить копию code.xml").setItems(names,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){try{
            Uri uri=(Uri)Reflect.call(Reflect.type("androidx.core.content.FileProvider"),"getUriForFile",a,a.getPackageName()+".fileProvider",files[which]);
            Intent send=new Intent(Intent.ACTION_SEND).setType("application/xml").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(send,"Отправить резервный код"));
        }catch(Exception e){info(a,"Ошибка",message(e));}}}).setNegativeButton("Закрыть",null).show();
    }
    private void save(Object p)throws Exception {
        String intended=Reflect.xml(p);Reflect.call(Reflect.serializer(),"saveProject",p);
        File directory=(File)Reflect.call(p,"getDirectory");
        String disk=read(new File(directory,"code.xml"),64*1024*1024);
        // The serializer adds a declaration; the project body must match exactly.
        String normalized=disk.replaceFirst("^\\s*<\\?xml[^>]*>\\s*","");
        String target=intended.replaceFirst("^\\s*<\\?xml[^>]*>\\s*","");
        if(!normalized.trim().equals(target.trim()))throw new IOException("Не удалось подтвердить сохранение code.xml");
    }
    private File backup(String xml,String sha)throws Exception {
        File folder=new File(app.getFilesDir(),"newcatroid-assistant-backups");if(!folder.exists()&&!folder.mkdirs())throw new IOException("Нет места для резервной копии");
        File file=new File(folder,System.currentTimeMillis()+"-"+sha.substring(0,12)+".xml");write(file,xml);return file;
    }
    private void exportContext(final Activity a){
        try{
            final Object p=project();final String xml=Reflect.xml(p);
            final JSONObject state=new Bridge(p,catalogue).context(xml);String rid=UUID.randomUUID().toString();state.put("request_id",rid);journal.receipt(p,rid,Bridge.sha(xml));
            busy=true;
            new Thread(new Runnable(){public void run(){
                try{
                    File folder=new File(app.getCacheDir(),"newcatroid-assistant");folder.mkdirs();
                    final File zip=new File(folder,"NewCatroid-context-"+System.currentTimeMillis()+".zip");
                    ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip));
                    try{entry(out,"project.json",state.toString(2));entry(out,"code.xml",xml);
                        entry(out,"supported-blocks.json",catalogue.blocks.toString(2));
                        entry(out,"PROTOCOL.md",read(new File(pluginDir,"PROTOCOL.md"),2*1024*1024));
                    }finally{out.close();}
                    handler.post(new Runnable(){public void run(){busy=false;
                        try{
                            Uri uri=(Uri)Reflect.call(Reflect.type("androidx.core.content.FileProvider"),"getUriForFile",a,a.getPackageName()+".fileProvider",zip);
                            Intent send=new Intent(Intent.ACTION_SEND);send.setType("application/zip");send.putExtra(Intent.EXTRA_STREAM,uri);send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            send.putExtra(Intent.EXTRA_TEXT,"Состояние моего проекта NewCatroid. Прочитай project.json, code.xml, PROTOCOL.md и supported-blocks.json. Помоги сделать игру и подготовь JSON-патч для плагина.");
                            a.startActivity(Intent.createChooser(send,"Отправить состояние проекта"));
                        }catch(Exception e){info(a,"Архив создан",zip.getAbsolutePath()+"\n\nПоделиться не удалось: "+message(e));}
                    }});
                }catch(final Exception e){handler.post(new Runnable(){public void run(){busy=false;info(a,"Ошибка экспорта",message(e));}});}
            }},"NewCatroidAssistantExport").start();
        }catch(Exception e){info(a,"Не удалось экспортировать",message(e));}
    }

    private final Runnable pollRemote=new Runnable(){public void run(){
        if(prefs!=null&&prefs.getBoolean("enabled",false)&&!remoteBusy&&!busy){
            final long generation=remoteGeneration;
            final String base=prefs.getString("url",""),token=prefs.getString("token","");
            remoteBusy=true;
            new Thread(new Runnable(){public void run(){try{
                flushReports(base,token);
                    final JSONObject result=Remote.call(base,token,"GET","/api/device/poll",null);
                handler.post(new Runnable(){public void run(){remoteBusy=false;
                    if(generation!=remoteGeneration||!prefs.getBoolean("enabled",false))return;
                    remoteStatus="Сервер доступен";
                    String request=result.optString("request_id","");
                    if(!request.isEmpty())sendSnapshot(null,request);
                }});
            }catch(final Exception e){handler.post(new Runnable(){public void run(){remoteBusy=false;remoteStatus=message(e);}});}}},"NCAssistantPoll").start();
        }
        handler.postDelayed(this,4000);
    }};
    private void remoteSettings(final Activity a){
        LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,12,24,12);
        TextView status=new TextView(a);status.setText(remoteStatus+"\nСервер получает код и переменные открытого проекта. Запросы обслуживаются только при остановленной игре. Скриншоты и ресурсы не передаются.");box.addView(status);
        final EditText url=new EditText(a);url.setSingleLine(true);url.setHint("http://127.0.0.1:8765");url.setText(prefs.getString("url","http://127.0.0.1:8765"));box.addView(url);
        final EditText token=new EditText(a);token.setSingleLine(true);token.setHint("Ключ устройства bridge");token.setInputType(129);token.setText(prefs.getString("token",""));box.addView(token);
        final CheckBox enabled=new CheckBox(a);enabled.setText("Разрешить запросы состояния с сервера");enabled.setChecked(prefs.getBoolean("enabled",false));box.addView(enabled);
        new AlertDialog.Builder(a).setTitle("Подключение к bridge").setView(box).setPositiveButton("Сохранить",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{
            String base=Remote.validate(url.getText().toString().trim()),key=token.getText().toString().trim();
            if(enabled.isChecked()&&key.length()<24)throw new Exception("Скопируй ключ устройства из Termux");
            remoteGeneration++;prefs.edit().putString("url",base).putString("token",key).putBoolean("enabled",enabled.isChecked()).commit();remoteStatus=enabled.isChecked()?"Ожидание соединения":"Отключён";toast(remoteStatus);
        }catch(Exception e){info(a,"Настройки не сохранены",message(e));}}}).setNegativeButton("Отмена",null).show();
    }
    private void uploadRemote(Activity a){sendSnapshot(a,"");}
    private void sendSnapshot(final Activity a,final String request){
        if(busy){if(a!=null)toast("Дождись завершения применения");return;}
        if(remoteBusy){if(a!=null)toast("Соединение занято, повтори через несколько секунд");return;}
        if(!prefs.getBoolean("enabled",false)){if(a!=null)remoteSettings(a);return;}
        final JSONObject payload=new JSONObject();
        try{
            Object p=project();String xml=Reflect.xml(p);String rid=request.isEmpty()?UUID.randomUUID().toString():request;journal.receipt(p,rid,Bridge.sha(xml));
            payload.put("project",new Bridge(p,catalogue).context(xml).put("request_id",rid)).put("code_xml",xml)
                .put("protocol",read(new File(pluginDir,"PROTOCOL.md"),2*1024*1024))
                .put("supported_blocks",catalogue.blocks)
                .put("request_id",rid);
        }catch(Exception e){try{payload.put("request_id",request).put("error",message(e));}catch(Exception ignored){}}
        final String base=prefs.getString("url",""),token=prefs.getString("token","");remoteBusy=true;
        new Thread(new Runnable(){public void run(){try{
            flushReports(base,token);Remote.call(base,token,"POST","/api/device/state",payload);
            handler.post(new Runnable(){public void run(){remoteBusy=false;remoteStatus=payload.has("error")?payload.optString("error"):"Состояние отправлено";if(a!=null)info(a,"Сервер",remoteStatus);}});
        }catch(final Exception e){handler.post(new Runnable(){public void run(){remoteBusy=false;remoteStatus=message(e);if(a!=null)info(a,"Ошибка соединения",remoteStatus);}});}}} ,"NCAssistantUpload").start();
    }
    private void fetchRemote(final Activity a){
        if(!prefs.getBoolean("enabled",false)){remoteSettings(a);return;}if(remoteBusy){toast("Повтори через несколько секунд");return;}
        final String base=prefs.getString("url",""),token=prefs.getString("token","");remoteBusy=true;
        new Thread(new Runnable(){public void run(){try{
            final JSONObject result=Remote.call(base,token,"GET","/api/device/patch",null);
            handler.post(new Runnable(){public void run(){remoteBusy=false;try{
                if(result.isNull("patch")||!result.has("patch")){info(a,"Изменения","Новых изменений нет");return;}
                Object p=project();infoPreview(a,p,preparePatch(p,result.getJSONObject("patch")));
            }catch(Exception e){info(a,"Патч не подходит",message(e));}}});
        }catch(final Exception e){handler.post(new Runnable(){public void run(){remoteBusy=false;info(a,"Ошибка соединения",message(e));}});}}} ,"NCAssistantPatch").start();
    }

    private void pick(final Activity a){
        // Non-lifecycle AndroidX registry registration also works for already-resumed activities.
        try{
            final Object registry=Reflect.call(a,"getActivityResultRegistry");
            Object contract=Reflect.make("androidx.activity.result.contract.ActivityResultContracts$GetContent");
            final Object[] holder=new Object[1];
            Class<?> callbackClass=Reflect.type("androidx.activity.result.ActivityResultCallback");
            Object callback=Proxy.newProxyInstance(Reflect.loader,new Class<?>[]{callbackClass},new InvocationHandler(){
                public Object invoke(Object proxy,Method method,Object[] args)throws Throwable {
                    if(method.getName().equals("onActivityResult")){
                        try{if(args[0]!=null){InputStream in=a.getContentResolver().openInputStream((Uri)args[0]);if(in==null)throw new IOException("Не удалось открыть файл");String data;try{data=read(in,1024*1024);}finally{in.close();}input(a,data);}}
                        catch(Exception e){info(a,"Ошибка чтения",message(e));}
                        finally{if(holder[0]!=null)Reflect.call(holder[0],"unregister");}
                        return null;
                    }
                    if(method.getName().equals("toString"))return "NewCatroidAssistantCallback";
                    if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                    if(method.getName().equals("equals"))return proxy==args[0];return null;
                }
            });
            holder[0]=Reflect.call(registry,"register","nc-assistant-"+System.nanoTime(),contract,callback);
            Reflect.call(holder[0],"launch","*/*");
        }catch(Exception e){info(a,"Выбор файла недоступен","Используй «Вставить изменения».\n\n"+message(e));}
    }

    private static void entry(ZipOutputStream z,String name,String text)throws Exception {z.putNextEntry(new ZipEntry(name));z.write(text.getBytes("UTF-8"));z.closeEntry();}
    static String read(File f,int max)throws IOException {InputStream in=new FileInputStream(f);try{return read(in,max);}finally{in.close();}}
    static String read(InputStream in,int max)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>max)throw new IOException("Слишком большой файл");out.write(b,0,n);}return new String(out.toByteArray(),"UTF-8");}
    static void write(File file,String s)throws IOException {FileOutputStream out=new FileOutputStream(file);try{out.write(s.getBytes("UTF-8"));out.getFD().sync();}finally{out.close();}}
    private void toast(String s){Toast.makeText(app,s,Toast.LENGTH_LONG).show();}
    private void info(Activity a,String title,String text){if(!a.isFinishing())new AlertDialog.Builder(a).setTitle(title).setMessage(text).setPositiveButton("Понятно",null).show();}
    static String message(Throwable e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():s;}
    public void onActivityResumed(Activity a){attach(a);}
    public void onActivityDestroyed(Activity a){View b=buttons.remove(a);if(b!=null&&b.getParent() instanceof ViewGroup)((ViewGroup)b.getParent()).removeView(b);}
    public void onActivityCreated(Activity a,Bundle b){}
    public void onActivityStarted(Activity a){}
    public void onActivityPaused(Activity a){}
    public void onActivityStopped(Activity a){}
    public void onActivitySaveInstanceState(Activity a,Bundle b){}
}
