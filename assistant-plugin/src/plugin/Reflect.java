package plugin;

import java.lang.reflect.*;
import java.util.*;

/** Compatibility layer: resolves host classes with the application's class loader. */
final class Reflect {
    static ClassLoader loader;
    static Class<?> type(String name) throws Exception { return Class.forName(name, true, loader); }
    static boolean matches(Class<?>[] types, Object[] args) {
        if (types.length != args.length) return false;
        for (int i=0;i<types.length;i++) {
            Class<?> t=types[i]; Object a=args[i];
            if (a==null) { if(t.isPrimitive()) return false; continue; }
            if(t==int.class)t=Integer.class; else if(t==double.class)t=Double.class;
            else if(t==float.class)t=Float.class; else if(t==boolean.class)t=Boolean.class;
            else if(t==long.class)t=Long.class;
            if(!t.isInstance(a))return false;
        }
        return true;
    }
    static Object call(Object obj,String name,Object... args) throws Exception {
        Class<?> cls=obj instanceof Class?(Class<?>)obj:obj.getClass();
        for(Method m:cls.getMethods())if(m.getName().equals(name)&&matches(m.getParameterTypes(),args)) {
            try{m.setAccessible(true);return m.invoke(obj instanceof Class?null:obj,args);}
            catch(InvocationTargetException e){throw unwrap(e);}
        }
        throw new NoSuchMethodException(cls.getSimpleName()+"."+name);
    }
    static Object make(String name,Object... args) throws Exception {
        Class<?> cls=type(name);
        for(Constructor<?> c:cls.getConstructors())if(matches(c.getParameterTypes(),args)) {
            try{return c.newInstance(args);}catch(InvocationTargetException e){throw unwrap(e);}
        }
        throw new NoSuchMethodException(name+" constructor");
    }
    static Exception unwrap(InvocationTargetException e) {
        Throwable t=e.getCause();return t instanceof Exception?(Exception)t:new Exception(t);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    static Object enumeration(String name,String value) throws Exception {
        return Enum.valueOf((Class)type(name),value);
    }
    @SuppressWarnings("unchecked")
    static List<Object> list(Object obj,String getter) throws Exception {return (List<Object>)call(obj,getter);}
    static Object manager() throws Exception {return call(type("org.catrobat.catroid.ProjectManager"),"getInstance");}
    static Object serializer() throws Exception {return call(type("org.catrobat.catroid.io.XstreamSerializer"),"getInstance");}
    static String xml(Object project) throws Exception {return (String)call(serializer(),"getXmlAsStringFromProject",project);}
}
