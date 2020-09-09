package com.aws.iot.evergreen.cli.adapter;

import com.aws.iot.evergreen.cli.adapter.impl.KernelAdapterHttpClientImpl;
import com.aws.iot.evergreen.cli.adapter.impl.KernelAdapterIpcClientImpl;
import com.google.inject.AbstractModule;

public class AdapterModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(KernelAdapter.class).to(KernelAdapterHttpClientImpl.class);
        bind(KernelAdapterIpc.class).to(KernelAdapterIpcClientImpl.class);
    }
}
