/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.catrobat.catroid.content.bricks;
import android.content.Context;
import android.view.View;
import android.widget.*;
import org.catrobat.catroid.R;
import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.audio.RuntimeAudioSession;
import org.catrobat.catroid.content.Scope;
import org.catrobat.catroid.content.Sprite;
import org.catrobat.catroid.content.actions.RuntimeAudioAction;
import org.catrobat.catroid.content.actions.ScriptSequenceAction;
import org.catrobat.catroid.formulaeditor.Formula;
public final class RuntimeAudioBrick extends UserVariableBrickWithFormula {
    private static final long serialVersionUID=1L;
    private RuntimeAudioSession.Command command=RuntimeAudioSession.Command.PICK;
    public RuntimeAudioBrick(){addAllowedBrickField(BrickField.VALUE,R.id.runtime_audio_value);}
    public RuntimeAudioBrick(RuntimeAudioSession.Command command,String value){this();this.command=command;setFormulaWithBrickField(BrickField.VALUE,new Formula(value));}
    @Override public int getViewResource(){return R.layout.brick_runtime_audio;}
    @Override protected int getSpinnerId(){return R.id.runtime_audio_result;}
    @Override public View getView(Context context){
        super.getView(context);Spinner spinner=view.findViewById(R.id.runtime_audio_command);
        ArrayAdapter<CharSequence> adapter=ArrayAdapter.createFromResource(context,R.array.runtime_audio_commands,android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(command.ordinal());
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int pos,long id){command=RuntimeAudioSession.Command.values()[pos];}
            public void onNothingSelected(AdapterView<?> p){}
        });return view;
    }
    @Override public void addActionToSequence(Sprite sprite,ScriptSequenceAction sequence){
        sequence.addAction(sprite.getActionFactory().createRuntimeAudioAction(sprite,sequence,command,getFormulaWithBrickField(BrickField.VALUE),userVariable));
    }
}
