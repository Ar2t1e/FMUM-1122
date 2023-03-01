package com.mcwb.client.module;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// TODO: maybe only leave one deferred renderer
@FunctionalInterface
public interface IDeferredPriorityRenderer extends IDeferredRenderer
{
	@SideOnly( Side.CLIENT )
	public default void prepare() { }
	
	/**
	 * Called before first person render to determine the order of deferred render
	 */
	@SideOnly( Side.CLIENT )
	public default float priority() { return 0F; }
	
//	@Override
//	@SideOnly( Side.CLIENT )
//	public default int compareTo( IDeferredPriorityRenderer o ) {
//		return this.priority() > o.priority() ? -1 : 1;
//	}
}
