package com.mcwb.util;

import java.util.Random;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagList;

public abstract class Util
{
	public static final float PI = ( float ) Math.PI;
	
	public static final float
		TO_DEGREES = 180F / PI,
		TO_RADIANS = PI / 180F;
	
	public static final float PRIMARY_SCALE = 1F / 16F;
	
	public static final Vec3f[] EMPTY_VEC_ARRAY = { };
	
	public static final Random RAND = new Random();
	
	public static final Pattern
		INTEGER_MATCHER = Pattern.compile( "-?[0-9]+" ),
		REAL_NUMBER_MATCHER = Pattern.compile( "-?[0-9]+\\.?[0-9]*" );
	
	private Util() { }
	
	public static < T extends NBTBase >void streamTagList(
		NBTTagList list,
		Class< T > type,
		Consumer< T > visitor
	) {
		for(
			int i = 0, size = list.tagCount();
			i < size;
			visitor.accept( type.cast( list.get( i++ ) ) )
		);
	}
	
	public static boolean intersectionOfLineAndPlane(
		Vec3f lineOrigin, Vec3f lineDirection,
		Vec3f planeOrigin, Vec3f planeNormal,
		Vec3f dst
	) {
		// Avoid zero divisor
		float var = lineDirection.dot( planeNormal );
		if( var == 0F ) return false;
		
		dst.set( planeOrigin );
		dst.subtract( lineOrigin );
		var = dst.dot( planeNormal ) / var;
		
		dst.set( lineDirection );
		dst.scale( var );
		dst.translate( lineOrigin );
		return true;
	}
	
	// TODO: remove this maybe
	/**
	 * Get intersection of the given line and plane
	 * 
	 * @param a1 X-position of the line 
	 * @param b1 Y-position of the line
	 * @param c1 Z-position of the line
	 * @param A1 X-direction of the line
	 * @param B1 Y-direction of the line
	 * @param C1 Z-direction of the line
	 * @param a2 X-position of the plane
	 * @param b2 Y-position of the plane
	 * @param c2 Z-position of the plane
	 * @param A2 X-normal of the plane
	 * @param B2 Y-normal of the plane
	 * @param C2 Z-normal of the plane
	 * @param dst Intersection will be saved into this vector
	 */
	public static boolean getLinePlaneIntersection(
		float a1, float b1, float c1,
		float A1, float B1, float C1, 
		float a2, float b2, float c2,
		float A2, float B2, float C2,
		Vec3f dst
	) {
		float var = A1 * A2 + B1 * B2 + C1 * C2;
		if( var == 0F ) return false;
		
		var = ( ( a2 - a1 ) * A2 + ( b2 - b1 ) * B2 + ( c2 - c1 ) * C2 ) / var;
		dst.set(
			a1 + A1 * var,
			b1 + B1 * var,
			c1 + C1 * var
		);
		return true;
	}
	
	public static boolean inBoxSpace( Vec3f v0, Vec3f v1, Vec3f point )
	{
		return(
			( point.x >= v0.x ? point.x <= v1.x : point.x >= v1.x )
			&& ( point.y >= v0.y ? point.y <= v1.y : point.y >= v1.y )
			&& ( point.z >= v0.z ? point.z <= v1.z : point.z >= v1.z )
		);
	}
}
