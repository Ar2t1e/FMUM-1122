package com.mcwb.common.ammo;

import javax.annotation.Nullable;

import com.mcwb.client.ammo.IAmmoRenderer;
import com.mcwb.common.item.IItem;
import com.mcwb.common.item.ItemType;
import com.mcwb.common.load.BuildableLoader;
import com.mcwb.common.meta.IContexted.ContextedWrapper;
import com.mcwb.common.meta.IMeta;
import com.mcwb.common.pack.IContentProvider;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class AmmoType extends ItemType< IItem, IAmmoRenderer< ? super IAmmoType, ? super IItem > >
	implements IAmmoType
{
	public static final BuildableLoader< IMeta >
		LOADER = new BuildableLoader<>( "ammo", AmmoType.class );
	
	protected String category;
	
	protected boolean isCase = false;
	
	protected int maxStackSize = 60;
	
	@Override
	public IMeta build( String name, IContentProvider provider )
	{
		super.build( name, provider );
		
		IAmmoType.REGISTRY.regis( this );
		
		if( this.category == null )
			this.category = this.name;
		return this;
	}
	
	@Override
	public String category() { return this.category; }
	
	@Override
	public boolean isCase() { return this.isCase; }
	
	@Override
	@SideOnly( Side.CLIENT )
	public void render() { this.renderer.render( this ); }
	
	@Override
	@SideOnly( Side.CLIENT )
	public ResourceLocation texture() { return this.texture; }
	
	@Override
	protected IMeta loader() { return LOADER; }
	
	@Override
	protected Item createItem()
	{
		return this.new VanillaItem( this.maxStackSize, 0 )
		{
			@Override
			public ICapabilityProvider initCapabilities(
				ItemStack stack,
				@Nullable NBTTagCompound capTag
			) {
				return new ContextedWrapper( new IItem()
				{
					@Override
					public IMeta meta() { return AmmoType.this; }
					
					@Override
					@SideOnly( Side.CLIENT )
					public boolean renderInHand( EnumHand hand ) {
						return AmmoType.this.renderer.renderInHand( this, hand );
					}
					
					@Override
					@SideOnly( Side.CLIENT )
					public boolean onRenderSpecificHand( EnumHand hand ) {
						return AmmoType.this.renderer.onRenderSpecificHand( this, hand );
					}
					
					@Override
					@SideOnly( Side.CLIENT )
					public ResourceLocation texture() { return AmmoType.this.texture; }
				} );
			}
		};
	}
}
