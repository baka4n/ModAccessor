package test.injection;

import mezz.jei.common.Internal;
import mezz.jei.common.JeiFeatures;
import mezz.jei.common.gui.textures.Textures;

public class Stack {
    private static void foo() {
        var a = new JeiFeatures();
        a.helloWorld();
        Stack stack = a.get();
        Textures textures = Internal.textures;

    }
}
