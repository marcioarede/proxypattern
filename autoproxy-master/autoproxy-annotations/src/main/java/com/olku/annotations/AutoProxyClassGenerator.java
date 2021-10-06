package com.olku.annotations;

import androidx.annotation.NonNull;

import java.util.List;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;


public interface AutoProxyClassGenerator {

    boolean compose(@NonNull final Filer filer);


    @NonNull
    String getErrors();


    @NonNull
    String getName();


    @NonNull
    List<Element> getOriginating();
}
