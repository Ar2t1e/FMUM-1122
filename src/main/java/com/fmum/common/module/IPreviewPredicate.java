package com.fmum.common.module;

import com.fmum.client.FMUMClient;

import net.minecraftforge.fml.relauncher.Side;

import net.minecraftforge.fml.relauncher.SideOnly;

@FunctionalInterface
public interface IPreviewPredicate extends IModifyPredicate
{
	static final IPreviewPredicate NO_PREVIEW = new IPreviewPredicate()
	{
		@Override
		public int index() { return -1; }
	};
	
	int index();
	
	@FunctionalInterface
	public interface NotOk extends IPreviewPredicate
	{
		@Override
		default boolean ok() { return false; }
		
		@Override
		default int index() { return -1; }
		
		@Override
		@SideOnly( Side.CLIENT )
		default boolean okOrNotifyWhy()
		{
			FMUMClient.MOD.sendPlayerMsg( this.why() );
			return false;
		}
		
		@SideOnly( Side.CLIENT )
		String why();
	}
}
