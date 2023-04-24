package com.mcwb.common.tab;

import com.mcwb.common.item.IItemType;
import com.mcwb.common.meta.IMeta;
import com.mcwb.common.meta.Registry;

import net.minecraft.creativetab.CreativeTabs;

public interface ICreativeTab extends IMeta
{
	static final Registry< ICreativeTab > REGISTRY = new Registry<>();
	
	CreativeTabs creativeTab();
	
	/**
	 * Called when an item requires to settle in this tab. Can be used to implement classical Flan's
	 * Mod creative tab which its icon changes among the items in it through time.
	 * 
	 * @param item Item that settled in.
	 */
	default void itemSettledIn( IItemType item ) { }
}
