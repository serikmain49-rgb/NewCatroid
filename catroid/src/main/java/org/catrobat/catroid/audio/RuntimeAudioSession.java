/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.catrobat.catroid.audio;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import org.catrobat.catroid.content.Project;
import org.catrobat.catroid.stage.StageActivity;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.*;

/** Stage-owned session. Player only on main, copying/validation on a worker, results on render thread. */
public final class RuntimeAudioSession {
    public static final int PICK_REQUEST=23089;
    public enum Command {PICK,IMPORT,PLAY,PAUSE,RESUME,STOP,SEEK,VOLUME}
    public static final class Job implements ImportedAudioStore.Cancelled {
        public volatile String result;private volatile boolean cancelled;
        public boolean get(){return cancelled;}
        public synchronized void cancel(){cancelled=true;if(result==null)result="cancelled";}
    }
    private final StageActivity activity;private final Project project;private final ImportedAudioStore store;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closed,foreground=true;
    private volatile String lastError="",state="stopped",playingId="";
    private volatile double position;
    private Job active;private volatile Job picker;
    private volatile Uri selected;private volatile String selectionToken;
    private MediaPlayer player;private boolean prepared,wantPlay;private float volume=1;
    public RuntimeAudioSession(StageActivity a,Project p)throws IOException{activity=a;project=p;store=new ImportedAudioStore(p.getDirectory());}
    public boolean owns(Project p){return p==project&&!closed;}
    public String error(){return lastError;}public String state(){return state;}
    public double position(String id){return playingId.equals(id)?position:0;}
    public static String describe(Project p)throws Exception{
        ImportedAudioStore store=new ImportedAudioStore(p.getDirectory());org.json.JSONArray assets=new org.json.JSONArray();
        for(String id:store.ids()){
            org.json.JSONObject item=new org.json.JSONObject().put("id",id);
            try{ImportedAudioStore.Metadata m=store.metadata(id);item.put("name",m.title).put("duration_seconds",m.durationMs/1000.0).put("mime",m.mime).put("exists",true);}
            catch(IOException e){item.put("exists",false);}assets.put(item);
        }return assets.toString();
    }
    public static Object query(Project p,String function,String id){
        StageActivity a=StageActivity.activeStageActivity==null?null:StageActivity.activeStageActivity.get();RuntimeAudioSession s=a==null?null:a.runtimeAudio;
        if(function.equals("AUDIO_ERROR"))return s!=null&&s.owns(p)?s.error():"NO_SESSION";
        if(function.equals("AUDIO_STATE"))return s!=null&&s.owns(p)?s.state():"stopped";
        if(function.equals("AUDIO_POSITION"))return s!=null&&s.owns(p)?s.position(id):0.0;
        try{
            ImportedAudioStore store=s!=null&&s.owns(p)?s.store:new ImportedAudioStore(p.getDirectory());
            if(function.equals("AUDIO_EXISTS"))return store.exists(id)?1.0:0.0;
            ImportedAudioStore.Metadata m=store.metadata(id);return function.equals("AUDIO_NAME")?m.title:m.durationMs/1000.0;
        }catch(Exception e){return function.equals("AUDIO_NAME")?"":0.0;}
    }
    public synchronized Job submit(Command command,String value){
        Job job=new Job();
        if(closed){job.result="error:NO_SESSION";return job;}
        if(active!=null&&active.result==null){if(command==Command.STOP){active.cancel();clearSelection();if(picker!=null)picker.cancel();}else{job.result="error:BUSY";return job;}}
        active=job;main.post(()->execute(job,command,value));return job;
    }
    private void execute(Job job,Command command,String value){
        if(closed||job.get()){job.cancel();return;}
        try{
            lastError="";
            switch(command){
                case PICK:pick(job);break;
                case IMPORT:importSelected(job,value);break;
                case PLAY:play(job,value);break;
                case PAUSE:requirePlayer();wantPlay=false;if(player.isPlaying())player.pause();state="paused";finish(job,"ok");break;
                case RESUME:requirePlayer();wantPlay=true;if(foreground){player.start();state="playing";}else state="paused_background";finish(job,"ok");break;
                case STOP:releasePlayer();state="stopped";finish(job,"ok");break;
                case SEEK:{
                    requirePlayer();double seconds=Double.parseDouble(value);
                    if(Double.isNaN(seconds)||Double.isInfinite(seconds)||seconds<0)throw new IOException("INVALID_POSITION");
                    int ms=(int)Math.min(player.getDuration(),seconds*1000);
                    player.setOnSeekCompleteListener(p->{position=p.getCurrentPosition()/1000.0;finish(job,"ok");});player.seekTo(ms);timeout(job,player);break;
                }
                case VOLUME:{
                    double percent=Double.parseDouble(value);
                    if(Double.isNaN(percent)||Double.isInfinite(percent)||percent<0||percent>100)throw new IOException("INVALID_VOLUME");
                    volume=(float)(percent/100);if(player!=null)player.setVolume(volume,volume);finish(job,"ok");break;
                }
                default:fail(job,"INVALID_COMMAND");
            }
        }catch(Exception e){if(command==Command.PLAY){releasePlayer();state="error";}fail(job,errorCode(e));}
    }
    private synchronized void finish(Job job,String value){if(!job.get()&&job.result==null)job.result=value;}
    private void fail(Job job,String code){lastError=code;finish(job,"error:"+code);}
    private static String errorCode(Exception e){String m=e.getMessage();return m!=null&&m.matches("[A-Z_]{3,60}")?m:"AUDIO_IO_ERROR";}
    private void pick(Job job){
        if(picker!=null){fail(job,"BUSY");return;}
        if(!foreground||activity.isFinishing()){fail(job,"NOT_FOREGROUND");return;}
        clearSelection();picker=job;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*")
            .putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/mpeg","audio/ogg","application/ogg"}).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{activity.startActivityForResult(intent,PICK_REQUEST);}catch(Exception e){picker=null;fail(job,"PICKER_UNAVAILABLE");}
    }
    public boolean onActivityResult(int request,int result,Intent data){
        if(request!=PICK_REQUEST)return false;Job job=picker;picker=null;
        if(job==null||job.get()||closed)return true;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null){finish(job,"cancelled");return true;}
        Uri uri=data.getData();if(!"content".equals(uri.getScheme())){fail(job,"INVALID_SELECTION");return true;}
        selected=uri;selectionToken="selection:"+UUID.randomUUID();finish(job,selectionToken);return true;
    }
    private void importSelected(Job job,String token){
        if(selected==null||!token.equals(selectionToken)){fail(job,"INVALID_SELECTION");return;}
        Uri uri=selected;clearSelection();state="importing";
        worker.execute(()->{
            try{
                String title="Audio";
                try(Cursor c=activity.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())title=c.getString(0);}
                InputStream in=activity.getContentResolver().openInputStream(uri);if(in==null)throw new IOException("CANNOT_READ_SELECTION");
                String id=store.importAudio(in,title,new AndroidAudioValidator(),job);
                main.post(()->completeImport(job,id,null));
            }catch(Exception e){String code=errorCode(e);main.post(()->completeImport(job,null,code));}
        });
    }
    private synchronized void completeImport(Job job,String id,String error){
        if(!closed&&active==job&&state.equals("importing"))state=player==null?"stopped":(wantPlay?(foreground?"playing":"paused_background"):"paused");
        if(error==null)finish(job,id);else if(!job.get())fail(job,error);
    }
    private void play(Job job,String id)throws Exception{
        File track=store.audio(id);store.metadata(id);releasePlayer();
        MediaPlayer p=new MediaPlayer();player=p;playingId=id;state="preparing";
        p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());p.setVolume(volume,volume);
        p.setOnErrorListener((mp,what,extra)->{if(player==mp){releasePlayer();state="error";fail(job,"PLAYBACK_ERROR");}return true;});
        p.setOnCompletionListener(mp->{if(player==mp){position=mp.getDuration()/1000.0;wantPlay=false;state="completed";}});
        p.setOnPreparedListener(mp->{
            if(closed||job.get()||player!=mp)return;
            try{prepared=true;wantPlay=true;if(foreground){mp.start();state="playing";}else state="paused_background";finish(job,"ok");tick(mp);}
            catch(Exception e){releasePlayer();state="error";fail(job,"PLAYBACK_ERROR");}
        });
        p.setDataSource(track.getAbsolutePath());p.prepareAsync();timeout(job,p);
    }
    private void timeout(Job job,MediaPlayer p){main.postDelayed(()->{if(job.result==null&&player==p&&!job.get()){releasePlayer();state="error";fail(job,"PLAYER_TIMEOUT");}},30000);}
    private void tick(MediaPlayer p){if(closed||player!=p||!prepared)return;try{position=p.getCurrentPosition()/1000.0;}catch(IllegalStateException ignored){}main.postDelayed(()->tick(p),100);}
    private void requirePlayer()throws IOException{if(player==null||!prepared)throw new IOException("NOT_PLAYING");}
    private void releasePlayer(){MediaPlayer p=player;player=null;prepared=false;wantPlay=false;playingId="";position=0;if(p!=null){p.setOnPreparedListener(null);p.setOnErrorListener(null);p.setOnCompletionListener(null);p.setOnSeekCompleteListener(null);p.release();}}
    private void clearSelection(){selected=null;selectionToken=null;}
    public void background(){foreground=false;main.post(()->{if(player!=null&&prepared&&wantPlay)try{if(player.isPlaying())player.pause();state="paused_background";}catch(IllegalStateException ignored){}});}
    public void foreground(){foreground=true;main.post(()->{if(player!=null&&prepared&&wantPlay)try{player.start();state="playing";}catch(IllegalStateException e){lastError="PLAYBACK_ERROR";releasePlayer();state="error";}});}
    public synchronized void cancelScene(){if(active!=null)active.cancel();if(picker!=null)picker.cancel();clearSelection();main.post(()->{releasePlayer();state="stopped";});}
    public synchronized void close(){closed=true;cancelScene();worker.shutdownNow();}
}
