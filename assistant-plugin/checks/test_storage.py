"""Compile the repository's actual Device*Accessor sources, then reopen on-disk storage.
Android Log/annotations and the data model are tiny fixtures; the storage code is unmodified.
"""
import pathlib,subprocess,tempfile,sys,re
base=pathlib.Path(__file__).resolve().parents[2];host=base/'NewCatroid' if (base/'NewCatroid/catroid').exists() else base;root=host/'catroid/src/main/java/org/catrobat/catroid'
jar=pathlib.Path(sys.argv[1]).resolve()
fixtures={
'android/util/Log.java':'package android.util;public class Log {public static int e(String a,String b){return 0;}}',
'androidx/annotation/VisibleForTesting.java':'package androidx.annotation;public @interface VisibleForTesting {}',
'org/catrobat/catroid/common/Constants.java':'package org.catrobat.catroid.common;public class Constants {public static final String DEVICE_VARIABLE_JSON_FILE_NAME="variables.json",DEVICE_LIST_JSON_FILE_NAME="lists.json";}',
'org/catrobat/catroid/formulaeditor/UserData.java':'''package org.catrobat.catroid.formulaeditor;import java.util.*;public class UserData<T> {Object value;UUID key;public UserData(UUID k,Object v){key=k;value=v;}public UUID getDeviceKey(){return key;}public Object getValue(){return value;}public void setValue(Object v){value=v;}public void reset(){value=null;}}''',
'org/catrobat/catroid/formulaeditor/UserVariable.java':'package org.catrobat.catroid.formulaeditor;import java.util.*;public class UserVariable extends UserData<Object>{public UserVariable(UUID k,Object v){super(k,v);}}',
'org/catrobat/catroid/content/Sprite.java':'package org.catrobat.catroid.content;import java.util.*;import org.catrobat.catroid.formulaeditor.*;public class Sprite {public List<UserVariable> getUserVariables(){return new ArrayList<UserVariable>();}public List<UserData> getUserLists(){return new ArrayList<UserData>();}}',
'org/catrobat/catroid/content/Scene.java':'package org.catrobat.catroid.content;import java.util.*;public class Scene {public List<Sprite> getSpriteList(){return new ArrayList<Sprite>();}}',
'org/catrobat/catroid/content/Project.java':'package org.catrobat.catroid.content;import java.util.*;import org.catrobat.catroid.formulaeditor.*;public class Project {public List<Scene> getSceneList(){return new ArrayList<Scene>();}public List<UserVariable> getUserVariables(){return new ArrayList<UserVariable>();}public List<UserVariable> getMultiplayerVariables(){return new ArrayList<UserVariable>();}public List<UserData> getUserLists(){return new ArrayList<UserData>();}}',
'StorageTest.java':'''import java.io.*;import java.util.*;import org.catrobat.catroid.io.*;import org.catrobat.catroid.formulaeditor.*;public class StorageTest {public static void main(String[] a)throws Exception {File dir=new File(a[0]);UUID key=UUID.randomUUID();UserVariable value=new UserVariable(key,"уровень:🟦;1,2,3");new DeviceVariableAccessor(dir).writeUserData(value);UserVariable reloaded=new UserVariable(key,"");if(!new DeviceVariableAccessor(dir).readUserData(reloaded)||!value.getValue().equals(reloaded.getValue()))throw new AssertionError("variable persistence failed");UUID lk=UUID.randomUUID();UserData list=new UserData(lk,Arrays.asList("level",2.0,3.0));new DeviceListAccessor(dir).writeUserData(list);UserData loadedList=new UserData(lk,new ArrayList());if(!new DeviceListAccessor(dir).readUserData(loadedList)||!list.getValue().equals(loadedList.getValue()))throw new AssertionError("list persistence failed");System.out.println("PASS: actual host DeviceVariableAccessor and DeviceListAccessor persisted/reopened values (2 checks)");}}'''
}
with tempfile.TemporaryDirectory(prefix='nc-storage-test-') as tmp:
 d=pathlib.Path(tmp)
 for name,text in fixtures.items():
  f=d/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 sources=list(d.rglob('*.java'))+[root/'io'/n for n in ['DeviceUserDataAccessor.java','DeviceVariableAccessor.java','DeviceListAccessor.java']]
 subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',str(jar),'-d',str(d/'classes')]+list(map(str,sources)),check=True)
 subprocess.run(['java','-cp',str(d/'classes')+':'+str(jar),'StorageTest',str(d)],check=True)
