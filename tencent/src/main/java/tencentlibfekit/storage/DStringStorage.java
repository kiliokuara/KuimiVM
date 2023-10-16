package tencentlibfekit.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class DStringStorage extends AbstractMap<String, String> {
    private static final ScheduledExecutorService AUTO_SAVE_SERVICE = Executors.newSingleThreadScheduledExecutor(t -> {
        var thread = new Thread(t, "DString Storage Save Daemon");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, String> delegate = new ConcurrentHashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Callable<OutputStream> saver;
    private volatile ScheduledFuture<?> saveTask;

    public DStringStorage(Callable<OutputStream> saver) {
        this.saver = saver;
    }

    public void saveNow(OutputStream out) {
        DataOutputStream dataOutputStream = new DataOutputStream(out);
        try {
            rwLock.writeLock().lock();
            try {
                for (Map.Entry<String, String> entry : delegate.entrySet()) {
                    dataOutputStream.writeBoolean(true);
                    dataOutputStream.writeUTF(entry.getKey());
                    dataOutputStream.writeUTF(entry.getValue());
                }

                dataOutputStream.writeBoolean(false);
            } finally {
                rwLock.writeLock().unlock();
            }

            dataOutputStream.flush();
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    public void load(InputStream inputStream) {
        DataInputStream dis = new DataInputStream(inputStream);
        rwLock.writeLock().lock();
        try {

            while (true) {
                var next = dis.readBoolean();
                if (!next) break;

                delegate.put(dis.readUTF(), dis.readUTF());
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void saveNow() {
        try (var out = saver.call()) {
            saveNow(out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void onModified() {
        var prevSaveTask = saveTask;
        saveTask = null;
        if (prevSaveTask != null) {
            prevSaveTask.cancel(false);
        }

        saveTask = AUTO_SAVE_SERVICE.schedule((Runnable) this::saveNow, 10, TimeUnit.SECONDS);
    }

    @Override
    public String put(String key, String value) {
        rwLock.readLock().lock();
        try {
            return delegate.put(key, value);
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }

    @Override
    public String remove(Object key) {
        rwLock.readLock().lock();
        try {
            return delegate.remove(key);
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ? extends String> m) {
        rwLock.readLock().lock();
        try {
            delegate.putAll(m);
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }

    @Override
    public void clear() {
        rwLock.readLock().lock();
        try {
            delegate.clear();
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }


    @Override
    public void replaceAll(BiFunction<? super String, ? super String, ? extends String> function) {
        rwLock.readLock().lock();
        try {
            delegate.replaceAll(function);
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }

    @Nullable
    @Override
    public String putIfAbsent(String key, String value) {
        rwLock.readLock().lock();
        try {
            return delegate.putIfAbsent(key, value);
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        rwLock.readLock().lock();
        try {
            return delegate.remove(key, value);
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }

    @Override
    public boolean replace(String key, String oldValue, String newValue) {
        rwLock.readLock().lock();
        try {
            return delegate.replace(key, oldValue, newValue);
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }

    @Nullable
    @Override
    public String replace(String key, String value) {
        rwLock.readLock().lock();
        try {
            return delegate.replace(key, value);
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }

    @Override
    public String computeIfAbsent(String key, @NotNull Function<? super String, ? extends String> mappingFunction) {
        rwLock.readLock().lock();
        try {
            return delegate.computeIfAbsent(key, mappingFunction);
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }

    @Override
    public String computeIfPresent(String key, @NotNull BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        rwLock.readLock().lock();
        try {
            return delegate.computeIfPresent(key, remappingFunction);
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }

    @Override
    public String compute(String key, @NotNull BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        rwLock.readLock().lock();
        try {
            return delegate.compute(key, remappingFunction);
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }

    @Override
    public String merge(String key, @NotNull String value, @NotNull BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        rwLock.readLock().lock();
        try {
            return delegate.merge(key, value, remappingFunction);
        } finally {
            rwLock.readLock().unlock();
            onModified();
        }
    }


    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public String get(Object key) {
        return delegate.get(key);
    }

    @NotNull
    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(delegate.keySet());
    }

    @NotNull
    @Override
    public Collection<String> values() {
        return Collections.unmodifiableCollection(delegate.values());
    }

    @NotNull
    @Override
    public Set<Entry<String, String>> entrySet() {
        return Collections.unmodifiableSet(delegate.entrySet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (o == delegate) return true;
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String getOrDefault(Object key, String defaultValue) {
        return delegate.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super String> action) {
        delegate.forEach(action);
    }

}
