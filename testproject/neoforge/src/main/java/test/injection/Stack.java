package test.injection;

import com.mojang.serialization.Codec;
import mezz.jei.common.Internal;
import mezz.jei.common.JeiFeatures;
import mezz.jei.common.codecs.TupleCodec;
import mezz.jei.common.gui.textures.Textures;
import test.injection.api.TestB;

public class Stack {
    private static void foo() {
        var a = new JeiFeatures();
        a.helloWorld();
        Stack stack = a.get();
        Textures textures = Internal.textures;
        TupleCodec.of(Codec.BOOL, Codec.INT).inject();
        TestB<Object, Object> objectObjectTestB = new TestB<>();
    }
}
