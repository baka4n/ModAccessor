package test.injection;

import mezz.jei.common.Internal;
import mezz.jei.common.JeiFeatures;
import mezz.jei.common.gui.elements.OffsetDrawable;
import mezz.jei.common.gui.textures.Textures;

public class Stack {
    @SuppressWarnings("DataFlowIssue")
    private static void foo() {
        JeiFeatures a = new JeiFeatures();
        a.helloWorld();
        OffsetDrawable stack = a.get();
        stack.selfBase();
        stack.self();
        stack.this_();
        Textures textures = Internal.textures;

    }
}
