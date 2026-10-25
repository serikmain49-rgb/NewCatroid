package plugin;
import android.content.*;
import org.json.*;
import java.net.*;
import java.io.*;

/** Authenticated transport. Never follows redirects carrying the pairing token. */
final class Remote {
    static String validate(String base)throws Exception {
        URL u=new URL(base);
        boolean local=u.getHost().equals("127.0.0.1")||u.getHost().equals("localhost")||u.getHost().equals("[::1]");
        if(!(u.getProtocol().equals("https")||(local&&u.getProtocol().equals("http"))))throw new Exception("Используй HTTPS или http://127.0.0.1:8765");
        if(u.getUserInfo()!=null||u.getQuery()!=null||u.getRef()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new Exception("Нужен адрес сервера без пути, пароля и параметров");
        return base.replaceAll("/+$","");
    }
    static final class Failure extends IOException {final int status;Failure(int status,String detail){super("Сервер HTTP "+status+": "+detail);this.status=status;}}
    static JSONObject call(String base,String token,String method,String path,JSONObject data)throws Exception {
        validate(base);if(token.length()<24)throw new Exception("Введи ключ подключения из Termux");
        HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(20000);
        c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Accept","application/json");
        try {
            if(data!=null){byte[] b=data.toString().getBytes("UTF-8");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setFixedLengthStreamingMode(b.length);OutputStream out=c.getOutputStream();try{out.write(b);}finally{out.close();}}
            int status=c.getResponseCode();InputStream in=status>=400?c.getErrorStream():c.getInputStream();
            String raw="";if(in!=null)try{raw=Main.read(in,2*1024*1024);}finally{in.close();}
            if(status<200||status>=300){String detail=status==401?"неверный ключ":"запрос отклонён";try{JSONObject error=new JSONObject(raw);detail=error.optString("code","")+": "+error.optString("error",error.optString("message",detail));}catch(Exception ignored){}throw new Failure(status,detail.substring(0,Math.min(500,detail.length())));}
            return raw.isEmpty()?new JSONObject():new JSONObject(raw);
        }finally{c.disconnect();}
    }
}
