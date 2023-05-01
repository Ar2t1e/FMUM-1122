package com.fmum.client.gun;

import com.fmum.common.gun.IGunPart;
import com.fmum.common.item.IEquippedItem;
import com.fmum.common.load.BuildableLoader;

import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly( Side.CLIENT )
public class JsonCarGripModel extends CarGripModel<
	IGunPart< ? >,
	IEquippedItem< ? extends IGunPart< ? > >,
	IEquippedGunPartRenderer< ? super IEquippedItem< ? extends IGunPart< ? > > >,
	IGunPartRenderer<
		? super IGunPart< ? >,
		? extends IEquippedGunPartRenderer< ? super IEquippedItem< ? extends IGunPart< ? > > >
	>
> {
	public static final BuildableLoader< ? >
		LOADER = new BuildableLoader<>( "car_grip", JsonCarGripModel.class );
	
	@Override
	public IGunPartRenderer<
		? super IGunPart< ? >,
		? extends IEquippedGunPartRenderer< ? super IEquippedItem< ? extends IGunPart< ? > > >
	> newRenderer()
	{
		return this.new CarGripRenderer()
		{
			@Override
			public IEquippedGunPartRenderer< ? super IEquippedItem< ? extends IGunPart< ? > > >
				onTakeOut( EnumHand hand ) { return this.new EquippedGunPartRenderer(); }
		};
	}
}
