package com.mcwb.common.gun;

import java.util.Collection;

import com.mcwb.client.module.IDeferredRenderer;
import com.mcwb.client.render.IAnimator;
import com.mcwb.common.item.IItem;
import com.mcwb.common.module.IModule;
import com.mcwb.common.paintjob.IPaintable;
import com.mcwb.util.ArmTracker;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface IGunPart< T extends IGunPart< ? extends T > >
	extends IItem, IModule< T >, IPaintable
{
	int leftHandPriority();
	
	int rightHandPriority();
	
	@SideOnly( Side.CLIENT )
	void prepareRenderInHandSP(
		IAnimator animator,
		Collection< IDeferredRenderer > renderQueue0,
		Collection< IDeferredRenderer > renderQueue1
	);
	
	// TODO: rename to track arm?
	@SideOnly( Side.CLIENT )
	void setupLeftArmToRender( IAnimator animator, ArmTracker leftArm );
	
	@SideOnly( Side.CLIENT )
	void setupRightArmToRender( IAnimator animator, ArmTracker rightArm );
}
