package com.mcwb.common.gun;

import javax.annotation.Nullable;

public interface IGun< T extends IGunPart< ? extends T > > extends IGunPart< T >
{
	public default boolean hasMag() { return this.getInstalledCount( 0 ) > 0; }
	
	@Nullable
	public default IMag< ? > mag() { 
		return this.hasMag() ? ( IMag< ? > ) this.getInstalled( 0, 0 ) : null;
	}
	
	public default boolean isAllowed( IMag< ? > mag ) { return this.getSlot( 0 ).isAllowed( mag ); }
	
	public default void loadMag( IMag< ? > mag ) { this.install( 0, mag ); }
	
	public default IMag< ? > unloadMag() { return ( IMag< ? > ) this.remove( 0, 0 ); }
}
