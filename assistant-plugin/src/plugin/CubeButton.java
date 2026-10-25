package plugin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.ImageButton;

/** Small vector cube, with a 48dp touch target. No bitmap assets required. */
final class CubeButton extends ImageButton {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    CubeButton(Context context){
        super(context);setBackgroundColor(0x00000000);setPadding(0,0,0,0);
        setContentDescription("Меню помощника проекта");setFocusable(true);
    }
    private void face(Canvas c,int color,float... xy){
        Path path=new Path();path.moveTo(xy[0],xy[1]);
        for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);
        path.close();paint.setColor(color);paint.setStyle(Paint.Style.FILL);c.drawPath(path,paint);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int saved=canvas.save();
        canvas.translate(getWidth()/2f,getHeight()/2f);
        float unit=Math.min(getWidth(),getHeight())/48f;canvas.scale(unit,unit);
        if(isPressed())canvas.scale(.9f,.9f);
        face(canvas,0xffa5b4fc,0,-16,14,-8,0,0,-14,-8);
        face(canvas,0xff6366f1,-14,-8,0,0,0,16,-14,8);
        face(canvas,0xff3730a3,0,0,14,-8,14,8,0,16);
        canvas.restoreToCount(saved);
    }
}
