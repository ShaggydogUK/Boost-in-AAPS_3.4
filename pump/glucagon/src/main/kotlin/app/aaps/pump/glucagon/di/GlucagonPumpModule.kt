package app.aaps.pump.glucagon.di

import app.aaps.core.interfaces.pump.GlucagonPump
import app.aaps.pump.glucagon.VirtualGlucagonPump
import dagger.Binds
import dagger.Module

@Module
@Suppress("unused")
abstract class GlucagonPumpModule {

    @Module
    interface Bindings {
        @Binds fun bindGlucagonPump(plugin: VirtualGlucagonPump): GlucagonPump
    }
}
