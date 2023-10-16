package tencentlibfekit.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class GlobalShared {
    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(@NotNull Runnable r) {
            var t = new Thread(r, "Vivo45#" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    };
    public static final ScheduledExecutorService SINGLE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(THREAD_FACTORY);
    public static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE = Executors.newScheduledThreadPool(8, THREAD_FACTORY);

    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(SCHEDULED_EXECUTOR_SERVICE)
            .build();


    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(byte[].class, HexByteArraySerializer.INSTANCE)
            .create();

    public static ResourceExtract.ApkResult MOBILEQQ_DOWNLOAD_RESULT;
}
