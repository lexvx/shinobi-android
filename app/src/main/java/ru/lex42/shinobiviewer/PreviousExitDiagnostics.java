package ru.lex42.shinobiviewer;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** OS exit metadata only: deliberately omit description and trace streams, which may contain URLs. */
public final class PreviousExitDiagnostics {
    private PreviousExitDiagnostics() {}
    public static String read(Context context) {
        StringBuilder out=new StringBuilder("Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT);
        if(Build.VERSION.SDK_INT<30)return out.append("\nИстория завершений доступна с Android 11.").toString();
        try {
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager==null)return out.append("\nИстория завершений недоступна.").toString();
            List<ApplicationExitInfo> exits=manager.getHistoricalProcessExitReasons(context.getPackageName(),0,5);
            if(exits.isEmpty())return out.append("\nAndroid не сохранил предыдущие завершения процесса.").toString();
            SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT);
            out.append("\nПоследние завершения по данным Android (могут относиться к старой версии):");
            for(ApplicationExitInfo exit:exits){
                out.append("\n").append(date.format(new Date(exit.getTimestamp())))
                    .append(" · ").append(reason(exit.getReason())).append(" · status=").append(exit.getStatus())
                    .append(" · PSS=").append(exit.getPss()).append(" KB · RSS=").append(exit.getRss()).append(" KB");
            }
        }catch(RuntimeException unavailable){out.append("\nНе удалось прочитать историю завершений Android.");}
        return out.toString();
    }
    static String reason(int reason){
        switch(reason){
            case ApplicationExitInfo.REASON_ANR:return "ANR — приложение не отвечало";
            case ApplicationExitInfo.REASON_CRASH:return "сбой Java";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:return "сбой нативного кода";
            case ApplicationExitInfo.REASON_LOW_MEMORY:return "нехватка памяти";
            case ApplicationExitInfo.REASON_SIGNALED:return "завершение сигналом";
            case ApplicationExitInfo.REASON_EXIT_SELF:return "выход приложения";
            case ApplicationExitInfo.REASON_USER_REQUESTED:return "остановлено пользователем или системой";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:return "ошибка запуска";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:return "превышение ресурсов";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED:return "завершилась зависимость";
            default:return "причина Android #"+reason;
        }
    }
}
