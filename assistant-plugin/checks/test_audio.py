"""Validate new audio patch literals using actual AudioSupport/Reflect and the persistent store."""
from pathlib import Path
import sys,tempfile,subprocess
r=Path(__file__).resolve().parents[1];host=r.parent/'catroid/src/main/java/org/catrobat/catroid/audio/ImportedAudioStore.java';jar=str(Path(sys.argv[1]).resolve())
with tempfile.TemporaryDirectory(prefix='nc-audio-adapter-') as tmp:
 t=Path(tmp)
 fixtures={
 'org/catrobat/catroid/content/Project.java':'package org.catrobat.catroid.content;public class Project {java.io.File dir;public Project(java.io.File d){dir=d;}public java.io.File getDirectory(){return dir;}}',
 'plugin/AudioAdapterChecks.java':'''package plugin;import org.json.*;import org.catrobat.catroid.audio.ImportedAudioStore;import org.catrobat.catroid.content.Project;public class AudioAdapterChecks {
 static int n;static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);n++;}
 static JSONObject spec(String cmd,Object value)throws Exception{return new JSONObject().put("properties",new JSONObject().put("command",cmd)).put("formulas",new JSONObject().put("VALUE",value));}
 static void fails(Project p,JSONObject spec,String where)throws Exception{try{AudioSupport.validate(p,spec);throw new AssertionError("accepted "+where);}catch(Exception e){check(e.getMessage().contains(where),where);}}
 public static void main(String[] args)throws Exception{
  Reflect.loader=AudioAdapterChecks.class.getClassLoader();Project p=new Project(java.nio.file.Files.createTempDirectory("nc-audio-model-").toFile());ImportedAudioStore store=new ImportedAudioStore(p.getDirectory());String id=store.importAudio(new java.io.ByteArrayInputStream(new byte[]{1}),"title",(f,t)->new ImportedAudioStore.Metadata(t,"audio/mpeg",1000),()->false);
  fails(p,new JSONObject(),"properties.command");fails(p,spec("FAKE",""),"properties.command");JSONObject noarg=spec("PICK","");noarg.remove("formulas");fails(p,noarg,"formulas.VALUE");
  for(String cmd:new String[]{"PICK","PAUSE","RESUME","STOP"}){AudioSupport.validate(p,spec(cmd,""));check(true,cmd);}
  AudioSupport.validate(p,spec("PLAY",id));check(true,"valid existing ID");fails(p,spec("PLAY","content://phone/audio"),"MISSING_AUDIO");fails(p,spec("PLAY","audio:00000000-0000-0000-0000-000000000000"),"MISSING_AUDIO");fails(p,spec("PLAY",5),"ожидается ID");
  fails(p,spec("SEEK",-1),"вне диапазона");fails(p,spec("SEEK","NaN"),"вне диапазона");fails(p,spec("SEEK","word"),"требуется число");fails(p,spec("VOLUME",101),"вне диапазона");fails(p,spec("VOLUME",-1),"вне диапазона");
  for(int value:new int[]{0,28,100}){AudioSupport.validate(p,spec("VOLUME",value));check(true,"valid volume");}
  AudioSupport.validate(p,spec("PLAY",new JSONObject().put("type","USER_VARIABLE").put("value","AudioId")));check(true,"dynamic ID delegated to runtime");
  JSONObject old=new JSONObject();AudioSupport.export(p,old);check(!old.has("runtime_audio_api"),"old host does not advertise feature");
  System.out.println("PASS: "+n+" audio patch adapter checks (real store, no Android runtime)");
 }
}'''}
 for path,body in fixtures.items():
  p=t/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body)
 sources=list(t.rglob('*.java'))+[r/'src/plugin/AudioSupport.java',r/'src/plugin/Reflect.java',host]
 subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-cp',jar,'-d',str(t/'classes')]+list(map(str,sources)),check=True)
 subprocess.run(['java','-cp',str(t/'classes')+':'+jar,'plugin.AudioAdapterChecks'],check=True)
