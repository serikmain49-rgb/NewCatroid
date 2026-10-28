import org.catrobat.catroid.audio.ImportedAudioStore;
import org.catrobat.catroid.io.ZipArchiver;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public final class StoreChecks {
 static int n;static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);n++;}
 static byte[] data={'I','D','3',0,1,2,3};static ImportedAudioStore.Validator valid=(f,t)->new ImportedAudioStore.Metadata(t,"audio/mpeg",3000);
 static ImportedAudioStore.Cancelled no=()->false;
 static void fails(ImportedAudioStore s,InputStream in,ImportedAudioStore.Validator v,ImportedAudioStore.Cancelled c,String code)throws Exception{
  List<String> before=s.ids();try{s.importAudio(in,"song.mp3",v,c);throw new AssertionError("accepted "+code);}catch(IOException e){check(e.getMessage().equals(code),code);}check(s.ids().equals(before),"failed import not published");
 }
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("nc-audio-");ImportedAudioStore s=new ImportedAudioStore(root.toFile());
  String id=s.importAudio(new ByteArrayInputStream(data),"song.mp3",valid,no);
  check(ImportedAudioStore.validId(id),"opaque ID");check(!id.contains("song"),"filename independent");check(s.exists(id),"exists");
  check(s.metadata(id).title.equals("song.mp3"),"title");check(s.metadata(id).durationMs==3000,"duration");check(Arrays.equals(Files.readAllBytes(s.audio(id).toPath()),data),"byte exact");
  check(new ImportedAudioStore(root.toFile()).exists(id),"load after restart");check(!id.equals(s.importAudio(new ByteArrayInputStream(data),"song.mp3",valid,no)),"same names never overwrite");
  String levelA=id,levelB=id;check(s.exists(levelA)&&s.exists(levelB),"several levels share one track");
  Path original=Files.createTempFile("original", ".mp3");Files.write(original,data);String copied=s.importAudio(new FileInputStream(original.toFile()),"original.mp3",valid,no);Files.delete(original);check(s.exists(copied),"original deletion independent");
  check(!s.exists("audio:../../x"),"path traversal rejected");check(!s.exists("content://phone/audio"),"URI is not ID");check(!s.exists("audio:00000000-0000-0000-0000-000000000000"),"missing audio");
  fails(s,new ByteArrayInputStream(data),valid,()->true,"CANCELLED");fails(s,new ByteArrayInputStream(new byte[0]),valid,no,"INVALID_AUDIO");
  fails(s,new ByteArrayInputStream(data),(f,t)->{throw new IOException("INVALID_AUDIO");},no,"INVALID_AUDIO");
  fails(s,new InputStream(){long left=ImportedAudioStore.MAX_BYTES+1;public int read(){return left-->0?1:-1;}public int read(byte[] b){if(left<=0)return -1;int c=(int)Math.min(left,b.length);left-=c;return c;}},valid,no,"FILE_TOO_LARGE");
  final boolean[] cancelLate={false};
  fails(s,new ByteArrayInputStream(data),(f,t)->{cancelLate[0]=true;return new ImportedAudioStore.Metadata(t,"audio/mpeg",3000);},()->cancelLate[0],"CANCELLED");
  final boolean[] cancelRead={false};
  fails(s,new InputStream(){int calls;public int read(){return 1;}public int read(byte[] b){if(++calls==2)cancelRead[0]=true;return b.length;}},valid,()->cancelRead[0],"CANCELLED");
  File assets=new File(root.toFile(),"files/"+ImportedAudioStore.DIRECTORY);check(Arrays.stream(assets.list()).noneMatch(x->x.startsWith(".pending-audio-")),"failed staging cleaned");
  File pending=new File(assets,".pending-audio-test");pending.mkdirs();Files.write(new File(pending,"track").toPath(),data);
  Path archive=Files.createTempFile("nc-audio", ".zip"),dest=Files.createTempDirectory("nc-audio-export-");
  new ZipArchiver().zip(archive.toFile(),root.toFile().listFiles());new ZipArchiver().unzip(archive.toFile(),dest.toFile());
  check(new ImportedAudioStore(dest.toFile()).exists(id),"full export transfers audio ID");check(new ImportedAudioStore(dest.toFile()).metadata(id).title.equals("song.mp3"),"export metadata");
  try(ZipFile zip=new ZipFile(archive.toFile())){check(Collections.list(zip.entries()).stream().noneMatch(e->e.getName().contains(".pending-audio-")),"unfinished imports excluded");}
  Files.delete(s.audio(id).toPath());check(!s.exists(id),"deleted payload marked missing");
  System.out.println("PASS: "+n+" persistent store/archive checks (validator fixture, not Android codec)");
 }
}
