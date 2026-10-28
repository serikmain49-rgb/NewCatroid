/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.catrobat.catroid.content.actions;
import com.badlogic.gdx.scenes.scene2d.Action;
import org.catrobat.catroid.audio.RuntimeAudioSession;
import org.catrobat.catroid.content.Scope;
import org.catrobat.catroid.formulaeditor.Formula;
import org.catrobat.catroid.formulaeditor.UserVariable;
import org.catrobat.catroid.stage.StageActivity;
public final class RuntimeAudioAction extends Action {
    private final Scope scope;private final RuntimeAudioSession.Command command;private final Formula value;private final UserVariable result;
    private RuntimeAudioSession.Job job;
    public RuntimeAudioAction(Scope scope,RuntimeAudioSession.Command command,Formula value,UserVariable result){this.scope=scope;this.command=command;this.value=value;this.result=result;}
    @Override public boolean act(float delta){
        if(job==null){
            StageActivity a=StageActivity.activeStageActivity==null?null:StageActivity.activeStageActivity.get();RuntimeAudioSession s=a==null?null:a.runtimeAudio;
            if(s==null||!s.owns(scope.getProject()))return complete("error:NO_SESSION");
            try{job=s.submit(command,value.interpretString(scope));}catch(Exception e){return complete("error:INVALID_ARGUMENT");}
        }return job.result!=null&&complete(job.result);
    }
    private boolean complete(String text){if(result!=null)result.setValue(text);return true;}
    @Override public void restart(){job=null;super.restart();}
}
