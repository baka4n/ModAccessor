package test.injection;

import mezz.jei.api.gui.placement.IPlaceable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.Internal;
import mezz.jei.common.JeiFeatures;
import mezz.jei.common.gui.elements.OffsetDrawable;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.library.gui.recipes.layout.builder.RecipeSlotBuilder;
import mezz.jei.library.ingredients.IngredientManager;
import mezz.jei.library.ingredients.RegisteredIngredients;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;

public class Stack {
    private static void foo() {
        var a = new JeiFeatures();
        a.helloWorld();
        OffsetDrawable stack = a.get();
        stack.selfBase();
        stack.this_();
        stack.self();
        Textures textures = Internal.textures;

    }
}
