package me.nagasonic.alkatraz.dom;

import me.nagasonic.alkatraz.Alkatraz;

public class Catch {
    public static <T> T catchOrElse(Fetcher<T> c, T r){
        return catchOrElse(c, r, null);
    }

    public static <T> T catchOrElse(Fetcher<T> c, T r, String log){
        try {
            return c.get();
        } catch (Exception e){
            if (log != null) Alkatraz.logWarning(log);
            return r;
        }
    }
    public static <T> void catchOrLog(Fetcher<T> c){
        catchOrElse(c, null);
    }

    public static <T> void catchOrLog(Fetcher<T> c, String log){
        try {
            c.get();
        } catch (Exception e){
            if (log != null) Alkatraz.logWarning(log);
        }
    }
}
