package com.aws.greengrass.cli.module;

import com.aws.greengrass.cli.adapter.KernelAdapterIpc;
import com.aws.greengrass.cli.adapter.impl.KernelAdapterIpcClientImpl;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public class AdapterModule {
    private final String ggcRootPath;

    public AdapterModule(final String ggcRootPath) {
        this.ggcRootPath = ggcRootPath;
    }

    @Provides
    @Singleton
    protected KernelAdapterIpc providesKernelAdapter() {
        return new KernelAdapterIpcClientImpl(ggcRootPath);
    }
}
