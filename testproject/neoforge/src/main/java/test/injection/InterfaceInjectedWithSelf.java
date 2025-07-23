package test.injection;

import test.injection.api.TestB;

/**
 * @author baka4n
 * @code @Date 2025/7/22 12:03:12
 */
public interface InterfaceInjectedWithSelf<F, S, E extends TestB<F, S>> {
    F inject();
    S inject1();
    E self();
}
