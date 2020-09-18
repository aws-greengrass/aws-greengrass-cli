package com.aws.greengrass.cli.adapter;

import com.aws.greengrass.cli.adapter.impl.KernelAdapterIpcClientImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;

public class AdapterModule extends AbstractModule {

    private String ggcRootPath;

    public AdapterModule(String ggcRootPath) {
        this.ggcRootPath = ggcRootPath;
    }

    @Override
    protected void configure() {
        bind(KernelAdapterIpc.class).to(KernelAdapterIpcClientImpl.class);
    }

    @Provides
    @Named("ggcRootPath")
    public String getGgcRootPath() {
        return ggcRootPath;
    }
}
