package com.artemis;
import com.artemis.utils.IntBag;
public final class EntitySubscription {
    public interface SubscriptionListener { void inserted(IntBag entities); void removed(IntBag entities); }
    private final IntBag bag = new IntBag();
    private SubscriptionListener listener;
    public IntBag getEntities() { return bag; }
    public void addSubscriptionListener(SubscriptionListener listener) { this.listener = listener; }
    public void setEntities(int[] ids) { bag.set(ids); if (listener != null) listener.inserted(bag); }
}
