package com.mcwb.common.item;

import com.mcwb.common.meta.IMeta;
import com.mcwb.common.meta.Registry;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @see IItem
 * @author Giant_Salted_Fish
 */
public interface IItemType extends IMeta
{
	public static final Registry< IItemType > REGISTRY = new Registry<>();
	
	public static final IItemType VANILLA = new IItemType()
	{
		@Override
		public String name() { return "vanilla"; }
		
		@Override
		public Item item() { return Items.AIR; }
		
		@Override
		public IItem getContexted( ItemStack stack ) { return IItem.VANILLA; }
	};
	
	// TODO: for paintjob name override?
//	public static final String
//		TRANSLATION_PREFIX = "item.",
//		TRANSLATION_SUFFIX = ".name";
	
	/**
	 * @return Corresponding vanilla item
	 */
	public Item item();
	
	public IItem getContexted( ItemStack stack );
	
	public default void onRegisterItem( RegistryEvent.Register< Item > evt ) {
		evt.getRegistry().register( this.item() );
	}
	
	// FIXME: model register need to be override if item has sub types
	@SideOnly( Side.CLIENT )
	public default void onModelRegister( ModelRegistryEvent evt )
	{
		final Item item = this.item();
		final ResourceLocation location = item.getRegistryName();
		final ModelResourceLocation modelRes = new ModelResourceLocation( location, "inventory" );
		ModelLoader.setCustomModelResourceLocation( item, 0, modelRes );
	}
}
