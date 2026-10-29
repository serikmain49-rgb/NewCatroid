"""Compile actual new classes against Android/GDX; run pure JVM persistence checks.
Usage: python run_checks.py ANDROID_JAR GDX_JAR JSON_JAR
Host model types are signature fixtures. No Android runtime/SAF/decoder is simulated here.
"""
from pathlib import Path
import sys,tempfile,subprocess
r=Path(__file__).resolve().parents[2];j=r/'catroid/src/main/java/org/catrobat/catroid'
android,gdx,json=[str(Path(x).resolve()) for x in sys.argv[1:4]]
f={
'common/Constants.java':'public class Constants {public static final int BUFFER_8K=8192;}',
'content/Project.java':'public class Project {public java.io.File getDirectory(){return null;}}',
'content/Sprite.java':'public class Sprite {public ActionFactory getActionFactory(){return null;}}',
'content/ActionFactory.java':'public class ActionFactory {public com.badlogic.gdx.scenes.scene2d.Action createRuntimeAudioAction(Sprite s,com.badlogic.gdx.scenes.scene2d.actions.SequenceAction a,org.catrobat.catroid.audio.RuntimeAudioSession.Command c,org.catrobat.catroid.formulaeditor.Formula f,org.catrobat.catroid.formulaeditor.UserVariable v){return null;}}',
'content/Scope.java':'public class Scope {public Scope(Project p,Sprite s,com.badlogic.gdx.scenes.scene2d.actions.SequenceAction a){} public Project getProject(){return null;}}',
'stage/StageActivity.java':'public class StageActivity extends android.app.Activity {public static java.lang.ref.WeakReference<StageActivity> activeStageActivity; public org.catrobat.catroid.audio.RuntimeAudioSession runtimeAudio;}',
'formulaeditor/Formula.java':'public class Formula {public Formula(String s){}public String interpretString(org.catrobat.catroid.content.Scope s){return "";}}',
'formulaeditor/UserVariable.java':'public class UserVariable {public void setValue(Object o){}}',
'content/actions/ScriptSequenceAction.java':'public class ScriptSequenceAction extends com.badlogic.gdx.scenes.scene2d.actions.SequenceAction {}',
'ProjectManager.java':'public class ProjectManager {public static ProjectManager getInstance(){return null;}public org.catrobat.catroid.content.Project getCurrentProject(){return null;}}',
'R.java':'public class R {public static class id {public static int runtime_audio_value=1,runtime_audio_result=2,runtime_audio_command=3;}public static class layout {public static int brick_runtime_audio=1;}public static class array {public static int runtime_audio_commands=1;}}',
'content/bricks/UserVariableBrickWithFormula.java':'''public abstract class UserVariableBrickWithFormula {public enum BrickField {VALUE} protected android.view.View view;protected org.catrobat.catroid.formulaeditor.UserVariable userVariable;protected void addAllowedBrickField(BrickField f,int id){}public void setFormulaWithBrickField(BrickField f,org.catrobat.catroid.formulaeditor.Formula v){}public org.catrobat.catroid.formulaeditor.Formula getFormulaWithBrickField(BrickField f){return null;}public android.view.View getView(android.content.Context c){return null;}protected abstract int getSpinnerId();public abstract int getViewResource();public abstract void addActionToSequence(org.catrobat.catroid.content.Sprite s,org.catrobat.catroid.content.actions.ScriptSequenceAction a);}'''
}
with tempfile.TemporaryDirectory(prefix='nc-audio-') as tmp:
 t=Path(tmp)
 for p,body in f.items():
  out=t/'org/catrobat/catroid'/p;out.parent.mkdir(parents=True,exist_ok=True);out.write_text('package org.catrobat.catroid'+('.'+p.rsplit('/',1)[0].replace('/','.') if '/' in p else '')+';'+body)
 sources=list(t.rglob('*.java'))+list((j/'audio').glob('*.java'))+[j/'content/actions/RuntimeAudioAction.java',j/'content/bricks/RuntimeAudioBrick.java',j/'io/ZipArchiver.java',r/'checks/runtime-audio/StoreChecks.java']
 cp=':'.join([android,gdx,json]);classes=t/'classes'
 subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-cp',cp,'-d',str(classes)]+list(map(str,sources)),check=True)
 print('PASS: new runtime classes compile with Android API34/GDX; host signatures are fixtures',flush=True)
 subprocess.run(['java','-cp',str(classes),'StoreChecks'],check=True)
