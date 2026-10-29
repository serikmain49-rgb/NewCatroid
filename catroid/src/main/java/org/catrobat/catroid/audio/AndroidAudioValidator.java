/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.catrobat.catroid.audio;
import android.media.*;
import java.io.*;

/** Validate copied bytes and the device's own decoder, never the supplied filename or MIME. */
public final class AndroidAudioValidator implements ImportedAudioStore.Validator {
    @Override public ImportedAudioStore.Metadata validate(File file,String title)throws Exception {
        byte[] sig=new byte[4];try(InputStream in=new FileInputStream(file)){if(in.read(sig)!=4)throw new IOException("INVALID_AUDIO");}
        boolean ogg=sig[0]=='O'&&sig[1]=='g'&&sig[2]=='g'&&sig[3]=='S';
        boolean mp3=(sig[0]=='I'&&sig[1]=='D'&&sig[2]=='3')||((sig[0]&255)==255&&(sig[1]&224)==224);
        if(!ogg&&!mp3)throw new IOException("UNSUPPORTED_AUDIO");
        MediaExtractor extractor=new MediaExtractor();MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try {
            extractor.setDataSource(file.getAbsolutePath());String mime=null;
            for(int i=0;i<extractor.getTrackCount();i++){
                String m=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if((mp3&&"audio/mpeg".equals(m))||(ogg&&("audio/vorbis".equals(m)||"audio/opus".equals(m)))){extractor.selectTrack(i);mime=m;break;}
            }
            if(mime==null||extractor.getSampleTime()<0)throw new IOException("INVALID_AUDIO");
            retriever.setDataSource(file.getAbsolutePath());long ms=Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if(ms<=0)throw new IOException("INVALID_AUDIO");
            MediaPlayer probe=new MediaPlayer();try{probe.setDataSource(file.getAbsolutePath());probe.prepare();}finally{probe.release();}
            return new ImportedAudioStore.Metadata(title,mime,ms);
        }catch(Exception e){throw new IOException("INVALID_AUDIO",e);}
        finally{extractor.release();retriever.release();}
    }
}
