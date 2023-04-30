package com.mcwb.common.load;

import com.google.gson.annotations.SerializedName;
import com.mcwb.common.meta.IMeta;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class TexturedMeta extends BuildableMeta
{
	@SideOnly( Side.CLIENT )
	@SerializedName( value = "texture", alternate = "skin" )
	protected ResourceLocation texture;
	
	@Override
	public IMeta build( String name, IContentProvider provider )
	{
		super.build( name, provider );
		
		provider.clientOnly( () -> this.checkTextureSetup() );
//		provider.clientOnly( this::checkTextureSetup ); // Write like this will crash.
		return this;
	}
	
	/**
	 * Called in {@link #build(String, IContentProvider)} to ensure that texture setup.
	 */
	@SideOnly( Side.CLIENT )
	protected void checkTextureSetup()
	{
		if ( this.texture == null ) {
			this.texture = this.provider.loadTexture( "textures/" + this.name + ".png" );
		}
	}
}
