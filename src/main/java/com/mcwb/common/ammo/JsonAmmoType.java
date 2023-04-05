package com.mcwb.common.ammo;

import com.mcwb.client.ammo.IAmmoModel;
import com.mcwb.client.ammo.JsonAmmoModel;
import com.mcwb.client.item.IEquippedItemRenderer;
import com.mcwb.client.item.IItemRenderer;
import com.mcwb.common.item.IEquippedItem;
import com.mcwb.common.item.IItem;
import com.mcwb.common.load.BuildableLoader;
import com.mcwb.common.meta.IMeta;

public class JsonAmmoType extends AmmoType<
	IItem,
	IAmmoModel<
		? super IAmmoType,
		? extends IItemRenderer<
			? super IItem,
			? extends IEquippedItemRenderer< ? super IEquippedItem< ? > >
		>
	>
> {
	public static final BuildableLoader< IMeta >
		LOADER = new BuildableLoader<>( "ammo", JsonAmmoType.class );
	
	@Override
	protected IAmmoModel<
		? super IAmmoType,
		? extends IItemRenderer<
			? super IItem,
			? extends IEquippedItemRenderer< ? super IEquippedItem< ? > >
		>
	> fallbackModel() { return JsonAmmoModel.NONE; }
	
	@Override
	protected IMeta typer() { return LOADER; }
}
