package tencentlibfekit;

public interface ConsumerCatch<T> {
    public void consume(T value) throws Exception;
}
