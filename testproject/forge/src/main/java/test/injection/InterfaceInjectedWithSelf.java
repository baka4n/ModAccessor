package test.injection;

import mezz.jei.api.gui.placement.IPlaceable;

/**
 * @author baka4n
 * @code @Date 2025/7/22 12:03:12
 */
@SuppressWarnings("UnusedReturnValue")
public interface InterfaceInjectedWithSelf<THIS extends IPlaceable<THIS>, SELF extends InterfaceInjectedSelf<SELF>> extends InterfaceInjectedSelf<SELF> {
    default THIS this_() {
        //noinspection unchecked
        return (THIS) this;
    }
    default SELF self() {
        return selfBase();
    }
}
