"""Exercise transaction logic against a small host model; no Android UI emulation."""
import pathlib,tempfile,subprocess,sys
root=pathlib.Path(__file__).resolve().parents[1]
jar=pathlib.Path(sys.argv[1]).resolve()
fixtures={
'org/catrobat/catroid/content/Project.java': '''package org.catrobat.catroid.content; import java.util.*; public class Project { public String name="Game";public java.io.File dir=new java.io.File(System.getProperty("java.io.tmpdir"),"nc-project-"+UUID.randomUUID());public java.io.File getDirectory(){dir.mkdirs();return dir;}public List<Object> lists=new ArrayList<Object>();public List<Object> getUserLists(){return lists;}public List<Object> scenes=new ArrayList<Object>();public List<Object> vars=new ArrayList<Object>();private Object variableCache;public String getName(){return "Game";}public List<Object> getSceneList(){return scenes;}public List<Object> getUserVariables(){return vars;}public String toString(){return "Game"+scenes+vars;} }''',
'org/catrobat/catroid/content/Scene.java': '''package org.catrobat.catroid.content; import java.util.*; public class Scene {public String name="Forest";Project project;public Scene(){}public Scene(String n,Project p){name=n;project=p;dir=new java.io.File(p.getDirectory(),n);}public String getSceneId(){return name;}public List<Object> sprites=new ArrayList<Object>();public java.io.File dir=new java.io.File(System.getProperty("java.io.tmpdir"),"nc-test-"+UUID.randomUUID());public java.io.File getDirectory(){return dir;}public String getName(){return name;}public List<Object> getSpriteList(){return sprites;}public String toString(){return name+sprites;} }''',
'org/catrobat/catroid/content/Sprite.java': '''package org.catrobat.catroid.content; import java.util.*; public class Sprite {String name;public List<Object> scripts=new ArrayList<Object>(),vars=new ArrayList<Object>(),looks=new ArrayList<Object>();public List<Object> sounds=new ArrayList<Object>(),lists=new ArrayList<Object>();public List<Object> getSoundList(){return sounds;}public List<Object> getUserLists(){return lists;}public List<Object> getLookList(){return looks;}private Object variableCache;public Sprite(String n){name=n;}public String getName(){return name;}public List<Object> getScriptList(){return scripts;}public List<Object> getUserVariables(){return vars;}public String toString(){return name+scripts+vars+looks;} }''',
'org/catrobat/catroid/content/StartScript.java': '''package org.catrobat.catroid.content; import java.util.*; public class StartScript {public List<Object> bricks=new ArrayList<Object>();String scriptId=UUID.randomUUID().toString();boolean commentedOut;float posX,posY;public List<Object> getBrickList(){return bricks;}public String getScriptId(){return scriptId;}public void setParents(){}public String toString(){return scriptId+bricks;} }''',
'org/catrobat/catroid/formulaeditor/UserVariable.java': '''package org.catrobat.catroid.formulaeditor;public class UserVariable {String name;Object value;public UserVariable(String n,Object v){name=n;value=v;}public String getName(){return name;}public Object getValue(){return value;}public String toString(){return name+value;} }''',
'org/catrobat/catroid/formulaeditor/Formula.java': '''package org.catrobat.catroid.formulaeditor;public class Formula {public FormulaElement root;public Formula(FormulaElement e){root=e;}public FormulaElement getRoot(){return root;}public String toString(){return root.toString();} }''',
'org/catrobat/catroid/formulaeditor/FormulaElement.java': '''package org.catrobat.catroid.formulaeditor;import java.util.*;public class FormulaElement {public enum ElementType {NUMBER,STRING,OPERATOR,FUNCTION,SENSOR,USER_VARIABLE,COLLISION_FORMULA}ElementType type;String value;public ElementType getElementType(){return type;}public String getValue(){return value;}FormulaElement leftChild,rightChild;List<FormulaElement> additionalChildren=new ArrayList<FormulaElement>();public FormulaElement(ElementType type,String value,FormulaElement parent){this.type=type;this.value=value;}public void setLeftChild(FormulaElement e){leftChild=e;}public void setRightChild(FormulaElement e){rightChild=e;}public void addAdditionalChild(FormulaElement e){additionalChildren.add(e);}public String toString(){return type+value+leftChild+rightChild+additionalChildren;} }''',
'org/catrobat/catroid/formulaeditor/Operators.java': 'package org.catrobat.catroid.formulaeditor;public enum Operators {PLUS,EQUAL}',
'org/catrobat/catroid/formulaeditor/Functions.java': 'package org.catrobat.catroid.formulaeditor;public enum Functions {RAND}',
'org/catrobat/catroid/formulaeditor/Sensors.java': 'package org.catrobat.catroid.formulaeditor;public enum Sensors {FINGER_TOUCHED}',
'org/catrobat/catroid/content/bricks/Brick.java': 'package org.catrobat.catroid.content.bricks;public interface Brick {enum BrickField {STEPS,VALUE_1,VALUE_2,VALUE_3}}',
'org/catrobat/catroid/content/bricks/UserVariableBrick.java': 'package org.catrobat.catroid.content.bricks;public class UserVariableBrick {}',
'org/catrobat/catroid/content/bricks/MoveNStepsBrick.java': '''package org.catrobat.catroid.content.bricks;import java.util.*;import org.catrobat.catroid.formulaeditor.*; public class MoveNStepsBrick implements Brick {Map<Brick.BrickField,Formula> map=new HashMap<Brick.BrickField,Formula>();public MoveNStepsBrick(){map.put(Brick.BrickField.STEPS,null);}public Map<Brick.BrickField,Formula> getAllFormulaFieldsWithFormulas(){return map;}public void setFormulaWithBrickField(Brick.BrickField f,Formula v){map.put(f,v);}public String toString(){return "Move"+map;} }''',
'org/catrobat/catroid/content/bricks/ForeverBrick.java': '''package org.catrobat.catroid.content.bricks;import java.util.*;public class ForeverBrick implements Brick {List<Object> list=new ArrayList<Object>();public List<Object> getNestedBricks(){return list;}public String toString(){return "Forever"+list;} }''',
'org/catrobat/catroid/io/XstreamSerializer.java': '''package org.catrobat.catroid.io;public class XstreamSerializer {static XstreamSerializer one=new XstreamSerializer();public static XstreamSerializer getInstance(){return one;}public String getXmlAsStringFromProject(Object p){return p.toString();} }''',

'android/util/Base64.java': 'package android.util;public class Base64 {public static byte[] decode(String s,int flags){return java.util.Base64.getDecoder().decode(s);}}',
'android/graphics/BitmapFactory.java': 'package android.graphics;public class BitmapFactory {public static Bitmap decodeByteArray(byte[] b,int o,int n)throws Exception {java.awt.image.BufferedImage i=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(b,o,n));return i==null?null:new Bitmap(i);} }',
'android/graphics/Bitmap.java': 'package android.graphics;public class Bitmap {java.awt.image.BufferedImage i;public Bitmap(java.awt.image.BufferedImage x){i=x;}public int getWidth(){return i.getWidth();}public int getHeight(){return i.getHeight();}public void recycle(){i.flush();}}',
'org/catrobat/catroid/common/LookData.java': 'package org.catrobat.catroid.common; public class LookData {String name,fileName;java.io.File file;public LookData(String n,java.io.File f){name=n;file=f;fileName=f.getName();}public String getName(){return name;}public java.io.File getFile(){return file;}public String toString(){return name+file;}}',
'org/catrobat/catroid/content/bricks/SetLookBrick.java': 'package org.catrobat.catroid.content.bricks; public class SetLookBrick {Object look;public void setLook(org.catrobat.catroid.common.LookData l){look=l;}public String toString(){return "Look"+look;}}',

'plugin/Main.java': 'package plugin;public class Main {static String read(java.io.File f,int max)throws Exception{return new String(java.nio.file.Files.readAllBytes(f.toPath()),"UTF-8");}static void write(java.io.File f,String s)throws Exception{java.nio.file.Files.write(f.toPath(),s.getBytes("UTF-8"));}}',
'org/catrobat/catroid/ui/recyclerview/controller/SceneController.java': 'package org.catrobat.catroid.ui.recyclerview.controller;import org.catrobat.catroid.content.*;public class SceneController {public static Scene newSceneWithBackgroundSprite(String n,String b,Project p){Scene s=new Scene(n,p);s.sprites.add(new Sprite(b));return s;}}',
'org/catrobat/catroid/content/WhenScript.java': 'package org.catrobat.catroid.content;public class WhenScript extends StartScript {}',
'org/catrobat/catroid/content/bricks/SceneTransitionBrick.java': 'package org.catrobat.catroid.content.bricks;public class SceneTransitionBrick {String sceneForTransition="";public void setSceneForTransition(String s){sceneForTransition=s;}public String toString(){return "Transition"+sceneForTransition;}}',
'org/catrobat/catroid/content/bricks/SetAmbientLightBrick.java': 'package org.catrobat.catroid.content.bricks;import java.util.*;import org.catrobat.catroid.formulaeditor.*;public class SetAmbientLightBrick implements Brick {Map<Brick.BrickField,Formula> map=new HashMap<Brick.BrickField,Formula>();public SetAmbientLightBrick(){map.put(Brick.BrickField.VALUE_1,null);map.put(Brick.BrickField.VALUE_2,null);map.put(Brick.BrickField.VALUE_3,null);}public Map<Brick.BrickField,Formula> getAllFormulaFieldsWithFormulas(){return map;}public void setFormulaWithBrickField(Brick.BrickField f,Formula v){map.put(f,v);}public String toString(){return "Ambient"+map;}}',
'org/catrobat/catroid/content/bricks/WriteVariableOnDeviceBrick.java': 'package org.catrobat.catroid.content.bricks;import org.catrobat.catroid.formulaeditor.UserVariable;public class WriteVariableOnDeviceBrick extends UserVariableBrick {UserVariable variable;public void setUserVariable(UserVariable v){variable=v;}public String toString(){return "Write"+variable;}}',
'org/catrobat/catroid/content/bricks/ReadVariableFromDeviceBrick.java': 'package org.catrobat.catroid.content.bricks;public class ReadVariableFromDeviceBrick extends WriteVariableOnDeviceBrick {}',

'org/catrobat/catroid/content/bricks/CloneBrick.java': 'package org.catrobat.catroid.content.bricks;import org.catrobat.catroid.content.Sprite;public class CloneBrick {Sprite objectToClone;public void onItemSelected(Integer id,Sprite s){objectToClone=s;}public Sprite getSelectedItem(){return objectToClone;}public String toString(){return "Clone"+(objectToClone==null?"":objectToClone.getName());}}',

'org/catrobat/catroid/R.java': 'package org.catrobat.catroid;public class R {public static class string {public static int formula_editor_function_rand=123;}}',
'org/catrobat/catroid/formulaeditor/InternFormulaKeyboardAdapter.java': 'package org.catrobat.catroid.formulaeditor;import java.util.*;public class InternFormulaKeyboardAdapter {public static int count=2;public static class Token {String type,value;public Token(String t,String v){type=t;value=v;}public String getInternTokenType(){return type;}public String getTokenStringValue(){return value;}}public List<Token> createInternTokenListByResourceId(int id,String name){List<Token> a=new ArrayList<Token>();a.add(new Token("FUNCTION_NAME","RAND"));a.add(new Token("FUNCTION_PARAMETERS_BRACKET_OPEN","("));for(int i=1;i<count;i++)a.add(new Token("FUNCTION_PARAMETER_DELIMITER",","));a.add(new Token("FUNCTION_PARAMETERS_BRACKET_CLOSE",")"));return a;}}',
'plugin/BridgeTest.java': '''package plugin;import org.json.*;import java.util.*;import org.catrobat.catroid.content.*;
public class BridgeTest {
 static int tests;static void check(boolean yes,String name){if(!yes)throw new AssertionError(name);tests++;}
 static Set<String> allowed=new HashSet<String>(Arrays.asList("MoveNStepsBrick","ForeverBrick","SetLookBrick","SceneTransitionBrick","SetAmbientLightBrick","WriteVariableOnDeviceBrick","ReadVariableFromDeviceBrick","CloneBrick"));
 static JSONObject move()throws Exception{return new JSONObject("{\\"class\\":\\"MoveNStepsBrick\\",\\"formulas\\":{\\"STEPS\\":10}}");}
 static JSONObject op(String kind)throws Exception{return new JSONObject().put("op",kind).put("scene","Forest").put("sprite","Hero");}
 static JSONObject patch(Project p,JSONArray ops)throws Exception{return new JSONObject().put("format",1).put("project","Game").put("id","test").put("title","Test").put("base_sha256",Bridge.sha(Reflect.xml(p))).put("operations",ops);}
 static boolean fails(Project p,JSONObject patch){try{new Bridge(p,allowed).prepare(patch);return false;}catch(Exception e){return true;}}
 public static void main(String[] args)throws Exception{
  Reflect.loader=BridgeTest.class.getClassLoader();Project p=new Project();Scene scene=new Scene();p.scenes.add(scene);Sprite hero=new Sprite("Hero");scene.sprites.add(hero);
  String original=Reflect.xml(p);
  JSONObject loop=new JSONObject().put("class","ForeverBrick").put("formulas",new JSONObject()).put("children",new JSONArray().put(move()));
  JSONObject add=op("script.add").put("script",new JSONObject().put("event","start").put("bricks",new JSONArray().put(loop)));
  Bridge.Plan plan=new Bridge(p,allowed).prepare(patch(p,new JSONArray().put(add)));
  check(hero.scripts.size()==0&&Reflect.xml(p).equals(original),"preview has no mutations");
  plan.apply();check(hero.scripts.size()==1,"apply adds script");
  Object script=hero.scripts.get(0);check(Reflect.list(Reflect.list(script,"getBrickList").get(0),"getNestedBricks").size()==1,"nested loop built");
  plan.undo();check(hero.scripts.isEmpty()&&Reflect.xml(p).equals(original),"undo restores exact original");
  JSONObject wrong=patch(p,new JSONArray().put(add)).put("project","Other");check(fails(p,wrong),"wrong project rejected");
  JSONObject stale=patch(p,new JSONArray().put(add)).put("base_sha256","wrong");check(fails(p,stale),"stale patch rejected");
  JSONObject create=new JSONObject().put("op","sprite.create").put("scene","Forest").put("sprite","New");
  JSONObject bad=op("unsupported");check(fails(p,patch(p,new JSONArray().put(create).put(bad)))&&scene.sprites.size()==1,"late validation failure is atomic");
  JSONObject invalid=op("script.add").put("script",new JSONObject().put("event","start").put("bricks",new JSONArray().put(new JSONObject().put("class","RunPythonScriptBrick"))));
  check(fails(p,patch(p,new JSONArray().put(invalid))),"unsupported block rejected");
  Bridge.Plan tx=new Bridge(p,allowed).prepare(patch(p,new JSONArray().put(add)));hero.vars.add("edit");
  try{tx.apply();throw new AssertionError("stale preview allowed");}catch(Exception expected){check(hero.scripts.isEmpty(),"stale preview blocked before mutation");}hero.vars.clear();
  plan=new Bridge(p,allowed).prepare(patch(p,new JSONArray().put(add)));plan.apply();
  String sid=String.valueOf(Reflect.call(hero.scripts.get(0),"getScriptId"));
  JSONObject replace=op("script.replace").put("script_id",sid).put("bricks",new JSONArray().put(move()));
  JSONObject append=op("script.append").put("script_id",sid).put("bricks",new JSONArray().put(move()));
  Bridge.Plan modify=new Bridge(p,allowed).prepare(patch(p,new JSONArray().put(replace).put(append)));String before=Reflect.xml(p);
  modify.apply();check(Reflect.list(hero.scripts.get(0),"getBrickList").size()==2,"replace and append compose");modify.undo();check(Reflect.xml(p).equals(before),"replacement undo preserves identity and data");
  hero.vars.add("later");try{plan.undo();throw new AssertionError("unsafe undo");}catch(Exception expected){check(hero.scripts.size()==1,"undo refuses external edits");}hero.vars.clear();
  JSONObject var=op("variable.create").put("name","score").put("value",0);
  Bridge.Plan vp=new Bridge(p,allowed).prepare(patch(p,new JSONArray().put(var)));vp.apply();check(hero.vars.size()==1,"variable creation");vp.undo();check(hero.vars.size()==0,"variable undo");
  JSONObject formula=new JSONObject().put("type","OPERATOR").put("value","PLUS").put("left",2).put("right",3);
  Object result=new Bridge(p,allowed).formula(formula,0);check(result.toString().contains("PLUS"),"expression trees");
  java.awt.image.BufferedImage bi=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_ARGB);
  bi.setRGB(0,0,0xff00ffff);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(bi,"png",out);
  String b64=java.util.Base64.getEncoder().encodeToString(out.toByteArray());
  JSONObject image=new JSONObject().put("mime","image/png").put("base64",b64);
  JSONObject withImage=new JSONObject().put("op","sprite.create").put("scene","Forest").put("sprite","Picture").put("image",image);
  Bridge.Plan ip=new Bridge(p,allowed).prepare(patch(p,new JSONArray().put(withImage)));
  check(!ip.assets.get(0).target.exists()&&scene.sprites.size()==1,"image preview writes nothing");
  ip.apply();check(scene.sprites.size()==2&&ip.assets.get(0).target.isFile(),"sprite and PNG committed");
  Sprite pic=(Sprite)scene.sprites.get(1);check(pic.looks.size()==1,"image assigned to sprite");
  check(java.util.Arrays.equals(out.toByteArray(),java.nio.file.Files.readAllBytes(ip.assets.get(0).target.toPath())),"PNG bytes and alpha preserved");
  ip.undo();check(scene.sprites.size()==1&&ip.assets.get(0).target.exists(),"undo restores model and retains backup assets");ip.rollback();
  JSONObject look=op("look.add").put("name","Frame").put("image",image);
  JSONObject select=op("script.add").put("script",new JSONObject().put("event","start").put("bricks",new JSONArray().put(new JSONObject().put("class","SetLookBrick").put("look","Frame"))));
  Bridge.Plan lp=new Bridge(p,allowed).prepare(patch(p,new JSONArray().put(look).put(select)));lp.apply();
  check(hero.looks.size()==1,"look.add and same-patch SetLook reference");
  check(fails(p,patch(p,new JSONArray().put(look))),"duplicate look rejected");lp.rollback();
  check(hero.looks.isEmpty()&&!lp.assets.get(0).target.exists(),"failed-save rollback removes new file and model");
  JSONObject broken=new JSONObject(image.toString()).put("base64","AAAA");
  check(fails(p,patch(p,new JSONArray().put(op("look.add").put("name","Bad").put("image",broken)))),"invalid PNG rejected");
  broken=new JSONObject(image.toString()).put("sha256","bad");
  check(fails(p,patch(p,new JSONArray().put(op("look.add").put("name","Bad").put("image",broken)))),"hash mismatch rejected");
  byte[] corrupt=out.toByteArray();corrupt[29]^=1;
  broken=new JSONObject(image.toString()).put("base64",java.util.Base64.getEncoder().encodeToString(corrupt));
  check(fails(p,patch(p,new JSONArray().put(op("look.add").put("name","Bad").put("image",broken)))),"CRC corruption rejected");
  Bridge.Plan failWrite=new Bridge(p,allowed).prepare(patch(p,new JSONArray().put(withImage).put(op("look.add").put("name","Other").put("image",image))));
  java.io.File collision=failWrite.assets.get(1).target;collision.getParentFile().mkdirs();java.nio.file.Files.write(collision.toPath(),new byte[]{42});
  try{failWrite.apply();throw new AssertionError("overwrite allowed");}catch(Exception expected){check(scene.sprites.size()==1&&!failWrite.assets.get(0).target.exists()&&java.nio.file.Files.readAllBytes(collision.toPath())[0]==42,"partial file failure rolls back without overwriting existing data");}collision.delete();
  java.awt.image.BufferedImage large=new java.awt.image.BufferedImage(1025,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);out.reset();javax.imageio.ImageIO.write(large,"png",out);
  broken=new JSONObject(image.toString()).put("base64",java.util.Base64.getEncoder().encodeToString(out.toByteArray()));
  check(fails(p,patch(p,new JSONArray().put(op("look.add").put("name","Big").put("image",broken)))),"oversize dimensions rejected");
  java.nio.file.Files.walk(p.getDirectory().toPath()).sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{java.nio.file.Files.delete(x);}catch(Exception e){throw new RuntimeException(e);}});
  Project p2=new Project();Scene a=new Scene("Forest",p2);p2.scenes.add(a);a.sprites.add(new Sprite("Background"));Sprite h=new Sprite("Hero");a.sprites.add(h);
  java.io.File resource=new java.io.File(a.getDirectory(),"images/hero.png");resource.getParentFile().mkdirs();java.nio.file.Files.write(resource.toPath(),new byte[]{1,2,3});
  h.looks.add(new org.catrobat.catroid.common.LookData("Hero",resource));h.vars.add(new org.catrobat.catroid.formulaeditor.UserVariable("Level","saved data"));
  JSONObject createScene=new JSONObject().put("op","scene.create").put("scene","Room");
  JSONObject go=op("script.add").put("script",new JSONObject().put("event","start").put("bricks",new JSONArray().put(new JSONObject().put("class","SceneTransitionBrick").put("scene","Room"))));
  Bridge.Plan sceneTx=new Bridge(p2,allowed).prepare(patch(p2,new JSONArray().put(createScene).put(go)));String orig=Reflect.xml(p2);
  check(p2.scenes.size()==1,"scene preview isolated");sceneTx.apply();check(p2.scenes.size()==2&&h.scripts.toString().contains("Room"),"scene creation and real scene transition constructed");
  String scriptId=String.valueOf(Reflect.call(h.scripts.get(0),"getScriptId"));Object unchanged=Reflect.list(h.scripts.get(0),"getBrickList").get(0);
  JSONObject eventChange=op("script.update").put("script_id",scriptId).put("script",new JSONObject().put("event","tap"));
  Bridge.Plan eventTx=new Bridge(p2,allowed).prepare(patch(p2,new JSONArray().put(eventChange)));eventTx.apply();
  check(h.scripts.get(0) instanceof WhenScript&&scriptId.equals(Reflect.call(h.scripts.get(0),"getScriptId"))&&Reflect.list(h.scripts.get(0),"getBrickList").get(0)==unchanged,"event update preserves script ID and untouched block identity");eventTx.undo();
  JSONObject renameScene=new JSONObject().put("op","scene.rename").put("scene","Room").put("name","Room2");
  Bridge.Plan renameTx=new Bridge(p2,allowed).prepare(patch(p2,new JSONArray().put(renameScene)));renameTx.apply();check(h.scripts.toString().contains("Room2"),"scene rename updates transition references");renameTx.undo();
  JSONObject moveOp=op("sprite.move").put("to_scene","Room");Bridge.Plan moveTx=new Bridge(p2,allowed).prepare(patch(p2,new JSONArray().put(moveOp)));moveTx.apply();
  Scene room=(Scene)p2.scenes.get(1);check(room.sprites.get(1)==h&&h.vars.size()==1&&h.scripts.size()==1,"move preserves sprite scripts and local variables by identity");
  java.io.File movedFile=((org.catrobat.catroid.common.LookData)h.looks.get(0)).getFile();check(movedFile.isFile()&&java.util.Arrays.equals(java.nio.file.Files.readAllBytes(movedFile.toPath()),new byte[]{1,2,3})&&resource.exists(),"move copies resources and preserves backup originals");moveTx.undo();check(a.sprites.get(1)==h&&((org.catrobat.catroid.common.LookData)h.looks.get(0)).getFile().equals(resource),"move undo restores locations and resource references");
  room.sprites.add(new Sprite("Hero"));check(fails(p2,patch(p2,new JSONArray().put(moveOp))),"move rejects destination name conflict");room.sprites.remove(1);
  JSONObject light=new JSONObject().put("class","SetAmbientLightBrick").put("formulas",new JSONObject().put("VALUE_1",0.2).put("VALUE_2",0.3).put("VALUE_3",0.4));
  JSONObject persistence=op("script.add").put("script",new JSONObject().put("event","start").put("bricks",new JSONArray().put(light).put(new JSONObject().put("class","WriteVariableOnDeviceBrick").put("variable","Level")).put(new JSONObject().put("class","ReadVariableFromDeviceBrick").put("variable","Level"))));
  Bridge.Plan persistTx=new Bridge(p2,allowed).prepare(patch(p2,new JSONArray().put(persistence)));persistTx.apply();check(h.scripts.toString().contains("Ambient")&&h.scripts.toString().contains("saved data"),"ambient light and persistent variable read/write bind actual variable object");persistTx.undo();
  JSONObject badFormula=op("script.add").put("script",new JSONObject().put("event","start").put("bricks",new JSONArray().put(new JSONObject().put("class","MoveNStepsBrick").put("formulas",new JSONObject().put("STEPS",new JSONObject().put("type","FUNCTION").put("value","IMAGINARY"))))));
  check(fails(p2,patch(p2,new JSONArray().put(badFormula))),"unsupported function rejected before mutation");
  JSONObject missingVar=new JSONObject().put("type","USER_VARIABLE").put("value","Missing");badFormula.getJSONObject("script").getJSONArray("bricks").getJSONObject(0).getJSONObject("formulas").put("STEPS",missingVar);
  check(fails(p2,patch(p2,new JSONArray().put(badFormula))),"missing formula variable rejected");
  java.io.File jf=new java.io.File(p2.getDirectory(),"journal.json");PatchJournal journal=new PatchJournal(jf);JSONObject jp=patch(p2,new JSONArray().put(persistence)).put("request_id","fresh");journal.receipt(p2,"fresh",jp.getString("base_sha256"));journal.check(p2,jp);journal.mark(p2,"test","applied");
  try{new PatchJournal(jf).check(p2,jp);throw new AssertionError("replay allowed");}catch(Exception expected){check(expected.getMessage().contains("DUPLICATE_PATCH"),"applied ID rejected after process journal reload");}
  jp.put("id","new").put("request_id","old");try{journal.check(p2,jp);throw new AssertionError("stale receipt allowed");}catch(Exception expected){check(expected.getMessage().contains("STALE_EXPORT"),"stale request_id rejected without hash substitution");}
  JSONObject collisionFormulaOp=op("script.add").put("script",new JSONObject().put("event","start").put("bricks",new JSONArray().put(new JSONObject().put("class","MoveNStepsBrick").put("formulas",new JSONObject().put("STEPS",new JSONObject().put("type","COLLISION_FORMULA").put("value","Hero"))))));
  Bridge.Plan collTx=new Bridge(p2,allowed).prepare(patch(p2,new JSONArray().put(collisionFormulaOp)));collTx.apply();
  Bridge.Plan rn=new Bridge(p2,allowed).prepare(patch(p2,new JSONArray().put(op("sprite.rename").put("name","Hero2"))));rn.apply();check(h.scripts.toString().contains("COLLISION_FORMULAHero2"),"sprite rename updates collision-name references");rn.undo();collTx.undo();
  JSONObject clone=op("script.add").put("sprite","Background").put("script",new JSONObject().put("event","start").put("bricks",new JSONArray().put(new JSONObject().put("class","CloneBrick").put("sprite","Hero"))));
  Bridge.Plan cloneTx=new Bridge(p2,allowed).prepare(patch(p2,new JSONArray().put(clone)));cloneTx.apply();
  check(fails(p2,patch(p2,new JSONArray().put(moveOp))),"cross-scene incoming sprite reference rejects move");
  check(fails(p2,patch(p2,new JSONArray().put(op("sprite.delete")))),"referenced sprite deletion rejected");cloneTx.undo();
  JSONObject splice=op("script.splice").put("script_id",scriptId).put("index",0).put("delete_count",0).put("bricks",new JSONArray().put(move()));
  Bridge.Plan spliceTx=new Bridge(p2,allowed).prepare(patch(p2,new JSONArray().put(splice)));spliceTx.apply();check(Reflect.list(h.scripts.get(0),"getBrickList").get(1)==unchanged,"script.splice preserves unedited block identity");spliceTx.undo();
  JSONArray entries=new JSONArray().put(new JSONObject().put("class","MoveNStepsBrick").put("properties",new JSONArray()).put("required_formulas",new JSONArray().put("STEPS")));
  Map<String,Integer> arities=new HashMap<String,Integer>();arities.put("RAND",2);Catalogue cat=new Catalogue(entries,arities,new HashSet<String>(Arrays.asList("PLUS")),new HashSet<String>());
  Bridge strictBridge=new Bridge(p2,cat);
  try{strictBridge.formula(new JSONObject().put("type","FUNCTION").put("value","RAND").put("left",1),0);throw new AssertionError("wrong arity accepted");}catch(Exception expected){check(expected.getMessage().contains("2"),"installed function argument layout enforced");}
  try{strictBridge.formula(new JSONObject().put("type","OPERATOR").put("value","FAKE").put("left",1).put("right",2),0);throw new AssertionError("invalid enum accepted");}catch(Exception expected){check(true,"invalid operator enum rejected");}
  JSONObject missing=op("script.add").put("script",new JSONObject().put("event","start").put("bricks",new JSONArray().put(new JSONObject().put("class","MoveNStepsBrick"))));
  try{new Bridge(p2,cat).prepare(patch(p2,new JSONArray().put(missing)));throw new AssertionError("mandatory field accepted");}catch(Exception expected){check(expected.getMessage().contains("Операция 1"),"required field error includes operation number");}
  Bridge.Plan deleteTx=new Bridge(p2,allowed).prepare(patch(p2,new JSONArray().put(op("script.delete").put("script_id",scriptId))));deleteTx.apply();check(h.scripts.isEmpty(),"script deletion");deleteTx.undo();
  Bridge.Plan deleteSprite=new Bridge(p2,allowed).prepare(patch(p2,new JSONArray().put(op("sprite.delete"))));deleteSprite.apply();check(a.sprites.size()==1&&resource.exists(),"sprite delete retains resource originals for rollback");deleteSprite.undo();
  sceneTx.undo();check(Reflect.xml(p2).equals(orig),"all structural changes undo to original model");
  java.nio.file.Files.walk(p2.getDirectory().toPath()).sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{java.nio.file.Files.delete(x);}catch(Exception e){throw new RuntimeException(e);}});
  Catalogue liveCatalogue=new Catalogue(new java.io.File(args[0]),"host","fixture-version");
  check(liveCatalogue.allowed().contains("SetAmbientLightBrick")&&!liveCatalogue.allowed().contains("SetSpotLightBrick"),"runtime catalogue includes only installed constructible adapted classes");
  check(liveCatalogue.arities.get("RAND")==2,"function arguments derived from installed keyboard tokens");
  org.catrobat.catroid.formulaeditor.InternFormulaKeyboardAdapter.count=3;Catalogue changedHost=new Catalogue(new java.io.File(args[0]),"host","fixture-version");
  check(changedHost.arities.get("RAND")==3,"catalogue follows host argument-layout changes instead of hardcoded arity");
  System.out.println("PASS: "+tests+" transaction checks");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='nc-bridge-test-') as tmp:
 d=pathlib.Path(tmp)
 for name,text in fixtures.items():
  f=d/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 sources=list(d.rglob('*.java'))+[root/'src/plugin/Reflect.java',root/'src/plugin/Bridge.java',root/'src/plugin/ImageAsset.java',root/'src/plugin/ModelOps.java',root/'src/plugin/FileEdits.java',root/'src/plugin/ReferenceCheck.java',root/'src/plugin/Catalogue.java',root/'src/plugin/PatchJournal.java']
 subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',str(jar),'-d',str(d/'classes')]+list(map(str,sources)),check=True)
 subprocess.run(['java','-cp',str(d/'classes')+':'+str(jar),'plugin.BridgeTest',str(root)],check=True)
