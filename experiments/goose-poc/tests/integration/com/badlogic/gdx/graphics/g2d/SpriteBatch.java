package com.badlogic.gdx.graphics.g2d;
import com.badlogic.gdx.graphics.Color;
import java.util.ArrayList;
import java.util.List;
public class SpriteBatch {
    public final List<Integer> drawn = new ArrayList<>();
    public void setProjectionMatrix(Object matrix) { }
    public void begin() { }
    public void end() { }
    public void setColor(Color color) { }
    public void draw(TextureRegion region, float x, float y, float ox, float oy, float w, float h, float sx, float sy, float rotation) { drawn.add(region.id); }
}
