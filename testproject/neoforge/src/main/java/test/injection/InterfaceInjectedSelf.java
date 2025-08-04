package test.injection;

/**
 * @author baka4n
 * @code @Date 2025/8/4 14:39:54
 */
@SuppressWarnings("UnusedReturnValue")
public interface InterfaceInjectedSelf<T extends InterfaceInjectedSelf<T>> {
    @SuppressWarnings("unchecked")
    default T selfBase() {
        return (T) this;
    }
}
