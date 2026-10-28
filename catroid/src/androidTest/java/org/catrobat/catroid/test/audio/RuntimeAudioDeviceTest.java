/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.catrobat.catroid.test.audio;

import android.media.MediaPlayer;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.catrobat.catroid.audio.AndroidAudioValidator;
import org.catrobat.catroid.audio.ImportedAudioStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Device-only codec/player tests; system picker UI/lifecycle still has a manual checklist. */
@RunWith(AndroidJUnit4.class)
public final class RuntimeAudioDeviceTest {
    private byte[] sample(String name)throws Exception {
        try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("runtime-audio/tone."+name+".b64");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return Base64.decode(out.toByteArray(),Base64.DEFAULT);
        }
    }
    @Test public void mp3AndOggPersistAndPlayAfterOriginalDeletion()throws Exception {
        File root=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"audio-test-"+java.util.UUID.randomUUID());assertTrue(root.mkdirs());
        try {
            for(String format:new String[]{"mp3","ogg"}){
                File original=new File(root,"source."+format);try(FileOutputStream out=new FileOutputStream(original)){out.write(sample(format));}
                ImportedAudioStore store=new ImportedAudioStore(root);
                String id=store.importAudio(new FileInputStream(original),"tone."+format,new AndroidAudioValidator(),()->false);
                assertTrue(original.delete());store=new ImportedAudioStore(root);assertTrue(store.exists(id));assertTrue(store.metadata(id).durationMs>2000);
                MediaPlayer player=new MediaPlayer();
                try {
                    player.setDataSource(store.audio(id).getAbsolutePath());player.prepare();player.setVolume(.28f,.28f);player.start();assertTrue(player.isPlaying());
                    player.pause();assertFalse(player.isPlaying());player.start();assertTrue(player.isPlaying());
                    CountDownLatch seek=new CountDownLatch(1);player.setOnSeekCompleteListener(p->seek.countDown());player.seekTo(1250);assertTrue(seek.await(5,TimeUnit.SECONDS));assertTrue(player.getCurrentPosition()>=900);
                    player.stop();
                } finally {player.release();}
            }
            ImportedAudioStore store=new ImportedAudioStore(root);int count=store.ids().size();
            try {store.importAudio(new ByteArrayInputStream(new byte[]{1,2,3,4}),"fake.mp3",new AndroidAudioValidator(),()->false);fail("Corrupt file accepted");}
            catch(IOException expected){assertEquals(count,store.ids().size());}
            try {store.importAudio(new ByteArrayInputStream(sample("mp3")),"cancel.mp3",new AndroidAudioValidator(),()->true);fail("Cancelled import accepted");}
            catch(IOException expected){assertEquals("CANCELLED",expected.getMessage());assertEquals(count,store.ids().size());}
        } finally {delete(root);}
    }
    private static void delete(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)delete(c);f.delete();}
}
