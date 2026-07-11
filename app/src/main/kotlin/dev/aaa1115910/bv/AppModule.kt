package dev.aaa1115910.bv

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/** Kept separate from [BVApp] so KSP can generate the module before its extension is referenced. */
@Module
@ComponentScan
class AppModule
