package com.fmum.client.gun;

import com.fmum.common.gun.IEquippedMag;
import com.fmum.common.gun.IMag;
import com.fmum.common.load.BuildableLoader;

import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly( Side.CLIENT )
public class JsonMagModel extends MagModel<
	IMag< ? >,
	IEquippedMag< ? extends IMag< ? > >,
	IEquippedGunPartRenderer< ? super IEquippedMag< ? extends IMag< ? > > >,
	IGunPartRenderer<
		? super IMag< ? >,
		? extends IEquippedGunPartRenderer< ? super IEquippedMag< ? extends IMag< ? > > >
	>
> {
	public static final BuildableLoader< ? >
		LOADER = new BuildableLoader<>( "mag", JsonMagModel.class );
	
	@Override
	public IGunPartRenderer<
		? super IMag< ? >,
		? extends IEquippedGunPartRenderer< ? super IEquippedMag< ? extends IMag< ? > > >
	> newRenderer() {
		return new MagRenderer()
		{
			@Override
			public IEquippedGunPartRenderer< ? super IEquippedMag< ? extends IMag< ? > > >
				onTakeOut( EnumHand hand ) { return new EquippedGunPartRenderer(); }
		};
	}
}
