package com.example.messaging.operator.conduktor.transformer;

import com.example.messaging.operator.conduktor.model.ConduktorResource;

/**
 * Transforms internal CRDs to Conduktor CRD format.
 *
 * @param <S> Source CRD type
 * @param <T> Target Conduktor resource type
 */
public interface CrdTransformer<S, T extends ConduktorResource<?>> {
    T transform(S source);
}
