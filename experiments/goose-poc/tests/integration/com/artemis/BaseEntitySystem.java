package com.artemis;
public abstract class BaseEntitySystem {
    private final EntitySubscription subscription = new EntitySubscription();
    protected BaseEntitySystem(Aspect aspect) { }
    protected EntitySubscription getSubscription() { return subscription; }
    protected void initialize() { }
    protected abstract void processSystem();
}
