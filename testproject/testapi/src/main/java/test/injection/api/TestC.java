package test.injection.api;

import java.util.function.Supplier;

/**
 * @author baka4n
 * @code @Date 2025/7/23 09:35:33
 */
public class TestC<T extends TestC<T>> implements Supplier<T> {

    @Override
    public T get() {
        return (T) this;
    }
}
