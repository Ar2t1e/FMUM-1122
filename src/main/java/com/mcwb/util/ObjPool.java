package com.mcwb.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A simple pool to buffer instances like {@link Vec3f}
 *
 * @param <T> Class of buffered instance
 * 
 * @author Giant_Salted_Fish
 */
public final class ObjPool< T >
{
	private final List< T > pool;
	private final Supplier< T > factory;
	private final BiConsumer< T, List< T > > recycler;
	
	/**
	 * In default it uses a synchronized {@link ArrayList} as pool which guarantee the thread safe
	 * 
	 * @param factory Instance factory which provides instance when there is none left in pool
	 */
	public ObjPool( Supplier< T > factory ) {
		this( factory, ( instance, pool ) -> { if( pool.size() < 64 ) pool.add( instance ); } );
	}
	
	public ObjPool( Supplier< T > factory, BiConsumer< T, List< T > > recycler ) {
		this( Collections.synchronizedList( new ArrayList<>() ), factory, recycler );
	}
	
	public ObjPool( List< T > pool, Supplier< T > factory, BiConsumer< T, List< T > > recycler )
	{
		this.pool = pool;
		this.factory = factory;
		this.recycler = recycler;
	}
	
	public T poll()
	{
		return (
			this.pool.size() > 0
			? this.pool.remove( this.pool.size() - 1 )
			: this.factory.get()
		);
	}
	
	public void back( T instance ) { this.recycler.accept( instance, this.pool ); }
}