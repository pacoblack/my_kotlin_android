package com.gang.video.http;

public interface Predicate<T> {

    /**
     * Evaluates an input.
     *
     * @param input The input to evaluate.
     * @return The evaluated result.
     */
    boolean evaluate(T input);

}
