package com.mcwb.client.gun;

import java.util.Collection;
import java.util.function.Function;

import org.lwjgl.opengl.GL11;

import com.google.gson.annotations.SerializedName;
import com.mcwb.client.module.IDeferredPriorityRenderer;
import com.mcwb.client.module.IDeferredRenderer;
import com.mcwb.client.render.IAnimator;
import com.mcwb.client.render.IRenderer;
import com.mcwb.common.MCWB;
import com.mcwb.common.gun.IGunPart;
import com.mcwb.common.load.BuildableLoader;
import com.mcwb.common.load.IContentProvider;
import com.mcwb.util.Mat4f;
import com.mcwb.util.Mesh;
import com.mcwb.util.Vec3f;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly( Side.CLIENT )
public class OpticSightRenderer< T extends IGunPart< ? > > extends GunPartRenderer< T >
{
	public static final BuildableLoader< IRenderer >
		LOADER = new BuildableLoader<>(
			"optic_sight",
			json -> MCWB.GSON.fromJson( json, OpticSightRenderer.class )
		);
	
	@SerializedName( value = "lenMeshPath", alternate = "lenMesh" )
	protected String lenMeshPath = "";
	
	protected transient Mesh lenMesh;
	
	@SerializedName( value = "reticleMesh" )
	protected String reticleMeshPath = "";
	
	protected transient Mesh reticleMesh;
	
	protected Vec3f reticlePos = Vec3f.ORIGIN;
	protected float reticleScale = 1F;
	protected float firstPersonOcclusionFactor = 1F;
	
	@Override
	public IRenderer build( String path, IContentProvider provider )
	{
		super.build( path, provider );
		
		this.reticlePos.scale( this.scale );
		return this;
	}
	
	@Override
	protected void onMeshLoad( IContentProvider provider )
	{
		super.onMeshLoad( provider );
		
		this.lenMesh = this.loadMesh( this.lenMeshPath, provider );
		this.reticleMesh = this.loadMesh( this.reticleMeshPath, provider );
	}
	
	@Override
	public void prepareInHandRender(
		T contexted, IAnimator animator,
		Collection< IDeferredRenderer > renderQueue0,
		Collection< IDeferredPriorityRenderer > renderQueue1
	) {
		this.doPrepareRender(
			contexted,
			animator,
			renderQueue0,
			renderQueue1,
			mat -> {
				final Vec3f vec = Vec3f.locate();
				vec.setZero();
				mat.transformAsPoint( vec );
				final float length = vec.length();
				vec.release();
				
				return ( float ) -Math.expm1( this.firstPersonOcclusionFactor * -length );
			}
		);
	}
	
	@Override
	public void prepareRender(
		T contexted,
		IAnimator animator,
		Collection< IDeferredRenderer > renderQueue0,
		Collection< IDeferredPriorityRenderer > renderQueue1
	) {
		this.doPrepareRender(
			contexted,
			animator,
			renderQueue0,
			renderQueue1,
			mat -> 1F
		);
	}
	
	protected void doPrepareRender(
		T contexted,
		IAnimator animator,
		Collection< IDeferredRenderer > renderQueue0,
		Collection< IDeferredPriorityRenderer > renderQueue1,
		Function< Mat4f, Float > occlusionFactor
	) {
		// Sight body still uses the original way to render
		super.prepareRender( contexted, animator, renderQueue0, renderQueue1 );
		
		// For the lens and reticle
		renderQueue1.add( new IDeferredPriorityRenderer() {
			private final Mat4f mat = Mat4f.locate(); {
				IAnimator.getChannel( animator, CHANNEL_MODULE, this.mat );
			}
			
			@Override
			public void render()
			{
				GL11.glPushMatrix();
				glMultMatrix( this.mat );
				
				final float occlusionFactor0 = occlusionFactor.apply( this.mat );
				contexted.modifyState().doRecommendedRender( contexted.texture(), () -> {
					GL11.glEnable( GL11.GL_STENCIL_TEST );
					
					// Mark the area of the lens
					GL11.glClear( GL11.GL_STENCIL_BUFFER_BIT );
					GL11.glStencilFunc( GL11.GL_ALWAYS, 1, 0xFF );
					GL11.glStencilOp( GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE );
					
					OpticSightRenderer.this.renderColoredGlass(
						OpticSightRenderer.this.lenMesh,
						occlusionFactor0
					);
					
					// Render reticle on lens
					// TODO: also blend for reticle
					GL11.glStencilFunc( GL11.GL_EQUAL, 1, 0xFF );
					GL11.glStencilOp( GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP );
					
					// Render reticle with max lightness
					GL11.glDisable( GL11.GL_DEPTH_TEST );
					RenderHelper.disableStandardItemLighting();
					glowOn();
					glTranslatef( OpticSightRenderer.this.reticlePos );
					glScalef( OpticSightRenderer.this.reticleScale );
					OpticSightRenderer.this.reticleMesh.render();
					glowOff();
					RenderHelper.enableStandardItemLighting();
					GL11.glEnable( GL11.GL_DEPTH_TEST );
					
					GL11.glDisable( GL11.GL_STENCIL_TEST );
				} );
				GL11.glPopMatrix();
			}
			
			// Not avoided for non-first-person rendering as sort will not be applied in that case
			@Override
			public float priority()
			{
				final Vec3f vec = Vec3f.locate();
				vec.setZero();
				this.mat.transformAsPoint( vec );
				final float distanceSquared = vec.lengthSquared();
				vec.release();
				
				return distanceSquared;
			}
			
			@Override
			public void release() { this.mat.release(); }
		} );
	}
	
	/**
	 * This will enable and disable the {@link GL11#GL_BLEND} on called
	 * 
	 * @param reflectFactor Reflection contribution of the glass will be multiplied by this factor
	 */
	protected final void renderColoredGlass( Mesh coloredClass, float reflectFactor )
	{
		// Get transmission light color
		GL11.glEnable( GL11.GL_BLEND );
		GL11.glBlendFunc( GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR );
		RenderHelper.disableStandardItemLighting();
		glowOn();
		this.lenMesh.render();
		glowOff();
		RenderHelper.enableStandardItemLighting();
		
		// Blend with reflection light color
		GL11.glBlendFunc( GL11.GL_ONE, GL11.GL_ONE );
		GL11.glColor3f( reflectFactor, reflectFactor, reflectFactor );
		this.lenMesh.render();
		GL11.glColor3f( 1F, 1F, 1F );
		
		// Do not forget to restore blend function
		GL11.glBlendFunc( GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA );
		GL11.glDisable( GL11.GL_BLEND );
	}
}
