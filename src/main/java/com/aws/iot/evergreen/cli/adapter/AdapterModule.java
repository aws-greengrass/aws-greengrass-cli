package com.aws.iot.evergreen.cli.adapter;

import com.google.inject.AbstractModule;

import com.aws.iot.evergreen.cli.adapter.impl.KernelAdapterHttpClientImpl;

public class AdapterModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(KernelAdapter.class).to(KernelAdapterHttpClientImpl.class);
    }
}
