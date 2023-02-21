package com.mcwb.util;

import javax.vecmath.Vector3f;

import com.mcwb.common.MCWB;
import com.mcwb.devtool.Dev;

public class Vec3f extends Vector3f implements IReleasable
{
	public static final Vec3f ORIGIN = new Vec3f();
	
	private static final ObjPool< Vec3f > POOL = new ObjPool<>( Vec3f::new );
	private static int count = Dev.refer;
	
	private static final long serialVersionUID = 8903442919042309432L;
	
	public static Vec3f locate()
	{
		if( ++count > 64 ) MCWB.MOD.error( "count vec over 64! could be something wrong!" );
		return POOL.poll();
	}
	
	public static Vec3f locate( float x, float y, float z )
	{
		if( ++count > 64 ) MCWB.MOD.error( "count vec over 64! could be something wrong!" );
		final Vec3f vec = POOL.poll();
		vec.set( x, y, z );
		return vec;
	}
	
	public Vec3f() { }
	
	public Vec3f( float x, float y, float z ) { super( x, y, z ); }
	
	public final void add( float x, float y, float z )
	{
		this.x += x;
		this.y += y;
		this.z += z;
	}
	
	public final void setZero() { this.x = this.y = this.z = 0F; }
	
	public final boolean nonZero() { return this.x != 0F || this.y != 0F || this.z != 0F; }
	
	public final void getEulerAngle( Vector3f dst )
	{
		final float pitch = ( float ) -Math.asin( this.y / this.length() );
		final float yaw = ( float ) Math.atan2( this.x, this.z );
		
		dst.y = yaw * Constants.TO_DEGREES;
		dst.x = pitch * Constants.TO_DEGREES;
	}
	
	@Override
	public final void release() { POOL.back( this ); --count; }
	
	public static Vec3f parse( String text )
	{
		final String[] split = text.split( "," );
		return new Vec3f(
			Float.parseFloat( split[ 0 ].substring( 1 ) ),
			Float.parseFloat( split[ 1 ] ),
			Float.parseFloat( split[ 2 ].substring( 0, split[ 2 ].length() - 1 ) )
		);
	}
}
