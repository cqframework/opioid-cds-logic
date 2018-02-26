package org.opencds.cqf.opioidcds;

public class BaseBuilder<T> {

    protected T complexProperty;

    public BaseBuilder(T complexProperty) {
        this.complexProperty = complexProperty;
    }

    public T build() {
        return complexProperty;
    }
}