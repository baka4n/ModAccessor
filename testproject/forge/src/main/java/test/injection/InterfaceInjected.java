package test.injection;

public interface InterfaceInjected {

    default void helloWorld() {
        System.out.println("Hello World");
    }

}
