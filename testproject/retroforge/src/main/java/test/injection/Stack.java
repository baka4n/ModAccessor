package test.injection;


import mezz.jei.Internal;
import mezz.jei.collect.Table;
import mezz.jei.startup.StackHelper;

public class Stack {
    private static void foo() {
        Table<Object, Object, Object> a = Table.hashBasedTable();
        a.helloWorld();
        Stack stack = a.get();
        StackHelper stackHelper = Internal.stackHelper;
    }
}
