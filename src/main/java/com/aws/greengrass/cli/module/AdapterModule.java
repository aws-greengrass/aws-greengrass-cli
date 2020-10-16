package com.aws.greengrass.cli.module;

import com.aws.greengrass.cli.adapter.NucleusAdapterIpc;
import com.aws.greengrass.cli.adapter.impl.NucleusAdapterIpcClientImpl;
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
    protected NucleusAdapterIpc providesAdapter() {
        return new NucleusAdapterIpcClientImpl(ggcRootPath);
    }
}
